import { useState } from 'react'
import { NavLink, useLocation } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { Menu, X } from 'lucide-react'
import { cn } from '../../lib/cn'
import { GitHubIcon, Logo } from '../ui/misc'
import { HealthPill } from './HealthPill'

export const GITHUB_URL = 'https://github.com/bhoomigundecha/PayCore'

const LINKS = [
  { to: '/playground', label: 'Playground' },
  { to: '/lab', label: 'Lab' },
  { to: '/explorer', label: 'Explorer' },
  { to: '/how-it-works', label: 'How it works' },
]

/** A compact pill that floats centred at the top of every page. */
export function Navbar() {
  const location = useLocation()
  // The mobile menu stays open only on the page where it was opened, so navigating closes it.
  const [openOn, setOpenOn] = useState(null)
  const open = openOn === location.pathname

  return (
    <header className="pointer-events-none fixed inset-x-0 top-4 z-50 flex flex-col items-center px-4">
      <nav className="pointer-events-auto flex h-12 items-center gap-1 rounded-full border border-line-strong bg-surface/75 py-1.5 pr-1.5 pl-4 shadow-[0_12px_40px_-12px_rgb(0_0_0/0.9)] backdrop-blur-xl">
        <NavLink to="/" className="mr-2 text-[15px] text-ink" aria-label="PayCore home"><Logo compact /></NavLink>

        <div className="hidden items-center md:flex">
          {LINKS.map((link) => (
            <NavLink key={link.to} to={link.to} className="relative rounded-full px-3.5 py-1.5 text-sm">
              {({ isActive }) => (
                <>
                  {isActive && <motion.span layoutId="nav-active" className="absolute inset-0 rounded-full bg-white/[0.08]" transition={{ type: 'spring', stiffness: 400, damping: 34 }} />}
                  <span className={cn('relative transition-colors', isActive ? 'text-ink' : 'text-muted hover:text-ink')}>{link.label}</span>
                </>
              )}
            </NavLink>
          ))}
        </div>

        <span aria-hidden className="mx-1 hidden h-5 w-px bg-line-strong md:block" />
        <HealthPill compact />
        <a href={GITHUB_URL} target="_blank" rel="noreferrer" className="grid size-9 place-items-center rounded-full text-muted transition-colors hover:bg-white/[0.06] hover:text-ink" aria-label="Source on GitHub">
          <GitHubIcon className="size-[17px]" />
        </a>
        <button
          type="button"
          onClick={() => setOpenOn(open ? null : location.pathname)}
          className="grid size-9 place-items-center rounded-full text-muted hover:bg-white/[0.06] hover:text-ink md:hidden"
          aria-label="Menu"
          aria-expanded={open}
        >
          {open ? <X className="size-[18px]" /> : <Menu className="size-[18px]" />}
        </button>
      </nav>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.98 }}
            transition={{ duration: 0.15 }}
            className="pointer-events-auto mt-2 w-full max-w-xs rounded-2xl border border-line-strong bg-surface/95 p-2 shadow-2xl backdrop-blur-xl md:hidden"
          >
            {LINKS.map((link) => (
              <NavLink key={link.to} to={link.to} className={({ isActive }) => cn('block rounded-xl px-3 py-2.5 text-sm', isActive ? 'bg-white/[0.07] text-ink' : 'text-muted')}>
                {link.label}
              </NavLink>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  )
}
