import { AnimatePresence, motion } from 'motion/react'
import { CircleCheck, CircleX, Play, RotateCcw } from 'lucide-react'
import { cn } from '../../lib/cn'
import { Button } from '../ui/Button'
import { SpotlightCard } from '../ui/Card'

export function ExperimentCard({ index, icon: Icon, title, question, proves, running, done, onRun, children, verdict }) {
  return (
    <SpotlightCard className="p-6 sm:p-8">
      <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
        <div className="max-w-xl">
          <p className="font-mono text-xs text-faint">Experiment {String(index).padStart(2, '0')}</p>
          <h2 className="mt-2 flex items-center gap-2.5 text-xl font-semibold tracking-tight text-ink">
            <Icon className="size-5 text-brand" /> {title}
          </h2>
          <p className="mt-2 text-pretty text-muted">{question}</p>
          <p className="mt-3 text-sm text-faint"><span className="text-muted">What it proves:</span> {proves}</p>
        </div>
        <Button variant={done ? 'secondary' : 'brand'} onClick={onRun} loading={running} className="self-start">
          {done ? <RotateCcw className="size-4" /> : <Play className="size-4" />}
          {done ? 'Run again' : 'Run experiment'}
        </Button>
      </div>

      <div className="mt-8">{children}</div>

      <AnimatePresence>
        {verdict && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className={cn(
              'mt-6 flex items-start gap-3 rounded-xl border p-4',
              verdict.ok ? 'border-brand/25 bg-brand/[0.07]' : 'border-decline/25 bg-decline/[0.07]',
            )}
          >
            {verdict.ok ? <CircleCheck className="mt-0.5 size-5 shrink-0 text-brand" /> : <CircleX className="mt-0.5 size-5 shrink-0 text-decline" />}
            <div>
              <p className={cn('font-semibold', verdict.ok ? 'text-brand' : 'text-decline')}>{verdict.title}</p>
              <p className="mt-0.5 text-sm text-muted">{verdict.text}</p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </SpotlightCard>
  )
}

export function Metric({ label, value, tone }) {
  return (
    <div className="rounded-xl border border-line bg-white/[0.02] p-3">
      <p className="text-[11px] text-faint uppercase tracking-wide">{label}</p>
      <p className={cn('mt-1 text-lg font-semibold tabular', tone === 'brand' ? 'text-brand' : tone === 'decline' ? 'text-decline' : tone === 'hold' ? 'text-hold' : 'text-ink')}>{value}</p>
    </div>
  )
}
