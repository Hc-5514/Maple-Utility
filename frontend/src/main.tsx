import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'

async function enableMocking() {
  if (import.meta.env.VITE_USE_MOCK !== 'true') return
  const { worker } = await import('./mocks/browser')
  return worker.start({ onUnhandledRequest: 'bypass' })
}

enableMocking().then(() => {
  const container = document.getElementById('root')
  if (!container) throw new Error('#root element not found')

  createRoot(container).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
