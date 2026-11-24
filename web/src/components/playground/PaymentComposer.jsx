import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowRight, Hand, Zap } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { useSandbox } from '../../hooks/useSandbox'
import { feeFor, formatPaise, rupeesToPaise } from '../../lib/money'
import { Button } from '../ui/Button'
import { Field, MoneyInput, Segmented, Select } from '../ui/Form'

export function PaymentComposer() {
  const { customers, merchants, rememberPayment, nameOf } = useSandbox()
  const [chosenCustomer, setCustomerId] = useState('')
  const [chosenMerchant, setMerchantId] = useState('')
  const [rupees, setRupees] = useState('250')
  const [mode, setMode] = useState('capture')
  const [busy, setBusy] = useState(false)

  // Fall back to the first available account when nothing (or a removed account) is selected.
  const customerId = customers.some((c) => c.id === chosenCustomer) ? chosenCustomer : customers[0]?.id ?? ''
  const merchantId = merchants.some((m) => m.id === chosenMerchant) ? chosenMerchant : merchants[0]?.id ?? ''

  const amount = rupeesToPaise(rupees)
  const valid = customerId && merchantId && Number.isInteger(amount) && amount > 0
  const fee = valid ? feeFor(amount) : 0

  const submit = async (e) => {
    e.preventDefault()
    if (!valid) return
    setBusy(true)
    try {
      const payment = await api.createPayment({
        customerAccountId: customerId, merchantAccountId: merchantId, amount, capture: mode === 'capture',
      })
      rememberPayment(payment.id)
      if (payment.status === 'FAILED') {
        toast.error(`Declined: ${nameOf(customerId)} can't afford ${formatPaise(amount)}`)
      } else {
        toast.success(payment.status === 'CAPTURED'
          ? `Paid ${formatPaise(amount)} to ${nameOf(merchantId)}`
          : `Authorized ${formatPaise(amount)}: money is on hold`)
      }
    } catch (err) {
      toast.error(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-[1fr_auto_1fr] sm:items-end">
        <Field label="From">
          {(id) => (
            <Select id={id} value={customerId} onChange={(e) => setCustomerId(e.target.value)} disabled={!customers.length}>
              {!customers.length && <option value="">No customer yet</option>}
              {customers.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
          )}
        </Field>
        <ArrowRight className="mx-auto mb-3.5 hidden size-4 text-faint sm:block" />
        <Field label="To">
          {(id) => (
            <Select id={id} value={merchantId} onChange={(e) => setMerchantId(e.target.value)} disabled={!merchants.length}>
              {!merchants.length && <option value="">No merchant yet</option>}
              {merchants.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
            </Select>
          )}
        </Field>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 sm:items-end">
        <Field label="Amount">
          {(id) => <MoneyInput id={id} value={rupees} onChange={(e) => setRupees(e.target.value)} placeholder="250" />}
        </Field>
        <Segmented
          layoutId="capture-mode"
          className="h-11 w-full"
          value={mode}
          onChange={setMode}
          options={[
            { value: 'capture', label: 'Charge now', icon: Zap },
            { value: 'authorize', label: 'Hold only', icon: Hand },
          ]}
        />
      </div>

      <AnimatePresence initial={false}>
        {valid && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} className="overflow-hidden">
            <div className="rounded-xl border border-line bg-white/[0.02] p-3 text-xs">
              <div className="flex h-2 overflow-hidden rounded-full">
                <div className="bg-brand/70" style={{ width: `${((amount - fee) / amount) * 100}%` }} />
                <div className="bg-violet/80" style={{ width: `${(fee / amount) * 100}%` }} />
              </div>
              <div className="mt-2 flex justify-between text-muted tabular">
                <span><span className="text-brand">●</span> Merchant gets {formatPaise(amount - fee)}</span>
                <span><span className="text-violet">●</span> PayCore fee {formatPaise(fee)}</span>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <Button type="submit" variant="brand" size="lg" className="w-full" loading={busy} disabled={!valid}>
        {mode === 'capture' ? 'Pay' : 'Authorize'} {valid ? formatPaise(amount) : ''}
      </Button>
      <p className="text-center text-[11px] text-faint">Sent with a fresh Idempotency-Key. Amounts travel as integer paise.</p>
    </form>
  )
}
