import { Suspense, lazy, useEffect } from 'react'
import { Route, Routes, useLocation } from 'react-router'
import { MotionConfig } from 'motion/react'
import { Toaster } from 'sonner'
import { Navbar } from './components/layout/Navbar'
import { Footer } from './components/layout/Footer'
import { SandboxProvider } from './hooks/useSandbox'
import Landing from './pages/Landing'
import NotFound from './pages/NotFound'

// The landing page ships in the main bundle; other pages load when first visited.
const Playground = lazy(() => import('./pages/Playground'))
const Lab = lazy(() => import('./pages/Lab'))
const Explorer = lazy(() => import('./pages/Explorer'))
const HowItWorks = lazy(() => import('./pages/HowItWorks'))

function PageFallback() {
  return <div className="mx-auto h-[60vh] max-w-7xl px-6 py-10"><div className="h-10 w-64 animate-pulse rounded-lg bg-white/[0.06]" /></div>
}

function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => { window.scrollTo(0, 0) }, [pathname])
  return null
}

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <SandboxProvider>
        <ScrollToTop />
        <div className="flex min-h-dvh flex-col">
          <Navbar />
          <main className="flex-1 pt-20">
            <Suspense fallback={<PageFallback />}>
            <Routes>
              <Route path="/" element={<Landing />} />
              <Route path="/playground" element={<Playground />} />
              <Route path="/lab" element={<Lab />} />
              <Route path="/explorer" element={<Explorer />} />
              <Route path="/how-it-works" element={<HowItWorks />} />
              <Route path="*" element={<NotFound />} />
            </Routes>
            </Suspense>
          </main>
          <Footer />
        </div>
        <Toaster
          theme="dark"
          position="bottom-right"
          toastOptions={{ className: '!bg-raised !border-line !text-ink !rounded-xl' }}
        />
      </SandboxProvider>
    </MotionConfig>
  )
}
