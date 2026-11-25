import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Check, Copy, CreditCard, Eye, EyeOff, Wifi } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { useApi } from '../../hooks/useApi'
import { useAuth } from '../../hooks/useAuth'
import { Badge } from '../ui/Badge'
import { LogoMark, Skeleton } from '../ui/misc'
import { TiltCard } from '../ui/TiltCard'
import { Tile } from './Tile'

const REVEAL_SECONDS = 30
const group = (n) => n.replace(/(.{4})/g, '$1 ').trim()

/**
 * The user's virtual card. Shows only the last 4 digits; "Reveal" fetches the full number and CVC
 * from the server for 30 seconds (they are never kept in the page longer than that).
 */
export function CardVisual() {
  const { user } = useAuth()
  const { data: cards } = useApi(() => api.listCards(), [user?.id])
  const card = cards?.[0]
  const [revealed, setRevealed] = useState(null)
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!revealed) return undefined
    const timer = setTimeout(() => setRevealed(null), REVEAL_SECONDS * 1000)
    return () => clearTimeout(timer)
  }, [revealed])

  if (!cards) return <Skeleton className="h-full min-h-72 w-full rounded-2xl" />
  if (!card) return null

  const toggle = async () => {
    if (revealed) {
      setRevealed(null)
      return
    }
    setBusy(true)
    try {
      setRevealed(await api.revealCard(card.id))
    } catch (e) {
      toast.error(e.message)
    } finally {
      setBusy(false)
    }
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(revealed.number)
      setCopied(true)
      setTimeout(() => setCopied(false), 1200)
    } catch {
      // clipboard blocked
    }
  }

  const expiry = `${String(card.expMonth).padStart(2, '0')}/${String(card.expYear).slice(-2)}`

  const controls = (
    <>
      <span className="text-xs text-faint">{revealed ? `Hides in ${REVEAL_SECONDS}s` : 'Last 4 digits only'}</span>
      <div className="flex gap-2 text-xs">
        {revealed && (
          <button type="button" onClick={copy} className="inline-flex items-center gap-1 rounded-lg border border-line bg-canvas px-2.5 py-1.5 text-muted hover:border-line-strong hover:text-ink">
            {copied ? <Check className="size-3.5 text-brand" /> : <Copy className="size-3.5" />} Copy
          </button>
        )}
        <button
          type="button"
          onClick={toggle}
          disabled={busy}
          className="inline-flex items-center gap-1 rounded-lg border border-line bg-canvas px-2.5 py-1.5 text-muted hover:border-line-strong hover:text-ink disabled:opacity-50"
        >
          {revealed ? <EyeOff className="size-3.5" /> : <Eye className="size-3.5" />} {revealed ? 'Hide' : 'Reveal'}
        </button>
      </div>
    </>
  )

  return (
    <Tile icon={CreditCard} accent="text-brand" label="Your card" meta={<Badge tone="hold">Test mode</Badge>} footer={controls}>
      <TiltCard maxTilt={6} outerClassName="my-auto mx-auto w-full max-w-sm">
        <div className="relative aspect-[1.586] w-full overflow-hidden rounded-2xl border border-line-strong bg-raised p-4 sm:p-5">
          <span aria-hidden className="absolute inset-y-0 left-0 w-1 bg-brand" />
          <div className="flex items-center gap-2 text-sm font-semibold tracking-tight text-ink">
            <LogoMark className="size-6" /> PayCore
          </div>

          <div className="mt-[8%] flex items-center gap-3">
            <span aria-hidden className="h-6 w-9 rounded-md border border-line-strong bg-canvas" />
            <Wifi aria-hidden className="size-4 rotate-90 text-faint" />
          </div>

          <AnimatePresence mode="wait" initial={false}>
            <motion.p
              key={revealed ? 'full' : 'masked'}
              initial={{ opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
              transition={{ duration: 0.15 }}
              className="mt-[6%] font-mono text-base tracking-[0.12em] whitespace-nowrap text-ink sm:text-lg"
            >
              {revealed ? group(revealed.number) : `•••• •••• •••• ${card.last4}`}
            </motion.p>
          </AnimatePresence>

          <div className="absolute inset-x-4 bottom-4 flex items-end justify-between gap-4 text-[11px] sm:inset-x-5 sm:bottom-5">
            <div className="min-w-0">
              <p className="text-faint uppercase">Card holder</p>
              <p className="mt-0.5 truncate font-medium text-ink uppercase">{card.holderName}</p>
            </div>
            <div className="flex gap-4 text-right">
              <div>
                <p className="text-faint uppercase">Expires</p>
                <p className="mt-0.5 font-mono text-ink">{expiry}</p>
              </div>
              <div>
                <p className="text-faint uppercase">CVC</p>
                <p className="mt-0.5 font-mono text-ink">{revealed ? revealed.cvc : '•••'}</p>
              </div>
            </div>
          </div>
        </div>
      </TiltCard>
    </Tile>
  )
}
