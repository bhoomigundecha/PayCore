import { Link } from 'react-router'
import { cn } from '../../lib/cn'

/**
 * Call-to-action with a light that travels around its border (a rotating conic gradient
 * behind a slightly smaller inner surface).
 */
export function MovingBorderButton({ to, children, className }) {
  return (
    <Link
      to={to}
      className={cn('group relative inline-flex h-12 overflow-hidden rounded-xl p-px transition-transform active:scale-[0.98]', className)}
    >
      <span
        aria-hidden
        className="absolute inset-[-200%] animate-spin-slow bg-[conic-gradient(from_90deg,transparent_0%,transparent_70%,#34d399_80%,#7dd3fc_88%,transparent_100%)]"
      />
      <span className="relative inline-flex size-full items-center justify-center gap-2 rounded-[11px] bg-surface/95 px-6 text-[15px] font-medium text-ink backdrop-blur transition-colors group-hover:bg-raised">
        {children}
      </span>
    </Link>
  )
}
