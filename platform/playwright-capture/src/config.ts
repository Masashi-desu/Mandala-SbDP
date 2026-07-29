import fs from 'node:fs';
import path from 'node:path';
import { load } from 'js-yaml';

type CaptureConfiguration = {
  mandala?: {
    source?: { frontend?: { root?: string } };
    playwright?: {
      baseUrl?: string;
      scenarios?: string[];
      observations?: string;
      screenshots?: string;
      output?: { observations?: string; screenshots?: string };
      webServer?: {
        command?: string;
        url?: string;
        reuseExistingServer?: boolean;
        timeoutMs?: number;
      };
    };
    output?: { site?: string };
  };
};

export type CaptureWebServer = {
  command: string;
  url: string;
  reuseExistingServer: boolean;
  timeoutMs: number;
};

export type CaptureOptions = {
  repositoryRoot: string;
  configPath: string;
  frontendRoot: string;
  scenarioPatterns: string[];
  observations: string;
  observationsRoot: string;
  screenshotsRoot: string;
  baseUrl: string;
  webServer?: CaptureWebServer;
};

type ParsedArguments = {
  values: Map<string, string[]>;
  booleans: Map<string, boolean>;
  passthrough: string[];
};

const valueFlags = new Set([
  'repository',
  'repository-root',
  'config',
  'frontend-root',
  'scenario',
  'scenarios',
  'observations',
  'screenshots',
  'base-url',
  'web-server-command',
  'web-server-url',
  'web-server-timeout',
]);

const booleanFlags = new Map<string, [string, boolean]>([
  ['reuse-existing-server', ['reuse-existing-server', true]],
  ['no-reuse-existing-server', ['reuse-existing-server', false]],
  ['web-server', ['web-server-enabled', true]],
  ['no-web-server', ['web-server-enabled', false]],
]);

function parseArguments(args: string[]): ParsedArguments {
  const parsed: ParsedArguments = { values: new Map(), booleans: new Map(), passthrough: [] };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (!argument?.startsWith('--')) {
      if (argument !== undefined) parsed.passthrough.push(argument);
      continue;
    }
    const equals = argument.indexOf('=');
    const name = argument.slice(2, equals < 0 ? undefined : equals);
    const boolean = booleanFlags.get(name);
    if (boolean) {
      parsed.booleans.set(boolean[0], boolean[1]);
      continue;
    }
    if (!valueFlags.has(name)) {
      parsed.passthrough.push(argument);
      continue;
    }
    const inlineValue = equals < 0 ? undefined : argument.slice(equals + 1);
    const nextValue = inlineValue ?? args[index + 1];
    if (nextValue === undefined || (inlineValue === undefined && nextValue.startsWith('--'))) {
      throw new Error(`Missing value for --${name}`);
    }
    if (inlineValue === undefined) index += 1;
    const values = parsed.values.get(name) ?? [];
    values.push(nextValue);
    parsed.values.set(name, values);
  }
  return parsed;
}

function lastValue(parsed: ParsedArguments, ...names: string[]): string | undefined {
  for (const name of names) {
    const values = parsed.values.get(name);
    if (values?.length) return values.at(-1);
  }
  return undefined;
}

function nonBlank(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function findRepository(start: string): string {
  let current = path.resolve(start);
  for (;;) {
    if (fs.existsSync(path.join(current, 'mandala/config/mandala.yml'))) return current;
    const parent = path.dirname(current);
    if (parent === current) return path.resolve(start);
    current = parent;
  }
}

function resolveFromRepository(repositoryRoot: string, configuredPath: string): string {
  return path.resolve(repositoryRoot, configuredPath);
}

function ensureArtifactInsideRepository(repositoryRoot: string, target: string, label: string): string {
  const resolved = resolveFromRepository(repositoryRoot, target);
  const relative = path.relative(repositoryRoot, resolved);
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error(`${label} must be inside repository root: ${target}`);
  }
  return resolved;
}

function parseScenarioEnvironment(value: string | undefined): string[] | undefined {
  const input = nonBlank(value);
  if (!input) return undefined;
  if (input.startsWith('[')) {
    const parsed = JSON.parse(input) as unknown;
    if (!Array.isArray(parsed) || parsed.some((item) => typeof item !== 'string')) {
      throw new Error('MANDALA_CAPTURE_SCENARIOS must be a JSON string array, comma-separated list, or newline-separated list');
    }
    return parsed;
  }
  return input.split(/[\n,]/).map((item) => item.trim()).filter(Boolean);
}

function parseBoolean(value: string | undefined, label: string): boolean | undefined {
  const input = nonBlank(value)?.toLowerCase();
  if (input === undefined) return undefined;
  if (['true', '1', 'yes', 'on'].includes(input)) return true;
  if (['false', '0', 'no', 'off'].includes(input)) return false;
  throw new Error(`${label} must be true or false`);
}

function parseTimeout(value: string | number | undefined): number | undefined {
  if (value === undefined || value === '') return undefined;
  const parsed = typeof value === 'number' ? value : Number.parseInt(value, 10);
  if (!Number.isFinite(parsed) || parsed <= 0) throw new Error('web server timeout must be a positive number');
  return parsed;
}

function validateUrl(value: string, label: string): string {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error(`${label} must use http or https`);
  return value.replace(/\/$/, '');
}

/** Returns the non-wildcard directory portion of a repository-relative glob. */
export function globRoot(repositoryRoot: string, pattern: string): string {
  const normalized = pattern.replaceAll('\\', '/');
  const wildcard = normalized.search(/[*?[{]/);
  if (wildcard < 0) {
    const resolved = resolveFromRepository(repositoryRoot, pattern);
    return path.extname(resolved) ? path.dirname(resolved) : resolved;
  }
  const prefix = normalized.slice(0, wildcard);
  const slash = prefix.lastIndexOf('/');
  const directory = slash < 0 ? '.' : prefix.slice(0, slash) || '/';
  return resolveFromRepository(repositoryRoot, directory);
}

export function loadCaptureOptions(
  args: string[] = process.argv.slice(2),
  environment: NodeJS.ProcessEnv = process.env,
  workingDirectory: string = process.cwd(),
): CaptureOptions {
  const parsed = parseArguments(args);
  const explicitRepository = nonBlank(lastValue(parsed, 'repository-root', 'repository'))
    ?? nonBlank(environment.MANDALA_CAPTURE_REPOSITORY_ROOT)
    ?? nonBlank(environment.MANDALA_CAPTURE_REPOSITORY)
    ?? nonBlank(environment.MANDALA_REPOSITORY_ROOT);
  const repositoryRoot = path.resolve(explicitRepository ?? findRepository(environment.INIT_CWD ?? workingDirectory));
  if (!fs.existsSync(repositoryRoot) || !fs.statSync(repositoryRoot).isDirectory()) {
    throw new Error(`Repository root does not exist: ${repositoryRoot}`);
  }

  const configValue = nonBlank(lastValue(parsed, 'config'))
    ?? nonBlank(environment.MANDALA_CAPTURE_CONFIG)
    ?? 'mandala/config/mandala.yml';
  const configPath = resolveFromRepository(repositoryRoot, configValue);
  if (!fs.existsSync(configPath)) throw new Error(`Mandala configuration does not exist: ${configPath}`);
  const configuration = load(fs.readFileSync(configPath, 'utf8')) as CaptureConfiguration;
  const mandala = configuration.mandala;
  const playwright = mandala?.playwright;

  const frontendValue = nonBlank(lastValue(parsed, 'frontend-root'))
    ?? nonBlank(environment.MANDALA_CAPTURE_FRONTEND_ROOT)
    ?? nonBlank(mandala?.source?.frontend?.root);
  if (!frontendValue) throw new Error('Frontend root is required (mandala.source.frontend.root, MANDALA_CAPTURE_FRONTEND_ROOT, or --frontend-root)');
  const frontendRoot = resolveFromRepository(repositoryRoot, frontendValue);
  if (!fs.existsSync(frontendRoot) || !fs.statSync(frontendRoot).isDirectory()) {
    throw new Error(`Frontend root does not exist: ${frontendRoot}`);
  }

  const cliScenarios = [...(parsed.values.get('scenario') ?? []), ...(parsed.values.get('scenarios') ?? [])];
  const scenarioPatterns = cliScenarios.length
    ? cliScenarios
    : parseScenarioEnvironment(environment.MANDALA_CAPTURE_SCENARIOS) ?? playwright?.scenarios ?? [];
  if (!scenarioPatterns.length) throw new Error('At least one scenario pattern is required');

  const observations = nonBlank(lastValue(parsed, 'observations'))
    ?? nonBlank(environment.MANDALA_CAPTURE_OBSERVATIONS)
    ?? nonBlank(playwright?.output?.observations)
    ?? nonBlank(playwright?.observations);
  if (!observations) throw new Error('Observations output is required');
  const observationsRoot = ensureArtifactInsideRepository(repositoryRoot, globRoot(repositoryRoot, observations), 'Observations output');

  const configuredScreenshots = nonBlank(lastValue(parsed, 'screenshots'))
    ?? nonBlank(environment.MANDALA_CAPTURE_SCREENSHOTS)
    ?? nonBlank(playwright?.output?.screenshots)
    ?? nonBlank(playwright?.screenshots);
  const siteOutput = nonBlank(mandala?.output?.site);
  const screenshotsValue = configuredScreenshots
    ?? (siteOutput ? path.join(path.dirname(siteOutput), 'screenshots') : undefined);
  if (!screenshotsValue) throw new Error('Screenshots output is required');
  const screenshotsRoot = ensureArtifactInsideRepository(repositoryRoot, screenshotsValue, 'Screenshots output');

  const baseUrlValue = nonBlank(lastValue(parsed, 'base-url'))
    ?? nonBlank(environment.MANDALA_CAPTURE_BASE_URL)
    ?? nonBlank(playwright?.baseUrl);
  if (!baseUrlValue) throw new Error('Playwright base URL is required');
  const baseUrl = validateUrl(baseUrlValue, 'Playwright base URL');

  const configuredWebServer = playwright?.webServer;
  const enabledFromArguments = parsed.booleans.get('web-server-enabled');
  const enabledFromEnvironment = parseBoolean(environment.MANDALA_CAPTURE_WEB_SERVER_ENABLED, 'MANDALA_CAPTURE_WEB_SERVER_ENABLED');
  const webServerCommand = nonBlank(lastValue(parsed, 'web-server-command'))
    ?? nonBlank(environment.MANDALA_CAPTURE_WEB_SERVER_COMMAND)
    ?? nonBlank(configuredWebServer?.command);
  const webServerEnabled = enabledFromArguments ?? enabledFromEnvironment ?? webServerCommand !== undefined;
  let webServer: CaptureWebServer | undefined;
  if (webServerEnabled) {
    if (!webServerCommand) throw new Error('A web server command is required when web server management is enabled');
    const url = validateUrl(
      nonBlank(lastValue(parsed, 'web-server-url'))
        ?? nonBlank(environment.MANDALA_CAPTURE_WEB_SERVER_URL)
        ?? nonBlank(configuredWebServer?.url)
        ?? baseUrl,
      'Playwright web server URL',
    );
    const reuseExistingServer = parsed.booleans.get('reuse-existing-server')
      ?? parseBoolean(environment.MANDALA_CAPTURE_REUSE_EXISTING_SERVER, 'MANDALA_CAPTURE_REUSE_EXISTING_SERVER')
      ?? configuredWebServer?.reuseExistingServer
      ?? true;
    const timeoutMs = parseTimeout(
      lastValue(parsed, 'web-server-timeout')
        ?? environment.MANDALA_CAPTURE_WEB_SERVER_TIMEOUT
        ?? configuredWebServer?.timeoutMs,
    ) ?? 120_000;
    webServer = { command: webServerCommand, url, reuseExistingServer, timeoutMs };
  }

  return {
    repositoryRoot,
    configPath,
    frontendRoot,
    scenarioPatterns,
    observations,
    observationsRoot,
    screenshotsRoot,
    baseUrl,
    ...(webServer ? { webServer } : {}),
  };
}

/** Environment passed to Playwright keeps discovery, config, and workers on identical resolved inputs. */
export function captureEnvironment(options: CaptureOptions): Record<string, string> {
  return {
    MANDALA_CAPTURE_REPOSITORY_ROOT: options.repositoryRoot,
    MANDALA_CAPTURE_CONFIG: options.configPath,
    MANDALA_CAPTURE_FRONTEND_ROOT: options.frontendRoot,
    MANDALA_CAPTURE_SCENARIOS: JSON.stringify(options.scenarioPatterns),
    MANDALA_CAPTURE_OBSERVATIONS: options.observations,
    MANDALA_CAPTURE_SCREENSHOTS: options.screenshotsRoot,
    MANDALA_CAPTURE_BASE_URL: options.baseUrl,
    MANDALA_CAPTURE_WEB_SERVER_ENABLED: String(options.webServer !== undefined),
    ...(options.webServer ? {
      MANDALA_CAPTURE_WEB_SERVER_COMMAND: options.webServer.command,
      MANDALA_CAPTURE_WEB_SERVER_URL: options.webServer.url,
      MANDALA_CAPTURE_REUSE_EXISTING_SERVER: String(options.webServer.reuseExistingServer),
      MANDALA_CAPTURE_WEB_SERVER_TIMEOUT: String(options.webServer.timeoutMs),
    } : {}),
  };
}

export function capturePassthroughArguments(args: string[]): string[] {
  return parseArguments(args).passthrough;
}

export function repositoryRelative(repositoryRoot: string, target: string): string {
  const relative = path.relative(repositoryRoot, target);
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error(`Artifact is outside repository root: ${target}`);
  }
  return relative.replaceAll(path.sep, '/');
}
