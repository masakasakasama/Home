import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createApp, isPrivateQuestion } from './server.mjs';

const TOKEN = 'test-token-0123456789abcdef0123456789';

test('blocks sensitive input and device control', () => {
  for (const question of [
    '自宅の住所はどこ？', '玄関の鍵を開けて', 'エアコンを消して',
    'My password is abc', 'contact x@y.com', 'api_key is here',
  ]) assert.equal(isPrivateQuestion(question), true, question);
  assert.equal(isPrivateQuestion('Explain the greenhouse effect'), false);
});

let currentApp;
afterEach(async () => {
  if (currentApp) {
    await new Promise(resolve => currentApp.close(resolve));
    currentApp = undefined;
  }
});

async function serve(upstreamFetch, maxPerHour = 30) {
  currentApp = createApp({
    apiKey: 'test-deepseek-key',
    gatewayToken: TOKEN,
    upstreamFetch,
    maxPerHour,
  });
  await new Promise(resolve => currentApp.listen(0, '127.0.0.1', resolve));
  return `http://127.0.0.1:${currentApp.address().port}`;
}
const post = (base, question, token = TOKEN) => fetch(`${base}/ask`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ question }),
});

test('requires authorization and forwards only explicit question', async () => {
  const calls = [];
  const base = await serve(async (url, init) => {
    calls.push({ url, init, payload: JSON.parse(init.body) });
    return { ok: true, json: async () => ({ choices: [{ message: { content: '4' } }] }) };
  });
  assert.equal((await post(base, 'What is 2+2?', 'wrong')).status, 401);
  const reply = await post(base, 'What is 2+2?');
  assert.equal(reply.status, 200);
  assert.deepEqual(await reply.json(), { answer: '4', model: 'deepseek-flash' });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://api.deepseek.com/chat/completions');
  assert.deepEqual(calls[0].payload.messages.map(m => m.role), ['system', 'user']);
  assert.equal(calls[0].payload.messages[1].content, 'What is 2+2?');
  assert.equal(calls[0].payload.tools, undefined);
  assert.equal(calls[0].init.redirect, 'error');
});

test('blocks sensitive requests before contacting DeepSeek', async () => {
  let called = false;
  const base = await serve(async () => { called = true; throw new Error('unexpected'); });
  const response = await post(base, '玄関の鍵を開けて');
  assert.equal(response.status, 422);
  assert.equal(called, false);
});

test('limits request volume, avoids leaking provider details', async () => {
  const base = await serve(async () => ({ ok: false }), 1);
  assert.equal((await post(base, 'One')).status, 502);
  assert.equal((await post(base, 'Two')).status, 429);
});

test('refuses startup without strong credentials', () => {
  assert.throws(() => createApp({ apiKey: '', gatewayToken: TOKEN }), /required/);
  assert.throws(() => createApp({ apiKey: 'valid', gatewayToken: 'short' }), /required/);
});
