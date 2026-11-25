import { useCallback, useEffect, useState } from 'react'
import { Check, ChevronRight, Copy, Inbox, KeyRound, RotateCw, Send, Server, Trash2, Webhook, Zap } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../../api/client'
import { cn } from '../../lib/cn'
import { Badge } from '../ui/Badge'
import { Button } from '../ui/Button'
import { Dialog } from '../ui/Dialog'
import { Field, Segmented, TextInput } from '../ui/Form'

const PIPELINE = ['Payment', 'Outbox', 'Kafka', 'Delivery', 'Server']

const DELIVERY_TONE = { DELIVERED: 'brand', PENDING: 'hold', FAILED: 'decline' }

function ago(iso) {
  if (!iso) return ''
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 0) return `in ${Math.max(1, -s)}s`
  if (s < 60) return `${s}s ago`
  if (s < 3600) return `${Math.floor(s / 60)}m ago`
  return `${Math.floor(s / 3600)}h ago`
}

/** Webhooks for one store: add an endpoint, send test events, watch deliveries and retries live. */
export function WebhooksDialog({ store, open, onClose }) {
  const [endpoints, setEndpoints] = useState(null)
  const [selected, setSelected] = useState(null)
  const [adding, setAdding] = useState(false)
  const [secret, setSecret] = useState(null)

  const load = useCallback(async () => {
    try {
      const all = await api.listWebhooks()
      const mine = all.filter((e) => e.accountId === store.id)
      setEndpoints(mine)
      setSelected((cur) => (mine.some((e) => e.id === cur) ? cur : mine[0]?.id ?? null))
    } catch (e) {
      toast.error(e.message)
    }
  }, [store.id])

  useEffect(() => {
    if (open) load()
  }, [open, load])

  const close = () => {
    setAdding(false)
    setSecret(null)
    onClose()
  }

  const endpoint = endpoints?.find((e) => e.id === selected)
  const showForm = adding || (endpoints && endpoints.length === 0)

  return (
    <Dialog open={open} onClose={close} wide title="Webhooks" description={`PayCore tells ${store.name}'s server about every payment, signed and retried.`}>
      <Pipeline />
      {secret ? (
        <SecretOnce secret={secret} onDone={() => setSecret(null)} />
      ) : showForm ? (
        <AddEndpoint
          store={store}
          canCancel={endpoints?.length > 0}
          onCancel={() => setAdding(false)}
          onCreated={async (created) => {
            setAdding(false)
            setSecret(created.secret)
            setSelected(created.endpoint.id)
            await load()
          }}
        />
      ) : endpoints === null ? (
        <p className="py-10 text-center text-sm text-muted">Loading…</p>
      ) : (
        <>
          <div className="mt-5 flex flex-wrap items-center gap-2">
            {endpoints.map((e) => (
              <button
                key={e.id}
                type="button"
                onClick={() => setSelected(e.id)}
                className={cn(
                  'inline-flex max-w-full items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs transition-colors',
                  e.id === selected ? 'border-line-strong bg-white/[0.08] text-ink' : 'border-line text-muted hover:text-ink',
                )}
              >
                {e.testReceiver ? <Inbox className="size-3.5 shrink-0" /> : <Server className="size-3.5 shrink-0" />}
                <span className="truncate">{e.testReceiver ? 'Test receiver' : e.url.replace(/^https?:\/\//, '')}</span>
              </button>
            ))}
            {endpoints.length < 3 && (
              <button type="button" onClick={() => setAdding(true)} className="rounded-lg border border-dashed border-line px-2.5 py-1.5 text-xs text-muted hover:text-ink">
                + Endpoint
              </button>
            )}
          </div>
          {endpoint && <EndpointPanel key={endpoint.id} endpoint={endpoint} onChanged={load} />}
        </>
      )}
    </Dialog>
  )
}

function Pipeline() {
  return (
    <div className="flex items-center justify-between gap-1 overflow-x-auto rounded-xl border border-line bg-canvas px-3 py-2.5">
      {PIPELINE.map((step, i) => (
        <div key={step} className="flex shrink-0 items-center gap-1">
          <span className={cn('text-xs font-medium', i === 2 ? 'text-brand' : 'text-ink')}>{step}</span>
          {i < PIPELINE.length - 1 && <ChevronRight className="size-3.5 text-faint" />}
        </div>
      ))}
    </div>
  )
}

function AddEndpoint({ store, canCancel, onCancel, onCreated }) {
  const [kind, setKind] = useState('test')
  const [url, setUrl] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      onCreated(await api.createWebhook(store.id, kind === 'test' ? { testReceiver: true } : { url: url.trim() }))
    } catch (err) {
      toast.error(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="mt-5 space-y-4">
      <Segmented
        layoutId="webhook-kind"
        className="w-full"
        value={kind}
        onChange={setKind}
        options={[
          { value: 'test', label: 'Test receiver', icon: Inbox },
          { value: 'url', label: 'My server', icon: Server },
        ]}
      />
      {kind === 'url' ? (
        <Field label="Endpoint URL" hint="Public https only. Private and internal addresses are refused.">
          {(id) => <TextInput id={id} autoFocus value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com/webhooks/paycore" />}
        </Field>
      ) : (
        <p className="rounded-xl border border-line bg-canvas p-3 text-sm text-muted">
          A receiver built into PayCore. It checks every signature and shows what arrived.
        </p>
      )}
      <div className="flex gap-2">
        {canCancel && <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>}
        <Button type="submit" variant="brand" className="flex-1" loading={busy} disabled={kind === 'url' && !url.trim()}>
          <Webhook className="size-4" /> Add endpoint
        </Button>
      </div>
    </form>
  )
}

function SecretOnce({ secret, onDone }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(secret)
      setCopied(true)
      setTimeout(() => setCopied(false), 1200)
    } catch {
      // clipboard blocked
    }
  }
  return (
    <div className="mt-5 space-y-4">
      <div className="rounded-xl border border-hold/25 bg-hold/[0.06] p-4">
        <p className="flex items-center gap-2 text-sm font-medium text-hold"><KeyRound className="size-4" /> Signing secret, shown once</p>
        <div className="mt-3 flex items-center gap-2 rounded-lg border border-line bg-canvas p-2 pl-3">
          <span className="min-w-0 flex-1 truncate font-mono text-xs text-ink">{secret}</span>
          <Button size="sm" variant="secondary" onClick={copy}>
            {copied ? <Check className="size-3.5 text-brand" /> : <Copy className="size-3.5" />} Copy
          </Button>
        </div>
        <p className="mt-3 font-mono text-[11px] leading-relaxed text-muted">
          PayCore-Signature: t=…,v1=HMAC_SHA256(secret, t + "." + body)
        </p>
      </div>
      <Button variant="brand" className="w-full" onClick={onDone}>Done</Button>
    </div>
  )
}

function EndpointPanel({ endpoint, onChanged }) {
  const [tab, setTab] = useState('deliveries')
  const [deliveries, setDeliveries] = useState([])
  const [received, setReceived] = useState([])
  const [failing, setFailing] = useState(endpoint.failing)
  const [sending, setSending] = useState(false)
  const [, tick] = useState(0)

  const refresh = useCallback(async () => {
    try {
      const [d, r] = await Promise.all([
        api.webhookDeliveries(endpoint.id),
        endpoint.testReceiver ? api.webhookReceived(endpoint.id) : Promise.resolve([]),
      ])
      setDeliveries(d)
      setReceived(r)
    } catch {
      // keep the last view; the next poll tries again
    }
  }, [endpoint.id, endpoint.testReceiver])

  useEffect(() => {
    refresh()
    const id = setInterval(() => { refresh(); tick((n) => n + 1) }, 1500)
    return () => clearInterval(id)
  }, [refresh])

  const sendTest = async () => {
    setSending(true)
    try {
      await api.sendTestWebhook(endpoint.id)
      toast.success('Test event written to the outbox')
      setTab('deliveries')
      setTimeout(refresh, 700)
    } catch (e) {
      toast.error(e.message)
    } finally {
      setSending(false)
    }
  }

  const toggleOutage = async () => {
    try {
      await api.setReceiverFailing(endpoint.id, !failing)
      setFailing(!failing)
    } catch (e) {
      toast.error(e.message)
    }
  }

  const remove = async () => {
    try {
      await api.deleteWebhook(endpoint.id)
      toast.success('Endpoint removed')
      onChanged()
    } catch (e) {
      toast.error(e.message)
    }
  }

  const retry = async (id) => {
    try {
      await api.retryDelivery(id)
      setTimeout(refresh, 400)
    } catch (e) {
      toast.error(e.message)
    }
  }

  return (
    <div className="mt-4 space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" variant="brand" onClick={sendTest} loading={sending}>
          <Send className="size-3.5" /> Send test event
        </Button>
        {endpoint.testReceiver && (
          <button
            type="button"
            role="switch"
            aria-checked={failing}
            onClick={toggleOutage}
            className={cn(
              'inline-flex h-8 items-center gap-2 rounded-lg border px-3 text-xs transition-colors',
              failing ? 'border-decline/30 bg-decline/10 text-decline' : 'border-line text-muted hover:text-ink',
            )}
          >
            <Zap className="size-3.5" /> {failing ? 'Outage on (503)' : 'Simulate outage'}
          </button>
        )}
        <button type="button" onClick={remove} aria-label="Remove endpoint" title="Remove endpoint" className="ml-auto grid size-8 place-items-center rounded-lg text-muted hover:bg-white/[0.06] hover:text-decline">
          <Trash2 className="size-4" />
        </button>
      </div>

      {endpoint.testReceiver ? (
        <Segmented
          layoutId="webhook-tab"
          className="w-full"
          value={tab}
          onChange={setTab}
          options={[
            { value: 'deliveries', label: `Sent (${deliveries.length})`, icon: Send },
            { value: 'received', label: `Received (${received.length})`, icon: Inbox },
          ]}
        />
      ) : (
        <p className="truncate font-mono text-xs text-muted">{endpoint.url}</p>
      )}

      <div className="max-h-[42vh] space-y-2 overflow-y-auto pr-1">
        {tab === 'deliveries' || !endpoint.testReceiver ? (
          deliveries.length === 0 ? (
            <Empty text="No deliveries yet. Send a test event, or make a payment to this store." />
          ) : (
            deliveries.map((d) => <DeliveryRow key={d.id} d={d} onRetry={() => retry(d.id)} />)
          )
        ) : received.length === 0 ? (
          <Empty text="Nothing received yet." />
        ) : (
          received.map((r, i) => <ReceivedRow key={`${r.eventId}-${r.at}-${i}`} r={r} />)
        )}
      </div>
    </div>
  )
}

function Empty({ text }) {
  return <p className="rounded-xl border border-dashed border-line py-8 text-center text-sm text-muted">{text}</p>
}

function DeliveryRow({ d, onRetry }) {
  const [openBody, setOpenBody] = useState(false)
  return (
    <div className="rounded-xl border border-line bg-canvas">
      <button type="button" onClick={() => setOpenBody((v) => !v)} className="flex w-full items-center gap-3 px-3 py-2.5 text-left">
        <span className="min-w-0 flex-1 truncate font-mono text-xs text-ink">{d.eventType}</span>
        <span className="hidden text-xs text-faint sm:inline">
          {d.attempts > 0 && `try ${d.attempts}`}
          {d.lastStatusCode ? ` · ${d.lastStatusCode}` : d.lastError ? ' · no answer' : ''}
        </span>
        <Badge tone={DELIVERY_TONE[d.status]} dot={d.status === 'PENDING'}>
          {d.status === 'PENDING' && d.attempts > 0 ? `Retry ${ago(d.nextAttemptAt)}` : d.status.toLowerCase()}
        </Badge>
      </button>
      {openBody && (
        <div className="border-t border-line px-3 py-2.5">
          {d.lastError && d.status !== 'DELIVERED' && <p className="mb-2 text-xs text-decline">{d.lastError}</p>}
          <pre className="max-h-48 overflow-auto font-mono text-[11px] leading-relaxed text-muted">{pretty(d.payload)}</pre>
          <div className="mt-2 flex items-center justify-between text-xs text-faint">
            <span>Event {d.eventId.slice(0, 8)} · {ago(d.createdAt)}</span>
            {d.status !== 'DELIVERED' && (
              <Button size="sm" variant="secondary" onClick={onRetry}><RotateCw className="size-3.5" /> Retry now</Button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function ReceivedRow({ r }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-line bg-canvas px-3 py-2.5">
      <span className="min-w-0 flex-1 truncate font-mono text-xs text-ink">{r.eventType}</span>
      <span className="hidden text-xs text-faint sm:inline">try {r.attempt} · {ago(r.at)}</span>
      {r.duplicate && <Badge tone="info">duplicate</Badge>}
      <Badge tone={r.signatureValid ? 'brand' : 'decline'}>{r.signatureValid ? 'signed ✓' : 'bad signature'}</Badge>
      <Badge tone={r.responseStatus === 200 ? 'neutral' : 'decline'}>{r.responseStatus}</Badge>
    </div>
  )
}

function pretty(json) {
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}
