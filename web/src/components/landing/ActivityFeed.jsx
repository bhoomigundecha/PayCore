import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowRight, CircleCheck, Clock, Plus, RotateCcw, Undo2, X } from 'lucide-react'
import { api } from '../../api/client'
import { useApi } from '../../hooks/useApi'
import { cn } from '../../lib/cn'
import { formatPaise } from '../../lib/money'
import { Skeleton } from '../ui/misc'

const VISIBLE = 6

const KIND = {
  CAPTURED: { label: 'Card payment captured', icon: CircleCheck, tone: 'text-brand border-brand/30 bg-brand/10' },
  AUTHORIZED: { label: 'Card authorized, money on hold', icon: Clock, tone: 'text-hold border-hold/30 bg-hold/10' },
  RELEASED: { label: 'Hold released', icon: RotateCcw, tone: 'text-muted border-line-strong bg-white/[0.04]' },
  REFUNDED: { label: 'Refund issued', icon: Undo2, tone: 'text-violet border-violet/30 bg-violet/10' },
  DECLINED: { label: 'Declined: insufficient funds', icon: X, tone: 'text-decline border-decline/30 bg-decline/10' },
  FUNDED: { label: 'Wallet funded', icon: Plus, tone: 'text-info border-info/30 bg-info/10' },
}

function ago(iso, now) {
  const s = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000))
  if (s < 5) return 'just now'
  if (s < 60) return `${s}s ago`
  if (s < 3600) return `${Math.floor(s / 60)}m ago`
  if (s < 86_400) return `${Math.floor(s / 3600)}h ago`
  return `${Math.floor(s / 86_400)}d ago`
}

/** What just happened on the ledger, anonymized. Polls every 5 seconds; new events slide in on top. */
export function ActivityFeed() {
  const { data } = useApi(() => api.activity(), [], { interval: 5_000 })
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1_000)
    return () => clearInterval(timer)
  }, [])

  const events = (data ?? []).slice(0, VISIBLE)

  return (
    <section aria-label="Live ledger activity" className="mx-auto max-w-3xl px-4 sm:px-6">
      <div className="rounded-2xl border border-line bg-surface">
        <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-3.5">
          <p className="flex items-center gap-2.5 text-sm font-medium text-ink">
            <span className="relative flex size-2" aria-hidden>
              <span className="absolute inline-flex size-full animate-ping rounded-full bg-brand opacity-60" />
              <span className="relative inline-flex size-2 rounded-full bg-brand" />
            </span>
            Live activity
          </p>
          <p className="text-xs text-faint">Real ledger events · anonymized</p>
        </div>

        {!data ? (
          <div className="space-y-3 p-5">
            {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-9 w-full" />)}
          </div>
        ) : events.length === 0 ? (
          <div className="flex flex-col items-center px-6 py-12 text-center">
            <p className="text-sm text-muted">Quiet right now.</p>
            <Link to="/playground" className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-brand hover:underline">
              Make the first payment <ArrowRight className="size-3.5" />
            </Link>
          </div>
        ) : (
          <ul className="divide-y divide-line">
            <AnimatePresence initial={false}>
              {events.map((e) => {
                const k = KIND[e.kind] ?? KIND.CAPTURED
                return (
                  <motion.li
                    key={e.key}
                    layout
                    initial={{ opacity: 0, y: -12 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0 }}
                    transition={{ type: 'spring', stiffness: 380, damping: 32 }}
                    className="flex items-center gap-3 px-5 py-3"
                  >
                    <span className={cn('grid size-8 shrink-0 place-items-center rounded-lg border', k.tone)}>
                      <k.icon className="size-4" aria-hidden />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm text-ink">{k.label}</p>
                      {e.fee > 0 && e.kind === 'CAPTURED' && <p className="text-xs text-faint tabular">PayCore fee {formatPaise(e.fee)}</p>}
                    </div>
                    <span className={cn('text-sm font-medium tabular', e.kind === 'DECLINED' ? 'text-faint line-through' : 'text-ink')}>
                      {formatPaise(e.amount)}
                    </span>
                    <span className="w-16 shrink-0 text-right text-xs text-faint tabular">{ago(e.at, now)}</span>
                  </motion.li>
                )
              })}
            </AnimatePresence>
          </ul>
        )}
      </div>
    </section>
  )
}
