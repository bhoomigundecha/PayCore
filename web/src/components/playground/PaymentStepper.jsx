import { motion } from 'motion/react'
import { Check, X } from 'lucide-react'
import { cn } from '../../lib/cn'

/** Which path a payment is on, and how far along it is. */
function pathFor(status) {
  if (status === 'FAILED') return { steps: ['Declined'], reached: 0, failed: true }
  if (['CANCELING', 'CANCELED'].includes(status)) {
    return { steps: ['Authorized', 'Canceled'], reached: status === 'CANCELED' ? 1 : 0, pendingNext: status === 'CANCELING' }
  }
  const steps = ['Authorized', 'Captured', 'Refunded']
  const reached = {
    REQUIRES_AUTHENTICATION: -1, AUTHORIZING: -1, AUTHORIZED: 0, CAPTURING: 0, CAPTURED: 1, REFUNDING: 1, REFUNDED: 2,
  }[status] ?? -1
  return { steps, reached, pendingNext: status.endsWith('ING') || status === 'REQUIRES_AUTHENTICATION' }
}

export function PaymentStepper({ status }) {
  const { steps, reached, failed, pendingNext } = pathFor(status)
  return (
    <ol className="flex items-center">
      {steps.map((label, i) => {
        const done = i <= reached
        const next = i === reached + 1 && pendingNext
        return (
          <li key={label} className={cn('flex items-center', i < steps.length - 1 && 'flex-1')}>
            <div className="flex items-center gap-2">
              <motion.span
                layout
                className={cn(
                  'grid size-6 place-items-center rounded-full border text-[10px]',
                  failed ? 'border-decline/50 bg-decline/15 text-decline'
                    : done ? 'border-brand/50 bg-brand/15 text-brand'
                      : next ? 'border-info/50 text-info'
                        : 'border-line text-faint',
                )}
              >
                {failed ? <X className="size-3" /> : done ? <Check className="size-3" /> : next ? <span className="size-1.5 rounded-full bg-info animate-pulse-dot" /> : i + 1}
              </motion.span>
              <span className={cn('text-xs', failed ? 'text-decline' : done ? 'text-ink' : 'text-faint')}>{label}</span>
            </div>
            {i < steps.length - 1 && (
              <div className="mx-3 h-px flex-1 overflow-hidden bg-line">
                <motion.div className="h-full bg-brand/60" initial={false} animate={{ width: i < reached ? '100%' : '0%' }} transition={{ duration: 0.5 }} />
              </div>
            )}
          </li>
        )
      })}
    </ol>
  )
}
