import { LoaderCircle } from 'lucide-react'
import { Link } from 'react-router'
import { cn } from '../../lib/cn'

const VARIANTS = {
  primary: 'bg-ink text-canvas hover:bg-white',
  secondary: 'border border-line bg-surface text-ink hover:border-line-strong hover:bg-raised',
  ghost: 'text-muted hover:text-ink hover:bg-white/[0.05]',
  brand: 'bg-brand text-canvas hover:bg-[#6ee7b7]',
  danger: 'bg-decline/10 text-decline border border-decline/25 hover:bg-decline/20',
}

const SIZES = {
  sm: 'h-8 px-3 text-xs gap-1.5 rounded-lg',
  md: 'h-10 px-4 text-sm gap-2 rounded-xl',
  lg: 'h-12 px-6 text-[15px] gap-2 rounded-xl',
}

/** Button or link styled as one. Pass `to` for an in-app link, `href` for an external one. */
export function Button({ variant = 'primary', size = 'md', loading = false, to, href, className, children, disabled, ...props }) {
  const classes = cn(
    'inline-flex shrink-0 items-center justify-center font-medium transition-all duration-200 select-none',
    'active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50',
    VARIANTS[variant], SIZES[size], className,
  )
  const content = (
    <>
      {loading && <LoaderCircle className="size-4 animate-spin" aria-hidden />}
      {children}
    </>
  )
  if (to) return <Link to={to} className={classes} {...props}>{content}</Link>
  if (href) return <a href={href} className={classes} target="_blank" rel="noreferrer" {...props}>{content}</a>
  return (
    <button className={classes} disabled={disabled || loading} {...props}>
      {content}
    </button>
  )
}
