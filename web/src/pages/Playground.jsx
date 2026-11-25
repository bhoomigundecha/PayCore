import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Plus, Receipt, Volume2, VolumeX } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import { useSandbox } from '../hooks/useSandbox'
import { useLocalStorage } from '../hooks/useLocalStorage'
import { Button } from '../components/ui/Button'
import { Card, CardHeader } from '../components/ui/Card'
import { Dialog } from '../components/ui/Dialog'
import { EmptyState, PageHeader, Skeleton } from '../components/ui/misc'
import { AuthGate } from '../components/auth/AuthGate'
import { AccountCard } from '../components/playground/AccountCard'
import { CardVisual } from '../components/playground/CardVisual'
import { CreateAccountForm } from '../components/playground/CreateAccountForm'
import { PaymentComposer } from '../components/playground/PaymentComposer'
import { PaymentCard } from '../components/playground/PaymentCard'
import { FeesChip } from '../components/playground/FeesChip'
import { PaymentFlightLayer } from '../components/playground/PaymentFlight'

/**
 * Two symmetric rows:
 *   1. card · wallet · store   (three equal tiles; extra accounts continue the same grid)
 *   2. new payment · payments  (two equal columns; the list scrolls instead of stretching the row)
 */
function Workspace() {
  const { customers, merchants, payments, loading } = useSandbox()
  const accounts = [...customers, ...merchants]

  return (
    <div className="mt-8 space-y-6">
      <section aria-label="Your card and accounts" className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        <motion.div layout className="md:col-span-2 lg:col-span-1">
          <CardVisual />
        </motion.div>
        {loading && accounts.length === 0
          ? [0, 1].map((i) => <Skeleton key={i} className="min-h-72 w-full rounded-2xl" />)
          : (
            <AnimatePresence initial={false}>
              {accounts.map((a) => (
                <motion.div key={a.id} data-account-id={a.id} layout initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }}>
                  <AccountCard account={a} />
                </motion.div>
              ))}
            </AnimatePresence>
          )}
      </section>

      <section aria-label="Payments" className="grid gap-6 lg:grid-cols-2">
        <Card className="p-5">
          <CardHeader icon={Receipt} title="New payment" description="Charge now, or hold it and decide later." action={<FeesChip />} />
          <div className="mt-5"><PaymentComposer /></div>
        </Card>

        {/* On large screens this column takes its height from the form beside it and scrolls inside. */}
        <div className="relative min-h-80">
          <Card className="flex h-full flex-col lg:absolute lg:inset-0">
            <div className="flex items-center justify-between border-b border-line px-5 py-4">
              <p className="text-[15px] font-semibold tracking-tight text-ink">Payments</p>
              <span className="text-xs text-faint tabular">{payments.length} total</span>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto p-3">
              {payments.length === 0 ? (
                <EmptyState icon={Receipt} title="No payments yet" description="Pay your store from your wallet to see the full lifecycle here." />
              ) : (
                <div className="space-y-3">
                  {payments.map((p) => <PaymentCard key={p.id} payment={p} />)}
                </div>
              )}
            </div>
          </Card>
        </div>
      </section>
      <PaymentFlightLayer />
    </div>
  )
}

export default function Playground() {
  const { user } = useAuth()
  const [sound, setSound] = useLocalStorage('paycore.sound', false)
  const [adding, setAdding] = useState(false)

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
      <PageHeader
        eyebrow="Playground"
        title={user ? `Hi ${user.guest ? 'there' : user.name.split(' ')[0]}, move some money` : 'Move some money'}
        actions={user && (
          <>
            <Button variant="secondary" onClick={() => setAdding(true)}>
              <Plus className="size-4" /> New account
            </Button>
            <Button variant="ghost" onClick={() => setSound((on) => !on)} aria-pressed={sound} title="Payment sounds">
              {sound ? <Volume2 className="size-4" /> : <VolumeX className="size-4" />} Sound {sound ? 'on' : 'off'}
            </Button>
          </>
        )}
      />
      <AuthGate title="Get your card to start">
        <Workspace />
      </AuthGate>
      <Dialog open={adding} onClose={() => setAdding(false)} title="New account" description="Open another wallet, or another store to receive payments.">
        <CreateAccountForm onCreated={() => setAdding(false)} />
      </Dialog>
    </div>
  )
}
