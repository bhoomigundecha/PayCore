import { useState } from 'react'
import { motion } from 'motion/react'
import { ArrowRight, Ban, CircleCheck, KeyRound, Undo2 } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { useSandbox } from '../../hooks/useSandbox'
import { formatPaise } from '../../lib/money'
import { StatusBadge } from '../ui/Badge'
import { Button } from '../ui/Button'
import { CopyId } from '../ui/misc'
import { PaymentStepper } from './PaymentStepper'
import { launchFlight } from '../../lib/flight'
import { ApprovePaymentDialog } from './ApprovePaymentDialog'

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
  authentication_failed: 'Cancelled: too many wrong one-time codes',
  authentication_expired: 'Cancelled: the one-time code was never entered',
  insufficient_funds: 'Not enough available balance',
  authorization_expired: 'The hold expired before capture',
  refund_failed_insufficient_merchant_balance: 'Refund failed: merchant balance too low',
  declined: 'Declined by the ledger',
}

export function PaymentCard({ payment: p }) {
  const { refresh, owns } = useSandbox()
  const [busy, setBusy] = useState(null)
  const [approving, setApproving] = useState(false)
  const paymentId = p.id
  const isMerchant = owns(p.merchantAccountId)
  const isPayer = owns(p.customerAccountId)

  const run = async (action) => {
    setBusy(action.key)
    try {
      await action.call(paymentId)
      const customer = p.customerAccountId
      const merchant = p.merchantAccountId
      const flight = {
        capture: { from: customer, to: merchant, kind: 'captured', fee: p.fee },
        cancel: { from: merchant, to: customer, kind: 'released', fee: 0 },
        refund: { from: merchant, to: customer, kind: 'refunded', fee: p.fee },
      }[action.key]
      await launchFlight({ ...flight, amount: p.amount })
      toast.success(action.done)
      refresh()
    } catch (e) {
      toast.error(e.message)
    } finally {
      setBusy(null)
    }
  }

  // Capture, release and refund are the merchant's decisions (the server enforces this too).
  const actions = isMerchant ? ACTIONS[p.status] ?? [] : []

  const onApproved = async (result) => {
    setApproving(false)
    const kind = result.status === 'FAILED' ? 'declined' : result.status === 'CAPTURED' ? 'captured' : 'held'
    await launchFlight({ from: result.customerAccountId, to: result.merchantAccountId, amount: result.amount, fee: result.fee, kind })
    refresh()
    if (result.status === 'FAILED') toast.error('Payment cancelled: it was not approved')
    else toast.success(`Approved: paid ${formatPaise(result.amount)} to ${result.merchantName}`)
  }

  return (
    <>
    <ApprovePaymentDialog payment={approving ? p : null} open={approving} onClose={() => setApproving(false)} onApproved={onApproved} />
    <motion.article layout initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="glass rounded-2xl p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-2xl font-semibold tracking-tight text-ink tabular">{formatPaise(p.amount)}</p>
          <p className="mt-1 flex items-center gap-1.5 text-sm text-muted">
            {p.customerName} <ArrowRight className="size-3.5 text-faint" /> {p.merchantName}
            <span className="ml-1 rounded-md border border-line px-1.5 py-px text-[10px] text-faint">
              {isMerchant && isPayer ? 'you → your store' : isMerchant ? 'incoming' : 'you paid'}
            </span>
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
        {!isMerchant && p.status === 'AUTHORIZED' && <p className="text-xs text-faint">Waiting for the merchant to capture</p>}
        {isPayer && p.status === 'REQUIRES_AUTHENTICATION' && (
          <Button size="sm" variant="brand" onClick={() => setApproving(true)}>
            <KeyRound className="size-3.5" /> Enter code
          </Button>
        )}
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
    </>
  )
}
