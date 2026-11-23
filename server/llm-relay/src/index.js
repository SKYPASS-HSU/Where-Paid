// LLM 중계 서버 (Cloudflare Workers). 앱의 유일한 서버다.
// 하는 일: Gemini API 키를 앱 밖(Workers 비밀 값)에 두고, 월 집계를 받아 칭호·요약을 돌려준다.
// 하지 않는 일: 저장하지 않는다. 요청·응답 본문을 로그로 남기지 않는다 (NFR-03).

import { BadRequest, LIMITS, extractReport, geminiRequestBody, parseReportRequest } from './report.js';

const TIMEOUT_MS = 10_000;
const DEFAULT_MODEL = 'gemini-flash-lite-latest';

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  });
}

/** 길이가 달라도 비교 시간이 내용에 따라 달라지지 않게 한다. */
function sameToken(a, b) {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

async function readJson(request) {
  const text = await request.text();
  if (new TextEncoder().encode(text).length > LIMITS.maxBodyBytes) return { tooLarge: true };
  try {
    return { body: JSON.parse(text) };
  } catch {
    throw new BadRequest('JSON 형식이 아닙니다');
  }
}

async function monthlyReport(request, env) {
  if (!env.GEMINI_API_KEY) return json(503, { error: 'not_configured' });
  if (env.APP_TOKEN && !sameToken(request.headers.get('x-app-token') ?? '', env.APP_TOKEN)) {
    return json(401, { error: 'unauthorized' });
  }

  let req;
  try {
    const { body, tooLarge } = await readJson(request);
    if (tooLarge) return json(413, { error: 'too_large' });
    req = parseReportRequest(body);
  } catch (e) {
    if (e instanceof BadRequest) return json(400, { error: 'bad_request', message: e.message });
    throw e;
  }

  const model = env.GEMINI_MODEL || DEFAULT_MODEL;
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
  let upstream;
  try {
    upstream = await fetch(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
      body: JSON.stringify(geminiRequestBody(req)),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (e) {
    const timedOut = e?.name === 'TimeoutError' || e?.name === 'AbortError';
    return json(timedOut ? 504 : 502, { error: timedOut ? 'timeout' : 'upstream_unreachable' });
  }

  // 한도 초과(429)는 앱이 "잠시 후 다시" 안내를 할 수 있게 그대로 알려 준다. 나머지 실패는 502로 묶는다.
  if (upstream.status === 429) return json(429, { error: 'rate_limited' });
  if (!upstream.ok) {
    console.warn(`gemini status ${upstream.status}`); // 상태 코드만 남긴다
    return json(502, { error: 'upstream_error' });
  }

  const report = extractReport(await upstream.json().catch(() => null));
  if (!report) return json(502, { error: 'bad_upstream_response' });
  return json(200, report);
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    if (pathname === '/health') return json(200, { ok: true });
    if (pathname !== '/v1/monthly-report') return json(404, { error: 'not_found' });
    if (request.method !== 'POST') return json(405, { error: 'method_not_allowed' });
    return monthlyReport(request, env);
  },
};
