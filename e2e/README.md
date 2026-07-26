# AppBana E2E Tests

Playwright end-to-end tests for AppBana Studio + Runtime. Kept in its own project
(with its own `node_modules` and `package.json`) so it doesn't interfere with
the frontend or backend build.

## Prerequisites

The full stack must already be running:

```powershell
.\scripts\start-everything.bat        # Windows
./scripts/start-everything.sh          # macOS / Linux
```

Expected ports:
- Studio:     http://localhost:5174
- Runtime:    http://localhost:5175
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

- `APPBANA_STUDIO_URL`     — base URL (default `http://localhost:5174`)
- `APPBANA_BACKEND_URL`    — backend REST base (default `http://localhost:8080`)
- `APPBANA_AI_BUILDER_URL` — AI Builder base (default `http://localhost:8081`)

## What runs

- `tests/ai-builder-chat.studio.spec.ts` — Registers a fresh user via
  `POST /api/auth/register`, logs into the Studio, opens the AI Builder chat,
  sends a message, and asserts the assistant responds and the preview reloads.
- `tests/stage-0-backend-contracts.spec.ts` — Verifies backend contracts
  (branding, app-context, SSE stream shape).
- `tests/stage-3-studio-drawers.spec.ts` — Verifies Data drawer + Session picker.
