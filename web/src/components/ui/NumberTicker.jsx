import { useEffect, useRef } from 'react'
import { animate, useInView, useReducedMotion } from 'motion/react'
import { cn } from '../../lib/cn'

/**
 * Animates a number from its previous value to `value` when it changes or scrolls into view.
 * `format` turns the in-between numbers into text (e.g. paise -> "₹1,234.00").
 */
export function NumberTicker({ value = 0, format = (n) => Math.round(n).toLocaleString('en-IN'), className, duration = 0.9 }) {
  const ref = useRef(null)
  const previous = useRef(0)
  const inView = useInView(ref, { once: true })
  const reduce = useReducedMotion()

  useEffect(() => {
    const node = ref.current
    if (!node || !inView) return undefined
    if (reduce) {
      node.textContent = format(value)
      previous.current = value
      return undefined
    }
    const controls = animate(previous.current, value, {
      duration,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (latest) => { node.textContent = format(latest) },
    })
    previous.current = value
    return () => controls.stop()
  }, [value, inView, reduce, duration, format])

  return <span ref={ref} className={cn('tabular', className)}>{format(0)}</span>
}
