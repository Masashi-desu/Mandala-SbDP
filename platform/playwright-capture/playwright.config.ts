import { defineConfig } from '@playwright/test';
import path from 'node:path';
import { loadCaptureOptions } from './src/config';

const capture = loadCaptureOptions();

export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.spec.ts',
  outputDir: path.join(capture.repositoryRoot, 'mandala/cache/playwright'),
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { outputFolder: path.join(capture.repositoryRoot, 'mandala/cache/playwright-report'), open: 'never' }]],
  expect: { timeout: 10_000 },
  use: {
    baseURL: capture.baseUrl,
    browserName: 'chromium',
    viewport: { width: 1440, height: 1000 },
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
    colorScheme: 'light',
    deviceScaleFactor: 1,
    serviceWorkers: 'block',
    trace: 'retain-on-failure',
  },
  webServer: capture.webServer ? {
    command: capture.webServer.command,
    cwd: capture.repositoryRoot,
    url: capture.webServer.url,
    reuseExistingServer: capture.webServer.reuseExistingServer,
    timeout: capture.webServer.timeoutMs,
  } : undefined,
});
