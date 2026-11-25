import { cn } from '../../lib/cn'

export function Card({ className, children, ...props }) {
  return (
    <div className={cn('glass rounded-2xl', className)} {...props}>
      {children}
    </div>
  )
}

export function CardHeader({ icon: Icon, title, description, action, className }) {
  return (
    <div className={cn('flex items-start justify-between gap-4', className)}>
      <div className="flex items-start gap-3">
        {Icon && (
          <span className="grid size-9 shrink-0 place-items-center rounded-xl border border-line bg-white/[0.04] text-muted">
            <Icon className="size-4.5" aria-hidden />
          </span>
        )}
        <div>
          <h3 className="text-[15px] font-semibold tracking-tight text-ink">{title}</h3>
          {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
        </div>
      </div>
      {action}
    </div>
  )
}

/** Card whose border brightens on hover. (Kept as its own component so pages read clearly.) */
export function SpotlightCard({ className, children, ...props }) {
  return (
    <div className={cn('group glass relative overflow-hidden rounded-2xl transition-colors duration-300 hover:border-line-strong', className)} {...props}>
      {children}
    </div>
  )
}
