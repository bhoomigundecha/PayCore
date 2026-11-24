import { useState } from 'react'
import { motion } from 'motion/react'
import { Swords } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { cn } from '../../lib/cn'
import { formatPaise } from '../../lib/money'
import { ExperimentCard, Metric } from './shared'
import { available, freshAccounts } from './labApi'

const ATTEMPTS = 20
const WALLET = 100_000 // ₹1,000
const AMOUNT = 20_000 // ₹200: the wallet can afford exactly 5

export function OverspendRace({ index }) {
  const [running, setRunning] = useState(false)
  const [tiles, setTiles] = useState(Array(ATTEMPTS).fill('idle'))
  const [result, setResult] = useState(null)

  const run = async () => {
    setRunning(true)
    setResult(null)
    setTiles(Array(ATTEMPTS).fill('idle'))
    try {
      const { customer, merchant } = await freshAccounts('Race', WALLET)
      setTiles(Array(ATTEMPTS).fill('pending'))
      const started = performance.now()

      const outcomes = await Promise.all(Array.from({ length: ATTEMPTS }, async (_, i) => {
        let outcome
        try {
          const p = await api.createPayment({ customerAccountId: customer.id, merchantAccountId: merchant.id, amount: AMOUNT })
          outcome = p.status === 'CAPTURED' ? 'ok' : 'declined'
        } catch {
          outcome = 'error'
        }
        setTiles((t) => t.map((x, j) => (j === i ? outcome : x)))
        return outcome
      }))

      const elapsed = Math.round(performance.now() - started)
      const after = await available(customer.id)
      setResult({
        approved: outcomes.filter((o) => o === 'ok').length,
        declined: outcomes.filter((o) => o === 'declined').length,
        errors: outcomes.filter((o) => o === 'error').length,
        after,
        elapsed,
      })
    } catch (e) {
      toast.error(e.message)
    } finally {
      setRunning(false)
    }
  }

  const expected = WALLET / AMOUNT
  const ok = result && result.approved === expected && result.after === 0
  const verdict = result && (ok
    ? { ok: true, title: 'No overdraft', text: `${result.approved} of ${ATTEMPTS} concurrent payments approved, the rest declined, and the wallet stopped at exactly ₹0 (${result.elapsed} ms for all ${ATTEMPTS}). The balance rule lives inside the ledger, so there's no check-then-spend gap to race through.` }
    : { ok: false, title: 'Unexpected result', text: `${result.approved} approved, ${result.declined} declined, ${result.errors} errors, wallet ${formatPaise(result.after)}.` })

  return (
    <ExperimentCard
      index={index}
      icon={Swords}
      title="Race to overspend"
      question={`A wallet holds ${formatPaise(WALLET)}. ${ATTEMPTS} payments of ${formatPaise(AMOUNT)} hit the API at the same instant. A naive "check balance, then debit" would approve too many. What happens here?`}
      proves="The no-overdraft rule is enforced atomically by TigerBeetle, not by Java code that could race."
      running={running}
      done={!!result}
      onRun={run}
      verdict={verdict}
    >
      <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
        <div className="grid grid-cols-5 gap-2 sm:grid-cols-10">
          {tiles.map((t, i) => (
            <motion.div
              key={i}
              initial={false}
              animate={{ scale: t === 'ok' || t === 'declined' ? [0.85, 1.06, 1] : 1 }}
              transition={{ duration: 0.35 }}
              className={cn(
                'grid aspect-square place-items-center rounded-lg border font-mono text-[10px] transition-colors duration-300',
                t === 'ok' && 'border-brand/60 bg-brand/30 text-[#d1fae5] shadow-[0_0_20px_-6px_rgb(52_211_153/0.8)]',
                t === 'declined' && 'border-decline/40 bg-decline/20 text-[#fecdd3]',
                t === 'error' && 'border-hold/30 bg-hold/15 text-hold',
                t === 'pending' && 'border-info/30 bg-info/10 text-info animate-pulse',
                t === 'idle' && 'border-line bg-white/[0.02] text-faint',
              )}
            >
              {t === 'ok' ? '₹200' : t === 'declined' ? '✕' : i + 1}
            </motion.div>
          ))}
        </div>
        <div className="grid grid-cols-2 gap-2 self-start">
          <Metric label="Approved" value={result ? result.approved : '–'} tone="brand" />
          <Metric label="Declined" value={result ? result.declined : '–'} tone="decline" />
          <Metric label="Wallet before" value={formatPaise(WALLET)} />
          <Metric label="Wallet after" value={result ? formatPaise(result.after) : '–'} tone={result && result.after >= 0 ? 'brand' : undefined} />
        </div>
      </div>
    </ExperimentCard>
  )
}
