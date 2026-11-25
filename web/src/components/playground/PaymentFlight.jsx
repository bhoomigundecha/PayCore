import { useCallback, useEffect, useState } from 'react'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { Check, Clock, RotateCcw, Undo2, X } from 'lucide-react'
import { cn } from '../../lib/cn'
import { formatPaise } from '../../lib/money'
import { playCoin, playDeclined, playHold, playPaid } from '../../lib/sound'
import { FLIGHT_EVENT } from '../../lib/flight'

/*
 * Payment animations for the Playground.
 *
 *   launchFlight({ from, to, amount, fee, kind })  ->  Promise resolved when the card lands
 *
 * `from`/`to` are account ids (elements marked data-account-id), kind is one of
 * captured | held | declined | released | refunded. A mini card flies along an arc between
 * the two account cards; for captured/refunded payments, fee coins split off towards the
 * "PayCore earned" chip (data-flight-target="fees").
 */

const CARD_W = 116
const CARD_H = 72
const FLIGHT_S = 0.95

const KINDS = {
  captured: { label: 'Paid', icon: Check, surface: 'bg-[#34d399]', glow: '52 211 153', sound: playPaid, coins: true },
  held: { label: 'On hold', icon: Clock, surface: 'bg-[#fbbf24]', glow: '251 191 36', sound: playHold },
  declined: { label: 'Declined', icon: X, surface: 'bg-[#fb7185]', glow: '251 113 133', sound: playDeclined },
  released: { label: 'Released', icon: RotateCcw, surface: 'bg-[#fbbf24]', glow: '251 191 36', sound: playHold },
  refunded: { label: 'Refunded', icon: Undo2, surface: 'bg-[#a78bfa]', glow: '167 139 250', sound: playPaid, coins: true, coinsReverse: true },
}

const centerOf = (el) => {
  const r = el.getBoundingClientRect()
  return { x: r.left + r.width / 2, y: r.top + r.height / 2, rect: r }
}
const easeInOut = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2)

/** Points along a quadratic curve that bows upwards between a and b. */
function arc(a, b, { lift = 140, until = 1, samples = 18 } = {}) {
  const c = { x: (a.x + b.x) / 2, y: Math.min(a.y, b.y) - lift }
  const xs = []
  const ys = []
  for (let i = 0; i <= samples; i++) {
    const t = easeInOut(i / samples) * until
    xs.push((1 - t) ** 2 * a.x + 2 * (1 - t) * t * c.x + t ** 2 * b.x)
    ys.push((1 - t) ** 2 * a.y + 2 * (1 - t) * t * c.y + t ** 2 * b.y)
  }
  return { xs, ys }
}

function Ripple({ rect, glow, delay = 0 }) {
  return (
    <motion.div
      className="fixed rounded-2xl"
      style={{ left: rect.left, top: rect.top, width: rect.width, height: rect.height, boxShadow: `0 0 0 2px rgb(${glow} / 0.9)` }}
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: [0, 1, 0], scale: [0.98, 1.0, 1.04] }}
      transition={{ duration: 0.9, delay, ease: 'easeOut' }}
    />
  )
}

function MiniCard({ kind, amount, landed }) {
  const k = KINDS[kind]
  const Icon = k.icon
  return (
    <div
      className={cn('relative h-full w-full overflow-hidden rounded-[13px] p-2.5 text-[#06070a]', k.surface)}
      style={{ boxShadow: '0 16px 32px -14px rgb(0 0 0 / 0.9)' }}
    >
      <div className="relative flex items-center justify-between">
        <span className="h-3.5 w-5 rounded-[3px] border border-black/15 bg-black/15" />
        <span className="text-[8px] font-bold tracking-[0.2em] opacity-70">PAYCORE</span>
      </div>
      <p className="relative mt-1.5 text-[15px] leading-none font-bold tabular">{formatPaise(amount)}</p>
      <p className="relative mt-1.5 flex items-center gap-1 text-[9px] font-semibold tracking-wide uppercase">
        {landed ? <Icon className="size-2.5" strokeWidth={3} /> : <span className="size-1.5 animate-pulse rounded-full bg-current" />}
        {landed ? k.label : kind === 'released' || kind === 'refunded' ? 'Returning…' : 'Authorizing…'}
      </p>
    </div>
  )
}

function Coin({ from, to, delay, onLand }) {
  const path = arc(from, to, { lift: 90, samples: 12 })
  return (
    <motion.div
      className="fixed top-0 left-0 grid size-[18px] place-items-center rounded-full border border-[#c4b5fd]/60 bg-violet text-[9px] font-bold text-[#06070a]"
      style={{ marginLeft: -9, marginTop: -9 }}
      initial={{ x: from.x, y: from.y, scale: 0, opacity: 0 }}
      animate={{ x: path.xs, y: path.ys, scale: [0, 1.15, 1, 0.6], opacity: [0, 1, 1, 0] }}
      transition={{ duration: 0.75, delay, ease: 'linear', scale: { times: [0, 0.15, 0.85, 1], duration: 0.75, delay }, opacity: { times: [0, 0.1, 0.85, 1], duration: 0.75, delay } }}
      onAnimationComplete={onLand}
    >
      ₹
    </motion.div>
  )
}

function Flight({ flight, onDone }) {
  const { from, to, amount, fee, kind, resolve } = flight
  const k = KINDS[kind]
  const [geometry] = useState(() => {
    const src = document.querySelector(`[data-account-id="${from}"]`)
    const dst = document.querySelector(`[data-account-id="${to}"]`)
    const fees = document.querySelector('[data-flight-target="fees"]')
    if (!src || !dst) return null
    return { a: centerOf(src), b: centerOf(dst), fees: fees ? centerOf(fees) : null }
  })
  const [landed, setLanded] = useState(false)
  const [ripples, setRipples] = useState([])

  useEffect(() => {
    if (!geometry) {
      resolve()
      onDone(flight.id)
      return undefined
    }
    const landAt = kind === 'declined' ? 0.45 : FLIGHT_S
    const t1 = setTimeout(() => {
      setLanded(true)
      k.sound()
      const target = kind === 'declined' ? geometry.a.rect : geometry.b.rect
      setRipples((r) => [...r, { key: 'card', rect: target, glow: k.glow }])
      resolve()
    }, landAt * 1000)
    const t2 = setTimeout(() => onDone(flight.id), 2600)
    return () => {
      clearTimeout(t1)
      clearTimeout(t2)
    }
    // Runs once per flight; onDone and resolve are stable for its lifetime.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [geometry])

  if (!geometry) return null
  const { a, b, fees } = geometry
  const half = { x: -CARD_W / 2, y: -CARD_H / 2 }

  // Card motion: a full arc, or (declined) 45% of the way, a shake, and back.
  let x
  let y
  let times
  if (kind === 'declined') {
    const out = arc(a, b, { until: 0.45, samples: 8 })
    const back = { xs: [...out.xs].reverse(), ys: [...out.ys].reverse() }
    const tip = out.xs[out.xs.length - 1]
    const tipY = out.ys[out.ys.length - 1]
    x = [...out.xs, tip - 10, tip + 10, tip - 6, tip, ...back.xs.slice(1)]
    y = [...out.ys, tipY, tipY, tipY, tipY, ...back.ys.slice(1)]
    times = x.map((_, i) => i / (x.length - 1))
  } else {
    const path = arc(a, b)
    x = path.xs
    y = path.ys
    times = x.map((_, i) => i / (x.length - 1))
  }
  const duration = kind === 'declined' ? 1.5 : FLIGHT_S

  // Fee coins split off mid-flight (captured), or come back from the fee chip (refunded).
  const coinCount = fee > 0 && k.coins && fees ? Math.min(5, Math.max(3, Math.round(fee / 1000) + 2)) : 0
  const mid = { x: (a.x + b.x) / 2, y: Math.min(a.y, b.y) - 70 }

  return (
    <>
      {/* outline trail */}
      {[3, 2, 1].map((n) => (
        <motion.div
          key={n}
          className="fixed top-0 left-0 rounded-[13px] border"
          style={{ width: CARD_W, height: CARD_H, marginLeft: half.x, marginTop: half.y, borderColor: `rgb(${k.glow} / ${0.14 * n})` }}
          initial={{ x: x[0], y: y[0], opacity: 0 }}
          animate={{ x, y, opacity: [0, 1, 1, 0] }}
          transition={{ duration, times, ease: 'linear', delay: n * 0.035, opacity: { duration, times: [0, 0.1, 0.85, 1], delay: n * 0.035 } }}
        />
      ))}

      <motion.div
        className="fixed top-0 left-0 [perspective:600px]"
        style={{ width: CARD_W, height: CARD_H, marginLeft: half.x, marginTop: half.y }}
        initial={{ x: x[0], y: y[0], scale: 0.4, rotate: -14, opacity: 0 }}
        animate={{
          x, y,
          scale: kind === 'declined' ? [0.4, 1, 1, 0.5] : [0.4, 1.08, 1, 0.55],
          rotate: kind === 'declined' ? [-14, 0, 0, -6] : [-14, 4, 0, 0],
          opacity: [0, 1, 1, 0],
        }}
        transition={{
          duration, times, ease: 'linear',
          scale: { duration: duration + 0.35, times: [0, 0.25, 0.75, 1] },
          rotate: { duration, times: [0, 0.45, 0.9, 1] },
          opacity: { duration: duration + 0.35, times: [0, 0.08, 0.78, 1] },
        }}
      >
        <MiniCard kind={kind} amount={amount} landed={landed} />
      </motion.div>

      {Array.from({ length: coinCount }).map((_, i) => (
        <Coin
          key={i}
          from={k.coinsReverse ? fees : mid}
          to={k.coinsReverse ? b : fees}
          delay={(k.coinsReverse ? 0.1 : FLIGHT_S * 0.45) + i * 0.07}
          onLand={() => {
            playCoin()
            if (i === coinCount - 1) setRipples((r) => [...r, { key: 'fees', rect: (k.coinsReverse ? b : fees).rect, glow: '167 139 250' }])
          }}
        />
      ))}

      {ripples.map((r) => <Ripple key={r.key} rect={r.rect} glow={r.glow} />)}
    </>
  )
}

/** Mount once on the page. Listens for launchFlight() calls and renders them above everything. */
export function PaymentFlightLayer() {
  const reduce = useReducedMotion()
  const [flights, setFlights] = useState([])
  const remove = useCallback((id) => setFlights((all) => all.filter((x) => x.id !== id)), [])

  useEffect(() => {
    const onFlight = (e) => {
      if (reduce) {
        e.detail.resolve()
        return
      }
      setFlights((f) => [...f, e.detail])
    }
    window.addEventListener(FLIGHT_EVENT, onFlight)
    return () => window.removeEventListener(FLIGHT_EVENT, onFlight)
  }, [reduce])

  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 z-[60]">
      <AnimatePresence>
        {flights.map((f) => (
          <Flight key={f.id} flight={f} onDone={remove} />
        ))}
      </AnimatePresence>
    </div>
  )
}
