# JARVIS Cloudflare Worker

This Worker is the backend expected by the Android build. It exposes:

- `GET /` health check
- `POST /chat` general conversational AI; accepts `{ "messages": [...] }` and returns `{ "reply": "..." }`
- `POST /intent` intent classification; accepts `{ "input": "...", "messages": [...] }` and returns `{ "intent", "confidence", "entities", "clarification" }`
- `POST /image` image generation; accepts `{ "prompt": "..." }` and returns `{ "status", "mime_type", "image_base64" }`

## Cloudflare configuration

Create a Workers AI binding named `AI` and a secret named `JARVIS_CLIENT_TOKEN`. Keep the same token configured as the GitHub Actions secret `JARVIS_CLIENT_TOKEN`; set `JARVIS_WORKER_URL` to the deployed Worker URL. Do not put either secret in source control.

Deploy this folder as the Worker entry point. The app build uses the Worker URL and token supplied through GitHub Actions secrets.

## Local contract tests

Run `npm test` from this folder. Tests use a mock Workers AI binding and do not consume AI credits.
