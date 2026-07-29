import fs from 'node:fs';
import path from 'node:path';
import MarkdownIt from 'markdown-it';
import { fileURLToPath } from 'node:url';
import { escapeHtml, relativeTarget, resetOfficialSiteOutput, resolveSampleReference, slugify } from './lib';

type Locale = {
  id: 'ja' | 'en';
  sourceRoot: string;
  landingPrefix: string;
  docsPrefix: string;
  labels: {
    language: string; theme: string; system: string; light: string; dark: string;
    menu: string; menuText: string; search: string; searchText: string; close: string;
    searchLabel: string; suggestionsLabel: string; resultsLabel: string; noResultsLabel: string;
    toc: string; overline: string;
    groups: [string, number, number][];
    landing: {
      navigation: string; docs: string; sample: string; artworkLabel: string;
      collectionLink: string;
    };
  };
};
type Document = {
  source: string; output: string; title: string; order: number; description: string;
  markdown: string; html: string; locale: Locale;
};

const siteRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = path.resolve(siteRoot, '..');
const sourceRoot = path.join(siteRoot, 'src');
const outputRoot = path.join(siteRoot, 'dist');
const legalOutputRoot = path.join(outputRoot, 'legal');
const sampleSourceRoot = path.join(repositoryRoot, 'mandala', 'generated', 'sample-app', 'site');
const sampleOutputRoot = path.join(outputRoot, 'sample');
const locales: Locale[] = [
  {
    id: 'ja', sourceRoot, landingPrefix: '', docsPrefix: 'docs',
    labels: {
      language: '言語', theme: 'テーマ', system: 'システム', light: 'ライト', dark: 'ダーク',
      menu: 'ナビゲーションを開く', menuText: '目次', search: '検索を開く', searchText: '検索', close: '閉じる',
      searchLabel: '公式ドキュメントを検索', suggestionsLabel: 'おすすめのドキュメント',
      resultsLabel: '検索結果', noResultsLabel: '一致するドキュメントはありません。',
      toc: 'このページ', overline: '公式技術ドキュメント',
      groups: [['はじめに', 0, 4], ['Adapters', 4, 9], ['運用', 9, 14], ['Reference', 14, 99]],
      landing: {
        navigation: '主要ナビゲーション', docs: 'ドキュメント', sample: 'サンプルMandala',
        artworkLabel: '公開作品', collectionLink: 'The Metropolitan Museum of Art'
      }
    }
  },
  {
    id: 'en', sourceRoot: path.join(sourceRoot, 'en'), landingPrefix: 'en', docsPrefix: 'docs/en',
    labels: {
      language: 'Language', theme: 'Theme', system: 'System', light: 'Light', dark: 'Dark',
      menu: 'Open navigation', menuText: 'Menu', search: 'Open search', searchText: 'Search', close: 'Close',
      searchLabel: 'Search the official documentation', suggestionsLabel: 'Suggested documentation',
      resultsLabel: 'Search results', noResultsLabel: 'No matching documentation found.',
      toc: 'On this page', overline: 'Official technical documentation',
      groups: [['Introduction', 0, 4], ['Adapters', 4, 9], ['Operations', 9, 14], ['Reference', 14, 99]],
      landing: {
        navigation: 'Primary navigation', docs: 'Documentation', sample: 'Sample Mandala',
        artworkLabel: 'Open artwork', collectionLink: 'The Metropolitan Museum of Art'
      }
    }
  }
];

for (const required of [
  'index.html',
  'page-map.json',
  'screens/transitions.html',
  'crud/index.html',
  'er/index.html',
  'reports/evidence.html'
]) {
  if (!fs.existsSync(path.join(sampleSourceRoot, required))) {
    throw new Error(`Sample Mandala output is incomplete (${required} is missing). Run ./scripts/refresh-mandala.sh --full first.`);
  }
}
for (const locale of locales) {
  if (!fs.existsSync(locale.sourceRoot) || !fs.statSync(locale.sourceRoot).isDirectory()) {
    throw new Error(`Documentation locale source is missing: ${locale.sourceRoot}`);
  }
}

const parsedPageMap: unknown = JSON.parse(fs.readFileSync(path.join(sampleSourceRoot, 'page-map.json'), 'utf8'));
if (!parsedPageMap || typeof parsedPageMap !== 'object' || Array.isArray(parsedPageMap)) {
  throw new Error('Sample Mandala page-map.json must be an object');
}
const samplePageMap = Object.fromEntries(Object.entries(parsedPageMap).map(([stableId, target]) => {
  if (typeof target !== 'string') throw new Error(`Sample Mandala page-map target must be a string: ${stableId}`);
  return [stableId, target];
}));

function frontmatter(markdown: string): { metadata: Record<string, string>; body: string } {
  const match = markdown.match(/^---\n([\s\S]*?)\n---\n/);
  if (!match) throw new Error('Every official document requires frontmatter');
  const metadata = Object.fromEntries((match[1] ?? '').split('\n').map((line) => line.split(/:\s*/, 2)).filter((pair) => pair.length === 2));
  return { metadata, body: markdown.slice(match[0].length) };
}

const md = new MarkdownIt({
  html: false, linkify: true, typographer: true,
  highlight: (code, language) => `<pre data-language="${escapeHtml(language || 'text')}"><code>${escapeHtml(code)}</code></pre>`
});
const defaultHeading = md.renderer.rules.heading_open ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options));
const defaultLinkOpen = md.renderer.rules.link_open ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options));
const headingCounts = new Map<string, number>();
md.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
  const title = tokens[idx + 1]?.content ?? '';
  const base = slugify(title); const count = headingCounts.get(base) ?? 0; headingCounts.set(base, count + 1);
  tokens[idx]?.attrSet('id', count ? `${base}-${count + 1}` : base);
  return defaultHeading(tokens, idx, options, env, self);
};
md.renderer.rules.link_open = (tokens, idx, options, env: { output?: string }, self) => {
  const href = tokens[idx]?.attrGet('href') ?? '';
  if (href.startsWith('sample-ref:')) {
    const stableId = decodeURIComponent(href.slice('sample-ref:'.length));
    const sampleTarget = resolveSampleReference(href, samplePageMap);
    tokens[idx]?.attrSet('href', relativeTarget(env.output ?? 'index.html', sampleTarget));
    tokens[idx]?.attrSet('data-sample-stable-id', stableId);
  }
  return defaultLinkOpen(tokens, idx, options, env, self);
};

const documents: Document[] = [];
const sourceNamesByLocale = new Map<string, string[]>();
for (const locale of locales) {
  const sourceNames = fs.readdirSync(locale.sourceRoot).filter((name) => name.endsWith('.md')).sort();
  sourceNamesByLocale.set(locale.id, sourceNames);
  for (const name of sourceNames) {
    const source = path.join(locale.sourceRoot, name);
    const markdown = fs.readFileSync(source, 'utf8');
    const parsed = frontmatter(markdown);
    const title = parsed.metadata.title;
    if (!title) throw new Error(`Missing title: ${source}`);
    const isLanding = name === 'index.md';
    const outputName = isLanding ? 'index.html' : name.replace(/\.md$/, '.html');
    const prefix = isLanding ? locale.landingPrefix : locale.docsPrefix;
    const output = prefix ? `${prefix}/${outputName}` : outputName;
    headingCounts.clear();
    documents.push({
      source: path.relative(sourceRoot, source).split(path.sep).join('/'), output, title,
      order: Number(parsed.metadata.order ?? 999), description: parsed.metadata.description ?? '',
      markdown: parsed.body, html: md.render(parsed.body, { output }), locale
    });
  }
}
const referenceNames = sourceNamesByLocale.get('ja') ?? [];
for (const locale of locales) {
  const names = sourceNamesByLocale.get(locale.id) ?? [];
  if (names.join('\n') !== referenceNames.join('\n')) {
    throw new Error(`Documentation locale ${locale.id} does not contain the same files as ja`);
  }
}
documents.sort((a, b) => a.locale.id.localeCompare(b.locale.id) || a.order - b.order || a.title.localeCompare(b.title));

function splitLandingHtml(html: string): { introduction: string; sections: string[] } {
  const firstSection = html.search(/<h2\b/);
  if (firstSection < 0) return { introduction: html, sections: [] };
  return {
    introduction: html.slice(0, firstSection),
    sections: html.slice(firstSection).split(/(?=<h2\b)/).filter(Boolean)
  };
}

function rewritePublishedLinks(document: Document, html: string): string {
  return html.replace(/href="([^"#:]+)(#[^"]*)?"/g, (all, target: string, anchor = '') => {
    const normalizedSourceTarget = path.posix.normalize(path.posix.join(path.posix.dirname(document.source), target));
    if (normalizedSourceTarget.endsWith('.md')) {
      const linkedDocument = documents.find((entry) => (
        entry.locale.id === document.locale.id && entry.source === normalizedSourceTarget
      ));
      if (!linkedDocument) throw new Error(`Unknown documentation link in ${document.source}: ${target}`);
      return `href="${relativeTarget(document.output, linkedDocument.output)}${anchor}"`;
    }
    if (normalizedSourceTarget === 'sample' || normalizedSourceTarget.startsWith('sample/')) {
      const relative = path.posix.relative(path.posix.dirname(document.output), normalizedSourceTarget);
      const rebasedTarget = target.endsWith('/') ? `${relative}/` : relative;
      return `href="${rebasedTarget || `${path.posix.basename(normalizedSourceTarget)}/`}${anchor}"`;
    }
    return all;
  });
}

resetOfficialSiteOutput(siteRoot, outputRoot);
fs.cpSync(sampleSourceRoot, sampleOutputRoot, {
  recursive: true,
  filter: (source) => path.basename(source) !== '.mandala-generated-files'
});
fs.mkdirSync(legalOutputRoot, { recursive: true });
for (const [sourceName, targetName] of [
  ['LICENSE', 'LICENSE.txt'],
  ['NOTICE', 'NOTICE.txt'],
  ['THIRD_PARTY_NOTICES.md', 'THIRD_PARTY_NOTICES.txt']
] as const) {
  const source = path.join(repositoryRoot, sourceName);
  if (!fs.existsSync(source) || !fs.statSync(source).isFile()) {
    throw new Error(`Required legal document is missing: ${sourceName}`);
  }
  fs.copyFileSync(source, path.join(legalOutputRoot, targetName));
}

for (const document of documents) {
  const localeDocuments = documents.filter((entry) => entry.locale.id === document.locale.id);
  const documentationPages = localeDocuments.filter((entry) => !entry.source.endsWith('index.md'));
  const navigation = document.locale.labels.groups.map(([label, min, max]) => {
    const items = documentationPages.filter((entry) => entry.order >= min && entry.order < max)
      .map((entry) => `<a ${entry.output === document.output ? 'aria-current="page"' : ''} href="${relativeTarget(document.output, entry.output)}">${escapeHtml(entry.title)}</a>`).join('');
    return items ? `<div class="nav-group"><strong>${escapeHtml(label)}</strong>${items}</div>` : '';
  }).join('');
  const documentIndex = documentationPages.indexOf(document);
  const previous = documentIndex > 0 ? documentationPages[documentIndex - 1] : undefined;
  const next = documentIndex >= 0 && documentIndex + 1 < documentationPages.length ? documentationPages[documentIndex + 1] : undefined;
  const pagination = `<nav class="pagination">${previous ? `<a href="${relativeTarget(document.output, previous.output)}">← ${escapeHtml(previous.title)}</a>` : '<span></span>'}${next ? `<a href="${relativeTarget(document.output, next.output)}">${escapeHtml(next.title)} →</a>` : ''}</nav>`;
  const canonicalLinks = rewritePublishedLinks(document, document.html);
  const translated = documents.find((entry) => entry.locale.id !== document.locale.id && entry.source.endsWith(path.posix.basename(document.source)));
  if (!translated) throw new Error(`Missing translated document for ${document.source}`);
  const home = localeDocuments.find((entry) => entry.source.endsWith('index.md'));
  if (!home) throw new Error(`Missing locale home for ${document.locale.id}`);
  const overview = localeDocuments.find((entry) => entry.source.endsWith('overview.md'));
  if (!overview) throw new Error(`Missing locale overview for ${document.locale.id}`);
  const asset = (name: string) => relativeTarget(document.output, `assets/${name}`);
  const sampleHome = relativeTarget(document.output, 'sample/index.html');
  const jaTarget = document.locale.id === 'ja' ? document.output : translated.output;
  const enTarget = document.locale.id === 'en' ? document.output : translated.output;
  const selected = (value: string) => value === document.locale.id ? ' selected' : '';
  const controls = `<div class="header-controls"><label><span>${escapeHtml(document.locale.labels.language)}</span><select data-language aria-label="${escapeHtml(document.locale.labels.language)}"><option data-locale="ja" value="${relativeTarget(document.output, jaTarget)}"${selected('ja')}>日本語</option><option data-locale="en" value="${relativeTarget(document.output, enTarget)}"${selected('en')}>English</option></select></label><label><span>${escapeHtml(document.locale.labels.theme)}</span><select data-theme-select aria-label="${escapeHtml(document.locale.labels.theme)}"><option value="system">${escapeHtml(document.locale.labels.system)}</option><option value="light">${escapeHtml(document.locale.labels.light)}</option><option value="dark">${escapeHtml(document.locale.labels.dark)}</option></select></label></div>`;
  const searchIndex = relativeTarget(document.output, `${document.locale.docsPrefix}/search-index.json`);
  const pageTitle = document.source.endsWith('index.md')
    ? `Mandala SbDP · ${document.locale.id === 'ja' ? '生きた技術ドキュメント' : 'Living Documentation Graph'}`
    : `${document.title} · Mandala SbDP`;
  const head = `<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="light dark"><meta name="theme-color" content="#191d35"><meta name="description" content="${escapeHtml(document.description)}"><title>${escapeHtml(pageTitle)}</title><link rel="icon" href="${asset('favicon.svg')}" type="image/svg+xml"><link rel="stylesheet" href="${asset('site.css')}"><link rel="stylesheet" href="${asset('theme.css')}"></head>`;
  const searchDialog = `<dialog data-search><form method="dialog"><button aria-label="${escapeHtml(document.locale.labels.close)}">×</button></form><label for="search">${escapeHtml(document.locale.labels.searchLabel)}</label><input id="search" type="search" autocomplete="off"><div data-results data-suggestions-label="${escapeHtml(document.locale.labels.suggestionsLabel)}" data-results-label="${escapeHtml(document.locale.labels.resultsLabel)}" data-empty-label="${escapeHtml(document.locale.labels.noResultsLabel)}" aria-live="polite"></div></dialog>`;
  const footer = '<footer class="site-footer"><span>Mandala SbDP · Apache-2.0</span></footer>';
  let html: string;
  if (document.source.endsWith('index.md')) {
    const landing = splitLandingHtml(canonicalLinks);
    const landingIntroduction = document.locale.id === 'ja'
      ? landing.introduction.replace(
        /(<h1\b[^>]*>)([^、<]+、)([^<]+)(<\/h1>)/,
        '$1<span>$2</span><span>$3</span>$4'
      )
      : landing.introduction;
    const sections = landing.sections.map((section, index) => `<section class="landing-section landing-section-${index + 1}">${section}</section>`).join('');
    const overviewTarget = relativeTarget(document.output, overview.output);
    html = `<!doctype html><html lang="${document.locale.id}" data-locale="${document.locale.id}" data-search-index="${searchIndex}">${head}<body class="landing-body"><a class="skip-link" href="#main">${escapeHtml(document.locale.labels.landing.docs)}</a><header class="site-header landing-header"><a class="wordmark" href="${relativeTarget(document.output, home.output)}">Mandala <span>SbDP</span></a><nav class="landing-primary-nav" aria-label="${escapeHtml(document.locale.labels.landing.navigation)}"><a href="${overviewTarget}">${escapeHtml(document.locale.labels.landing.docs)}</a><a href="${sampleHome}">${escapeHtml(document.locale.labels.landing.sample)}</a></nav>${controls}<button data-search-open aria-label="${escapeHtml(document.locale.labels.search)}">${escapeHtml(document.locale.labels.searchText)}</button></header><main id="main" class="landing-page"><section class="landing-hero"><img class="hero-artwork" src="${asset('chakrasamvara-mandala.webp')}" width="1197" height="1600" alt="" aria-hidden="true" fetchpriority="high" decoding="async"><div class="landing-copy"><div class="landing-kicker">Documentation Graph · OSS / Apache-2.0</div>${landingIntroduction}</div><aside class="artwork-credit" aria-label="${escapeHtml(document.locale.labels.landing.artworkLabel)}"><strong>Chakrasamvara Mandala</strong><a href="https://www.metmuseum.org/art/collection/search/38021" rel="external">${escapeHtml(document.locale.labels.landing.collectionLink)} ↗</a></aside></section><div class="landing-sections">${sections}</div></main>${searchDialog}${footer}<script src="${asset('site.js')}" defer></script></body></html>`;
  } else {
    html = `<!doctype html><html lang="${document.locale.id}" data-locale="${document.locale.id}" data-search-index="${searchIndex}">${head}<body><a class="skip-link" href="#main">${escapeHtml(document.locale.labels.overline)}</a><header class="site-header"><a class="wordmark" href="${relativeTarget(document.output, home.output)}">Mandala <span>SbDP</span></a><a class="sample-link" href="${sampleHome}">Sample Mandala</a>${controls}<button data-menu aria-label="${escapeHtml(document.locale.labels.menu)}">${escapeHtml(document.locale.labels.menuText)}</button><button data-search-open aria-label="${escapeHtml(document.locale.labels.search)}">${escapeHtml(document.locale.labels.searchText)}</button></header><div class="shell"><aside data-nav>${navigation}</aside><main id="main" class="docs-main"><div class="overline">${escapeHtml(document.locale.labels.overline)}</div>${canonicalLinks}${pagination}</main><nav class="toc" data-toc><strong>${escapeHtml(document.locale.labels.toc)}</strong></nav></div>${searchDialog}${footer}<script src="${asset('site.js')}" defer></script></body></html>`;
  }
  const target = path.join(outputRoot, document.output);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, html);
}

for (const locale of locales) {
  const localeDocuments = documents.filter((document) => (
    document.locale.id === locale.id && !document.source.endsWith('index.md')
  ));
  const search = localeDocuments.map((document) => ({
    title: document.title, description: document.description, url: path.posix.basename(document.output),
    text: document.markdown.replace(/[`#>*_\[\]()|-]/g, ' ').replace(/\s+/g, ' ').trim()
  }));
  const searchTarget = path.join(outputRoot, locale.docsPrefix, 'search-index.json');
  fs.mkdirSync(path.dirname(searchTarget), { recursive: true });
  fs.writeFileSync(searchTarget, `${JSON.stringify(search, null, 2)}\n`);
}
fs.cpSync(path.join(siteRoot, 'assets'), path.join(outputRoot, 'assets'), { recursive: true });
process.stdout.write(`Built landing pages at the site root, ${referenceNames.length - 1} documentation pages in ${locales.length} locales under site/dist/docs, and the sample Mandala under site/dist/sample\n`);
