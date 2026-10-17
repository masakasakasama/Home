import { createServer } from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const MODEL = 'deepseek-flash';
const UPSTREAM = 'https://api.deepseek.com/chat/completions';
const MAX_BODY = 8192;
const MAX_QUESTION = 1600;
const MAX_ANSWER = 6000;
const HOUR_MS = 60 * 60 * 1000;

// Defense in depth only: patterns cannot reliably identify all sensitive text.
// The client intentionally never gathers device state, location or app data.
const PRIVATE_CONTENT = [
  /(?:パスワード|暗証番号|認証コード|ワンタイムパス|秘密鍵|API[\s_-]*キー|アクセストークン|口座番号|カード番号|住所|自宅の場所|正確な位置|居場所)/i,
  /(?:鍵|ドア|玄関|照明|エアコン|監視カメラ|防犯カメラ|スマートロック|家電).{0,18}(?:開け|閉め|つけ|消し|操作|解除|施錠|解錠|現在|状態)/i,
  /(?:開け|閉め|つけ|消し|操作|解除|施錠|解錠).{0,18}(?:鍵|ドア|玄関|照明|エアコン|監視カメラ|スマートロック|家電)/i,
  /\b(?:password|passwd|passcode|secret|api[\s_-]?key|access[\s_-]?token|bearer|private[\s_-]?key|otp|credit[\s_-]?card|bank[\s_-]?account|home[\s_-]?address|my[\s_-]?location)\b/i,
  /\b(?:unlock|lock|open|close|turn\s+on|turn\s+off|disarm)\b.{0,35}\b(?:door|camera|lock|light|alarm|thermostat|home)\b/i,
  /[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/,
  /\b(?:\+?\d{1,3}[- .]?)?(?:\d{2,4}[- .]?){2,3}\d{3,4}\b/,
  /\bsk-[A-Za-z0-9_-]{12,}\b/,
];

export function isPrivateQuestion(question) {
  return PRIVATE_CONTENT.some(pattern => pattern.test(question));
}

function safeEqual(a, b) {
  const one = Buffer.from(a ?? '');
  const two = Buffer.from(b ?? '');
  return one.length === two.length && timingSafeEqual(one, two);
}

function json(res, status, payload) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'Pragma': 'no-cache',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'Content-Security-Policy': "default-src 'none'",
  });
  res.end(JSON.stringify(payload));
}

async function readLimitedJson(req) {
  let bytes = 0;
  const chunks = [];
  for await (const chunk of req) {
    bytes += chunk.length;
    if (bytes > MAX_BODY) throw new Error('oversized');
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

export function createApp({ apiKey, gatewayToken, upstreamFetch = fetch, maxPerHour = 30 }) {
  if (!apiKey || !gatewayToken || gatewayToken.length < 32) {
    throw new Error('DEEPSEEK_API_KEY and AI_PROXY_TOKEN (32+ chars) are required');
  }

  let windowStarted = Date.now();
  let requests = 0;
  return createServer(async (req, res) => {
    if (req.url === '/health' && req.method === 'GET') {
      json(res, 200, { status: 'ready' });
      return;
    }
    if (req.url !== '/ask' || req.method !== 'POST') {
      json(res, 404, { error: 'not_found' });
      return;
    }
    const auth = req.headers.authorization ?? '';
    if (!auth.startsWith('Bearer ') || !safeEqual(auth.slice(7), gatewayToken)) {
      json(res, 401, { error: 'unauthorized' });
      return;
    }
    if (!req.headers['content-type']?.toLowerCase().startsWith('application/json')) {
      json(res, 415, { error: 'json_required' });
      return;
    }

    let question;
    try {
      const payload = await readLimitedJson(req);
      question = payload?.question;
    } catch {
      json(res, 400, { error: 'invalid_json' });
      return;
    }
    if (typeof question !== 'string' || !question.trim() || question.length > MAX_QUESTION) {
      json(res, 400, { error: 'invalid_question' });
      return;
    }
    if (isPrivateQuestion(question)) {
      json(res, 422, { error: 'sensitive_content_blocked' });
      return;
    }

    const now = Date.now();
    if (now - windowStarted >= HOUR_MS) {
      windowStarted = now;
      requests = 0;
    }
    if (requests >= maxPerHour) {
      json(res, 429, { error: 'rate_limited' });
      return;
    }
    requests += 1;

    try {
      const upstream = await upstreamFetch(UPSTREAM, {
        method: 'POST',
        redirect: 'error',
        signal: AbortSignal.timeout(30000),
        headers: {
          Authorization: `Bearer ${apiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model: MODEL,
          messages: [
            { role: 'system', content: 'Answer general knowledge questions only. You have no access to devices, home controls, personal data, tools or user location. Do not claim to control anything. Be concise.' },
            { role: 'user', content: question.trim() },
          ],
          max_tokens: 700,
          stream: false,
        }),
      });
      if (!upstream.ok) {
        json(res, 502, { error: 'provider_unavailable' });
        return;
      }
      const data = await upstream.json();
      const answer = data?.choices?.[0]?.message?.content;
      if (typeof answer !== 'string' || !answer.trim()) {
        json(res, 502, { error: 'invalid_provider_response' });
        return;
      }
      json(res, 200, { answer: answer.slice(0, MAX_ANSWER), model: MODEL });
    } catch {
      json(res, 502, { error: 'provider_unavailable' });
    }
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const app = createApp({
    apiKey: process.env.DEEPSEEK_API_KEY,
    gatewayToken: process.env.AI_PROXY_TOKEN,
  });
  // Loopback-only, never bind directly to the public internet.
  const port = Number(process.env.PORT ?? '8787');
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid PORT');
  app.listen(port, '127.0.0.1', () => {
    process.stdout.write(`AI proxy ready on localhost:${port} (TLS reverse proxy required)\n`);
  });
}
