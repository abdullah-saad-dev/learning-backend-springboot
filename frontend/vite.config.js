import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server proxies /auth and /api straight through to Spring Boot.
//
// This is what makes live API calls work without touching the backend. The
// browser only ever talks to localhost:5173, so there is no cross-origin
// request, no CORS bean to add, and the refresh cookie — which is
// SameSite=Strict and scoped to Path=/auth/refresh — is actually sent.
// Point a fetch at "/api/tasks", not "http://localhost:8080/api/tasks".
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
      '/auth': { target: 'http://localhost:8080', changeOrigin: false }
    }
  }
});
