import { useRef } from 'react'
import { Link } from 'react-router'
import { motion, useMotionTemplate, useMotionValue, useSpring } from 'motion/react'
import { GitHubIcon } from '../ui/misc'
import { GITHUB_URL } from './Navbar'

/**
 * Credit line on top, then a giant "PayCore" wordmark that bleeds off the bottom of the page.
 * The letters are a dark grey; a soft light follows the cursor across them.
 */
export function Footer() {
  const ref = useRef(null)
  const x = useSpring(useMotionValue(-1000), { stiffness: 220, damping: 30 })
  const y = useSpring(useMotionValue(-1000), { stiffness: 220, damping: 30 })
  const light = useMotionTemplate`radial-gradient(420px circle at ${x}px ${y}px, #3a4150, #16181d 60%)`

  const onMove = (e) => {
    const rect = ref.current.getBoundingClientRect()
    x.set(e.clientX - rect.left)
    y.set(e.clientY - rect.top)
  }

  return (
    <footer className="relative mt-32 overflow-hidden border-t border-line">
      <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 px-4 pt-10 text-sm text-muted">
        <span>Designed and built by <span className="text-ink">Bhoomi Gundecha</span></span>
        <span aria-hidden className="hidden h-4 w-px bg-line-strong sm:block" />
        <Link to="/how-it-works" className="transition-colors hover:text-ink">How it works</Link>
        <a href="/swagger-ui/index.html" className="transition-colors hover:text-ink">API docs</a>
        <a href={GITHUB_URL} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 transition-colors hover:text-ink">
          <GitHubIcon className="size-4" /> Source
        </a>
      </div>

      <div
        ref={ref}
        onMouseMove={onMove}
        onMouseLeave={() => { x.set(-1000); y.set(-1000) }}
        className="relative mt-8 select-none"
        aria-hidden
      >
        <motion.p
          className="translate-y-[14%] text-center text-[25vw] leading-[0.8] font-semibold tracking-[-0.07em] whitespace-nowrap text-transparent"
          style={{ backgroundImage: light, WebkitBackgroundClip: 'text', backgroundClip: 'text' }}
        >
          PayCore
        </motion.p>
        {/* Fade the bottom of the letters into the page */}
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-canvas to-transparent" />
      </div>
    </footer>
  )
}
