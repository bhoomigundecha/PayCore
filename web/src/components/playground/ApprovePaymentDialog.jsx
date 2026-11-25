import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion } from 'motion/react'
import { MessageSquare, ShieldCheck } from 'lucide-react'
import { api } from '../../api/client'
import { cn } from '../../lib/cn'
import { formatPaise } from '../../lib/money'
import { Button } from '../ui/Button'
import { Dialog } from '../ui/Dialog'

const LENGTH = 6
const RESEND_SECONDS = 30

/** Finds the code sent for this payment in the simulated phone's messages. */
function codeFromMessages(messages, payment) {
  const amount = formatPaise(payment.amount)
  const message = messages?.find((m) => m.text.includes(amount) && m.text.includes(payment.merchantName))
  const match = message?.text.match(/\b(\d{6})\b/)
  return match ? { code: match[1], message } : null
}

/** iOS-style banner sliding in from the top: the "SMS" that carries the code. */
function PhoneBanner({ message, onUse }) {
  return createPortal(
    <AnimatePresence>
      {message && (
        <motion.button
          key={message.id}
          type="button"
          onClick={onUse}
          initial={{ opacity: 0, y: -40, scale: 0.96 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -30 }}
          transition={{ type: 'spring', stiffness: 320, damping: 26 }}
          className="fixed top-4 right-4 z-[80] w-[min(360px,calc(100vw-2rem))] rounded-2xl border border-line-strong bg-raised/95 p-3.5 text-left shadow-2xl backdrop-blur-xl"
        >
          <div className="flex items-center gap-2 text-[11px] text-faint">
            <span className="grid size-5 place-items-center rounded-md bg-brand text-canvas"><MessageSquare className="size-3" /></span>
            MESSAGES · now
            <span className="ml-auto rounded border border-hold/30 px-1 text-[9px] text-hold">TEST MODE</span>
          </div>
          <p className="mt-2 text-sm leading-snug text-ink">{message.text}</p>
          <p className="mt-2 text-xs font-medium text-brand">Tap to fill the code</p>
        </motion.button>
      )}
    </AnimatePresence>,
    document.body,
  )
}

/**
 * Six-box one-time code entry for a payment that needs approval. Reads the simulated phone,
 * shows the "SMS" as a banner and offers to fill the code, like iOS one-time-code autofill.
 */
export function ApprovePaymentDialog({ payment, open, onClose, onApproved, submitCode, canReadInbox = true }) {
  const [digits, setDigits] = useState(Array(LENGTH).fill(''))
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [messages, setMessages] = useState(null)
  const [resendIn, setResendIn] = useState(RESEND_SECONDS)
  const [shake, setShake] = useState(0)
  const inputs = useRef([])

  // Poll the simulated phone while the dialog is open.
  useEffect(() => {
    if (!open) return undefined
    let cancelled = false
    const load = () => (canReadInbox ? api.notifications() : Promise.resolve([])).then((m) => !cancelled && setMessages(m)).catch(() => {})
    load()
    const poll = setInterval(load, 1500)
    const tick = setInterval(() => setResendIn((s) => Math.max(0, s - 1)), 1000)
    return () => {
      cancelled = true
      clearInterval(poll)
      clearInterval(tick)
    }
  }, [open, canReadInbox])

  const sms = useMemo(() => (payment ? codeFromMessages(messages, payment) : null), [messages, payment])

  const submit = async (code) => {
    setBusy(true)
    setError(null)
    try {
      const result = submitCode ? await submitCode(code) : await api.authenticatePayment(payment.id, code)
      if (result.status === 'FAILED') {
        setError(result.failureReason === 'authentication_expired' ? 'The code expired. This payment was cancelled.' : 'Too many wrong codes. This payment was cancelled.')
        onApproved(result)
        return
      }
      onApproved(result)
    } catch (e) {
      setError(e.message)
      setShake((n) => n + 1)
      setDigits(Array(LENGTH).fill(''))
      inputs.current[0]?.focus()
    } finally {
      setBusy(false)
    }
  }

  const setDigit = (i, value) => {
    const clean = value.replace(/\D/g, '')
    if (clean.length > 1) {
      fill(clean)
      return
    }
    const next = [...digits]
    next[i] = clean
    setDigits(next)
    if (clean && i < LENGTH - 1) inputs.current[i + 1]?.focus()
    if (next.every(Boolean)) submit(next.join(''))
  }

  const fill = (code) => {
    const next = code.slice(0, LENGTH).split('')
    while (next.length < LENGTH) next.push('')
    setDigits(next)
    if (next.every(Boolean)) submit(next.join(''))
  }

  const resend = async () => {
    try {
      await api.resendPaymentCode(payment.id)
      setResendIn(RESEND_SECONDS)
      setError(null)
    } catch (e) {
      setError(e.message)
    }
  }

  if (!payment) return null

  return (
    <>
      <Dialog open={open} onClose={onClose} title="Approve this payment" description="Payments of ₹2,000 or more need a one-time code. Nothing moves until you approve.">
        <div className="rounded-xl border border-line bg-canvas p-4 text-center">
          <p className="text-3xl font-semibold tracking-tight text-ink tabular">{formatPaise(payment.amount)}</p>
          <p className="mt-1 text-sm text-muted">to {payment.merchantName}</p>
        </div>

        <motion.div
          key={shake}
          animate={shake ? { x: [0, -8, 8, -6, 6, 0] } : {}}
          transition={{ duration: 0.35 }}
          className="mt-6 flex justify-center gap-2"
        >
          {digits.map((d, i) => (
            <input
              key={i}
              ref={(el) => { inputs.current[i] = el }}
              value={d}
              autoFocus={i === 0}
              inputMode="numeric"
              autoComplete={i === 0 ? 'one-time-code' : 'off'}
              maxLength={LENGTH}
              aria-label={`Digit ${i + 1}`}
              disabled={busy}
              onChange={(e) => setDigit(i, e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Backspace' && !d && i > 0) inputs.current[i - 1]?.focus() }}
              onPaste={(e) => { e.preventDefault(); fill(e.clipboardData.getData('text').replace(/\D/g, '')) }}
              className={cn(
                'size-12 rounded-xl border bg-canvas text-center font-mono text-xl text-ink outline-none transition-colors',
                error ? 'border-decline/50' : 'border-line focus:border-brand/70',
              )}
            />
          ))}
        </motion.div>

        {/* Autofill suggestion, like the "From Messages" chip above an iPhone keyboard. */}
        <div className="mt-4 flex h-9 justify-center">
          {sms && !busy && (
            <button type="button" onClick={() => fill(sms.code)} className="inline-flex items-center gap-2 rounded-full border border-line-strong bg-raised px-3.5 text-sm text-ink hover:border-brand/50">
              <span className="text-xs text-faint">From Messages</span>
              <span className="font-mono tracking-widest">{sms.code}</span>
            </button>
          )}
        </div>

        {error && <p role="alert" className="mt-2 text-center text-sm text-decline">{error}</p>}

        <div className="mt-5 flex items-center justify-between border-t border-line pt-4 text-xs">
          <span className="flex items-center gap-1.5 text-faint"><ShieldCheck className="size-3.5 text-brand" /> Code works only for this amount and payee</span>
          <Button size="sm" variant="ghost" onClick={resend} disabled={resendIn > 0}>
            {resendIn > 0 ? `Resend in ${resendIn}s` : 'Resend code'}
          </Button>
        </div>
      </Dialog>
      <PhoneBanner message={open ? sms?.message : null} onUse={() => sms && fill(sms.code)} />
    </>
  )
}
