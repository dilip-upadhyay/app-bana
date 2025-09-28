import { defineConfig } from 'vite';

// Vite configuration for AppBana Studio
// - Dev server proxies backend API calls to the Java service on :8080
// - Build outputs production assets into resources so they can be packaged into the shaded JAR
export default defineConfig({
  root: '.',
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/schema': 'http://localhost:8080',
      '/openapi.json': 'http://localhost:8080',
      '/ui/datasource': 'http://localhost:8080',
      '/ui/datasource/': 'http://localhost:8080'
    }
  },
  build: {
    outDir: 'src/main/resources/ui/dist',
    emptyOutDir: true,
    sourcemap: true,
    assetsDir: 'assets'
  }
});

