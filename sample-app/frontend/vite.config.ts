import { defineConfig } from 'vite';

export default defineConfig(() => {
  const backendPort = process.env.BACKEND_PORT ?? '18080';
  const frontendPort = Number.parseInt(process.env.FRONTEND_PORT ?? '5173', 10);
  if (!Number.isInteger(frontendPort) || frontendPort < 1 || frontendPort > 65_535) {
    throw new Error('FRONTEND_PORT must be an integer between 1 and 65535');
  }
  return {
    server: {
      port: frontendPort,
      strictPort: true,
      proxy: { '/api': `http://127.0.0.1:${backendPort}` },
    },
    preview: {
      port: frontendPort,
      strictPort: true,
    },
    build: { sourcemap: true },
  };
});
