# Tatsu Home: Secure AI gateway

Optional general-knowledge Q&A using DeepSeek `deepseek-flash` (currently V4.1-Flash-1017).

## Privacy boundary

- Android sends **only the question typed in the AI screen** to **your own** HTTPS proxy.
- The proxy sends only a system instruction + that question to DeepSeek.
- No app inventory, stock data, health data, precise location, conversation history, authentication data or device state is collected.
- No tools, house-control APIs, commands or function calls are exposed to the model.
- Sensitive text is blocked on both client and server, but automated filters cannot guarantee removal of all personal information. Never type confidential information.
- The DeepSeek API key stays on the proxy server, never in the Android app or GitHub.
- The Android proxy access token is encrypted using Android Keystore in `noBackupFilesDir`. The gateway token is a separate, low-scope secret: **never reuse the DeepSeek API key as this token**.
- Requests are capped at 30 per hour per running proxy process; also configure provider billing limits and monitoring.
- The proxy listens only on `127.0.0.1`, not the public internet. A private HTTPS reverse proxy / zero-trust access gateway is required for phone use.

## Setup (Node.js 20+)

1. Create a DeepSeek API key in your own account and set `DEEPSEEK_API_KEY` as an environment variable on the server.
2. Generate a separate strong random token (for example `openssl rand -hex 32`) and set `AI_PROXY_TOKEN` to it. Keep both out of source control and shell history.
3. Run `node --test ai-proxy/server.test.mjs`, then `node ai-proxy/server.mjs`.
4. Expose localhost port 8787 through your authenticated private network and HTTPS proxy (for example, a properly secured Tailscale Serve endpoint).
5. In Tatsu Home, tap `AI`, enter `https://YOUR-PRIVATE-HOST/ask` and the separately generated proxy token. Do not enter the DeepSeek API key.
6. Send a general question. Reject all private/home-control prompts. Server does not store conversation history.

Without steps 1–5, the Home screen shows setup but **will not send anything**.

## Endpoint

- `GET /health` returns `{"status":"ready"}`, without secrets.
- `POST /ask` requires `Authorization: Bearer <AI_PROXY_TOKEN>`, JSON `{"question":"..."}`, returns `{"answer":"...","model":"deepseek-flash"}`.
- No CORS; no anonymous API; redirect following disabled; body bounded; fail-closed on API errors.
- Keep HTTPS certificate validation active. Never proxy an unauthenticated publicly accessible endpoint.
- The in-process rate limit resets on restart, so it is not a substitute for account quota/cost caps.

## Existing signing-key risk

The `Home` repository currently includes `shared-debug.jks` and its password in build configuration. That is a separate pre-existing distribution security risk. Rotate to a private signing key before distributing the APK more widely, and assess upgrade/signature migration separately. Do not commit any live AI credentials.
