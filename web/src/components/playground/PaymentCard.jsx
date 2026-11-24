import { useState } from 'react'
import { motion } from 'motion/react'
import { ArrowRight, Ban, CircleCheck, Trash2, Undo2 } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { useApi } from '../../hooks/useApi'
import { useSandbox } from '../../hooks/useSandbox'
import { formatPaise } from '../../lib/money'
import { StatusBadge } from '../ui/Badge'
import { Button } from '../ui/Button'
import { CopyId, Skeleton } from '../ui/misc'
import { PaymentStepper } from './PaymentStepper'

const ACTIONS = {
  AUTHORIZED: [
    { key: 'capture', label: 'Capture', icon: CircleCheck, variant: 'brand', call: api.capturePayment, done: 'Captured: money moved to the merchant' },
    { key: 'cancel', label: 'Release hold', icon: Ban, variant: 'secondary', call: api.cancelPayment, done: 'Hold released' },
  ],
  CAPTURED: [
    { key: 'refund', label: 'Refund', icon: Undo2, variant: 'secondary', call: api.refundPayment, done: 'Refunded to the customer' },
  ],
}

const REASONS = {
  insufficient_funds: 'Not enough available balance',
  authorization_expired: 'The hold expired before capture',
  refund_failed_insufficient_merchant_balance: 'Refund failed: merchant balance too low',
  declined: 'Declined by the ledger',
}

export function PaymentCard({ paymentId }) {
  const { version, refresh, nameOf, forgetPayment } = useSandbox()
  const { data: p, error } = useApi(() => api.getPayment(paymentId), [paymentId, version])
  const [busy, setBusy] = useState(null)

  const run = async (action) => {
    setBusy(action.key)
    try {
      await action.call(paymentId)
      toast.success(action.done)
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
        Payment no longer exists.
        <button type="button" onClick={() => forgetPayment(paymentId)} className="inline-flex items-center gap-1 text-xs hover:text-ink"><Trash2 className="size-3.5" /> Forget</button>
      </div>
    )
  }
  if (!p) return <Skeleton className="h-36 w-full rounded-2xl" />

  const actions = ACTIONS[p.status] ?? []

  return (
    <motion.article layout initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="glass rounded-2xl p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-2xl font-semibold tracking-tight text-ink tabular">{formatPaise(p.amount)}</p>
          <p className="mt-1 flex items-center gap-1.5 text-sm text-muted">
            {nameOf(p.customerAccountId) ?? 'Customer'} <ArrowRight className="size-3.5 text-faint" /> {nameOf(p.merchantAccountId) ?? 'Merchant'}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1.5">
          <StatusBadge status={p.status} />
          <CopyId id={p.id} />
        </div>
      </div>

      <div className="mt-5"><PaymentStepper status={p.status} /></div>

      {p.failureReason && (
        <p className="mt-4 rounded-lg border border-decline/20 bg-decline/[0.06] px-3 py-2 text-xs text-decline">
          {REASONS[p.failureReason] ?? p.failureReason}
        </p>
      )}

      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
        <p className="text-xs text-faint tabular">
          Fee {formatPaise(p.fee)} · merchant {formatPaise(p.amount - p.fee)} · {new Date(p.createdAt).toLocaleTimeString()}
        </p>
        {actions.length > 0 && (
          <div className="flex gap-2">
            {actions.map((a) => (
              <Button key={a.key} size="sm" variant={a.variant} loading={busy === a.key} disabled={busy !== null} onClick={() => run(a)}>
                <a.icon className="size-3.5" /> {a.label}
              </Button>
            ))}
          </div>
        )}
      </div>
    </motion.article>
  )
}
