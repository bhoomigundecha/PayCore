import { useState } from 'react'
import { KeyRound } from 'lucide-react'
import { toast } from 'sonner'
import { api, newIdempotencyKey } from '../../api/client'
import { formatPaise } from '../../lib/money'
import { ExperimentCard, Metric } from './shared'
import { freshAccounts } from './labApi'

export function KeyReuse({ index }) {
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState(null)

  const run = async () => {
    setRunning(true)
    setResult(null)
    try {
      const { customer, merchant } = await freshAccounts('Key reuse', 100_000)
      const key = newIdempotencyKey()
      const first = await api.createPayment({ customerAccountId: customer.id, merchantAccountId: merchant.id, amount: 10_000 }, key)
      let second
      try {
        await api.createPayment({ customerAccountId: customer.id, merchantAccountId: merchant.id, amount: 90_000 }, key)
        second = { status: 'accepted' }
      } catch (e) {
        second = { status: e.status, message: e.message }
      }
      setResult({ first, second })
    } catch (e) {
      toast.error(e.message)
    } finally {
      setRunning(false)
    }
  }

  const ok = result && result.second.status === 422
  const verdict = result && (ok
    ? { ok: true, title: 'Rejected with 422', text: 'Reusing a key for a different request is a client bug, so PayCore refuses loudly instead of silently returning the old payment or creating a new one.' }
    : { ok: false, title: 'Unexpected result', text: `Second request returned ${result.second.status}.` })

  return (
    <ExperimentCard
      index={index}
      icon={KeyRound}
      title="Same key, different request"
      question={`Pay ${formatPaise(10_000)} with a key, then send ${formatPaise(90_000)} with the same key. Should that be a retry, a new payment, or an error?`}
      proves="Idempotency keys are bound to the exact request they were first used with."
      running={running}
      done={!!result}
      onRun={run}
      verdict={verdict}
    >
      <div className="grid gap-2 sm:grid-cols-2">
        <Metric label="1st request · ₹100" value={result ? `${result.first.status}` : '–'} tone={result ? 'brand' : undefined} />
        <Metric label="2nd request · ₹900, same key" value={result ? `HTTP ${result.second.status}` : '–'} tone={result ? (ok ? 'brand' : 'decline') : undefined} />
      </div>
      {result?.second.message && <p className="mt-3 font-mono text-xs text-faint">{result.second.message}</p>}
    </ExperimentCard>
  )
}
