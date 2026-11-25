import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// In development the UI runs on :5173 and forwards API calls to Spring Boot on :8080.
// In production Spring Boot serves the built UI itself, so the API is same-origin.
const API = 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/auth': API,
      '/activity': API,
      '/notifications': API,
      '/checkout-sessions': API,
      '/cards': API,
      '/accounts': API,
      '/payments': API,
      '/ledger': API,
      '/actuator': API,
      '/swagger-ui': API,
      '/v3': API,
    },
  },
})
