import { useRef } from 'react'
import { motion, useMotionTemplate, useMotionValue, useReducedMotion, useSpring, useTransform } from 'motion/react'
import { cn } from '../../lib/cn'

/** A card that tilts in 3D towards the cursor, with a glare highlight. */
export function TiltCard({ className, children, maxTilt = 8 }) {
  const ref = useRef(null)
  const reduce = useReducedMotion()
  const px = useMotionValue(0.5)
  const py = useMotionValue(0.5)
  const rotateX = useSpring(useTransform(py, [0, 1], [maxTilt, -maxTilt]), { stiffness: 200, damping: 20 })
  const rotateY = useSpring(useTransform(px, [0, 1], [-maxTilt, maxTilt]), { stiffness: 200, damping: 20 })
  const glareX = useTransform(px, [0, 1], ['0%', '100%'])
  const glareY = useTransform(py, [0, 1], ['0%', '100%'])
  const glare = useMotionTemplate`radial-gradient(400px circle at ${glareX} ${glareY}, rgb(255 255 255 / 0.12), transparent 60%)`

  const onMove = (e) => {
    if (reduce) return
    const rect = ref.current.getBoundingClientRect()
    px.set((e.clientX - rect.left) / rect.width)
    py.set((e.clientY - rect.top) / rect.height)
  }

  return (
    <div style={{ perspective: 1000 }}>
      <motion.div
        ref={ref}
        onMouseMove={onMove}
        onMouseLeave={() => { px.set(0.5); py.set(0.5) }}
        style={{ rotateX, rotateY, transformStyle: 'preserve-3d' }}
        className={cn('relative overflow-hidden rounded-2xl', className)}
      >
        <motion.div aria-hidden className="pointer-events-none absolute inset-0 z-10" style={{ background: glare }} />
        {children}
      </motion.div>
    </div>
  )
}
