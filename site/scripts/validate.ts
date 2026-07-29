import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { resolveSampleReference } from './lib';

const siteRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outputRoot = path.join(siteRoot, 'dist');
const sampleRoot = path.join(outputRoot, 'sample');
const errors: string[] = [];

function walkFiles(root: string): string[] {
  if (!fs.existsSync(root)) return [];
  return fs.readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const absolute = path.join(root, entry.name);
    return entry.isDirectory() ? walkFiles(absolute) : [path.relative(outputRoot, absolute).split(path.sep).join('/')];
  });
}

function isFile(target: string): boolean {
  return fs.existsSync(target) && fs.statSync(target).isFile();
}

function localTarget(source: string, href: string): string | undefined {
  if (!href || href.startsWith('#') || /^[a-z][a-z0-9+.-]*:/i.test(href)) return undefined;
  const withoutFragment = href.split('#', 1)[0]?.split('?', 1)[0] ?? '';
  if (!withoutFragment) return undefined;
  let decoded: string;
  try {
    decoded = decodeURI(withoutFragment);
  } catch {
    errors.push(`${source} -> invalid URL encoding: ${href}`);
    return undefined;
  }
  const candidate = decoded.startsWith('/')
    ? path.resolve(outputRoot, decoded.slice(1))
    : path.resolve(outputRoot, path.dirname(source), decoded);
  const relative = path.relative(outputRoot, candidate);
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    errors.push(`${source} -> target escapes the published bundle: ${href}`);
    return undefined;
  }
  if (fs.existsSync(candidate) && fs.statSync(candidate).isDirectory()) return path.join(candidate, 'index.html');
  return candidate;
}

const files = walkFiles(outputRoot);
const htmlFiles = files.filter((name) => name.endsWith('.html'));
const japaneseDocumentationFiles = htmlFiles.filter((name) => /^docs\/[^/]+\.html$/.test(name));
const englishDocumentationFiles = htmlFiles.filter((name) => /^docs\/en\/[^/]+\.html$/.test(name));
const sampleHtmlFiles = htmlFiles.filter((name) => name.startsWith('sample/'));
const officialHtmlFiles = htmlFiles.filter((name) => !name.startsWith('sample/'));
const linkPattern = /(?:href|src)=["']([^"']+)["']/g;
let stableSampleLinkCount = 0;

for (const name of htmlFiles) {
  const html = fs.readFileSync(path.join(outputRoot, name), 'utf8');
  stableSampleLinkCount += html.match(/data-sample-stable-id=/g)?.length ?? 0;
  if (name.startsWith('sample/') && (!html.includes('data-language') || !html.includes('data-theme-select'))) {
    errors.push(`${name} -> generated Mandala is missing language or theme controls in the header`);
  }
  for (const match of html.matchAll(linkPattern)) {
    const href = match[1] ?? '';
    if (href.startsWith('/')) {
      errors.push(`${name} -> root-relative URL is not GitHub project-site safe: ${href}`);
      continue;
    }
    const target = localTarget(name, href);
    if (target && !isFile(target)) errors.push(`${name} -> missing ${href}`);
  }
}

const expectedDocumentationPages = fs.readdirSync(path.join(siteRoot, 'src'))
  .filter((name) => name.endsWith('.md') && name !== 'index.md').length;
const expectedOfficialHtmlFiles = new Set([
  'index.html',
  'en/index.html',
  ...japaneseDocumentationFiles,
  ...englishDocumentationFiles
]);
for (const name of officialHtmlFiles) {
  if (!expectedOfficialHtmlFiles.has(name)) {
    errors.push(`${name} -> official page is outside the /, /en/, /docs/, and /docs/en/ route contract`);
  }
}
if (japaneseDocumentationFiles.length !== expectedDocumentationPages) {
  errors.push(`Japanese documentation is incomplete under docs/: expected ${expectedDocumentationPages}, found ${japaneseDocumentationFiles.length}`);
}
if (englishDocumentationFiles.length !== expectedDocumentationPages) {
  errors.push(`English documentation is incomplete under docs/en/: expected ${expectedDocumentationPages}, found ${englishDocumentationFiles.length}`);
}
for (const source of japaneseDocumentationFiles) {
  const counterpart = `docs/en/${path.posix.basename(source)}`;
  if (!englishDocumentationFiles.includes(counterpart)) errors.push(`English documentation is missing the counterpart for ${source}`);
}
for (const name of ['index.html', 'en/index.html', ...japaneseDocumentationFiles, ...englishDocumentationFiles]) {
  const html = fs.readFileSync(path.join(outputRoot, name), 'utf8');
  const expectedLocale = name === 'en/index.html' || name.startsWith('docs/en/') ? 'en' : 'ja';
  if (!html.includes(`lang="${expectedLocale}"`) || !html.includes(`data-locale="${expectedLocale}"`)) {
    errors.push(`${name} -> incorrect documentation locale metadata`);
  }
  if (!html.includes('data-language') || !html.includes('data-theme-select')) {
    errors.push(`${name} -> missing language or theme control in the header`);
  }
}
for (const name of ['index.html', 'en/index.html']) {
  const html = fs.readFileSync(path.join(outputRoot, name), 'utf8');
  for (const contract of [
    'class="landing-page"',
    'assets/chakrasamvara-mandala.webp',
    'Chakrasamvara Mandala',
    'https://www.metmuseum.org/art/collection/search/38021'
  ]) {
    if (!html.includes(contract)) errors.push(`${name} -> landing page is missing: ${contract}`);
  }
  const documentationRoute = name === 'index.html' ? 'docs/overview.html' : 'docs/en/overview.html';
  if (!html.includes(documentationRoute) || !html.includes('sample/index.html')) {
    errors.push(`${name} -> landing page is missing a documentation or sample route`);
  }
  const sampleOverviewPrefix = name === 'index.html' ? 'sample/' : '../sample/';
  for (const overviewRoute of [
    `${sampleOverviewPrefix}er/`,
    `${sampleOverviewPrefix}screens/transitions.html`,
    `${sampleOverviewPrefix}crud/`
  ]) {
    if (!html.includes(`href="${overviewRoute}"`)) {
      errors.push(`${name} -> landing page is missing the sample overview route: ${overviewRoute}`);
    }
  }
}
for (const required of [
  'docs/overview.html',
  'docs/en/overview.html',
  'docs/search-index.json',
  'docs/en/search-index.json',
  'legal/LICENSE.txt',
  'legal/NOTICE.txt',
  'legal/THIRD_PARTY_NOTICES.txt',
  'sample/index.html',
  'sample/screens/transitions.html',
  'sample/crud/index.html',
  'sample/er/index.html',
  'sample/reports/evidence.html',
  'sample/page-map.json'
]) {
  if (!files.includes(required)) errors.push(`Published site is incomplete: ${required} is missing`);
}
const licenseTextPath = path.join(outputRoot, 'legal', 'LICENSE.txt');
const noticeTextPath = path.join(outputRoot, 'legal', 'NOTICE.txt');
const thirdPartyTextPath = path.join(outputRoot, 'legal', 'THIRD_PARTY_NOTICES.txt');
if (isFile(licenseTextPath) && !fs.readFileSync(licenseTextPath, 'utf8').includes('Apache License')) {
  errors.push('Published LICENSE.txt does not contain the Apache License');
}
if (isFile(noticeTextPath) && !fs.readFileSync(noticeTextPath, 'utf8').includes('Mandala SbDP')) {
  errors.push('Published NOTICE.txt does not identify Mandala SbDP');
}
if (isFile(thirdPartyTextPath)) {
  const thirdPartyText = fs.readFileSync(thirdPartyTextPath, 'utf8');
  for (const expected of ['Spring Boot', 'Playwright Test', 'Chakrasamvara Mandala', 'CC0-1.0']) {
    if (!thirdPartyText.includes(expected)) errors.push(`Published third-party inventory is missing: ${expected}`);
  }
}
if (sampleHtmlFiles.length < 500) errors.push(`Published sample Mandala is incomplete: only ${sampleHtmlFiles.length} HTML pages`);
const sampleScreenshotCount = files.filter((name) => name.startsWith('sample/screenshots/') && name.endsWith('.png')).length;
if (sampleScreenshotCount < 17) errors.push(`Published sample Mandala is incomplete: only ${sampleScreenshotCount} screenshots`);
if (stableSampleLinkCount < 8) errors.push(`Official documentation has too few stable sample artifact links: ${stableSampleLinkCount}`);
const sampleScriptPath = path.join(sampleRoot, 'assets', 'mandala.js');
const sampleStylesheetPath = path.join(sampleRoot, 'assets', 'mandala.css');
if (isFile(sampleScriptPath)) {
  const script = fs.readFileSync(sampleScriptPath, 'utf8');
  for (const contract of ['mandala.language', 'mandala.theme', "'node.specification': 'Specification'"]) {
    if (!script.includes(contract)) errors.push(`Published sample Mandala does not implement the display contract: ${contract}`);
  }
} else errors.push('Published sample Mandala is missing assets/mandala.js');
if (isFile(sampleStylesheetPath)) {
  const stylesheet = fs.readFileSync(sampleStylesheetPath, 'utf8');
  for (const contract of [
    ':root[data-theme=dark]',
    '@media(prefers-color-scheme:dark)',
    '--canvas:var(--mandala-light-page,#f1eadc)',
    '--accent:var(--mandala-dark-accent,#df7867)'
  ]) {
    if (!stylesheet.includes(contract)) errors.push(`Published sample Mandala does not implement the theme contract: ${contract}`);
  }
} else errors.push('Published sample Mandala is missing assets/mandala.css');

const pageMapPath = path.join(sampleRoot, 'page-map.json');
if (fs.existsSync(pageMapPath)) {
  const pageMap = JSON.parse(fs.readFileSync(pageMapPath, 'utf8')) as Record<string, string>;
  for (const stableId of Object.keys(pageMap)) {
    try {
      const target = path.join(outputRoot, resolveSampleReference(`sample-ref:${encodeURIComponent(stableId)}`, pageMap));
      if (!isFile(target)) errors.push(`page-map.json -> missing target for ${stableId}: ${pageMap[stableId]}`);
    } catch (error) {
      errors.push(error instanceof Error ? error.message : String(error));
    }
  }
}

const safeSampleExtensions = new Set(['.css', '.gif', '.html', '.ico', '.jpeg', '.jpg', '.js', '.json', '.png', '.svg', '.txt', '.webp', '.woff', '.woff2']);
for (const name of files.filter((entry) => entry.startsWith('sample/'))) {
  const base = path.posix.basename(name);
  const extension = path.posix.extname(base).toLowerCase();
  const isUnexpectedJson = extension === '.json' && name !== 'sample/page-map.json' && name !== 'sample/search-index.json';
  if (base === 'mandala.json' || base === 'otlp.json' || isUnexpectedJson || !safeSampleExtensions.has(extension)) {
    errors.push(`Unsafe raw artifact in published sample Mandala: ${name}`);
  }
}

if (errors.length) {
  process.stderr.write(`${[...new Set(errors)].join('\n')}\n`);
  process.exit(1);
}
process.stdout.write(`Validated landing pages at / and /en/, ${japaneseDocumentationFiles.length} documentation pages in 2 locales under /docs, and ${sampleHtmlFiles.length} sample pages under /sample (${sampleScreenshotCount} screenshots, ${stableSampleLinkCount} stable documentation links)\n`);
