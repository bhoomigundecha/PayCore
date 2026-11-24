import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { cn } from '../../lib/cn'
import { useHealth } from '../../hooks/useHealth'
import { StatusDot } from '../ui/Badge'

/** Live service status. `compact` shows only the dot (for the navbar pill); hover or focus for details. */
export function HealthPill({ className, compact = false }) {
  const { services, allUp, reachable } = useHealth()
  const [open, setOpen] = useState(false)
  const overall = !reachable ? 'UNKNOWN' : allUp ? 'UP' : 'DOWN'
  const label = !reachable ? 'Connecting…' : allUp ? 'All systems live' : 'Degraded'

  return (
    <div className={cn('relative inline-flex', className)} onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button
        type="button"
        aria-label={`Service status: ${label}`}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        className={cn(
          'inline-flex items-center gap-2 text-xs text-muted',
          compact ? 'size-9 justify-center rounded-full hover:bg-white/[0.06]' : 'h-8 rounded-full border border-line bg-white/[0.03] px-3',
        )}
      >
        <StatusDot status={overall} />
        {!compact && label}
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.97 }}
            transition={{ duration: 0.15 }}
            className="absolute top-11 right-0 z-50 w-60 rounded-2xl border border-line-strong bg-raised/95 p-2 shadow-2xl backdrop-blur-xl"
          >
            <p className="px-2.5 pt-1.5 pb-2 text-xs font-medium text-ink">{label}</p>
            {services.map((s) => (
              <div key={s.key} className="flex items-center justify-between rounded-lg px-2.5 py-2">
                <span className="flex items-center gap-2.5 text-sm text-ink">
                  <StatusDot status={s.status} />
                  {s.label}
                </span>
                <span className="text-xs text-faint">{s.role}</span>
              </div>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
