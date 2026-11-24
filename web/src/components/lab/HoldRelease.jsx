import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Hand } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { cn } from '../../lib/cn'
import { formatPaise } from '../../lib/money'
import { ExperimentCard } from './shared'
import { freshAccounts, sleep } from './labApi'

const WALLET = 100_000 // ₹1,000
const HOLD = 60_000 // ₹600
const SPEND = 50_000 // ₹500

export function HoldRelease({ index }) {
  const [running, setRunning] = useState(false)
  const [balance, setBalance] = useState({ available: WALLET, held: 0 })
  const [log, setLog] = useState([])
  const [result, setResult] = useState(null)

  const step = (text, tone = 'neutral') => setLog((l) => [...l, { text, tone, at: l.length }])
  const read = async (id) => {
    const b = (await api.getAccount(id)).balance
    setBalance({ available: b.available, held: b.debitsPending })
    return b
  }

  const run = async () => {
    setRunning(true)
    setResult(null)
    setLog([])
    try {
      const { customer, merchant } = await freshAccounts('Hold', WALLET)
      await read(customer.id)
      step(`Wallet funded with ${formatPaise(WALLET)}`)
      await sleep(700)

      const hold = await api.createPayment({ customerAccountId: customer.id, merchantAccountId: merchant.id, amount: HOLD, capture: false })
      await read(customer.id)
      step(`Authorized ${formatPaise(HOLD)}: money is held, not moved`, 'hold')
      await sleep(900)

      const blocked = await api.createPayment({ customerAccountId: customer.id, merchantAccountId: merchant.id, amount: SPEND })
      await read(customer.id)
      const blockedOk = blocked.status === 'FAILED'
      step(blockedOk ? `Tried to spend ${formatPaise(SPEND)}: declined, the held money is not spendable` : `Spend of ${formatPaise(SPEND)} unexpectedly ${blocked.status}`, blockedOk ? 'decline' : 'neutral')
      await sleep(900)

      await api.cancelPayment(hold.id)
      await read(customer.id)
      step(`Released the hold: ${formatPaise(HOLD)} is available again`, 'brand')
      await sleep(900)

      const retry = await api.createPayment({ customerAccountId: customer.id, merchantAccountId: merchant.id, amount: SPEND })
      const final = await read(customer.id)
      const retryOk = retry.status === 'CAPTURED'
      step(retryOk ? `Spent ${formatPaise(SPEND)}: approved this time` : `Second spend ${retry.status}`, retryOk ? 'brand' : 'decline')

      setResult({ ok: blockedOk && retryOk && final.available === WALLET - SPEND })
    } catch (e) {
      toast.error(e.message)
    } finally {
      setRunning(false)
    }
  }

  const total = Math.max(1, balance.available + balance.held)
  const verdict = result && (result.ok
    ? { ok: true, title: 'Holds are real', text: 'Authorized money left the spendable balance immediately and came back the moment the hold was released, like a card authorization at a hotel.' }
    : { ok: false, title: 'Unexpected result', text: 'Check the log above.' })

  return (
    <ExperimentCard
      index={index}
      icon={Hand}
      title="Hold and release"
      question={`A hotel authorizes ${formatPaise(HOLD)} on a ${formatPaise(WALLET)} wallet. Can the guest still spend ${formatPaise(SPEND)} somewhere else? And after check-out?`}
      proves="Two-phase transfers: pending money counts against the balance until it is posted or voided."
      running={running}
      done={!!result}
      onRun={run}
      verdict={verdict}
    >
      <div className="grid gap-6 lg:grid-cols-2">
        <div>
          <div className="flex items-end justify-between">
            <div>
              <p className="text-xs text-muted">Available</p>
              <p className="text-2xl font-semibold text-ink tabular">{formatPaise(balance.available)}</p>
            </div>
            <div className="text-right">
              <p className="text-xs text-muted">On hold</p>
              <p className={cn('text-2xl font-semibold tabular', balance.held ? 'text-hold' : 'text-faint')}>{formatPaise(balance.held)}</p>
            </div>
          </div>
          <div className="mt-3 flex h-3 overflow-hidden rounded-full bg-white/[0.05]">
            <motion.div className="h-full bg-brand/70" animate={{ width: `${(balance.available / total) * 100}%` }} transition={{ type: 'spring', stiffness: 120, damping: 20 }} />
            <motion.div className="h-full bg-hold/80" animate={{ width: `${(balance.held / total) * 100}%` }} transition={{ type: 'spring', stiffness: 120, damping: 20 }} />
          </div>
        </div>
        <ol className="space-y-2">
          <AnimatePresence initial={false}>
            {log.map((entry) => (
              <motion.li
                key={entry.at}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                className="flex gap-3 text-sm"
              >
                <span className="font-mono text-xs text-faint">{String(entry.at + 1).padStart(2, '0')}</span>
                <span className={cn(entry.tone === 'brand' && 'text-brand', entry.tone === 'hold' && 'text-hold', entry.tone === 'decline' && 'text-decline', entry.tone === 'neutral' && 'text-muted')}>{entry.text}</span>
              </motion.li>
            ))}
          </AnimatePresence>
          {log.length === 0 && <li className="text-sm text-faint">Run the experiment to see each step.</li>}
        </ol>
      </div>
    </ExperimentCard>
  )
}
