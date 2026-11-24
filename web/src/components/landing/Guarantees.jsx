import { motion } from 'motion/react'
import { ArrowRight, Hand, Link2, Repeat, Swords, Zap } from 'lucide-react'
import { Link } from 'react-router'
import { cn } from '../../lib/cn'
import { SpotlightCard } from '../ui/Card'

/* Small looping illustrations, one per guarantee. Pure CSS/motion, no images. */

function RetriesVisual() {
  return (
    <div className="relative flex h-36 items-center justify-between px-2">
      <div className="flex flex-col gap-1.5">
        {[0, 1, 2, 3, 4].map((i) => (
          <div key={i} className="relative">
            {/* faint copy stays put so the tile never looks empty */}
            <div className="rounded-md border border-info/15 bg-info/[0.04] px-2 py-0.5 font-mono text-[10px] text-info/40">POST key=a1f…</div>
            <motion.div
              className="absolute inset-0 rounded-md border border-info/40 bg-info/15 px-2 py-0.5 font-mono text-[10px] text-info"
              animate={{ x: [0, 0, 120], opacity: [0, 1, 0] }}
              transition={{ duration: 2.6, delay: i * 0.12, repeat: Infinity, times: [0, 0.15, 0.55], ease: 'easeIn' }}
            >
              POST key=a1f…
            </motion.div>
          </div>
        ))}
      </div>
      <motion.div
        className="grid size-24 place-items-center rounded-2xl border border-brand/30 bg-brand/10 text-center"
        animate={{ scale: [1, 1, 1.08, 1] }}
        transition={{ duration: 2.6, repeat: Infinity, times: [0, 0.42, 0.55, 0.75] }}
      >
        <div>
          <p className="text-2xl font-semibold text-brand">1</p>
          <p className="text-[10px] text-muted">payment</p>
        </div>
      </motion.div>
    </div>
  )
}

function CrashVisual() {
  const steps = ['Intent', 'Money', 'Outcome']
  return (
    <div className="flex h-36 flex-col justify-center gap-4 px-2">
      <div className="flex items-center gap-2">
        {steps.map((s, i) => (
          <div key={s} className="flex flex-1 items-center gap-2">
            <motion.span
              className="grid size-7 shrink-0 place-items-center rounded-full border text-[11px] font-semibold"
              animate={{
                borderColor: ['rgb(255 255 255 / 0.15)', i < 2 ? 'rgb(52 211 153 / 0.6)' : 'rgb(255 255 255 / 0.15)', 'rgb(52 211 153 / 0.6)'],
                color: ['#5d6472', i < 2 ? '#34d399' : '#5d6472', '#34d399'],
              }}
              transition={{ duration: 3.6, repeat: Infinity, times: [0, 0.35 + i * 0.05, 0.85] }}
            >
              {i + 1}
            </motion.span>
            {i < 2 && <span className="h-px flex-1 bg-line-strong" />}
          </div>
        ))}
      </div>
      <motion.div
        className="flex items-center gap-2 text-xs"
        animate={{ opacity: [0, 1, 1, 0] }}
        transition={{ duration: 3.6, repeat: Infinity, times: [0.35, 0.42, 0.7, 0.78] }}
      >
        <Zap className="size-3.5 text-decline" /> <span className="text-decline">crash after step 2</span>
      </motion.div>
      <motion.div
        className="-mt-9 flex items-center gap-2 text-xs"
        animate={{ opacity: [0, 0, 1, 1] }}
        transition={{ duration: 3.6, repeat: Infinity, times: [0, 0.78, 0.85, 1] }}
      >
        <Repeat className="size-3.5 text-hold" /> <span className="text-hold">recovery job finished it · charged once</span>
      </motion.div>
    </div>
  )
}

function RaceVisual() {
  return (
    <div className="flex h-36 flex-col justify-center gap-3 px-2">
      <div className="grid grid-cols-10 gap-1.5">
        {Array.from({ length: 20 }).map((_, i) => {
          const ok = [2, 5, 9, 13, 17].includes(i)
          return (
            <motion.span
              key={i}
              className="aspect-square rounded-[5px] border border-line"
              animate={{
                backgroundColor: ['rgb(255 255 255 / 0.03)', ok ? 'rgb(52 211 153 / 0.55)' : 'rgb(251 113 133 / 0.35)', ok ? 'rgb(52 211 153 / 0.55)' : 'rgb(251 113 133 / 0.35)', 'rgb(255 255 255 / 0.03)'],
              }}
              transition={{ duration: 3.4, repeat: Infinity, delay: (i % 7) * 0.05, times: [0.1, 0.3, 0.85, 1] }}
            />
          )
        })}
      </div>
      <p className="text-xs text-muted"><span className="text-brand">5 approved</span> · <span className="text-decline">15 declined</span> · balance never below ₹0</p>
    </div>
  )
}

function SplitVisual() {
  return (
    <div className="flex h-36 flex-col justify-center gap-3 px-2">
      <div className="flex h-9 overflow-hidden rounded-xl border border-line">
        <motion.div className="flex items-center overflow-hidden bg-brand/25 px-3 text-xs font-medium whitespace-nowrap text-brand" initial={{ width: '0%' }} whileInView={{ width: '88%' }} viewport={{ once: true }} transition={{ duration: 1.1, ease: [0.16, 1, 0.3, 1] }}>
          Merchant 98%
        </motion.div>
        <motion.div className="flex items-center justify-center bg-violet/25 text-[10px] font-medium text-violet" initial={{ width: '0%' }} whileInView={{ width: '12%' }} viewport={{ once: true }} transition={{ duration: 1.1, delay: 0.2 }}>
          2%
        </motion.div>
      </div>
      <p className="text-xs text-muted">Two legs, one <span className="font-mono text-ink">linked</span> chain: both post, or neither does.</p>
    </div>
  )
}

function HoldVisual() {
  return (
    <div className="flex h-36 flex-col justify-center gap-2.5 px-2">
      {[
        { label: 'Available', color: 'bg-brand/50', to: ['100%', '70%', '70%', '100%'] },
        { label: 'On hold', color: 'bg-hold/60', to: ['0%', '30%', '30%', '0%'] },
      ].map((row) => (
        <div key={row.label}>
          <div className="mb-1 text-[11px] text-muted">{row.label}</div>
          <div className="h-2.5 overflow-hidden rounded-full bg-white/[0.05]">
            <motion.div className={cn('h-full rounded-full', row.color)} animate={{ width: row.to }} transition={{ duration: 4, repeat: Infinity, times: [0, 0.25, 0.7, 0.95] }} />
          </div>
        </div>
      ))}
      <p className="text-[11px] text-faint">authorize → hold … cancel → released</p>
    </div>
  )
}

const ITEMS = [
  { title: 'Exactly-once charging', text: 'Retries reuse the Idempotency-Key and every transfer id is derived from the payment, so a retry can only return the same result.', icon: Repeat, Visual: RetriesVisual, span: 'md:col-span-2', link: '/lab' },
  { title: 'Crash-safe', text: 'Intent is written before money moves, and a recovery job finishes anything a crash interrupted.', icon: Zap, Visual: CrashVisual, link: '/how-it-works' },
  { title: 'No overdrafts, even in a race', text: 'The balance rule lives inside the ledger, so concurrent payments can never overspend a wallet.', icon: Swords, Visual: RaceVisual, link: '/lab' },
  { title: 'Atomic fee split', text: 'The merchant share and the platform fee are one linked batch: both post, or neither does.', icon: Link2, Visual: SplitVisual, link: '/explorer' },
  { title: 'Authorize & capture', text: 'Card-style holds reserve money until captured or released, and expire on their own after 7 days.', icon: Hand, Visual: HoldVisual, link: '/playground' },
]

export function Guarantees() {
  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {ITEMS.map(({ title, text, icon: Icon, Visual, span, link }, i) => (
        <motion.div
          key={title}
          className={cn(span)}
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-60px' }}
          transition={{ duration: 0.5, delay: (i % 3) * 0.07 }}
        >
          <SpotlightCard className="flex h-full flex-col p-5">
            <Visual />
            <div className="mt-4 flex items-center gap-2">
              <Icon className="size-4 text-brand" />
              <h3 className="font-semibold tracking-tight text-ink">{title}</h3>
            </div>
            <p className="mt-1.5 flex-1 text-sm text-pretty text-muted">{text}</p>
            <Link to={link} className="mt-4 inline-flex items-center gap-1 text-xs font-medium text-muted transition-colors hover:text-ink">
              See it <ArrowRight className="size-3 transition-transform group-hover:translate-x-0.5" />
            </Link>
          </SpotlightCard>
        </motion.div>
      ))}
    </div>
  )
}
