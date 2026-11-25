import { Link } from 'react-router'
import { cn } from '../../lib/cn'

/** Primary call-to-action: solid, high-contrast, with an arrow that nudges on hover. */
export function MovingBorderButton({ to, children, className }) {
  return (
    <Link
      to={to}
      className={cn(
        'group inline-flex h-12 items-center justify-center gap-2 rounded-xl bg-ink px-6 text-[15px] font-medium text-canvas',
        'transition-colors hover:bg-white active:scale-[0.98] [&_svg]:transition-transform hover:[&_svg]:translate-x-0.5',
        className,
      )}
    >
      {children}
    </Link>
  )
}
