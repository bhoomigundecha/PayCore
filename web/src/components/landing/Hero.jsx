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
    <section className="relative -mt-20">
      <div className="mx-auto max-w-5xl px-4 pt-44 pb-20 text-center sm:px-6 sm:pt-52 sm:pb-24">
        <motion.h1 {...fadeUp(0)} className="text-[3.25rem] leading-[0.98] font-semibold tracking-[-0.045em] text-balance text-ink sm:text-8xl">
          Every payment.
          <span className="block pb-2 text-brand">Exactly once.</span>
        </motion.h1>

        <motion.p {...fadeUp(0.08)} className="mx-auto mt-6 max-w-xl text-base text-pretty text-muted sm:text-lg">
          A card payments engine on a double-entry ledger. Retries, races and crashes can&apos;t charge anyone twice.
        </motion.p>

        <motion.div {...fadeUp(0.16)} className="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row">
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
