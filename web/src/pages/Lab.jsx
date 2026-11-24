import { motion } from 'motion/react'
import { Info } from 'lucide-react'
import { PageHeader } from '../components/ui/misc'
import { RetryStorm } from '../components/lab/RetryStorm'
import { OverspendRace } from '../components/lab/OverspendRace'
import { HoldRelease } from '../components/lab/HoldRelease'
import { KeyReuse } from '../components/lab/KeyReuse'

const EXPERIMENTS = [RetryStorm, OverspendRace, HoldRelease, KeyReuse]

export default function Lab() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6">
      <PageHeader
        eyebrow="Break-it Lab"
        title="Try to make it lose money"
        description="Each experiment fires real, concurrent requests at the live API using fresh play-money accounts. Nothing is simulated in the browser."
      />
      <p className="mt-6 flex items-start gap-2 rounded-xl border border-line bg-white/[0.02] p-3 text-sm text-muted">
        <Info className="mt-0.5 size-4 shrink-0 text-info" />
        Accounts created here show up in the Ledger Explorer, so you can inspect every transfer an experiment made.
      </p>
      <div className="mt-8 space-y-6">
        {EXPERIMENTS.map((Experiment, i) => (
          <motion.div key={i} initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-40px' }} transition={{ duration: 0.45 }}>
            <Experiment index={i + 1} />
          </motion.div>
        ))}
      </div>
    </div>
  )
}
