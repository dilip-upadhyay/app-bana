import { defineConfig, devices } from '@playwright/test';

/**
 * AppBana E2E test config.
 *
 * Assumes the full stack is already running via `.\scripts\start-everything.bat`:
 *   - Studio:     http://localhost:5174
 *   - Runtime:    http://localhost:5175
 *   - Backend:    http://localhost:8080
 *   - AI Builder: http://localhost:8081
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.APPBANA_STUDIO_URL ?? 'http://localhost:5174',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 20_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
