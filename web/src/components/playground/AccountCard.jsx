import { useState } from 'react'
import { Link2, Plus, Store, Wallet, Webhook } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { useSandbox } from '../../hooks/useSandbox'
import { cn } from '../../lib/cn'
import { formatPaise } from '../../lib/money'
import { NumberTicker } from '../ui/NumberTicker'
import { CopyId, Skeleton } from '../ui/misc'
import { Tile } from './Tile'
import { PaymentLinkDialog } from './PaymentLinkDialog'
import { WebhooksDialog } from './WebhooksDialog'

const STYLE = {
  CUSTOMER_WALLET: { icon: Wallet, label: 'Wallet', accent: 'text-brand' },
  MERCHANT: { icon: Store, label: 'Store', accent: 'text-violet' },
}

const TOP_UPS = [50_000, 200_000] // ₹500, ₹2,000

export function AccountCard({ account }) {
  const { refresh } = useSandbox()
  const [busy, setBusy] = useState(null)
  const [linking, setLinking] = useState(false)
  const [hooks, setHooks] = useState(false)
  const style = STYLE[account.type]
  const isStore = account.type === 'MERCHANT'
  const b = account.balance // comes with the account list; refreshed after every change
  const held = isStore ? b?.creditsPending : b?.debitsPending
  const heldShare = b && held ? Math.min(100, (held / Math.max(1, b.available + held)) * 100) : 0

  const topUp = async (amount) => {
    setBusy(amount)
    try {
      await api.topUp(account.id, amount)
      toast.success(`Added ${formatPaise(amount)} to ${account.name}`)
      refresh()
    } catch (e) {
      toast.error(e.message)
    } finally {
      setBusy(null)
    }
  }

  const footer = isStore ? (
    <>
      <span className="text-xs text-muted">Received <span className="text-ink tabular">{formatPaise(b?.creditsPosted ?? 0)}</span></span>
      <div className="flex gap-2">
      <button
        type="button"
        onClick={() => setHooks(true)}
        aria-label="Webhooks"
        title="Webhooks"
        className="inline-flex items-center gap-1 rounded-lg border border-line bg-canvas px-2 py-1.5 text-xs text-ink transition-colors hover:border-line-strong"
      >
        <Webhook className="size-3.5" />
      </button>
      <button
        type="button"
        onClick={() => setLinking(true)}
        className="inline-flex items-center gap-1 rounded-lg border border-line bg-canvas px-2.5 py-1.5 text-xs text-ink transition-colors hover:border-line-strong"
      >
        <Link2 className="size-3" /> Payment link
      </button>
      </div>
    </>
  ) : (
    <>
      <span className="text-xs text-muted">Add money</span>
      <div className="flex gap-2">
        {TOP_UPS.map((amount) => (
          <button
            key={amount}
            type="button"
            disabled={busy !== null}
            onClick={() => topUp(amount)}
            className="inline-flex items-center gap-1 rounded-lg border border-line bg-canvas px-2.5 py-1.5 text-xs text-ink transition-colors hover:border-line-strong disabled:opacity-50"
          >
            <Plus className="size-3" /> {formatPaise(amount).replace('.00', '')}
          </button>
        ))}
      </div>
    </>
  )

  return (
    <>
    {isStore && <PaymentLinkDialog store={account} open={linking} onClose={() => setLinking(false)} />}
    {isStore && <WebhooksDialog store={account} open={hooks} onClose={() => setHooks(false)} />}
    <Tile icon={style.icon} accent={style.accent} label={style.label} meta={<CopyId id={account.id} />} footer={footer}>
      <p className="text-lg font-semibold tracking-tight text-ink">{account.name}</p>

      {/* Balances sit at the bottom of the body so they line up across tiles of different heights. */}
      <div className="mt-auto pt-6">
        <p className="text-xs text-muted">Available</p>
        <p className="mt-0.5 text-3xl font-semibold tracking-tight text-ink">
          {b ? <NumberTicker value={b.available} format={formatPaise} /> : <Skeleton className="h-9 w-40" />}
        </p>
        <div className="mt-4 flex h-1.5 overflow-hidden rounded-full bg-white/[0.08]">
          <div className={cn('h-full transition-all duration-700', isStore ? 'bg-violet/70' : 'bg-brand/70')} style={{ width: `${100 - heldShare}%` }} />
          <div className="h-full bg-hold/80 transition-all duration-700" style={{ width: `${heldShare}%` }} />
        </div>
        <p className="mt-2 text-xs text-muted">
          {isStore ? 'Incoming holds' : 'On hold'}{' '}
          <span className={cn('tabular', held ? 'text-hold' : 'text-faint')}>{formatPaise(held ?? 0)}</span>
        </p>
      </div>
    </Tile>
    </>
  )
}
