# Zora backend (alpha)

Node.js/Express API and provider callback scaffold. This is not yet a production voice agent: the Exotel WebSocket adapter currently accepts and logs lifecycle events but does not yet perform STT/TTS or return audio. Do not point live callers at it until the voice pipeline is implemented and tested.

## Local setup
1. Install Node.js 20+.
2. `cp .env.example .env` and fill `MONGODB_URI`, `APP_TOKEN`, `EXOTEL_WEBHOOK_TOKEN`.
3. `npm install && npm start`.
4. Health check: `GET /health`.

## Render
Create a Web Service from this repository, root directory `backend`, build command `npm install`, start command `npm start`. Add secrets in Render Environment, not in Git. Add `MONGODB_URI`, `APP_TOKEN`, `EXOTEL_WEBHOOK_TOKEN`. Enable WebSockets.

## API
- `GET /health` public health check
- `GET /api/v1/status` bearer APP_TOKEN
- `PUT /api/v1/assistant` bearer APP_TOKEN `{ "enabled": true }`
- `GET /api/v1/calls` bearer APP_TOKEN
- `GET /api/v1/calls/:id` bearer APP_TOKEN
- `DELETE /api/v1/calls/:id` bearer APP_TOKEN
- `POST /provider/exotel/events` provider webhook token
- `POST /provider/transcripts` provider webhook token

Provider webhook field names and signatures must be mapped to the actual Exotel account configuration before production. The sample token header is a temporary integration gate, not a substitute for validating Exotel's native webhook signatures/IP controls.
