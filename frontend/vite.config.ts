import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ command, mode }) => {
  const { VITE_API_PROXY_TARGET = 'http://localhost:8080' } = loadEnv(mode, '.', 'VITE_');

  return {
    plugins: [vue()],
    base: command === 'serve' ? '/' : '/app/',
    server: {
      host: '0.0.0.0',
      proxy: {
        '/resources': VITE_API_PROXY_TARGET,
        '/reservations': VITE_API_PROXY_TARGET,
        '/admin': VITE_API_PROXY_TARGET,
        '/actuator': VITE_API_PROXY_TARGET,
        '/console.css': VITE_API_PROXY_TARGET
      }
    },
    build: {
      outDir: '../target/classes/static/app',
      emptyOutDir: true,
      rollupOptions: {
        input: 'src/main.ts',
        output: {
          entryFileNames: 'app.js',
          chunkFileNames: 'assets/[name]-[hash].js',
          assetFileNames: 'assets/[name]-[hash][extname]'
        }
      }
    }
  };
});