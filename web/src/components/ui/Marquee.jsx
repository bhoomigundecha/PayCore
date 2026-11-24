import { cn } from '../../lib/cn'

/** Infinite horizontal scroll. Content is rendered twice so the loop is seamless. */
export function Marquee({ children, className, duration = '40s' }) {
  return (
    <div
      className={cn('group relative flex overflow-hidden [mask-image:linear-gradient(to_right,transparent,black_12%,black_88%,transparent)]', className)}
      style={{ '--marquee-duration': duration }}
    >
      <div className="flex w-max animate-marquee gap-4 pr-4 group-hover:[animation-play-state:paused]">
        {children}
        {children}
      </div>
    </div>
  )
}
