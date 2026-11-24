import { motion } from 'motion/react'
import { Banknote, Boxes, Coins, Scale } from 'lucide-react'
import { api } from '../../api/client'
import { useApi } from '../../hooks/useApi'
import { formatPaise } from '../../lib/money'
import { NumberTicker } from '../ui/NumberTicker'
import { Skeleton } from '../ui/misc'

/** Real numbers from the live ledger, refreshed every 20 seconds. */
export function LiveStats() {
  const overview = useApi(() => api.ledgerOverview(), [], { interval: 20_000 })
  const totals = useApi(() => api.ledgerTotals(), [], { interval: 20_000 })
  const ready = overview.data && totals.data

  const stats = ready ? [
    { icon: Banknote, label: 'Money in the ledger', value: overview.data.totalMoneyIn, format: formatPaise },
    { icon: Coins, label: 'Platform fees earned', value: overview.data.platformRevenue, format: formatPaise },
    { icon: Boxes, label: 'Ledger accounts', value: totals.data.accounts },
    {
      icon: Scale,
      label: 'Debits = credits',
      custom: totals.data.balanced
        ? <span className="text-brand">Balanced</span>
        : <span className="text-decline">Mismatch</span>,
    },
  ] : null

  return (
    <section aria-label="Live ledger statistics" className="mx-auto max-w-6xl px-4 sm:px-6">
      <div className="grid grid-cols-2 overflow-hidden rounded-2xl border border-line bg-line lg:grid-cols-4 [&>*]:bg-surface">
        {(stats ?? Array.from({ length: 4 })).map((s, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            transition={{ delay: i * 0.06 }}
            className="relative p-5 sm:p-6"
            style={{ marginRight: -1, marginBottom: -1 }}
          >
            {s ? (
              <>
                <div className="flex items-center gap-2 text-xs text-muted">
                  <s.icon className="size-3.5" /> {s.label}
                </div>
                <div className="mt-3 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">
                  {s.custom ?? <NumberTicker value={s.value} format={s.format} />}
                </div>
              </>
            ) : (
              <>
                <Skeleton className="h-3 w-28" />
                <Skeleton className="mt-4 h-8 w-36" />
              </>
            )}
          </motion.div>
        ))}
      </div>
      <p className="mt-3 flex items-center justify-center gap-2 text-xs text-faint">
        <span className="size-1.5 rounded-full bg-brand animate-pulse-dot" /> Live from the production ledger
      </p>
    </section>
  )
}
