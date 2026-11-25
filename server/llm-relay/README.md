# LLM 중계 서버 (`server/llm-relay`)

월간 리포트(S-08, 분석서 FR-04)의 **소비 칭호와 요약**을 만드는 Cloudflare Workers 서버입니다. 앱의 유일한 서버이고, 하는 일은 Gemini API 키를 앱 밖에 두는 것 하나입니다 (NFR-03).

- 저장하지 않습니다. 요청·응답 본문을 로그로 남기지 않습니다 (Gemini 실패 시 상태 코드만 남김).
- 앱은 **월 집계만** 보냅니다. 매장 이름, 주소, 좌표, 결제 시각은 보내지 않습니다. Gemini 무료 등급은 입력을 사람이 검토할 수 있어서입니다.
- Gemini가 실패하면 오류 코드를 돌려주고, 앱은 집계만 보여 줍니다 (FR-04).

## API

`POST /v1/monthly-report`

헤더: `content-type: application/json`, `x-app-token: <APP_TOKEN>` (서버에 `APP_TOKEN`을 설정한 경우)

```json
{
  "month": "2026-10",
  "offlineTotal": 129800,
  "onlineTotal": 88900,
  "paymentCount": 24,
  "categories": [
    { "name": "카페", "total": 21000, "count": 5 },
    { "name": "편의점", "total": 12400, "count": 4 }
  ],
  "previousMonthTotal": 98000
}
```

| 필드 | 규칙 | 앱에서 만드는 법 |
|---|---|---|
| `month` | `YYYY-MM` | 리포트 대상 달 |
| `offlineTotal`, `onlineTotal` | 0 이상 정수(원) | `MonthSummary`의 같은 이름 값 (취소 반영) |
| `paymentCount` | 0 이상 정수 | 그 달 결제 건수 |
| `categories` | 최대 10개, `name` 1~20자 | 지도 핀(`MAPPED`) 결제를 카카오 카테고리 마지막 칸("음식점 > 카페" → "카페")으로 묶은 합계·건수 |
| `previousMonthTotal` | 정수 또는 `null` | 지난달 `offlineTotal + onlineTotal`, 기록 없으면 `null` |

모르는 필드는 버립니다. 본문은 4KB까지입니다.

**응답**

| 상태 | 본문 | 앱 동작 |
|---|---|---|
| 200 | `{ "title": "카페 단골 탐험가", "summary": "…" }` (칭호 12자, 요약 150자 이내) | 칭호·요약 표시 |
| 400 | `{ "error": "bad_request", "message": "…" }` | 앱 버그. 집계만 표시 |
| 401 | `{ "error": "unauthorized" }` | 앱 토큰 불일치. 집계만 표시 |
| 429 | `{ "error": "rate_limited" }` | Gemini 한도 초과. 집계만 표시, 나중에 다시 |
| 502, 503, 504 | `{ "error": "…" }` | 집계만 표시 |

서버는 Gemini를 10초까지 기다립니다. 앱의 요청 제한 시간은 그보다 길게(15초) 둡니다.

`GET /health` → `{ "ok": true }`

## 처음 설정 (④ 담당)

필요한 것: Node.js 20 이상, Cloudflare 계정(카드 등록 불필요), Google AI Studio의 Gemini API 키(무료 등급, 결제 계정 연결 안 함).

```bash
cd server/llm-relay
npm install
npm test                      # 단위 테스트 (Gemini를 실제로 부르지 않음)

cp .dev.vars.example .dev.vars   # GEMINI_API_KEY, APP_TOKEN 입력. 커밋 금지
npm run dev                      # http://localhost:8787 에서 실제 Gemini로 1건 확인

npx wrangler login
npx wrangler secret put GEMINI_API_KEY
npx wrangler secret put APP_TOKEN
npm run deploy                   # https://wherepaid-llm-relay.<계정>.workers.dev
```

로컬 확인용 요청:

```bash
curl -s -X POST http://localhost:8787/v1/monthly-report \
  -H 'content-type: application/json' -H "x-app-token: $APP_TOKEN" \
  -d '{"month":"2026-10","offlineTotal":129800,"onlineTotal":88900,"paymentCount":24,"categories":[{"name":"카페","total":21000,"count":5}],"previousMonthTotal":98000}'
```

모델은 `wrangler.toml`의 `GEMINI_MODEL`로 바꿉니다. 모델 ID는 자주 바뀌니 AI Studio에서 무료 등급으로 쓸 수 있는 ID를 확인해 넣습니다.

## 비밀 값

- `GEMINI_API_KEY`: Workers 비밀 값과 `.dev.vars`에만 둡니다. 앱, 저장소, 단톡방에 두지 않습니다.
- `APP_TOKEN`: 아무나 이 주소로 Gemini 한도를 써 버리지 않게 막는 값입니다. 앱에도 들어가므로(APK에서 꺼낼 수 있음) 비밀번호처럼 믿지는 않습니다. 앱 쪽에는 `local.properties`의 `wherepaid.relayToken`으로 넣을 예정입니다.
