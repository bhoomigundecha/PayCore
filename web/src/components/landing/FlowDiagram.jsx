import { useEffect, useRef, useState } from 'react'
import { motion, useReducedMotion } from 'motion/react'
import { cn } from '../../lib/cn'

/*
 * One payment travelling through PayCore, drawn as SVG. A requestAnimationFrame loop moves a
 * glowing packet along each path in turn and highlights the matching step underneath,
 * so the picture and the explanation stay in sync.
 */

const NODES = {
  client: { x: 30, y: 150, w: 150, h: 64, title: 'Client', sub: 'Idempotency-Key' },
  api: { x: 335, y: 150, w: 170, h: 64, title: 'PayCore API', sub: 'Spring Boot · Java 21' },
  pg: { x: 660, y: 40, w: 200, h: 64, title: 'PostgreSQL', sub: 'what a payment IS' },
  tb: { x: 660, y: 260, w: 200, h: 64, title: 'TigerBeetle', sub: 'where the money IS' },
}

const mid = (n, side) => {
  if (side === 'r') return [n.x + n.w, n.y + n.h / 2]
  if (side === 'l') return [n.x, n.y + n.h / 2]
  return [n.x + n.w / 2, n.y + n.h / 2]
}
const curve = ([x1, y1], [x2, y2]) => `M${x1},${y1} C${(x1 + x2) / 2},${y1} ${(x1 + x2) / 2},${y2} ${x2},${y2}`

const PATHS = {
  request: curve(mid(NODES.client, 'r'), mid(NODES.api, 'l')),
  toPg: curve([NODES.api.x + NODES.api.w, NODES.api.y + 22], mid(NODES.pg, 'l')),
  toTb: curve([NODES.api.x + NODES.api.w, NODES.api.y + 42], mid(NODES.tb, 'l')),
  response: curve(mid(NODES.api, 'l'), mid(NODES.client, 'r')),
}

// Copy is kept to the same length so the five cards stay the same size.
const STEPS = [
  { path: 'request', color: '#7dd3fc', label: 'Request', detail: 'POST /payments with an Idempotency-Key, so a retry is always safe.' },
  { path: 'toPg', color: '#a78bfa', label: 'Record intent', detail: 'Postgres saves the payment as CAPTURING with its transfer ids.' },
  { path: 'toTb', color: '#34d399', label: 'Move money', detail: 'TigerBeetle posts the merchant and fee legs as one linked batch.' },
  { path: 'toPg', color: '#a78bfa', label: 'Record outcome', detail: 'Postgres marks it CAPTURED. A crash here is finished by recovery.' },
  { path: 'response', color: '#7dd3fc', label: 'Respond', detail: '201 Created. The customer is charged exactly once, whatever broke.' },
]
const STEP_MS = 1500
const PAUSE_MS = 900

export function FlowDiagram() {
  const reduce = useReducedMotion()
  const [active, setActive] = useState(0)
  const pathRefs = useRef({})
  const dotRef = useRef(null)
  const glowRef = useRef(null)

  useEffect(() => {
    if (reduce) return undefined
    let frame
    const start = performance.now()
    const cycle = STEPS.length * STEP_MS + PAUSE_MS
    let lastStep = -1
    const tick = (now) => {
      const t = (now - start) % cycle
      const step = Math.min(Math.floor(t / STEP_MS), STEPS.length - 1)
      const inPause = t >= STEPS.length * STEP_MS
      if (step !== lastStep) {
        lastStep = step
        setActive(step)
      }
      const path = pathRefs.current[STEPS[step].path]
      const dot = dotRef.current
      if (path && dot) {
        const progress = inPause ? 1 : Math.min((t - step * STEP_MS) / (STEP_MS * 0.8), 1)
        const eased = 1 - Math.pow(1 - progress, 3)
        const point = path.getPointAtLength(eased * path.getTotalLength())
        dot.setAttribute('cx', point.x)
        dot.setAttribute('cy', point.y)
        dot.setAttribute('fill', STEPS[step].color)
        glowRef.current.setAttribute('cx', point.x)
        glowRef.current.setAttribute('cy', point.y)
        glowRef.current.setAttribute('fill', STEPS[step].color)
        dot.style.opacity = inPause ? 0 : 1
        glowRef.current.style.opacity = inPause ? 0 : 0.5
      }
      frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [reduce])

  const activePath = STEPS[active].path

  return (
    <div className="glass overflow-hidden rounded-3xl p-4 sm:p-8">
      <svg viewBox="0 0 890 360" className="w-full" role="img" aria-label="A payment flows from the client to the PayCore API, which records intent in PostgreSQL, moves money in TigerBeetle, then records the outcome.">
        <defs>
          <filter id="glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="6" />
          </filter>
          <linearGradient id="node" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="rgb(255 255 255 / 0.07)" />
            <stop offset="1" stopColor="rgb(255 255 255 / 0.02)" />
          </linearGradient>
        </defs>

        {Object.entries(PATHS).filter(([k]) => k !== 'response').map(([key, d]) => (
          <path key={key} d={d} fill="none" stroke={activePath === key || (key === 'request' && activePath === 'response') ? 'rgb(255 255 255 / 0.28)' : 'rgb(255 255 255 / 0.1)'} strokeWidth="1.5" strokeDasharray="4 6" style={{ transition: 'stroke 300ms' }} />
        ))}
        {Object.entries(PATHS).map(([key, d]) => (
          <path key={`m-${key}`} ref={(el) => { pathRefs.current[key] = el }} d={d} fill="none" stroke="none" />
        ))}

        {/* Recovery job loop under the API */}
        <g opacity="0.75">
          <path d="M420 214 C420 300 560 330 660 300" fill="none" stroke="rgb(251 191 36 / 0.35)" strokeWidth="1.2" strokeDasharray="2 5" />
          <text x="470" y="345" textAnchor="middle" className="fill-[#fbbf24] text-[12px]" fontFamily="var(--font-mono)">recovery job · every 10s</text>
        </g>

        {Object.entries(NODES).map(([key, n]) => {
          const lit = (key === 'api') || (key === 'client' && ['request', 'response'].includes(activePath))
            || (key === 'pg' && activePath === 'toPg') || (key === 'tb' && activePath === 'toTb')
          return (
            <g key={key}>
              <rect x={n.x} y={n.y} width={n.w} height={n.h} rx="14" fill="url(#node)" stroke={lit ? 'rgb(255 255 255 / 0.35)' : 'rgb(255 255 255 / 0.12)'} style={{ transition: 'stroke 300ms' }} />
              <text x={n.x + n.w / 2} y={n.y + 28} textAnchor="middle" className="fill-[#f4f5f7] text-[15px] font-semibold">{n.title}</text>
              <text x={n.x + n.w / 2} y={n.y + 47} textAnchor="middle" className="fill-[#9aa1ad] text-[12px]">{n.sub}</text>
            </g>
          )
        })}

        <circle ref={glowRef} r="14" filter="url(#glow)" opacity="0" />
        <circle ref={dotRef} r="5" opacity="0" />
      </svg>

      <ol className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
        {STEPS.map((s, i) => {
          const isActive = i === active && !reduce
          return (
            <li
              key={s.label}
              className={cn(
                'relative flex h-full flex-col overflow-hidden rounded-2xl border p-4 transition-colors duration-300',
                isActive ? 'border-line-strong bg-white/[0.05]' : 'border-line bg-white/[0.015]',
              )}
            >
              <div className="flex items-center gap-2.5">
                <span
                  className="grid size-6 shrink-0 place-items-center rounded-full border font-mono text-[11px] transition-colors duration-300"
                  style={{ borderColor: isActive ? s.color : 'rgb(255 255 255 / 0.12)', color: isActive ? s.color : '#5d6472' }}
                >
                  {i + 1}
                </span>
                <p className={cn('text-sm font-medium whitespace-nowrap transition-colors duration-300', isActive ? 'text-ink' : 'text-muted')}>{s.label}</p>
              </div>
              <p className={cn('mt-3 text-[13px] leading-relaxed transition-colors duration-300', isActive ? 'text-muted' : 'text-faint')}>{s.detail}</p>
              {/* Progress bar along the bottom of the active step */}
              <span aria-hidden className="absolute inset-x-0 bottom-0 h-0.5 bg-white/[0.04]">
                {isActive && (
                  <motion.span
                    key={`bar-${active}`}
                    className="block h-full"
                    style={{ background: s.color }}
                    initial={{ width: '0%' }}
                    animate={{ width: '100%' }}
                    transition={{ duration: STEP_MS / 1000, ease: 'linear' }}
                  />
                )}
              </span>
            </li>
          )
        })}
      </ol>
    </div>
  )
}
