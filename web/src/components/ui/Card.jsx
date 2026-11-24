import { useRef } from 'react'
import { motion, useMotionTemplate, useMotionValue } from 'motion/react'
import { cn } from '../../lib/cn'

export function Card({ className, children, ...props }) {
  return (
    <div className={cn('glass rounded-2xl', className)} {...props}>
      {children}
    </div>
  )
}

export function CardHeader({ icon: Icon, title, description, action, className }) {
  return (
    <div className={cn('flex items-start justify-between gap-4', className)}>
      <div className="flex items-start gap-3">
        {Icon && (
          <span className="grid size-9 shrink-0 place-items-center rounded-xl border border-line bg-white/[0.04] text-muted">
            <Icon className="size-4.5" aria-hidden />
          </span>
        )}
        <div>
          <h3 className="text-[15px] font-semibold tracking-tight text-ink">{title}</h3>
          {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
        </div>
      </div>
      {action}
    </div>
  )
}

/**
 * A card with a soft light that follows the cursor across its border and surface.
 */
export function SpotlightCard({ className, children, color = 'rgb(52 211 153 / 0.14)', ...props }) {
  const ref = useRef(null)
  const x = useMotionValue(-400)
  const y = useMotionValue(-400)
  const background = useMotionTemplate`radial-gradient(360px circle at ${x}px ${y}px, ${color}, transparent 70%)`

  const onMove = (e) => {
    const rect = ref.current.getBoundingClientRect()
    x.set(e.clientX - rect.left)
    y.set(e.clientY - rect.top)
  }

  return (
    <div
      ref={ref}
      onMouseMove={onMove}
      onMouseLeave={() => { x.set(-400); y.set(-400) }}
      className={cn('group glass relative overflow-hidden rounded-2xl', className)}
      {...props}
    >
      <motion.div aria-hidden className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100" style={{ background }} />
      <div className="relative">{children}</div>
    </div>
  )
}
