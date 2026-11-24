import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Receipt, RotateCcw, Sparkles, Users } from 'lucide-react'
import { toast } from 'sonner'
import { useSandbox } from '../hooks/useSandbox'
import { Button } from '../components/ui/Button'
import { Card, CardHeader } from '../components/ui/Card'
import { EmptyState, PageHeader } from '../components/ui/misc'
import { AccountCard } from '../components/playground/AccountCard'
import { CreateAccountForm } from '../components/playground/CreateAccountForm'
import { PaymentComposer } from '../components/playground/PaymentComposer'
import { PaymentCard } from '../components/playground/PaymentCard'

export default function Playground() {
  const { accounts, customers, merchants, payments, quickStart, reset } = useSandbox()
  const ordered = [...customers, ...merchants]
  const [starting, setStarting] = useState(false)

  const start = async () => {
    setStarting(true)
    try {
      await quickStart()
      toast.success('Sandbox ready: a wallet with ₹5,000 and a merchant')
    } catch (e) {
      toast.error(e.message)
    } finally {
      setStarting(false)
    }
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
      <PageHeader
        eyebrow="Playground"
        title="Move some (play) money"
        description="Create a customer and a merchant, then pay, hold, capture and refund. Every number comes straight from the TigerBeetle ledger."
        actions={(
          <>
            <Button variant="brand" onClick={start} loading={starting}>
              <Sparkles className="size-4" /> Quick start
            </Button>
            {accounts.length > 0 && (
              <Button variant="ghost" onClick={() => { reset(); toast('Sandbox cleared') }}>
                <RotateCcw className="size-4" /> Reset
              </Button>
            )}
          </>
        )}
      />

      <div className="mt-8 grid gap-6 lg:grid-cols-12">
        <section className="space-y-4 lg:col-span-5" aria-label="Accounts">
          <Card className="p-5">
            <CardHeader icon={Users} title="Accounts" description="Customer wallets can't go below zero. The ledger enforces it." />
            <div className="mt-5"><CreateAccountForm /></div>
          </Card>

          {accounts.length === 0 ? (
            <EmptyState
              icon={Sparkles}
              title="Your sandbox is empty"
              description="Quick start creates a customer wallet with ₹5,000 and a merchant, so you can make a payment in seconds."
              action={<Button variant="brand" onClick={start} loading={starting}>Quick start</Button>}
            />
          ) : (
            <div className="space-y-4">
              <AnimatePresence initial={false}>
                {ordered.map((a) => (
                  <motion.div key={a.id} layout initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }}>
                    <AccountCard account={a} />
                  </motion.div>
                ))}
              </AnimatePresence>
            </div>
          )}
        </section>

        <section className="space-y-4 lg:col-span-7" aria-label="Payments">
          <Card className="p-5">
            <CardHeader icon={Receipt} title="New payment" description="Charge now, or hold the money and decide later, like a hotel deposit." />
            <div className="mt-5"><PaymentComposer /></div>
          </Card>

          {payments.length === 0 ? (
            <EmptyState icon={Receipt} title="No payments yet" description="Payments you make show up here with their full lifecycle." />
          ) : (
            <div className="space-y-3">
              {payments.map((id) => <PaymentCard key={id} paymentId={id} />)}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
