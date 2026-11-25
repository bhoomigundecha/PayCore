import { NavLink } from 'react-router'
import { motion } from 'motion/react'
import { BookOpen, FlaskConical, ScrollText, Wallet } from 'lucide-react'
import { cn } from '../../lib/cn'
import { GitHubIcon, LogoMark } from '../ui/misc'
import { HealthPill } from './HealthPill'
import { UserMenu } from './UserMenu'

export const GITHUB_URL = 'https://github.com/bhoomigundecha/PayCore'

const LINKS = [
  { to: '/playground', label: 'Playground', icon: Wallet },
  { to: '/lab', label: 'Break-it Lab', icon: FlaskConical },
  { to: '/explorer', label: 'Ledger Explorer', icon: ScrollText },
  { to: '/how-it-works', label: 'How it works', icon: BookOpen },
]

/** Small label that appears under an icon on hover or keyboard focus. */
export function Tip({ children, className }) {
  return (
    <span
      role="tooltip"
      className={cn(className, "pointer-events-none absolute top-full left-1/2 mt-2.5 -translate-x-1/2 rounded-md border border-line-strong bg-raised px-2 py-1 text-[11px] font-medium whitespace-nowrap text-ink opacity-0 transition-opacity duration-150 group-hover:opacity-100 group-focus-visible:opacity-100")}
    >
      {children}
    </span>
  )
}

/** A compact, icon-only pill that floats centred at the top of every page. Names show as tooltips. */
export function Navbar() {
  return (
    <header className="pointer-events-none fixed inset-x-0 top-4 z-50 flex justify-center px-4">
      <nav className="pointer-events-auto flex h-12 items-center gap-1 rounded-full sm:gap-2.5 border border-line-strong bg-surface/75 px-2 py-1.5 shadow-[0_12px_40px_-12px_rgb(0_0_0/0.9)] backdrop-blur-xl">
        <NavLink to="/" className="group relative grid size-9 place-items-center rounded-full transition-colors hover:bg-white/[0.06]" aria-label="PayCore home">
          <LogoMark />
          <Tip>PayCore</Tip>
        </NavLink>
        <span aria-hidden className="h-5 w-px bg-line-strong" />

        {LINKS.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} aria-label={label} className="group relative grid size-9 place-items-center rounded-full">
            {({ isActive }) => (
              <>
                {isActive && (
                  <motion.span layoutId="nav-active" className="absolute inset-0 rounded-full bg-white/[0.08]" transition={{ type: 'spring', stiffness: 400, damping: 34 }} />
                )}
                <Icon className={cn('relative size-[18px] transition-colors', isActive ? 'text-brand' : 'text-muted group-hover:text-ink')} aria-hidden />
                <Tip>{label}</Tip>
              </>
            )}
          </NavLink>
        ))}

        <span aria-hidden className="h-5 w-px bg-line-strong" />
        <HealthPill compact />
        <a href={GITHUB_URL} target="_blank" rel="noreferrer" aria-label="Source on GitHub" className="group relative grid size-9 place-items-center rounded-full text-muted transition-colors hover:bg-white/[0.06] hover:text-ink">
          <GitHubIcon className="size-[17px]" />
          <Tip>Source code</Tip>
        </a>
        <UserMenu />
      </nav>
    </header>
  )
}
