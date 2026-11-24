import { motion } from 'motion/react'
import { ArrowRight, FlaskConical } from 'lucide-react'
import { MovingBorderButton } from '../ui/MovingBorderButton'
import { Button } from '../ui/Button'

const fadeUp = (delay) => ({
  initial: { opacity: 0, y: 16, filter: 'blur(6px)' },
  animate: { opacity: 1, y: 0, filter: 'blur(0px)' },
  transition: { duration: 0.7, delay, ease: [0.16, 1, 0.3, 1] },
})

export function Hero() {
  return (
    // Pulled up under the floating navbar so the background starts at the very top.
    <section className="relative isolate -mt-20 overflow-hidden">
      {/* Background: fading grid, a conic spotlight from the top, and two soft colour blooms. */}
      <div aria-hidden className="absolute inset-0 -z-10 bg-grid" />
      <motion.div
        aria-hidden
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 1.6, ease: 'easeOut' }}
        className="absolute -top-40 left-1/2 -z-10 h-[620px] w-[1100px] -translate-x-1/2 bg-[conic-gradient(from_180deg_at_50%_0%,transparent_35%,rgb(52_211_153/0.22)_47%,rgb(125_211_252/0.18)_53%,transparent_65%)] blur-2xl"
      />
      <div aria-hidden className="absolute top-40 -left-40 -z-10 size-[420px] rounded-full bg-brand/10 blur-[120px]" />
      <div aria-hidden className="absolute top-20 -right-40 -z-10 size-[420px] rounded-full bg-violet/10 blur-[120px]" />

      <div className="mx-auto max-w-5xl px-4 pt-44 pb-20 text-center sm:px-6 sm:pt-52 sm:pb-24">
        <motion.h1 {...fadeUp(0)} className="text-[3.25rem] leading-[0.98] font-semibold tracking-[-0.045em] text-balance text-ink sm:text-8xl">
          Every payment.
          <span className="block pb-2 text-gradient">Exactly once.</span>
        </motion.h1>

        <motion.div {...fadeUp(0.12)} className="mt-12 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <MovingBorderButton to="/playground">
            Try it live <ArrowRight className="size-4" />
          </MovingBorderButton>
          <Button to="/lab" variant="secondary" size="lg">
            <FlaskConical className="size-4" /> Break it in the Lab
          </Button>
        </motion.div>
      </div>
    </section>
  )
}
