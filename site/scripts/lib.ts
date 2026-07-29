import fs from 'node:fs';
import path from 'node:path';

export function slugify(value: string): string {
  return value.normalize('NFKC').toLowerCase().trim().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '') || 'section';
}

export function relativeTarget(fromHtml: string, target: string): string {
  return path.posix.relative(path.posix.dirname(fromHtml), target) || path.posix.basename(target);
}

export function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character] ?? character);
}

export function resolveSampleReference(reference: string, pageMap: Readonly<Record<string, string>>): string {
  if (!reference.startsWith('sample-ref:')) {
    throw new Error(`Invalid sample reference: ${reference}`);
  }
  const stableId = decodeURIComponent(reference.slice('sample-ref:'.length));
  const target = pageMap[stableId];
  if (!target) {
    throw new Error(`Unknown sample Mandala stable ID: ${stableId}`);
  }
  const normalized = path.posix.normalize(target);
  if (
    target.includes('\\')
    || normalized !== target
    || normalized.startsWith('../')
    || path.posix.isAbsolute(normalized)
    || /^[a-z][a-z0-9+.-]*:/i.test(normalized)
  ) {
    throw new Error(`Unsafe sample Mandala target for ${stableId}: ${target}`);
  }
  return `sample/${normalized}`;
}

export function resetOfficialSiteOutput(siteRoot: string, outputRoot: string): void {
  const normalizedSite = path.resolve(siteRoot);
  const normalizedOutput = path.resolve(outputRoot);
  if (normalizedOutput !== path.join(normalizedSite, 'dist')) {
    throw new Error(`Refusing to clean an unexpected official-site output: ${normalizedOutput}`);
  }
  fs.rmSync(normalizedOutput, { recursive: true, force: true });
  fs.mkdirSync(normalizedOutput, { recursive: true });
}
