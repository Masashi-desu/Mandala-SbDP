import { describe, expect, it } from 'vitest';
import {
  buildActionTransitions,
  normalizePath,
  pathMatches,
  portablePageUrl,
  sanitize,
  sanitizeActions,
  type Scenario,
} from '../src/model';

describe('capture boundaries', () => {
  it('normalizes instance identifiers for later HTTP matching', () => {
    expect(normalizePath('/api/projects/42/tasks')).toBe('/api/projects/{id}/tasks');
    expect(normalizePath('https://example.test/orders/42/items/7?view=full')).toBe('/orders/{id}/items/{id}');
    expect(normalizePath('/api/releases/v2/items/abc-123')).toBe('/api/releases/v2/items/abc-123');
    expect(pathMatches('/api/tasks/{id}/status', '/api/tasks/8/status')).toBe(true);
  });

  it('persists page locations without environment-specific origins or query data', () => {
    expect(portablePageUrl('http://127.0.0.1:15173/projects/42?token=local#details')).toBe('/projects/42');
    expect(portablePageUrl('/login')).toBe('/login');
  });

  it('recursively redacts secrets before persistence', () => {
    expect(sanitize({ username: 'admin', password: 'secret', nested: { authorization: 'Bearer x' } }))
      .toEqual({ username: 'admin', password: '[REDACTED]', nested: { authorization: '[REDACTED]' } });
    expect(sanitizeActions([
      { kind: 'fill', label: 'パスワード', value: 'local-credential' },
      { kind: 'fill', label: 'Project name', value: 'Visible specification data' },
    ])).toEqual([
      { kind: 'fill', label: 'パスワード', value: '[REDACTED]' },
      { kind: 'fill', label: 'Project name', value: 'Visible specification data' },
    ]);
  });

  it('records action-level screen transitions, conditions and related HTTP', () => {
    const scenario: Scenario = {
      id: 'project-create',
      title: 'Project create',
      route: '/projects/new',
      state: 'success',
      environment: { role: 'ADMIN', featureFlags: { projectCreation: true } },
      expect: { role: 'heading', name: 'Project' },
      actions: [
        { kind: 'fill', label: 'Project name', value: 'Mandala' },
        { kind: 'click', role: 'button', name: 'Create project' },
      ],
    };

    expect(buildActionTransitions(scenario, [
      { route: '/projects/new', state: 'normal' },
      { route: '/projects/new', state: 'normal' },
      { route: '/projects/{id}', state: 'success' },
    ], [
      {
        method: 'POST',
        path: '/api/projects',
        status: 201,
        mockId: 'project-create:0',
        undefined: false,
        actionSequence: 1,
      },
    ])).toEqual([
      {
        sequence: 0,
        from: { route: '/projects/new', state: 'normal' },
        action: { kind: 'fill', label: 'Project name', value: 'Mandala' },
        to: { route: '/projects/new', state: 'normal' },
        condition: {
          role: 'ADMIN',
          featureFlags: { projectCreation: true },
          outcome: 'normal',
          scenarioOutcome: 'success',
        },
        relatedHttp: [],
      },
      {
        sequence: 1,
        from: { route: '/projects/new', state: 'normal' },
        action: { kind: 'click', role: 'button', name: 'Create project' },
        to: { route: '/projects/{id}', state: 'success' },
        condition: {
          role: 'ADMIN',
          featureFlags: { projectCreation: true },
          outcome: 'success',
          scenarioOutcome: 'success',
        },
        relatedHttp: [{
          method: 'POST',
          path: '/api/projects',
          status: 201,
          mockId: 'project-create:0',
          undefined: false,
        }],
      },
    ]);
  });
});
