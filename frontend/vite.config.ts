import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Dev: Vite on :5173 proxies /api to Spring Boot on :8080.
// Build: output goes straight into Spring Boot's static folder, so one jar serves everything.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } },
  build: { outDir: '../backend/src/main/resources/static', emptyOutDir: true, chunkSizeWarningLimit: 900 },
})
