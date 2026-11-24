import { useId } from 'react'
import { motion } from 'motion/react'
import { cn } from '../../lib/cn'

const inputBase =
  'h-11 w-full rounded-xl border border-line bg-white/[0.03] px-3.5 text-sm text-ink placeholder:text-faint ' +
  'transition-colors outline-none hover:border-line-strong focus:border-brand/60 focus:bg-white/[0.05]'

export function Field({ label, hint, children, className }) {
  const id = useId()
  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={id} className="text-xs font-medium tracking-wide text-muted uppercase">{label}</label>
      {children(id)}
      {hint && <p className="text-xs text-faint">{hint}</p>}
    </div>
  )
}

export function TextInput({ className, ...props }) {
  return <input className={cn(inputBase, className)} {...props} />
}

/** Rupee input; the parent converts to paise. */
export function MoneyInput({ className, ...props }) {
  return (
    <div className="relative">
      <span className="pointer-events-none absolute inset-y-0 left-3.5 grid place-items-center text-sm text-muted">₹</span>
      <input inputMode="decimal" className={cn(inputBase, 'pl-8 tabular', className)} {...props} />
    </div>
  )
}

export function Select({ className, children, ...props }) {
  return (
    <select className={cn(inputBase, 'appearance-none bg-[url("data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 width=%2716%27 height=%2716%27 fill=%27none%27 stroke=%27%239aa1ad%27 stroke-width=%272%27%3E%3Cpath d=%27m4 6 4 4 4-4%27/%3E%3C/svg%3E")] bg-[position:right_12px_center] bg-no-repeat pr-9', className)} {...props}>
      {children}
    </select>
  )
}

/** Pill-shaped segmented control with a sliding highlight. */
export function Segmented({ options, value, onChange, className, layoutId }) {
  return (
    <div role="radiogroup" className={cn('inline-flex rounded-xl border border-line bg-white/[0.03] p-1', className)}>
      {options.map((opt) => {
        const active = opt.value === value
        return (
          <button
            key={opt.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(opt.value)}
            className={cn('relative flex-1 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors', active ? 'text-ink' : 'text-muted hover:text-ink')}
          >
            {active && (
              <motion.span layoutId={layoutId} className="absolute inset-0 rounded-lg border border-line-strong bg-white/[0.08]" transition={{ type: 'spring', stiffness: 400, damping: 32 }} />
            )}
            <span className="relative inline-flex items-center gap-1.5">{opt.icon && <opt.icon className="size-3.5" />}{opt.label}</span>
          </button>
        )
      })}
    </div>
  )
}
