import { defineConfig } from 'vite';
import { resolve } from 'path';

// Vite configuration for AppBana Studio
// - Dev server proxies backend API calls to the Java service on :8080
// - Build outputs production assets into resources so they can be packaged into the shaded JAR
export default defineConfig({
  root: '.',
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      },
      '/apps': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      },
      '/schema': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      },
      '/openapi.json': 'http://localhost:8080',
      '/ui/datasource': 'http://localhost:8080',
      '/ui/datasource/': 'http://localhost:8080'
    }
  },
  build: {
    outDir: 'src/main/resources/ui/dist',
    emptyOutDir: true,
    sourcemap: true,
    assetsDir: 'assets',
    rollupOptions: {
      input: {
        index: resolve(__dirname, 'index.html'),
        studio: resolve(__dirname, 'studio.html')
      },
      output: {
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]'
      }
    }
  }
});
