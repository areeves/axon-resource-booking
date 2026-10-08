import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const { VITE_API_PROXY_TARGET = 'http://localhost:8080' } = loadEnv(mode, '.', 'VITE_');

  return {
    plugins: [vue()],
    base: '/',
    server: {
      host: '0.0.0.0',
      proxy: {
        '/resources': VITE_API_PROXY_TARGET,
        '/reservations': VITE_API_PROXY_TARGET,
        '/admin': VITE_API_PROXY_TARGET,
        '/actuator': VITE_API_PROXY_TARGET
      }
    }
  };
});