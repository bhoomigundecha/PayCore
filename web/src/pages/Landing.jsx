import { motion } from 'motion/react'
import { Hero } from '../components/landing/Hero'
import { LiveStats } from '../components/landing/LiveStats'
import { FlowDiagram } from '../components/landing/FlowDiagram'
import { Guarantees } from '../components/landing/Guarantees'
import { TechLogos } from '../components/landing/TechLogos'
import { SectionHeading } from '../components/ui/misc'

export default function Landing() {
  return (
    <>
      <Hero />
      <LiveStats />

      <section className="mx-auto mt-28 max-w-6xl px-4 sm:px-6">
        <SectionHeading
          eyebrow="Follow one payment"
          title="Three writes, two databases, zero ways to double-charge"
          description="Postgres knows what a payment is. TigerBeetle knows where the money is. PayCore writes its intent first, so a crash anywhere can be finished later, never repeated."
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
    </>
  )
}
