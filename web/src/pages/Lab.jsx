import { motion } from 'motion/react'
import { Info } from 'lucide-react'
import { PageHeader } from '../components/ui/misc'
import { AuthGate } from '../components/auth/AuthGate'
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
      <div className="mt-8">
        <AuthGate title="Sign in to run experiments" description="Experiments create play-money accounts in your name, so they need a session. Guest mode is one click.">
          <p className="flex items-start gap-2 rounded-xl border border-line bg-surface p-3 text-sm text-muted">
            <Info className="mt-0.5 size-4 shrink-0 text-info" />
            Each run creates fresh Lab accounts in your name. Inspect every transfer they made in the Ledger Explorer.
          </p>
          <div className="mt-8 space-y-6">
            {EXPERIMENTS.map((Experiment, i) => (
              <motion.div key={i} initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-40px' }} transition={{ duration: 0.45 }}>
                <Experiment index={i + 1} />
              </motion.div>
            ))}
          </div>
        </AuthGate>
      </div>
    </div>
  )
}
