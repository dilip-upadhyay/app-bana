import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            '@': path.resolve(__dirname, 'src'),
        },
    },
    server: {
        port: 5174,
        proxy: {
            '/api/ai': { target: 'http://localhost:8081', changeOrigin: true },
            '/api': { target: 'http://localhost:8080', changeOrigin: true },
            '/appbana-studio': { target: 'http://localhost:8080', changeOrigin: true },
            '/schema': { target: 'http://localhost:8080', changeOrigin: true },
            '/health': { target: 'http://localhost:8080', changeOrigin: true },
        },
    },
});
