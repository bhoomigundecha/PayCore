import { cn } from '../../lib/cn'

/**
 * The shared shell for the Playground's top row: same header, same padding, footer pinned to the
 * bottom. Tiles in a grid row stretch to the tallest one, so the row always lines up.
 */
export function Tile({ icon: Icon, accent = 'text-muted', label, meta, footer, children, className, ...props }) {
  return (
    <div className={cn('glass flex h-full flex-col rounded-2xl p-5', className)} {...props}>
      <div className="flex items-center justify-between gap-3">
        <p className="flex items-center gap-2.5 text-sm font-medium text-ink">
          <span className={cn('grid size-7 place-items-center rounded-lg border border-line bg-canvas', accent)}>
            <Icon className="size-3.5" aria-hidden />
          </span>
          {label}
        </p>
        {meta}
      </div>
      <div className="mt-5 flex flex-1 flex-col">{children}</div>
      {footer && <div className="mt-5 flex h-12 shrink-0 items-center justify-between gap-2 border-t border-line pt-4">{footer}</div>}
    </div>
  )
}
