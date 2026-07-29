import fs from 'node:fs';
import path from 'node:path';
import { load } from 'js-yaml';
import { loadCaptureOptions, globRoot, type CaptureOptions } from './config';
import type { Scenario } from './model';

function yamlFiles(root: string): string[] {
  if (!fs.existsSync(root)) return [];
  const stat = fs.statSync(root);
  if (stat.isFile()) return /\.ya?ml$/i.test(root) ? [root] : [];
  return fs.readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(root, entry.name);
    return entry.isDirectory() ? yamlFiles(target) : /\.ya?ml$/i.test(entry.name) ? [target] : [];
  });
}

function absolutePattern(repositoryRoot: string, pattern: string): string {
  return path.resolve(repositoryRoot, pattern).replaceAll(path.sep, '/');
}

export function findScenarioFiles(options: CaptureOptions): string[] {
  const files = options.scenarioPatterns.flatMap((pattern) => {
    const root = globRoot(options.repositoryRoot, pattern);
    const wildcard = /[*?[{]/.test(pattern);
    if (!wildcard) return yamlFiles(path.resolve(options.repositoryRoot, pattern));
    const matcher = absolutePattern(options.repositoryRoot, pattern);
    return yamlFiles(root).filter((file) => path.matchesGlob(file.replaceAll(path.sep, '/'), matcher));
  });
  const unique = [...new Set(files.map((file) => path.resolve(file)))].sort();
  if (!unique.length) {
    throw new Error(`No YAML scenario files match: ${options.scenarioPatterns.join(', ')}`);
  }
  return unique;
}

export function loadScenarios(options: CaptureOptions = loadCaptureOptions()): Scenario[] {
  const scenarios = findScenarioFiles(options).flatMap((file) => {
    const value = load(fs.readFileSync(file, 'utf8')) as { defaults?: Pick<Scenario, 'environment'>; scenarios?: unknown } | undefined;
    if (!value || !Array.isArray(value.scenarios)) throw new Error(`Scenario file must contain a scenarios array: ${file}`);
    return (value.scenarios as Scenario[]).map((scenario) => ({
      ...scenario,
      environment: { ...value.defaults?.environment, ...scenario.environment },
    }));
  });
  const ids = new Set<string>();
  for (const scenario of scenarios) {
    if (!scenario || typeof scenario !== 'object' || !scenario.id || !scenario.title || !scenario.route || !scenario.state || !scenario.expect) {
      throw new Error(`Invalid scenario: ${JSON.stringify(scenario)}`);
    }
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(scenario.id)) {
      throw new Error(`Scenario id must be a portable semantic file name: ${scenario.id}`);
    }
    if (ids.has(scenario.id)) throw new Error(`Duplicate scenario id: ${scenario.id}`);
    ids.add(scenario.id);
  }
  return scenarios;
}
