import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
  root: 'src',
  server: {
    port: 5173,
    host: '0.0.0.0',
    // Allow Spring Boot at :8080 to load assets from Vite
    cors: true,
    // Serve images from src/img/ at /img/* for Vite dev
    // Thymeleaf references /assets/img/* — handled by the origin config below
    origin: 'http://localhost:5173',
  },
  build: {
    outDir: '../../src/main/resources/static',
    emptyOutDir: true,
    manifest: true,
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'src/css/main.css'),
        app: resolve(__dirname, 'src/js/main.js'),
      },
      output: {
        entryFileNames: 'assets/js/[name].[hash].js',
        chunkFileNames: 'assets/js/[name].[hash].js',
        assetFileNames: 'assets/css/[name].[hash][extname]',
      },
    },
  },
});
