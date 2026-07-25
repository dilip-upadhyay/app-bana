# AppBana E2E Tests

Playwright end-to-end tests for AppBana Studio. Kept in its own project
(with its own `node_modules` and `package.json`) so it doesn't interfere
with the UI or backend build.

## Prerequisites

The full stack must already be running:

```powershell
.\scripts\start-everything.bat        # Windows
./scripts/start-everything.sh          # macOS / Linux
```

Expected ports:
- UI:         http://localhost:5173
- Backend:    http://localhost:8080
- AI Builder: http://localhost:8081

## Install (one time)

```powershell
cd e2e
npm install
npx playwright install chromium
```

## Run

```powershell
npm test                # headless
npm run test:headed     # visible browser
npm run test:ui         # Playwright UI runner
npm run report          # open last HTML report
```

## Environment overrides

- `APPBANA_UI_URL`         — base URL (default `http://localhost:5173`)
- `APPBANA_BACKEND_URL`    — backend REST base (default `http://localhost:8080`)
- `APPBANA_AI_BUILDER_URL` — AI Builder base (default `http://localhost:8081`)

## What runs

- `tests/ai-builder-chat.spec.ts` — Registers a fresh user via
  `POST /api/auth/register`, logs in through the Studio UI, opens the
  AI Builder chat, sends a message, and asserts the assistant responds.
