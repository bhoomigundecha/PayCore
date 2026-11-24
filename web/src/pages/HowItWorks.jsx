import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowRight, Database, KeyRound, Landmark, Lock, Repeat, ShieldAlert, ShieldCheck, Timer, Zap } from 'lucide-react'
import { cn } from '../lib/cn'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Card, SpotlightCard } from '../components/ui/Card'
import { PageHeader, SectionHeading } from '../components/ui/misc'
import { GITHUB_URL } from '../components/layout/Navbar'

/*
 * Crash scenarios for one auto-captured payment. Each describes what both databases hold at the
 * moment of the crash and how PayCore gets back to a correct state.
 */
const STEPS = [
  { id: 'intent', title: 'Record intent', db: 'PostgreSQL', text: 'INSERT payment … status = AUTHORIZING, transfer ids derived from the payment id' },
  { id: 'money', title: 'Move money', db: 'TigerBeetle', text: 'create_transfers: two linked pending transfers, then post them' },
  { id: 'outcome', title: 'Record outcome', db: 'PostgreSQL', text: 'UPDATE payment SET status = CAPTURED' },
]

const CRASHES = [
  {
    at: 0,
    label: 'Before step 1',
    pg: { status: 'no row', tone: 'neutral' },
    tb: { status: 'no transfers', tone: 'neutral' },
    danger: 'Nothing was written anywhere.',
    recovery: 'The client gets an error or a timeout and retries with the same Idempotency-Key. It is a brand-new request, so it simply runs.',
    result: 'Charged once',
  },
  {
    at: 1,
    label: 'Between 1 and 2',
    pg: { status: 'AUTHORIZING', tone: 'info' },
    tb: { status: 'no transfers', tone: 'neutral' },
    danger: 'Postgres says a payment started, but no money moved.',
    recovery: 'The recovery job finds the row stuck in AUTHORIZING and re-sends the ledger step. TigerBeetle answers "created": the money moves now.',
    result: 'Charged once',
  },
  {
    at: 2,
    label: 'Between 2 and 3',
    pg: { status: 'AUTHORIZING', tone: 'info' },
    tb: { status: 'transfers exist', tone: 'brand' },
    danger: 'The dangerous one. Money moved, but Postgres doesn\'t know. A naive system would charge again on retry.',
    recovery: 'The recovery job re-sends the same transfer ids (they are derived from the payment id). TigerBeetle answers "exists", so nothing moves twice, and Postgres is updated to CAPTURED.',
    result: 'Charged once',
  },
  {
    at: 3,
    label: 'After step 3',
    pg: { status: 'CAPTURED', tone: 'brand' },
    tb: { status: 'transfers exist', tone: 'brand' },
    danger: 'Everything is written; only the HTTP response was lost.',
    recovery: 'The client retries with the same Idempotency-Key and gets the existing CAPTURED payment back.',
    result: 'Charged once',
  },
]

function CrashSimulator() {
  const [crash, setCrash] = useState(2)
  const scenario = CRASHES[crash]

  return (
    <Card className="p-5 sm:p-8">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm font-medium text-ink">Pick where the server crashes</p>
        <p className="text-xs text-faint">Tap a lightning bolt</p>
      </div>

      {/* Timeline of three steps with four possible crash points */}
      <div className="mt-6 flex flex-col gap-2 md:flex-row md:items-stretch md:gap-0">
        {[0, 1, 2, 3].map((point) => (
          <div key={point} className={cn('flex flex-col gap-2 md:flex-row md:items-stretch md:gap-0', point < 3 && 'md:flex-1')}>
            <button
              type="button"
              onClick={() => setCrash(point)}
              aria-pressed={crash === point}
              aria-label={`Crash ${CRASHES[point].label.toLowerCase()}`}
              className={cn(
                'grid h-9 w-full shrink-0 place-items-center rounded-xl border transition-all md:h-auto md:w-10',
                crash === point ? 'border-decline/60 bg-decline/15 text-decline shadow-[0_0_24px_-6px_rgb(251_113_133/0.8)]' : 'border-dashed border-line text-faint hover:border-decline/40 hover:text-decline',
              )}
            >
              <Zap className="size-4" />
            </button>
            {point < 3 && (
              <div className={cn('flex-1 rounded-xl border p-3 transition-opacity sm:p-4 md:mx-2', point < crash ? 'border-brand/30 bg-brand/[0.05]' : 'border-line opacity-50')}>
                <div className="flex items-center justify-between gap-2">
                  <p className="text-xs font-semibold text-ink sm:text-sm">{point + 1}. {STEPS[point].title}</p>
                  <span className="hidden text-[10px] text-faint sm:inline">{STEPS[point].db}</span>
                </div>
                <p className="mt-1 font-mono text-[11px] leading-relaxed text-muted">{STEPS[point].text}</p>
              </div>
            )}
          </div>
        ))}
      </div>

      <AnimatePresence mode="wait">
        <motion.div key={crash} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.25 }} className="mt-8 grid gap-4 lg:grid-cols-3">
          <div className="rounded-2xl border border-line bg-white/[0.02] p-5">
            <p className="text-xs font-semibold tracking-wider text-faint uppercase">At the moment of the crash</p>
            <div className="mt-4 space-y-3 text-sm">
              <div className="flex items-center justify-between"><span className="flex items-center gap-2 text-muted"><Database className="size-4" /> PostgreSQL</span><Badge tone={scenario.pg.tone}>{scenario.pg.status}</Badge></div>
              <div className="flex items-center justify-between"><span className="flex items-center gap-2 text-muted"><Landmark className="size-4" /> TigerBeetle</span><Badge tone={scenario.tb.tone}>{scenario.tb.status}</Badge></div>
            </div>
            <p className="mt-4 flex gap-2 text-sm text-pretty text-muted"><ShieldAlert className="mt-0.5 size-4 shrink-0 text-hold" />{scenario.danger}</p>
          </div>
          <div className="rounded-2xl border border-line bg-white/[0.02] p-5 lg:col-span-2">
            <p className="text-xs font-semibold tracking-wider text-faint uppercase">How PayCore recovers</p>
            <p className="mt-4 flex gap-2 text-pretty text-ink"><Repeat className="mt-1 size-4 shrink-0 text-info" />{scenario.recovery}</p>
            <p className="mt-5 inline-flex items-center gap-2 rounded-full border border-brand/30 bg-brand/10 px-3 py-1 text-sm font-medium text-brand">
              <ShieldCheck className="size-4" /> {scenario.result}
            </p>
          </div>
        </motion.div>
      </AnimatePresence>
    </Card>
  )
}

const STATES = [
  { id: 'AUTHORIZING', x: 0, y: 1, tone: 'info' },
  { id: 'AUTHORIZED', x: 1, y: 1, tone: 'hold' },
  { id: 'CAPTURING', x: 2, y: 1, tone: 'info' },
  { id: 'CAPTURED', x: 3, y: 1, tone: 'brand' },
  { id: 'REFUNDING', x: 4, y: 1, tone: 'info' },
  { id: 'REFUNDED', x: 5, y: 1, tone: 'violet' },
  { id: 'FAILED', x: 0, y: 2.2, tone: 'decline' },
  { id: 'CANCELING', x: 1, y: 2.2, tone: 'info' },
  { id: 'CANCELED', x: 2, y: 2.2, tone: 'neutral' },
]
const EDGES = [
  ['AUTHORIZING', 'AUTHORIZED'], ['AUTHORIZED', 'CAPTURING'], ['CAPTURING', 'CAPTURED'], ['CAPTURED', 'REFUNDING'],
  ['REFUNDING', 'REFUNDED'], ['AUTHORIZING', 'FAILED'], ['AUTHORIZED', 'CANCELING'], ['CANCELING', 'CANCELED'],
]
const TONE_FILL = { info: '#7dd3fc', hold: '#fbbf24', brand: '#34d399', violet: '#a78bfa', decline: '#fb7185', neutral: '#9aa1ad' }

function StateMachine() {
  const W = 150
  const H = 80
  const pos = (s) => ({ cx: 70 + s.x * W, cy: s.y * H })
  const byId = Object.fromEntries(STATES.map((s) => [s.id, s]))
  return (
    <div className="overflow-x-auto">
      <svg viewBox="0 0 900 220" className="min-w-[720px]" role="img" aria-label="Payment state machine">
        {EDGES.map(([a, b]) => {
          const p = pos(byId[a])
          const q = pos(byId[b])
          return <line key={a + b} x1={p.cx} y1={p.cy} x2={q.cx} y2={q.cy} stroke="rgb(255 255 255 / 0.15)" strokeWidth="1.5" />
        })}
        {STATES.map((s) => {
          const { cx, cy } = pos(s)
          const inFlight = s.id.endsWith('ING')
          return (
            <g key={s.id}>
              <rect x={cx - 62} y={cy - 16} width="124" height="32" rx="16" fill="#0c0e13" stroke={TONE_FILL[s.tone]} strokeOpacity={inFlight ? 0.5 : 0.9} strokeDasharray={inFlight ? '4 4' : undefined} />
              <text x={cx} y={cy + 4} textAnchor="middle" fontSize="11" fontFamily="var(--font-mono)" fill={TONE_FILL[s.tone]}>{s.id}</text>
            </g>
          )
        })}
      </svg>
      <p className="mt-2 text-xs text-faint">Dashed states are written <span className="text-muted">before</span> the ledger is called. The recovery job only ever has to finish a dashed state.</p>
    </div>
  )
}

const DECISIONS = [
  { icon: KeyRound, title: 'Deterministic transfer ids', text: 'Every ledger transfer id is a hash of payment id + step + leg. Any retry, from any machine, sends identical ids, which TigerBeetle de-duplicates.' },
  { icon: Lock, title: 'Fail closed for money', text: 'If the ledger is unreachable, PayCore returns 503 rather than guessing. The recovery job completes the work later.' },
  { icon: Zap, title: 'Fail open for rate limits', text: 'If Redis is down, requests go through. Rate limiting protects capacity, not money, so availability wins there.' },
  { icon: Database, title: 'Two databases, two jobs', text: 'Postgres for what a payment is (state machine, idempotency keys). TigerBeetle for where money is (double-entry, overdraft rules).' },
  { icon: Repeat, title: 'Optimistic locking', text: 'Payment rows carry a @Version. If a client retry and the recovery job advance the same payment, one wins and the other reads the result.' },
  { icon: Timer, title: 'Holds expire on their own', text: 'Authorizations are pending transfers with a 7-day timeout inside the ledger, like real card holds.' },
]

export default function HowItWorks() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6">
      <PageHeader
        eyebrow="How it works"
        title="Crash it anywhere. It still charges once."
        description="Moving money means writing to two databases that can't share a transaction. Here's how PayCore makes that safe, and what happens at every possible crash point."
      />

      <section className="mt-12">
        <SectionHeading align="left" eyebrow="The dual-write problem" title="Choose a crash point" description="A payment needs three writes. The server can die between any two of them. Pick a point and see what each database holds, and how PayCore recovers." />
        <div className="mt-8"><CrashSimulator /></div>
      </section>

      <section className="mt-20">
        <SectionHeading align="left" eyebrow="State machine" title="Every payment, every state" />
        <Card className="mt-8 p-5 sm:p-8"><StateMachine /></Card>
      </section>

      <section className="mt-20">
        <SectionHeading align="left" eyebrow="Design decisions" title="Trade-offs, on purpose" />
        <div className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {DECISIONS.map(({ icon: Icon, title, text }) => (
            <SpotlightCard key={title} className="p-6">
              <Icon className="size-5 text-brand" />
              <h3 className="mt-4 font-semibold text-ink">{title}</h3>
              <p className="mt-1.5 text-sm text-pretty text-muted">{text}</p>
            </SpotlightCard>
          ))}
        </div>
      </section>

      <section className="mt-20 flex flex-col items-center rounded-3xl border border-line bg-[radial-gradient(60%_100%_at_50%_0%,rgb(52_211_153/0.12),transparent)] px-6 py-14 text-center">
        <h2 className="text-2xl font-semibold tracking-tight text-ink sm:text-3xl">Read the code behind it</h2>
        <p className="mt-3 max-w-xl text-muted">Start with PaymentService.java: the state machine and its recovery path fit in one file, with integration tests for every case above.</p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Button href={GITHUB_URL} variant="primary">View on GitHub <ArrowRight className="size-4" /></Button>
          <Button href="/swagger-ui/index.html" variant="secondary">API reference</Button>
        </div>
      </section>
    </div>
  )
}
