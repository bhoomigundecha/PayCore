import { motion } from 'motion/react'
import { Hero } from '../components/landing/Hero'
import { ActivityFeed } from '../components/landing/ActivityFeed'
import { FlowDiagram } from '../components/landing/FlowDiagram'
import { Guarantees } from '../components/landing/Guarantees'
import { TechLogos } from '../components/landing/TechLogos'
import { SectionHeading } from '../components/ui/misc'

export default function Landing() {
  return (
    <div className="relative isolate">
      {/* Page-wide grid; starts under the floating navbar. */}
      <div aria-hidden className="pointer-events-none absolute inset-x-0 -top-20 bottom-0 -z-10 bg-grid-page" />
      <Hero />
      <ActivityFeed />

      <section className="mx-auto mt-28 max-w-6xl px-4 sm:px-6">
        <SectionHeading
          eyebrow="Follow one payment"
          title="Three writes, two databases, zero ways to double-charge"
        />
        <motion.div className="mt-12" initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.6 }}>
          <FlowDiagram />
        </motion.div>
      </section>

      <section id="guarantees" className="mx-auto mt-28 max-w-6xl scroll-mt-24 px-4 sm:px-6">
        <SectionHeading
          eyebrow="Guarantees"
          title="The hard parts of payments, handled"
          description="Each of these is covered by an integration test against the real ledger, and most of them you can trigger yourself."
        />
        <div className="mt-12">
          <Guarantees />
        </div>
      </section>

      <TechLogos />
    </div>
  )
}
