import { expect, test, type Locator, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { loadCaptureOptions, repositoryRelative } from '../src/config';
import { loadScenarios } from '../src/scenarios';
import {
  buildActionTransitions,
  normalizePath,
  pathMatches,
  portablePageUrl,
  sanitize,
  sanitizeActions,
  type ActionDefinition,
  type NetworkObservation,
  type Scenario,
  type ScreenObservation,
} from '../src/model';

const captureOptions = loadCaptureOptions();

async function installDeterminism(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const fixed = new Date('2026-01-15T03:00:00.000Z').valueOf();
    const OriginalDate = Date;
    class FixedDate extends OriginalDate {
      constructor(value?: string | number) { super(value === undefined ? fixed : value); }
      static now(): number { return fixed; }
    }
    Object.defineProperty(window, 'Date', { value: FixedDate });
    Math.random = () => 0.421337;
    localStorage.clear();
    document.addEventListener('DOMContentLoaded', () => {
      const style = document.createElement('style');
      style.textContent = '*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}';
      document.head.append(style);
    });
  });
  await page.emulateMedia({ reducedMotion: 'reduce', colorScheme: 'light' });
}

function actionLocator(page: Page, action: ActionDefinition, scenario: Scenario): Locator {
  if ((action.kind === 'fill' || action.kind === 'select') && action.label) return page.getByLabel(action.label);
  if (action.kind === 'click' && action.role && action.name) {
    return page.getByRole(action.role as 'button' | 'link', { name: action.name });
  }
  throw new Error(`Unsupported action in ${scenario.id}: ${JSON.stringify(action)}`);
}

async function performAction(page: Page, action: ActionDefinition, scenario: Scenario): Promise<void> {
  const locator = actionLocator(page, action, scenario);
  if (action.kind === 'fill') await locator.fill(action.value ?? '');
  else if (action.kind === 'select') await locator.selectOption(action.value ?? '');
  else await locator.click();
}

async function observeScreen(page: Page, fallbackState: string): Promise<ScreenObservation> {
  const body = page.locator('body');
  const declaredState = await body.evaluate((element) => {
    const html = element as HTMLElement;
    return html.dataset.mandalaState ?? html.dataset.docState ?? '';
  });
  const heading = (await page.locator('h1').filter({ visible: true }).first()
    .textContent().catch(() => null))?.trim();
  return {
    route: normalizePath(page.url()),
    state: declaredState.trim() || fallbackState,
    ...(heading ? { name: heading } : {}),
  };
}

for (const scenario of loadScenarios(captureOptions)) {
  test(`${scenario.id} — ${scenario.title}`, async ({ page }) => {
    const observations: NetworkObservation[] = [];
    const consoleErrors: string[] = [];
    const loadingReleases: Array<() => void> = [];
    const mockCalls = new Map<string, number>();
    let activeActionSequence: number | undefined;
    page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });
    page.on('dialog', (dialog) => dialog.accept());
    await page.route('**/api/**', async (route) => {
      const request = route.request();
      const actualPath = new URL(request.url()).pathname;
      const matching = (scenario.mocks ?? []).map((candidate, index) => ({ candidate, index }))
        .filter(({ candidate }) => candidate.method === request.method() && pathMatches(candidate.path, actualPath));
      const mockKey = `${request.method()}:${actualPath}`;
      const callIndex = mockCalls.get(mockKey) ?? 0;
      const selected = matching[Math.min(callIndex, Math.max(0, matching.length - 1))];
      const mockIndex = selected?.index ?? -1;
      mockCalls.set(mockKey, callIndex + 1);
      const mock = mockIndex >= 0 ? scenario.mocks?.[mockIndex] : undefined;
      let requestBody: unknown;
      try { requestBody = request.postDataJSON(); } catch { requestBody = request.postData(); }
      if (!mock) {
        observations.push({
          method: request.method(),
          path: normalizePath(actualPath),
          status: 599,
          requestBody: sanitize(requestBody),
          undefined: true,
          ...(activeActionSequence === undefined ? {} : { actionSequence: activeActionSequence }),
        });
        await route.fulfill({ status: 599, contentType: 'application/json', body: JSON.stringify({ error: 'UNDEFINED_MOCK' }) });
        return;
      }
      const status = mock.status ?? 200;
      observations.push({
        method: request.method(),
        path: normalizePath(actualPath),
        status,
        requestBody: sanitize(requestBody),
        responseBody: sanitize(mock.body),
        mockId: `${scenario.id}:${mockIndex}`,
        undefined: false,
        ...(activeActionSequence === undefined ? {} : { actionSequence: activeActionSequence }),
      });
      if (mock.delayUntil === 'capture') await new Promise<void>((resolve) => loadingReleases.push(resolve));
      await route.fulfill({ status, contentType: 'application/json', body: mock.body === undefined ? '' : JSON.stringify(mock.body) });
    });
    await installDeterminism(page);
    await page.goto(scenario.route);
    const actions = scenario.actions ?? [];
    const transitionPoints: ScreenObservation[] = [];
    const screenshot = path.join(captureOptions.screenshotsRoot, `${scenario.id}.png`);
    const screenshotRelative = repositoryRelative(captureOptions.repositoryRoot, screenshot);
    fs.mkdirSync(path.dirname(screenshot), { recursive: true });
    let currentState = scenario.initialState ?? 'normal';
    for (const [sequence, action] of actions.entries()) {
      await actionLocator(page, action, scenario).waitFor({ state: 'visible' });
      await page.evaluate(() => document.fonts.ready);
      const pointScreenshot = path.join(
        captureOptions.screenshotsRoot, `${scenario.id}-step-${sequence}.png`);
      await page.screenshot({ path: pointScreenshot, fullPage: true, animations: 'disabled' });
      transitionPoints.push({
        ...await observeScreen(page, currentState),
        screenshot: repositoryRelative(captureOptions.repositoryRoot, pointScreenshot),
      });
      activeActionSequence = sequence;
      await performAction(page, action, scenario);
      currentState = action.resultState ?? (sequence === actions.length - 1 ? scenario.state : currentState);
    }
    if (scenario.expect.role && scenario.expect.name) {
      const role = page.getByRole(scenario.expect.role as 'heading' | 'alert' | 'status');
      if (scenario.expect.role === 'heading') await expect(page.getByRole('heading', { name: scenario.expect.name, exact: true })).toBeVisible();
      else {
        const visibleRole = role.filter({ visible: true }).first();
        await expect(visibleRole).toBeVisible();
        for (const phrase of scenario.expect.name.split(/\s+/)) await expect(visibleRole).toContainText(phrase);
      }
    }
    if (scenario.expect.text) await expect(page.getByText(scenario.expect.text, { exact: true }).filter({ visible: true }).first()).toBeVisible();
    await expect(page.locator('body')).toHaveAttribute('data-doc-ready', scenario.captureWhileLoading ? 'false' : 'true');
    await page.evaluate(() => document.fonts.ready);
    const finalScreen = await observeScreen(page, scenario.state);
    await page.screenshot({ path: screenshot, fullPage: true, animations: 'disabled' });
    if (actions.length) {
      transitionPoints.push({
        ...finalScreen,
        screenshot: screenshotRelative,
      });
    }
    activeActionSequence = undefined;

    loadingReleases.forEach((release) => release());
    const body = page.locator('body');
    const observation = {
      schemaVersion: '1.1', id: scenario.id, title: scenario.title, route: scenario.route,
      pageUrl: portablePageUrl(page.url()), state: scenario.state,
      screenName: finalScreen.name,
      screenshot: screenshotRelative, ariaSnapshot: await body.ariaSnapshot(), domSnapshot: sanitize(await body.innerText()),
      actions: sanitizeActions(actions),
      transitions: actions.length ? buildActionTransitions(scenario, transitionPoints, observations) : [],
      requests: observations, consoleErrors, undefinedCommunications: observations.filter((item) => item.undefined),
      environment: {
        viewport: { width: 1440, height: 1000 }, locale: 'ja-JP', timezone: 'Asia/Tokyo', colorScheme: 'light',
        time: '2026-01-15T03:00:00.000Z', role: scenario.environment?.role ?? 'UNSPECIFIED',
        featureFlags: scenario.environment?.featureFlags ?? {},
      },
      evidence: 'PLAYWRIGHT_OBSERVATION', confidence: 'OBSERVED', capturedAt: '2026-01-15T03:00:00.000Z',
    };
    const output = path.join(captureOptions.observationsRoot, `${scenario.id}.json`);
    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, `${JSON.stringify(observation, null, 2)}\n`);
    expect(observations.filter((item) => item.undefined), 'undefined API communications').toEqual([]);
    const hasExpectedHttpError = (scenario.mocks ?? []).some((mock) => (mock.status ?? 200) >= 400);
    const unexpectedConsoleErrors = consoleErrors.filter((message) => !(hasExpectedHttpError && message.startsWith('Failed to load resource:')));
    expect(unexpectedConsoleErrors, 'unexpected browser console errors').toEqual([]);
  });
}
