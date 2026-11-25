import { useRef } from 'react'
import { motion, useMotionValue, useReducedMotion, useSpring, useTransform } from 'motion/react'
import { cn } from '../../lib/cn'

/** A card that tilts slightly in 3D towards the cursor. */
export function TiltCard({ className, outerClassName, children, maxTilt = 8 }) {
  const ref = useRef(null)
  const reduce = useReducedMotion()
  const px = useMotionValue(0.5)
  const py = useMotionValue(0.5)
  const rotateX = useSpring(useTransform(py, [0, 1], [maxTilt, -maxTilt]), { stiffness: 200, damping: 20 })
  const rotateY = useSpring(useTransform(px, [0, 1], [-maxTilt, maxTilt]), { stiffness: 200, damping: 20 })

  const onMove = (e) => {
    if (reduce) return
    const rect = ref.current.getBoundingClientRect()
    px.set((e.clientX - rect.left) / rect.width)
    py.set((e.clientY - rect.top) / rect.height)
  }

  return (
    <div style={{ perspective: 1000 }} className={outerClassName}>
      <motion.div
        ref={ref}
        onMouseMove={onMove}
        onMouseLeave={() => { px.set(0.5); py.set(0.5) }}
        style={{ rotateX, rotateY, transformStyle: 'preserve-3d' }}
        className={cn('relative overflow-hidden rounded-2xl', className)}
      >
        {children}
      </motion.div>
    </div>
  )
}
