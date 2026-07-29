import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { captureEnvironment, capturePassthroughArguments, loadCaptureOptions } from './config';
import { runDiscovery } from './discover';

const args = process.argv.slice(2);
const options = loadCaptureOptions(args);
runDiscovery(options);

const packageDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const result = spawnSync(npm, ['exec', '--', 'playwright', 'test', ...capturePassthroughArguments(args)], {
  cwd: packageDirectory,
  env: { ...process.env, ...captureEnvironment(options) },
  stdio: 'inherit',
});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
