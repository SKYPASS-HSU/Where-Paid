import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';
import { BadRequest, buildUserPrompt, extractReport, parseReportRequest } from '../src/report.js';

// 가짜 집계. 실제 결제 기록이 아니다.
const valid = () => ({
  month: '2026-10',
  offlineTotal: 129_800,
  onlineTotal: 88_900,
  paymentCount: 24,
  categories: [
    { name: '카페', total: 21_000, count: 5 },
    { name: '편의점', total: 12_400, count: 4 },
  ],
  previousMonthTotal: 98_000,
});

const geminiReply = (obj) => ({ candidates: [{ content: { parts: [{ text: JSON.stringify(obj) }] } }] });

function call(body, { env = { GEMINI_API_KEY: 'test-key' }, headers = {}, method = 'POST', path = '/v1/monthly-report' } = {}) {
  const init = { method, headers: { 'content-type': 'application/json', ...headers } };
  if (method !== 'GET') init.body = typeof body === 'string' ? body : JSON.stringify(body);
  return worker.fetch(new Request(`https://relay.test${path}`, init), env);
}

function stubFetch(impl) {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    return impl(url, init);
  };
  return { calls, restore: () => (globalThis.fetch = original) };
}

test('올바른 집계는 알려진 필드만 남긴다', () => {
  const req = parseReportRequest({ ...valid(), merchant: '스타벅스', lat: 37.5 });
  assert.deepEqual(Object.keys(req).sort(), ['categories', 'month', 'offlineTotal', 'onlineTotal', 'paymentCount', 'previousMonthTotal']);
  assert.equal(parseReportRequest({ ...valid(), previousMonthTotal: undefined }).previousMonthTotal, null);
});

test('형식이 틀린 집계는 BadRequest', () => {
  for (const bad of [
    null,
    [],
    { ...valid(), month: '2026-13' },
    { ...valid(), offlineTotal: -1 },
    { ...valid(), onlineTotal: 1.5 },
    { ...valid(), paymentCount: '24' },
    { ...valid(), categories: 'x' },
    { ...valid(), categories: Array(11).fill({ name: 'a', total: 1, count: 1 }) },
    { ...valid(), categories: [{ name: '', total: 1, count: 1 }] },
  ]) {
    assert.throws(() => parseReportRequest(bad), BadRequest, JSON.stringify(bad));
  }
});

test('프롬프트에는 집계 숫자만 들어간다', () => {
  const prompt = buildUserPrompt(parseReportRequest(valid()));
  assert.match(prompt, /전체 합계: 218,700원 \(지난달보다 123% 많음\)/);
  assert.match(prompt, /온라인 결제: 88,900원 \(전체의 41%\)/);
  assert.match(prompt, /"카페": 21,000원, 16%, 5건/);
  assert.ok(prompt.indexOf('카페') < prompt.indexOf('편의점')); // 큰 카테고리부터
  assert.match(buildUserPrompt(parseReportRequest({ ...valid(), categories: [], previousMonthTotal: null })), /기록 없음/);
});

test('Gemini 응답에서 칭호와 요약을 꺼내고 길이를 자른다', () => {
  assert.deepEqual(extractReport(geminiReply({ title: '카페 단골', summary: '이번 달은 카페에서 자주 쉬었어요.' })), {
    title: '카페 단골',
    summary: '이번 달은 카페에서 자주 쉬었어요.',
  });
  assert.equal(extractReport(geminiReply({ title: '아주아주아주아주아주긴칭호입니다', summary: 's' })).title.length, 12);
  assert.equal(extractReport({ candidates: [] }), null);
  assert.equal(extractReport({ candidates: [{ content: { parts: [{ text: '그냥 글' }] } }] }), null);
  assert.equal(extractReport(geminiReply({ title: '칭호만' })), null);
});

test('정상 요청은 Gemini를 한 번 부르고 칭호·요약을 돌려준다', async () => {
  const stub = stubFetch(async () => Response.json(geminiReply({ title: '카페 단골', summary: '요약' })));
  try {
    const res = await call(valid(), { env: { GEMINI_API_KEY: 'test-key', GEMINI_MODEL: 'some-model' } });
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { title: '카페 단골', summary: '요약' });
    assert.equal(stub.calls.length, 1);
    assert.match(stub.calls[0].url, /models\/some-model:generateContent$/);
    assert.equal(stub.calls[0].init.headers['x-goog-api-key'], 'test-key');
    assert.doesNotMatch(stub.calls[0].url, /test-key/); // 키는 URL이 아니라 헤더로만 보낸다
  } finally {
    stub.restore();
  }
});

test('Gemini 실패는 502, 한도 초과는 429, 이상한 응답은 502', async () => {
  for (const [upstream, expected] of [
    [() => new Response('x', { status: 500 }), 502],
    [() => new Response('x', { status: 429 }), 429],
    [() => Response.json({ candidates: [] }), 502],
    [() => { throw new TypeError('network'); }, 502],
  ]) {
    const stub = stubFetch(async () => upstream());
    try {
      assert.equal((await call(valid())).status, expected);
    } finally {
      stub.restore();
    }
  }
});

test('Gemini를 부르기 전에 거르는 요청들', async () => {
  const stub = stubFetch(async () => assert.fail('Gemini를 부르면 안 된다'));
  try {
    assert.equal((await call(valid(), { env: {} })).status, 503);
    assert.equal((await call(valid(), { env: { GEMINI_API_KEY: 'k', APP_TOKEN: 'secret' } })).status, 401);
    assert.equal((await call(valid(), { env: { GEMINI_API_KEY: 'k', APP_TOKEN: 'secret' }, headers: { 'x-app-token': 'wrong' } })).status, 401);
    assert.equal((await call('{not json')).status, 400);
    assert.equal((await call({ ...valid(), month: 'x' })).status, 400);
    assert.equal((await call({ ...valid(), pad: 'x'.repeat(5000) })).status, 413);
    assert.equal((await call(null, { method: 'GET' })).status, 405);
    assert.equal((await call(null, { method: 'GET', path: '/other' })).status, 404);
    assert.equal((await call(null, { method: 'GET', path: '/health' })).status, 200);
  } finally {
    stub.restore();
  }
});

test('앱 토큰이 맞으면 통과한다', async () => {
  const stub = stubFetch(async () => Response.json(geminiReply({ title: 't', summary: 's' })));
  try {
    const res = await call(valid(), { env: { GEMINI_API_KEY: 'k', APP_TOKEN: 'secret' }, headers: { 'x-app-token': 'secret' } });
    assert.equal(res.status, 200);
  } finally {
    stub.restore();
  }
});
