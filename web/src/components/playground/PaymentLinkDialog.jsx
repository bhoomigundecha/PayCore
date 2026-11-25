import { useState } from 'react'
import { Check, Copy, ExternalLink, Link2 } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { formatPaise, rupeesToPaise } from '../../lib/money'
import { Button } from '../ui/Button'
import { Dialog } from '../ui/Dialog'
import { Field, MoneyInput, TextInput } from '../ui/Form'

/** Create a payment link for one of your stores: anyone can pay it by card on the checkout page. */
export function PaymentLinkDialog({ store, open, onClose }) {
  const [rupees, setRupees] = useState('499')
  const [description, setDescription] = useState('')
  const [busy, setBusy] = useState(false)
  const [link, setLink] = useState(null)
  const [copied, setCopied] = useState(false)

  const amount = rupeesToPaise(rupees)
  const valid = Number.isInteger(amount) && amount > 0 && description.trim()
  const url = link ? `${window.location.origin}/pay/${link.id}` : ''

  const create = async (e) => {
    e.preventDefault()
    if (!valid) return
    setBusy(true)
    try {
      setLink(await api.createCheckout(store.id, amount, description.trim()))
    } catch (err) {
      toast.error(err.message)
    } finally {
      setBusy(false)
    }
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 1200)
    } catch {
      // clipboard blocked
    }
  }

  const close = () => {
    setLink(null)
    setDescription('')
    onClose()
  }

  return (
    <Dialog open={open} onClose={close} title={link ? 'Your payment link' : 'New payment link'} description={link ? `Anyone with this link can pay ${store.name} by card.` : `Ask for money to ${store.name}. Valid for 24 hours, paid once.`}>
      {link ? (
        <div className="space-y-4">
          <div className="rounded-xl border border-line bg-canvas p-4 text-center">
            <p className="text-sm text-muted">{link.description}</p>
            <p className="mt-1 text-3xl font-semibold tracking-tight text-ink tabular">{formatPaise(link.amount)}</p>
          </div>
          <div className="flex items-center gap-2 rounded-xl border border-line bg-canvas p-2 pl-3">
            <Link2 className="size-4 shrink-0 text-brand" />
            <span className="min-w-0 flex-1 truncate font-mono text-xs text-ink">{url}</span>
            <Button size="sm" variant="secondary" onClick={copy}>
              {copied ? <Check className="size-3.5 text-brand" /> : <Copy className="size-3.5" />} Copy
            </Button>
          </div>
          <Button href={url} variant="brand" className="w-full">
            Open checkout <ExternalLink className="size-4" />
          </Button>
        </div>
      ) : (
        <form onSubmit={create} className="space-y-4">
          <Field label="What is it for?">
            {(id) => <TextInput id={id} autoFocus value={description} maxLength={200} onChange={(e) => setDescription(e.target.value)} placeholder="e.g. Hoodie, size M" />}
          </Field>
          <Field label="Amount">
            {(id) => <MoneyInput id={id} value={rupees} onChange={(e) => setRupees(e.target.value)} />}
          </Field>
          <Button type="submit" variant="brand" size="lg" className="w-full" loading={busy} disabled={!valid}>
            Create link {valid ? `for ${formatPaise(amount)}` : ''}
          </Button>
        </form>
      )}
    </Dialog>
  )
}
