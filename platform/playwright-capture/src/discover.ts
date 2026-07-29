import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';
import { fileURLToPath } from 'node:url';
import { loadCaptureOptions, repositoryRelative, type CaptureOptions } from './config';

type Candidate = { value: string; file: string; line: number };
export type Discovery = {
  generatedAt: string;
  frontendRoot: string;
  routes: Candidate[];
  clientApis: Array<Candidate & { method: string }>;
  controls: Array<Candidate & { tag: string }>;
  proposedFlows: Array<{ id: string; route: string; confidence: 'INFERRED'; evidence: string[] }>;
};

const excludedDirectories = new Set(['.git', 'build', 'dist', 'node_modules']);

function sourceFiles(root: string): string[] {
  return fs.readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(root, entry.name);
    if (entry.isDirectory()) return excludedDirectories.has(entry.name) ? [] : sourceFiles(target);
    return /\.tsx?$/i.test(entry.name) && !/\.(?:test|spec)\.tsx?$/i.test(entry.name) ? [target] : [];
  });
}

function lineOf(source: ts.SourceFile, node: ts.Node): number {
  return source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
}

function templateValue(node: ts.Expression): string | undefined {
  if (ts.isStringLiteralLike(node)) return node.text;
  if (ts.isTemplateExpression(node)) return node.head.text + node.templateSpans.map((span) => `{${span.expression.getText()}}${span.literal.text}`).join('');
  return undefined;
}

export function runDiscovery(options: CaptureOptions = loadCaptureOptions()): Discovery {
  const generatedAt = process.env.MANDALA_ANALYZED_AT ?? '2026-01-15T03:00:00.000Z';
  const discovery: Discovery = {
    generatedAt,
    frontendRoot: repositoryRelative(options.repositoryRoot, options.frontendRoot),
    routes: [],
    clientApis: [],
    controls: [],
    proposedFlows: [],
  };
  for (const file of sourceFiles(options.frontendRoot)) {
    const source = ts.createSourceFile(file, fs.readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true);
    const relative = repositoryRelative(options.repositoryRoot, file);
    const visit = (node: ts.Node): void => {
      if (ts.isStringLiteralLike(node) && node.text.startsWith('/') && !node.text.startsWith('/api') && /^\/[\w/:.-]+$/.test(node.text)) {
        discovery.routes.push({ value: node.text, file: relative, line: lineOf(source, node) });
      }
      if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'request' && node.arguments.length >= 2) {
        const method = node.arguments[0] && templateValue(node.arguments[0]);
        const apiPath = node.arguments[1] && templateValue(node.arguments[1]);
        if (method && apiPath?.startsWith('/api')) {
          discovery.clientApis.push({ method, value: apiPath.replace(/\{[^}]+\}/g, '{id}'), file: relative, line: lineOf(source, node) });
        }
      }
      if (ts.isNoSubstitutionTemplateLiteral(node) || ts.isTemplateExpression(node)) {
        const value = templateValue(node as ts.Expression) ?? '';
        for (const match of value.matchAll(/<(button|a|input|select|textarea)\b[^>]*>/g)) {
          discovery.controls.push({ tag: match[1] ?? 'unknown', value: match[0], file: relative, line: lineOf(source, node) });
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(source);
  }

  discovery.routes = [...new Map(discovery.routes.map((route) => [route.value, route])).values()]
    .sort((a, b) => a.value.localeCompare(b.value));
  discovery.clientApis = [...new Map(discovery.clientApis.map((call) => [`${call.method}:${call.value}`, call])).values()]
    .sort((a, b) => `${a.method}:${a.value}`.localeCompare(`${b.method}:${b.value}`));
  discovery.proposedFlows = discovery.routes.map((route) => ({
    id: `flow:candidate:${route.value.replaceAll('/', '-').replaceAll(':', '') || 'home'}`,
    route: route.value,
    confidence: 'INFERRED',
    evidence: [
      `${route.file}:${route.line}`,
      ...discovery.clientApis.filter((api) => api.file === route.file).map((api) => `${api.method} ${api.value}`),
    ],
  }));
  const output = path.join(options.observationsRoot, 'discovery.json');
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, `${JSON.stringify(discovery, null, 2)}\n`);
  process.stdout.write(`Discovered ${discovery.routes.length} routes, ${discovery.clientApis.length} client APIs and ${discovery.controls.length} controls -> ${repositoryRelative(options.repositoryRoot, output)}\n`);
  return discovery;
}

const invokedFile = process.argv[1] ? path.resolve(process.argv[1]) : undefined;
if (invokedFile === fileURLToPath(import.meta.url)) runDiscovery();
