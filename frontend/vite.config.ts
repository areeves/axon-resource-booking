import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ command }) => ({
  plugins: [vue()],
  base: command === 'serve' ? '/' : '/app/',
  server: {
    host: '0.0.0.0',
    proxy: {
      '/resources': 'http://api:8080',
      '/reservations': 'http://api:8080',
      '/admin': 'http://api:8080',
      '/actuator': 'http://api:8080',
      '/console.css': 'http://api:8080'
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
}));