import { Coins } from 'lucide-react'
import { api } from '../../api/client'
import { useApi } from '../../hooks/useApi'
import { useSandbox } from '../../hooks/useSandbox'
import { formatPaise } from '../../lib/money'
import { NumberTicker } from '../ui/NumberTicker'

/** PayCore's own fee revenue, live from the ledger. Fee coins in the payment animation fly here. */
export function FeesChip() {
  const { version } = useSandbox()
  const { data } = useApi(() => api.ledgerOverview(), [version])
  return (
    <div
      data-flight-target="fees"
      className="flex shrink-0 items-center gap-2.5 rounded-2xl border border-violet/25 bg-violet/[0.07] py-2 pr-3.5 pl-2.5"
      title="Platform fees PayCore has earned across the whole ledger"
    >
      <span className="grid size-8 place-items-center rounded-xl bg-violet/15 text-violet"><Coins className="size-4" /></span>
      <div className="leading-tight">
        <p className="text-[10px] font-medium tracking-wide text-violet/80 uppercase">PayCore earned</p>
        <p className="text-sm font-semibold text-ink">
          <NumberTicker value={data?.platformRevenue ?? 0} format={formatPaise} />
        </p>
      </div>
    </div>
  )
}
