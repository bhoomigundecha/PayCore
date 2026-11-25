import { useState } from 'react'
import { useParams } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowLeft, Check, CircleAlert, CreditCard, Lock, ShieldCheck, Store, Wifi } from 'lucide-react'
import { api, newIdempotencyKey } from '../api/client'
import { useApi } from '../hooks/useApi'
import { useAuth } from '../hooks/useAuth'
import { cn } from '../lib/cn'
import { formatPaise, shortId } from '../lib/money'
import { digitsOnly, formatCardNumber, formatExpiry, luhnValid, parseExpiry, PAYCORE_BIN, TEST_DECLINE_CARD } from '../lib/cards'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Field, TextInput } from '../components/ui/Form'
import { LogoMark, Skeleton } from '../components/ui/misc'
import { ApprovePaymentDialog } from '../components/playground/ApprovePaymentDialog'

/** Live preview of the card being typed; flips to the back while the CVC field is focused. */
function CardPreview({ number, expiry, cvc, flipped }) {
  const masked = (formatCardNumber(number) + ' •••• •••• •••• ••••'.slice(formatCardNumber(number).length)).slice(0, 19)
  return (
    <div className="mx-auto w-full max-w-sm [perspective:1200px]">
      <motion.div
        animate={{ rotateY: flipped ? 180 : 0 }}
        transition={{ type: 'spring', stiffness: 160, damping: 20 }}
        className="relative aspect-[1.586] w-full [transform-style:preserve-3d]"
      >
        {/* front */}
        <div className="absolute inset-0 overflow-hidden rounded-2xl border border-line-strong bg-raised p-5 [backface-visibility:hidden]">
          <span aria-hidden className="absolute inset-y-0 left-0 w-1 bg-brand" />
          <div className="flex items-center gap-2 text-sm font-semibold text-ink"><LogoMark className="size-6" /> PayCore</div>
          <div className="mt-[8%] flex items-center gap-3">
            <span aria-hidden className="h-6 w-9 rounded-md border border-line-strong bg-canvas" />
            <Wifi aria-hidden className="size-4 rotate-90 text-faint" />
          </div>
          <p className="mt-[6%] font-mono text-base tracking-[0.12em] whitespace-nowrap text-ink sm:text-lg">{number ? masked : '•••• •••• •••• ••••'}</p>
          <div className="absolute inset-x-5 bottom-5 flex justify-between text-[11px]">
            <span className="text-faint uppercase">Test card</span>
            <span className="font-mono text-ink">{expiry || 'MM/YY'}</span>
          </div>
        </div>
        {/* back */}
        <div className="absolute inset-0 overflow-hidden rounded-2xl border border-line-strong bg-raised [backface-visibility:hidden] [transform:rotateY(180deg)]">
          <div className="mt-6 h-10 bg-canvas" />
          <div className="mx-5 mt-5 flex items-center justify-end gap-3">
            <div className="h-8 flex-1 rounded bg-white/[0.06]" />
            <div className="grid h-8 w-14 place-items-center rounded border border-line-strong font-mono text-sm text-ink">{cvc || '•••'}</div>
          </div>
          <p className="mx-5 mt-3 text-right text-[11px] text-faint">Security code</p>
        </div>
      </motion.div>
    </div>
  )
}

function Receipt({ session, result }) {
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="text-center">
      <motion.span
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ type: 'spring', stiffness: 260, damping: 16, delay: 0.1 }}
        className="mx-auto grid size-16 place-items-center rounded-full border border-brand/40 bg-brand/15 text-brand"
      >
        <Check className="size-8" strokeWidth={2.5} />
      </motion.span>
      <h2 className="mt-5 text-2xl font-semibold tracking-tight text-ink">Payment successful</h2>
      <p className="mt-1 text-muted">{formatPaise(session.amount)} paid to {session.merchantName}</p>
      <dl className="mx-auto mt-6 max-w-xs space-y-2 rounded-xl border border-line bg-canvas p-4 text-left text-sm">
        <div className="flex justify-between"><dt className="text-muted">For</dt><dd className="text-ink">{session.description}</dd></div>
        {result?.cardLast4 && <div className="flex justify-between"><dt className="text-muted">Card</dt><dd className="font-mono text-ink">•••• {result.cardLast4}</dd></div>}
        {result?.paymentId && <div className="flex justify-between"><dt className="text-muted">Payment</dt><dd className="font-mono text-xs text-ink">{shortId(result.paymentId)}</dd></div>}
      </dl>
      <Button to="/" variant="secondary" className="mt-6"><ArrowLeft className="size-4" /> Back to PayCore</Button>
    </motion.div>
  )
}

export default function Checkout() {
  const { id } = useParams()
  const { signedIn } = useAuth()
  const { data: session, error, reload } = useApi(() => api.getCheckout(id), [id])
  const [number, setNumber] = useState('')
  const [expiry, setExpiry] = useState('')
  const [cvc, setCvc] = useState('')
  const [cvcFocused, setCvcFocused] = useState(false)
  const [busy, setBusy] = useState(false)
  const [decline, setDecline] = useState(null)
  const [result, setResult] = useState(null)
  const [approving, setApproving] = useState(null)
  const [attemptKey, setAttemptKey] = useState(newIdempotencyKey)

  const digits = digitsOnly(number)
  const numberOk = digits.length === 16 && luhnValid(digits)
  const exp = parseExpiry(expiry)
  const valid = numberOk && exp && /^\d{3}$/.test(cvc)

  const useMyCard = async () => {
    try {
      const cards = await api.listCards()
      if (!cards.length) return
      const card = await api.revealCard(cards[0].id)
      setNumber(formatCardNumber(card.number))
      setExpiry(`${String(card.expMonth).padStart(2, '0')}/${String(card.expYear).slice(-2)}`)
      setCvc(card.cvc)
    } catch {
      // ignore: the user can still type
    }
  }

  const pay = async (e) => {
    e.preventDefault()
    if (!valid) return
    setBusy(true)
    setDecline(null)
    try {
      const res = await api.payCheckout(id, { number: digits, expMonth: exp.month, expYear: exp.year, cvc }, attemptKey)
      if (res.outcome === 'REQUIRES_ACTION') {
        setApproving({ id: res.paymentId, amount: session.amount, merchantName: session.merchantName, last4: res.cardLast4 })
      } else {
        setResult(res)
      }
    } catch (err) {
      setDecline({ message: err.message, code: err.problem?.declineCode })
      setAttemptKey(newIdempotencyKey()) // a new attempt after a decline
      if (err.status === 409) reload()
    } finally {
      setBusy(false)
    }
  }

  // The dialog's code goes to the checkout endpoint; a definitive "not approved" comes back as a decline.
  const submitCode = async (code) => {
    try {
      const res = await api.authenticateCheckout(id, approving.id, code)
      return { ...res, status: 'CAPTURED' }
    } catch (err) {
      if (err.problem?.declineCode === 'authentication_failed') return { status: 'FAILED', failureReason: 'authentication_failed' }
      throw err
    }
  }

  if (error?.status === 404) {
    return <div className="mx-auto max-w-md px-6 py-32 text-center text-muted">This payment link doesn&apos;t exist.</div>
  }
  if (!session) return <div className="mx-auto max-w-4xl px-6 py-16"><Skeleton className="h-96 w-full rounded-3xl" /></div>

  const closed = ['PAID', 'REFUNDED', 'EXPIRED'].includes(session.status) && !result

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6 sm:py-14">
      <div className="grid overflow-hidden rounded-3xl border border-line bg-surface lg:grid-cols-[1fr_1.1fr]">
        {/* Order summary */}
        <div className="border-b border-line p-6 sm:p-8 lg:border-r lg:border-b-0">
          <div className="flex items-center justify-between">
            <p className="flex items-center gap-2 text-sm text-muted"><Store className="size-4 text-violet" /> {session.merchantName}</p>
            <Badge tone="hold">Test mode</Badge>
          </div>
          <p className="mt-8 text-sm text-muted">{session.description}</p>
          <p className="mt-1 text-5xl font-semibold tracking-tight text-ink tabular">{formatPaise(session.amount)}</p>

          <div className="mt-10 rounded-2xl border border-line bg-canvas p-4 text-sm">
            <p className="flex items-center gap-2 font-medium text-ink"><CreditCard className="size-4 text-brand" /> Test cards</p>
            <ul className="mt-3 space-y-2 text-xs text-muted">
              <li><span className="text-ink">Your PayCore card</span>: reveal it in the Playground{signedIn ? ', or use the button on the right' : ''}.</li>
              <li><button type="button" onClick={() => { setNumber(TEST_DECLINE_CARD); setExpiry('12/30'); setCvc('123') }} className="font-mono text-ink underline decoration-line-strong underline-offset-2 hover:decoration-ink">{TEST_DECLINE_CARD}</button> is always declined.</li>
              <li>A wrong CVC or a past expiry shows the matching decline code.</li>
              <li>₹2,000 or more asks the cardholder for a one-time code, like 3-D Secure.</li>
            </ul>
          </div>
          <p className="mt-6 flex items-center gap-2 text-xs text-faint"><Lock className="size-3.5" /> Card details are verified and discarded. Only a token is ever stored.</p>
        </div>

        {/* Payment */}
        <div className="p-6 sm:p-8">
          {result ? (
            <Receipt session={session} result={result} />
          ) : closed ? (
            <div className="py-16 text-center">
              <p className="text-lg font-semibold text-ink">{session.status === 'EXPIRED' ? 'This link has expired' : 'This link has already been paid'}</p>
              <Button to="/" variant="secondary" className="mt-6"><ArrowLeft className="size-4" /> Back to PayCore</Button>
            </div>
          ) : (
            <>
              <CardPreview number={number} expiry={expiry} cvc={cvc} flipped={cvcFocused} />
              <form onSubmit={pay} className="mt-8 space-y-4">
                <Field label="Card number">
                  {(fid) => (
                    <div className="relative">
                      <TextInput
                        id={fid}
                        inputMode="numeric"
                        autoComplete="cc-number"
                        placeholder={`${PAYCORE_BIN.slice(0, 4)} ${PAYCORE_BIN.slice(4)}•• •••• ••••`}
                        value={number}
                        onChange={(e) => setNumber(formatCardNumber(e.target.value))}
                        className={cn('pr-10 font-mono tracking-wider', digits.length === 16 && !numberOk && 'border-decline/50')}
                      />
                      {numberOk && <Check className="absolute top-1/2 right-3 size-4 -translate-y-1/2 text-brand" aria-label="Valid number" />}
                    </div>
                  )}
                </Field>
                <div className="grid grid-cols-2 gap-3">
                  <Field label="Expiry">
                    {(fid) => <TextInput id={fid} inputMode="numeric" autoComplete="cc-exp" placeholder="MM/YY" value={expiry} onChange={(e) => setExpiry(formatExpiry(e.target.value))} className="font-mono" />}
                  </Field>
                  <Field label="CVC">
                    {(fid) => (
                      <TextInput
                        id={fid}
                        inputMode="numeric"
                        autoComplete="cc-csc"
                        placeholder="123"
                        value={cvc}
                        maxLength={3}
                        onFocus={() => setCvcFocused(true)}
                        onBlur={() => setCvcFocused(false)}
                        onChange={(e) => setCvc(digitsOnly(e.target.value).slice(0, 3))}
                        className="font-mono"
                      />
                    )}
                  </Field>
                </div>

                <AnimatePresence>
                  {decline && (
                    <motion.p
                      role="alert"
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: 'auto' }}
                      exit={{ opacity: 0, height: 0 }}
                      className="flex items-start gap-2 overflow-hidden rounded-lg border border-decline/25 bg-decline/[0.07] px-3 py-2 text-sm text-decline"
                    >
                      <CircleAlert className="mt-0.5 size-4 shrink-0" />
                      <span>{decline.message} {decline.code && <span className="font-mono text-xs opacity-70">({decline.code})</span>}</span>
                    </motion.p>
                  )}
                </AnimatePresence>

                <Button type="submit" variant="brand" size="lg" className="w-full" loading={busy} disabled={!valid}>
                  Pay {formatPaise(session.amount)}
                </Button>
                {signedIn && (
                  <button type="button" onClick={useMyCard} className="w-full text-center text-sm text-muted hover:text-ink">
                    Use my PayCore card
                  </button>
                )}
                <p className="flex items-center justify-center gap-1.5 text-xs text-faint"><ShieldCheck className="size-3.5 text-brand" /> Powered by PayCore · double-entry ledger</p>
              </form>
            </>
          )}
        </div>
      </div>

      <ApprovePaymentDialog
        payment={approving}
        open={!!approving}
        canReadInbox={signedIn}
        submitCode={submitCode}
        onClose={() => setApproving(null)}
        onApproved={(res) => {
          const last4 = approving?.last4
          setApproving(null)
          if (res.status === 'FAILED') {
            setDecline({ message: 'The payment was not approved.', code: 'authentication_failed' })
            setAttemptKey(newIdempotencyKey())
          } else {
            setResult({ ...res, cardLast4: last4 })
          }
          reload()
        }}
      />
    </div>
  )
}
