// 월간 리포트 요청 검증, Gemini 프롬프트, 응답 정리. Workers와 무관한 순수 함수라 node --test로 시험한다.
//
// 개인정보 (분석서 NFR-03): 앱은 월 집계(합계, 건수, 카테고리별 합계)만 보낸다.
// 매장 이름·주소·좌표·결제 시각은 받지 않는다. Gemini 무료 등급은 입력을 사람이 검토할 수 있어서다.

export const LIMITS = {
  maxBodyBytes: 4 * 1024,
  maxCategories: 10,
  maxCategoryName: 20,
  maxAmount: 100_000_000,
  maxCount: 10_000,
  titleMax: 12,
  summaryMax: 150,
};

export class BadRequest extends Error {}

const MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;

function amount(value, field, { nullable = false } = {}) {
  if (nullable && value === null) return null;
  if (!Number.isInteger(value) || value < 0 || value > LIMITS.maxAmount) {
    throw new BadRequest(`${field}는 0 이상 ${LIMITS.maxAmount} 이하 정수여야 합니다`);
  }
  return value;
}

function count(value, field) {
  if (!Number.isInteger(value) || value < 0 || value > LIMITS.maxCount) {
    throw new BadRequest(`${field}는 0 이상 ${LIMITS.maxCount} 이하 정수여야 합니다`);
  }
  return value;
}

/** 앱이 보낸 JSON을 검증하고, 알려진 필드만 남긴 새 객체를 돌려준다. */
export function parseReportRequest(body) {
  if (body === null || typeof body !== 'object' || Array.isArray(body)) {
    throw new BadRequest('본문은 JSON 객체여야 합니다');
  }
  if (typeof body.month !== 'string' || !MONTH.test(body.month)) {
    throw new BadRequest('month는 "2026-10" 형식이어야 합니다');
  }
  if (!Array.isArray(body.categories) || body.categories.length > LIMITS.maxCategories) {
    throw new BadRequest(`categories는 ${LIMITS.maxCategories}개 이하 배열이어야 합니다`);
  }
  const categories = body.categories.map((c, i) => {
    if (c === null || typeof c !== 'object') throw new BadRequest(`categories[${i}]가 객체가 아닙니다`);
    const name = typeof c.name === 'string' ? c.name.trim() : '';
    if (!name || name.length > LIMITS.maxCategoryName) {
      throw new BadRequest(`categories[${i}].name은 1~${LIMITS.maxCategoryName}자여야 합니다`);
    }
    return { name, total: amount(c.total, `categories[${i}].total`), count: count(c.count, `categories[${i}].count`) };
  });

  return {
    month: body.month,
    offlineTotal: amount(body.offlineTotal, 'offlineTotal'),
    onlineTotal: amount(body.onlineTotal, 'onlineTotal'),
    paymentCount: count(body.paymentCount, 'paymentCount'),
    categories,
    previousMonthTotal: amount(body.previousMonthTotal ?? null, 'previousMonthTotal', { nullable: true }),
  };
}

export const SYSTEM_PROMPT = [
  '너는 소비 기록 앱 "어디서돈썼지도"의 월간 리포트 작가다.',
  '사용자의 한 달 소비 집계를 받아 한국어로 소비 칭호와 짧은 요약을 쓴다.',
  `칭호: ${LIMITS.titleMax}자 이내, 재치 있고 다정하게, 소비 패턴이 드러나게 .`,
  `요약: 2~3문장, ${LIMITS.summaryMax}자 이내, 해요체. 숫자는 주어진 값(금액, 비율, 건수)만 그대로 쓰고 새로 계산하지 않는다.`,
  '지역·동네·매장 이름은 받지 않으므로 칭호와 요약에 쓰지 않는다. 칭호는 소비 성향만 담는다 (예: "카페 순례자", "편의점 야행자").',
  '비난하거나 훈계하지 않는다. 투자·대출·금융 상품을 권하지 않는다.',
  '집계에 없는 사실(장소, 사람, 이유)을 지어내지 않는다.',
].join('\n');

/**
 * 검증된 집계를 모델에 넘길 문장으로 만든다. 비율·증감은 여기서 계산해 넘긴다 (모델이 숫자를 지어내지 않게).
 * 사용자 입력은 카테고리 이름뿐이라 따옴표로 감싼다.
 */
export function buildUserPrompt(req) {
  const won = (n) => `${n.toLocaleString('ko-KR')}원`;
  const pct = (part, whole) => (whole > 0 ? `${Math.round((part / whole) * 100)}%` : '0%');
  const total = req.offlineTotal + req.onlineTotal;
  let change = '지난달 기록 없음';
  if (req.previousMonthTotal !== null && req.previousMonthTotal > 0) {
    const diff = Math.round(((total - req.previousMonthTotal) / req.previousMonthTotal) * 100);
    change = diff === 0 ? '지난달과 같음' : `지난달보다 ${Math.abs(diff)}% ${diff > 0 ? '많음' : '적음'}`;
  }
  const lines = [
    `대상 달: ${req.month}`,
    `전체 합계: ${won(total)} (${change})`,
    `오프라인 결제: ${won(req.offlineTotal)} (전체의 ${pct(req.offlineTotal, total)})`,
    `온라인 결제: ${won(req.onlineTotal)} (전체의 ${pct(req.onlineTotal, total)})`,
    `결제 건수: ${req.paymentCount}건`,
    '카테고리별 (합계, 오프라인 결제 중 비율, 건수):',
    ...(req.categories.length
      ? [...req.categories]
          .sort((a, b) => b.total - a.total)
          .map((c) => `- "${c.name}": ${won(c.total)}, ${pct(c.total, req.offlineTotal)}, ${c.count}건`)
      : ['- (지도에 표시된 결제 없음)']),
  ];
  return lines.join('\n');
}

/** Gemini generateContent 요청 본문. JSON 스키마로 칭호·요약 두 칸만 받는다. */
export function geminiRequestBody(req) {
  return {
    systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
    contents: [{ role: 'user', parts: [{ text: buildUserPrompt(req) }] }],
    generationConfig: {
      temperature: 0.8,
      maxOutputTokens: 1024,
      responseMimeType: 'application/json',
      responseSchema: {
        type: 'OBJECT',
        properties: { title: { type: 'STRING' }, summary: { type: 'STRING' } },
        required: ['title', 'summary'],
      },
    },
  };
}

function clip(text, max) {
  const t = String(text).replace(/\s+/g, ' ').trim();
  return t.length <= max ? t : `${t.slice(0, max - 1)}…`;
}

/** Gemini 응답에서 칭호와 요약을 꺼낸다. 형식이 어긋나면 null (앱은 집계만 보여 준다, FR-04). */
export function extractReport(geminiJson) {
  const text = geminiJson?.candidates?.[0]?.content?.parts?.map((p) => p.text ?? '').join('');
  if (!text) return null;
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    return null;
  }
  if (typeof parsed?.title !== 'string' || typeof parsed?.summary !== 'string') return null;
  const title = clip(parsed.title, LIMITS.titleMax);
  const summary = clip(parsed.summary, LIMITS.summaryMax);
  if (!title || !summary) return null;
  return { title, summary };
}
