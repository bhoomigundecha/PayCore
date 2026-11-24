import { useState } from 'react'
import { Plus, Store, Trash2, Wallet } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { useApi } from '../../hooks/useApi'
import { useSandbox } from '../../hooks/useSandbox'
import { cn } from '../../lib/cn'
import { formatPaise } from '../../lib/money'
import { NumberTicker } from '../ui/NumberTicker'
import { TiltCard } from '../ui/TiltCard'
import { CopyId, Skeleton } from '../ui/misc'

const STYLE = {
  CUSTOMER_WALLET: {
    icon: Wallet,
    label: 'Customer wallet',
    surface: 'bg-[radial-gradient(120%_120%_at_0%_0%,rgb(52_211_153/0.28),transparent_55%),radial-gradient(120%_120%_at_100%_100%,rgb(125_211_252/0.18),transparent_50%)]',
  },
  MERCHANT: {
    icon: Store,
    label: 'Merchant',
    surface: 'bg-[radial-gradient(120%_120%_at_0%_0%,rgb(167_139_250/0.28),transparent_55%),radial-gradient(120%_120%_at_100%_100%,rgb(125_211_252/0.14),transparent_50%)]',
  },
}

const TOP_UPS = [50_000, 200_000] // ₹500, ₹2,000

export function AccountCard({ account }) {
  const { version, refresh, forgetAccount } = useSandbox()
  const { data, error } = useApi(() => api.getAccount(account.id), [account.id, version])
  const [busy, setBusy] = useState(null)
  const style = STYLE[account.type]
  const Icon = style.icon
  const b = data?.balance
  const held = account.type === 'MERCHANT' ? b?.creditsPending : b?.debitsPending
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

  if (error?.status === 404) {
    return (
      <div className="flex items-center justify-between rounded-2xl border border-dashed border-line p-4 text-sm text-muted">
        <span>{account.name} no longer exists on the server.</span>
        <button type="button" onClick={() => forgetAccount(account.id)} className="inline-flex items-center gap-1 text-xs hover:text-ink">
          <Trash2 className="size-3.5" /> Forget
        </button>
      </div>
    )
  }

  return (
    <TiltCard className="border border-line-strong bg-raised">
      <div aria-hidden className={cn('absolute inset-0', style.surface)} />
      <div aria-hidden className="absolute inset-0 bg-grid opacity-40 [mask-image:none]" />
      <div className="relative p-5">
        <div className="flex items-start justify-between">
          <div>
            <p className="flex items-center gap-1.5 text-xs text-muted"><Icon className="size-3.5" /> {style.label}</p>
            <p className="mt-1 text-lg font-semibold tracking-tight text-ink">{account.name}</p>
          </div>
          <CopyId id={account.id} />
        </div>

        <div className="mt-6">
          <p className="text-xs text-muted">Available</p>
          <p className="mt-0.5 text-3xl font-semibold tracking-tight text-ink">
            {b ? <NumberTicker value={b.available} format={formatPaise} /> : <Skeleton className="h-9 w-40" />}
          </p>
        </div>

        <div className="mt-4">
          <div className="flex h-1.5 overflow-hidden rounded-full bg-white/[0.08]">
            <div className="h-full bg-brand/70 transition-all duration-700" style={{ width: `${100 - heldShare}%` }} />
            <div className="h-full bg-hold/80 transition-all duration-700" style={{ width: `${heldShare}%` }} />
          </div>
          <div className="mt-2 flex justify-between text-xs">
            <span className="text-muted">
              {account.type === 'MERCHANT' ? 'Incoming holds' : 'On hold'}{' '}
              <span className={cn('tabular', held ? 'text-hold' : 'text-faint')}>{formatPaise(held ?? 0)}</span>
            </span>
            <span className="text-faint tabular">
              in {formatPaise(b?.creditsPosted ?? 0)} · out {formatPaise(b?.debitsPosted ?? 0)}
            </span>
          </div>
        </div>

        {account.type === 'CUSTOMER_WALLET' && (
          <div className="mt-5 flex gap-2">
            {TOP_UPS.map((amount) => (
              <button
                key={amount}
                type="button"
                disabled={busy !== null}
                onClick={() => topUp(amount)}
                className="inline-flex items-center gap-1 rounded-lg border border-line bg-black/20 px-2.5 py-1.5 text-xs text-ink backdrop-blur transition-colors hover:border-line-strong hover:bg-black/30 disabled:opacity-50"
              >
                <Plus className="size-3" /> {formatPaise(amount).replace('.00', '')}
              </button>
            ))}
          </div>
        )}
      </div>
    </TiltCard>
  )
}
