import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { relativeTarget, resetOfficialSiteOutput, resolveSampleReference, slugify } from './lib';

describe('official site utilities', () => {
  it('creates stable Unicode heading anchors', () => expect(slugify('Observed と Inferred')).toBe('observed-と-inferred'));
  it('builds portable relative links', () => expect(relativeTarget('guide/start.html', 'reference/cli.html')).toBe('../reference/cli.html'));
  it('resolves a stable sample reference through the generated page map', () => {
    expect(resolveSampleReference('sample-ref:flow:project.create.success', {
      'flow:project.create.success': 'flows/project-create-a1b2/index.html'
    })).toBe('sample/flows/project-create-a1b2/index.html');
  });
  it('rejects missing and unsafe sample references', () => {
    expect(() => resolveSampleReference('sample-ref:flow:missing', {})).toThrow(/Unknown sample Mandala stable ID/);
    expect(() => resolveSampleReference('sample-ref:flow:unsafe', { 'flow:unsafe': '../mandala.json' })).toThrow(/Unsafe sample Mandala target/);
  });
  it('removes stale files only from the dedicated dist directory', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'mandala-site-'));
    const output = path.join(root, 'dist');
    fs.mkdirSync(output);
    fs.writeFileSync(path.join(output, 'removed-page.html'), 'stale');
    try {
      resetOfficialSiteOutput(root, output);
      expect(fs.readdirSync(output)).toEqual([]);
      expect(() => resetOfficialSiteOutput(root, root)).toThrow(/Refusing/);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });
});
