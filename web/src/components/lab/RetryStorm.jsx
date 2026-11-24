import { useState } from 'react'
import { motion } from 'motion/react'
import { Repeat } from 'lucide-react'
import { toast } from 'sonner'
import { api, newIdempotencyKey } from '../../api/client'
import { cn } from '../../lib/cn'
import { formatPaise, shortId } from '../../lib/money'
import { ExperimentCard, Metric } from './shared'
import { available, freshAccounts } from './labApi'

const REQUESTS = 5
const WALLET = 100_000 // ₹1,000
const AMOUNT = 30_000 // ₹300

export function RetryStorm({ index }) {
  const [running, setRunning] = useState(false)
  const [lanes, setLanes] = useState([])
  const [result, setResult] = useState(null)

  const run = async () => {
    setRunning(true)
    setResult(null)
    setLanes(Array.from({ length: REQUESTS }, () => ({ state: 'idle' })))
    try {
      const { customer, merchant } = await freshAccounts('Retry storm', WALLET)
      const key = newIdempotencyKey()
      setLanes((l) => l.map(() => ({ state: 'sending' })))

      const responses = await Promise.all(Array.from({ length: REQUESTS }, async (_, i) => {
        const started = performance.now()
        try {
          const p = await api.createPayment({ customerAccountId: customer.id, merchantAccountId: merchant.id, amount: AMOUNT }, key)
          const lane = { state: 'ok', id: p.id, status: p.status, ms: Math.round(performance.now() - started) }
          setLanes((l) => l.map((x, j) => (j === i ? lane : x)))
          return lane
        } catch (e) {
          const lane = { state: 'error', error: e.message, ms: Math.round(performance.now() - started) }
          setLanes((l) => l.map((x, j) => (j === i ? lane : x)))
          return lane
        }
      }))

      const after = await available(customer.id)
      const ids = new Set(responses.filter((r) => r.id).map((r) => r.id))
      setResult({ key, uniquePayments: ids.size, before: WALLET, after })
    } catch (e) {
      toast.error(e.message)
    } finally {
      setRunning(false)
    }
  }

  const ok = result && result.uniquePayments === 1 && result.before - result.after === AMOUNT
  const verdict = result && (ok
    ? { ok: true, title: 'Charged exactly once', text: `${REQUESTS} identical requests, 1 payment, ${formatPaise(AMOUNT)} debited. Each retry got the same payment back instead of a new charge.` }
    : { ok: false, title: 'Unexpected result', text: `${result.uniquePayments} payments, ${formatPaise(result.before - result.after)} debited.` })

  return (
    <ExperimentCard
      index={index}
      icon={Repeat}
      title="Retry storm"
      question={`A flaky network makes the app send the same ${formatPaise(AMOUNT)} payment ${REQUESTS} times at once, all with one Idempotency-Key. How many times is the customer charged?`}
      proves="Idempotency keys plus deterministic ledger transfer ids make retries safe, even when they race each other."
      running={running}
      done={!!result}
      onRun={run}
      verdict={verdict}
    >
      <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
        <div className="space-y-2">
          {(lanes.length ? lanes : Array.from({ length: REQUESTS }, () => ({ state: 'idle' }))).map((lane, i) => (
            <div key={i} className="flex items-center gap-3">
              <span className="w-20 shrink-0 font-mono text-xs text-faint">req #{i + 1}</span>
              <div className="relative h-9 flex-1 overflow-hidden rounded-lg border border-line bg-white/[0.02]">
                <motion.div
                  className={cn('absolute inset-y-0 left-0', lane.state === 'error' ? 'bg-decline/20' : 'bg-info/15')}
                  initial={false}
                  animate={{ width: lane.state === 'idle' ? '0%' : lane.state === 'sending' ? '70%' : '100%' }}
                  transition={{ duration: lane.state === 'sending' ? 2 : 0.3, ease: 'easeOut' }}
                />
                <div className="relative flex h-full items-center justify-between px-3 font-mono text-xs">
                  <span className="text-muted">POST /payments</span>
                  {lane.state === 'ok' && (
                    <span className="text-ink">→ <span className="text-brand">{shortId(lane.id)}</span> <span className="text-faint">{lane.ms}ms</span></span>
                  )}
                  {lane.state === 'error' && <span className="text-decline">{lane.error}</span>}
                  {lane.state === 'sending' && <span className="text-info">sending…</span>}
                </div>
              </div>
            </div>
          ))}
        </div>
        <div className="grid grid-cols-2 gap-2 self-start">
          <Metric label="Requests" value={REQUESTS} />
          <Metric label="Payments" value={result ? result.uniquePayments : '–'} tone={result ? (result.uniquePayments === 1 ? 'brand' : 'decline') : undefined} />
          <Metric label="Wallet before" value={formatPaise(WALLET)} />
          <Metric label="Wallet after" value={result ? formatPaise(result.after) : '–'} />
        </div>
      </div>
    </ExperimentCard>
  )
}
