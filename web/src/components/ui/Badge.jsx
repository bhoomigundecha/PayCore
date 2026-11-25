import { cn } from '../../lib/cn'

const TONES = {
  neutral: 'border-line text-muted bg-white/[0.03]',
  brand: 'border-brand/25 text-brand bg-brand/10',
  hold: 'border-hold/25 text-hold bg-hold/10',
  decline: 'border-decline/25 text-decline bg-decline/10',
  info: 'border-info/25 text-info bg-info/10',
  violet: 'border-violet/25 text-violet bg-violet/10',
}

export function Badge({ tone = 'neutral', className, children, dot = false }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium', TONES[tone], className)}>
      {dot && <span className="size-1.5 rounded-full bg-current animate-pulse-dot" aria-hidden />}
      {children}
    </span>
  )
}

const STATUS = {
  REQUIRES_AUTHENTICATION: { tone: 'hold', label: 'Needs approval', live: true },
  AUTHORIZING: { tone: 'info', label: 'Authorizing', live: true },
  AUTHORIZED: { tone: 'hold', label: 'Authorized' },
  CAPTURING: { tone: 'info', label: 'Capturing', live: true },
  CAPTURED: { tone: 'brand', label: 'Captured' },
  CANCELING: { tone: 'info', label: 'Canceling', live: true },
  CANCELED: { tone: 'neutral', label: 'Canceled' },
  REFUNDING: { tone: 'info', label: 'Refunding', live: true },
  REFUNDED: { tone: 'violet', label: 'Refunded' },
  FAILED: { tone: 'decline', label: 'Declined' },
}

export function StatusBadge({ status }) {
  const s = STATUS[status] ?? { tone: 'neutral', label: status }
  return <Badge tone={s.tone} dot={s.live}>{s.label}</Badge>
}

export function StatusDot({ status }) {
  const color = status === 'UP' ? 'bg-brand' : status === 'DOWN' ? 'bg-decline' : 'bg-faint'
  return (
    <span className="relative flex size-2" aria-hidden>
      {status === 'UP' && <span className={cn('absolute inline-flex size-full rounded-full opacity-60 animate-ping', color)} />}
      <span className={cn('relative inline-flex size-2 rounded-full', color)} />
    </span>
  )
}
