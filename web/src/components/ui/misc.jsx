import { useState } from 'react'
import { Check, Copy } from 'lucide-react'
import { motion } from 'motion/react'
import { cn } from '../../lib/cn'
import { shortId } from '../../lib/money'

export function GitHubIcon({ className }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
      <path d="M12 .5C5.65.5.5 5.65.5 12a11.5 11.5 0 0 0 7.86 10.92c.58.1.79-.25.79-.56v-2c-3.2.7-3.87-1.37-3.87-1.37-.53-1.33-1.28-1.69-1.28-1.69-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.68 0-1.26.45-2.28 1.19-3.08-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.17 1.18a11 11 0 0 1 5.77 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.8 1.19 1.82 1.19 3.08 0 4.41-2.7 5.38-5.26 5.67.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.5 11.5 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5Z" />
    </svg>
  )
}

/** The PayCore mark on its own (a card with a stripe), used where the wordmark would be too wide. */
export function LogoMark({ className }) {
  return (
    <svg viewBox="0 0 32 32" className={cn('size-6', className)} aria-hidden>
      <rect width="32" height="32" rx="9" fill="rgb(52 211 153 / 0.14)" />
      <rect x="7" y="9" width="18" height="14" rx="3" fill="none" stroke="#34d399" strokeWidth="2.2" />
      <path d="M7 14h18" stroke="#34d399" strokeWidth="2.2" />
      <circle cx="21" cy="19" r="1.6" fill="#7dd3fc" />
    </svg>
  )
}

export function Logo({ className, compact = false }) {
  return (
    <span className={cn('inline-flex items-center gap-2 font-semibold tracking-tight', className)}>
      <svg viewBox="0 0 32 32" className={compact ? 'size-6' : 'size-7'} aria-hidden>
        <rect width="32" height="32" rx="8" fill="rgb(52 211 153 / 0.12)" />
        <rect x="7" y="9" width="18" height="14" rx="3" fill="none" stroke="#34d399" strokeWidth="2.2" />
        <path d="M7 14h18" stroke="#34d399" strokeWidth="2.2" />
        <circle cx="21" cy="19" r="1.6" fill="#7dd3fc" />
      </svg>
      PayCore
    </span>
  )
}

export function CopyId({ id, className }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(id)
      setCopied(true)
      setTimeout(() => setCopied(false), 1200)
    } catch {
      // clipboard blocked; nothing to do
    }
  }
  return (
    <button type="button" onClick={copy} title="Copy id" className={cn('inline-flex items-center gap-1 font-mono text-[11px] text-faint transition-colors hover:text-muted', className)}>
      {shortId(id)}
      {copied ? <Check className="size-3 text-brand" /> : <Copy className="size-3" />}
    </button>
  )
}

export function SectionHeading({ eyebrow, title, description, className, align = 'center' }) {
  return (
    <div className={cn(align === 'center' ? 'mx-auto max-w-2xl text-center' : 'max-w-2xl', className)}>
      {eyebrow && <p className="text-xs font-semibold tracking-[0.2em] text-brand uppercase">{eyebrow}</p>}
      <h2 className="mt-3 text-3xl font-semibold tracking-tight text-balance text-ink sm:text-4xl">{title}</h2>
      {description && <p className="mt-4 text-base text-pretty text-muted">{description}</p>}
    </div>
  )
}

export function PageHeader({ eyebrow, title, description, actions }) {
  return (
    <div className="flex flex-col gap-6 border-b border-line pb-8 md:flex-row md:items-end md:justify-between">
      <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }}>
        {eyebrow && <p className="text-xs font-semibold tracking-[0.2em] text-brand uppercase">{eyebrow}</p>}
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">{title}</h1>
        {description && <p className="mt-3 max-w-2xl text-pretty text-muted">{description}</p>}
      </motion.div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function Skeleton({ className }) {
  return <div className={cn('animate-pulse rounded-lg bg-white/[0.06]', className)} />
}

export function EmptyState({ icon: Icon, title, description, action }) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-line px-6 py-12 text-center">
      {Icon && (
        <span className="grid size-12 place-items-center rounded-2xl border border-line bg-white/[0.03] text-muted">
          <Icon className="size-5" />
        </span>
      )}
      <h3 className="mt-4 font-semibold text-ink">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}
