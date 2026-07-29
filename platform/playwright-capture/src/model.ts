export type MockDefinition = {
  method: string;
  path: string;
  status?: number;
  body?: unknown;
  delayUntil?: string;
};

export type ActionDefinition = {
  kind: 'click' | 'fill' | 'select';
  role?: string;
  name?: string;
  label?: string;
  value?: string;
  /** Semantic state reached after this action when it is not the scenario's final action. */
  resultState?: string;
};

export type Scenario = {
  id: string;
  title: string;
  route: string;
  state: string;
  initialState?: string;
  environment?: {
    role?: string;
    featureFlags?: Record<string, boolean | number | string>;
  };
  expect: { role?: string; name?: string; text?: string };
  mocks?: MockDefinition[];
  actions?: ActionDefinition[];
  captureWhileLoading?: boolean;
};

export type NetworkObservation = {
  method: string;
  path: string;
  status: number;
  requestBody?: unknown;
  responseBody?: unknown;
  mockId?: string;
  undefined: boolean;
  actionSequence?: number;
};

export type ScreenObservation = {
  route: string;
  state: string;
  /** Verbatim primary heading observed on the page. */
  name?: string;
  /** Repository-relative capture of this exact transition point. */
  screenshot?: string;
};

export type RelatedHttpObservation = {
  method: string;
  path: string;
  status: number;
  mockId?: string;
  undefined: boolean;
};

export type ActionTransitionObservation = {
  sequence: number;
  from: ScreenObservation;
  action: ActionDefinition;
  to: ScreenObservation;
  condition: {
    role: string;
    featureFlags: Record<string, boolean | number | string>;
    outcome: string;
    scenarioOutcome: string;
  };
  relatedHttp: RelatedHttpObservation[];
};

const sensitiveKey = /^(authorization|cookie|set-cookie|password|token|accessToken|refreshToken|sessionId|email)$/i;
const sensitiveControl = /(password|passcode|secret|token|パスワード|暗証|トークン)/i;

export function sanitize(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sanitize);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([key, item]) => [key, sensitiveKey.test(key) ? '[REDACTED]' : sanitize(item)]));
  }
  return value;
}

/** Redacts values typed into credential-like controls while retaining the UI action itself. */
export function sanitizeActions(actions: ActionDefinition[]): ActionDefinition[] {
  return actions.map((action) => {
    const copy = sanitize(action) as ActionDefinition;
    if (copy.value !== undefined && sensitiveControl.test(`${copy.label ?? ''} ${copy.name ?? ''}`)) {
      return { ...copy, value: '[REDACTED]' };
    }
    return copy;
  });
}

export function buildActionTransitions(
  scenario: Scenario,
  points: ScreenObservation[],
  requests: NetworkObservation[],
): ActionTransitionObservation[] {
  const actions = sanitizeActions(scenario.actions ?? []);
  if (points.length !== actions.length + 1) {
    throw new Error(`Transition points for ${scenario.id} must contain one point before and after every action`);
  }
  const role = scenario.environment?.role ?? 'UNSPECIFIED';
  const featureFlags = scenario.environment?.featureFlags ?? {};
  return actions.map((action, sequence) => ({
    sequence,
    from: points[sequence] as ScreenObservation,
    action,
    to: points[sequence + 1] as ScreenObservation,
    condition: {
      role,
      featureFlags,
      outcome: (points[sequence + 1] as ScreenObservation).state,
      scenarioOutcome: scenario.state,
    },
    relatedHttp: requests
      .filter((request) => request.actionSequence === sequence)
      .map(({ method, path, status, mockId, undefined: undefinedCommunication }) => ({
        method,
        path,
        status,
        ...(mockId === undefined ? {} : { mockId }),
        undefined: undefinedCommunication,
      })),
  }));
}

export function normalizePath(input: string): string {
  const pathname = new URL(input, 'http://localhost').pathname;
  return pathname
    .split('/')
    .map((segment) => /^\d+$/.test(segment) ? '{id}' : segment)
    .join('/');
}

/** Drops environment-specific origins and query data before persisting a captured page location. */
export function portablePageUrl(input: string): string {
  return new URL(input, 'http://localhost').pathname;
}

export function pathMatches(template: string, actual: string): boolean {
  const escaped = template.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/\\\{[^}]+\\\}/g, '[^/]+');
  return new RegExp(`^${escaped}$`).test(actual);
}
