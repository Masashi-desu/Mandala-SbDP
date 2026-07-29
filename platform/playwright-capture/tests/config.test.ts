import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { captureEnvironment, capturePassthroughArguments, loadCaptureOptions } from '../src/config';
import { findScenarioFiles, loadScenarios } from '../src/scenarios';

const temporaryRepositories: string[] = [];

function fixtureRepository(): string {
  const repository = fs.mkdtempSync(path.join(os.tmpdir(), 'mandala-playwright-'));
  temporaryRepositories.push(repository);
  fs.mkdirSync(path.join(repository, 'web/src'), { recursive: true });
  fs.mkdirSync(path.join(repository, 'fixtures/nested'), { recursive: true });
  fs.mkdirSync(path.join(repository, 'mandala/config'), { recursive: true });
  fs.writeFileSync(path.join(repository, 'web/src/app.ts'), 'export const route = "/orders/42";\n');
  fs.writeFileSync(path.join(repository, 'fixtures/nested/orders.yml'), `defaults:
  environment: { role: AUDITOR }
scenarios:
  - id: orders-list
    title: Orders
    route: /orders
    state: normal
    expect: { role: heading, name: Orders }
`);
  fs.writeFileSync(path.join(repository, 'mandala/config/mandala.yml'), `mandala:
  source:
    frontend:
      root: web/src
  playwright:
    baseUrl: http://127.0.0.1:4300/
    scenarios:
      - fixtures/**/*.yml
    output:
      observations: artifacts/observations/**/*.json
      screenshots: artifacts/screenshots
    webServer:
      command: npm run dev
      url: http://127.0.0.1:4300/ready
      reuseExistingServer: true
      timeoutMs: 90000
`);
  return repository;
}

afterEach(() => {
  for (const repository of temporaryRepositories.splice(0)) fs.rmSync(repository, { recursive: true, force: true });
});

describe('capture configuration', () => {
  it('resolves generic repository inputs and recursive scenario globs from mandala.yml', () => {
    const repository = fixtureRepository();
    const options = loadCaptureOptions([], { INIT_CWD: repository }, repository);

    expect(options.repositoryRoot).toBe(repository);
    expect(options.frontendRoot).toBe(path.join(repository, 'web/src'));
    expect(options.observationsRoot).toBe(path.join(repository, 'artifacts/observations'));
    expect(options.screenshotsRoot).toBe(path.join(repository, 'artifacts/screenshots'));
    expect(options.baseUrl).toBe('http://127.0.0.1:4300');
    expect(options.webServer).toEqual({
      command: 'npm run dev',
      url: 'http://127.0.0.1:4300/ready',
      reuseExistingServer: true,
      timeoutMs: 90_000,
    });
    expect(findScenarioFiles(options)).toEqual([path.join(repository, 'fixtures/nested/orders.yml')]);
    expect(loadScenarios(options).map((scenario) => scenario.id)).toEqual(['orders-list']);
    expect(loadScenarios(options)[0]?.environment?.role).toBe('AUDITOR');
  });

  it('applies CLI overrides, can disable web-server management, and forwards Playwright-only flags', () => {
    const repository = fixtureRepository();
    const args = [
      '--repository-root', repository,
      '--base-url', 'http://localhost:4400',
      '--observations', 'different/observations',
      '--screenshots', 'different/screenshots',
      '--no-web-server',
      '--grep', 'orders',
    ];
    const options = loadCaptureOptions(args, { MANDALA_CAPTURE_BASE_URL: 'http://localhost:4500' }, repository);

    expect(options.baseUrl).toBe('http://localhost:4400');
    expect(options.webServer).toBeUndefined();
    expect(options.observationsRoot).toBe(path.join(repository, 'different/observations'));
    expect(capturePassthroughArguments(args)).toEqual(['--grep', 'orders']);
    expect(captureEnvironment(options)).toMatchObject({
      MANDALA_CAPTURE_REPOSITORY_ROOT: repository,
      MANDALA_CAPTURE_BASE_URL: 'http://localhost:4400',
      MANDALA_CAPTURE_WEB_SERVER_ENABLED: 'false',
    });
  });
});
