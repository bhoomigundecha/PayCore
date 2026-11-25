import { useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowDownLeft, ArrowUpRight, BookOpen, Building2, FlaskConical, RefreshCw, Scale, Store, Wallet } from 'lucide-react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { useSandbox } from '../hooks/useSandbox'
import { cn } from '../lib/cn'
import { formatPaise, shortId } from '../lib/money'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { NumberTicker } from '../components/ui/NumberTicker'
import { CopyId, EmptyState, PageHeader, Skeleton } from '../components/ui/misc'
import { AuthGate } from '../components/auth/AuthGate'

const KIND = {
  POSTED: { label: 'Posted', tone: 'neutral' },
  PENDING: { label: 'Hold', tone: 'hold' },
  POST_PENDING: { label: 'Hold captured', tone: 'brand' },
  VOID_PENDING: { label: 'Hold released', tone: 'violet' },
}
const CODE = { TOP_UP: 'Top-up', PAYMENT: 'Payment', PLATFORM_FEE: 'Platform fee', REFUND: 'Refund' }

function Invariant() {
  const { data, loading, reload } = useApi(() => api.ledgerTotals(), [])
  return (
    <Card className="relative overflow-hidden p-6 sm:p-8">
      <div className="relative flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
        <div className="max-w-md">
          <p className="flex items-center gap-2 text-xs font-semibold tracking-[0.2em] text-brand uppercase"><Scale className="size-3.5" /> The double-entry invariant</p>
          <p className="mt-3 text-pretty text-muted">
            Every transfer adds the same amount to one account&apos;s debits and another&apos;s credits. So across the whole ledger, total debits must equal total credits. If they ever differ, money was created or destroyed.
          </p>
        </div>
        <div className="flex items-center gap-3 sm:gap-6">
          <div className="text-right">
            <p className="text-xs text-muted">Total debits</p>
            <p className="text-2xl font-semibold text-ink sm:text-3xl">{data ? <NumberTicker value={data.debitsPosted} format={formatPaise} /> : <Skeleton className="h-9 w-32" />}</p>
          </div>
          <span className={cn('grid size-10 place-items-center rounded-full border text-lg font-semibold', data?.balanced ? 'border-brand/40 bg-brand/10 text-brand' : 'border-line text-faint')}>=</span>
          <div>
            <p className="text-xs text-muted">Total credits</p>
            <p className="text-2xl font-semibold text-ink sm:text-3xl">{data ? <NumberTicker value={data.creditsPosted} format={formatPaise} /> : <Skeleton className="h-9 w-32" />}</p>
          </div>
        </div>
      </div>
      <div className="relative mt-6 flex flex-wrap items-center gap-3 border-t border-line pt-4 text-xs text-muted">
        {data && (
          <>
            <Badge tone={data.balanced ? 'brand' : 'decline'}>{data.balanced ? 'Balanced' : 'Mismatch'}</Badge>
            <span className="tabular">{data.accounts.toLocaleString('en-IN')} accounts summed</span>
            <span className="tabular">Pending: {formatPaise(data.debitsPending)} = {formatPaise(data.creditsPending)}</span>
          </>
        )}
        <Button size="sm" variant="ghost" className="ml-auto" onClick={reload} loading={loading}>
          <RefreshCw className="size-3.5" /> Recheck
        </Button>
      </div>
    </Card>
  )
}

function AccountList({ groups, selected, onSelect }) {
  return (
    <nav aria-label="Accounts" className="space-y-6">
      {groups.map((group) => group.accounts.length > 0 && (
        <div key={group.title}>
          <p className="mb-2 flex items-center gap-1.5 px-2 text-[11px] font-semibold tracking-wider text-faint uppercase">
            <group.icon className="size-3.5" /> {group.title}
          </p>
          <div className="space-y-1">
            {group.accounts.map((a) => {
              const Icon = a.type === 'MERCHANT' ? Store : a.type === 'SYSTEM' ? Building2 : Wallet
              const active = a.id === selected
              return (
                <button
                  key={a.id}
                  type="button"
                  onClick={() => onSelect(a.id)}
                  className={cn('relative flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-left text-sm transition-colors', active ? 'text-ink' : 'text-muted hover:bg-white/[0.03] hover:text-ink')}
                >
                  {active && <motion.span layoutId="explorer-active" className="absolute inset-0 rounded-xl border border-line-strong bg-white/[0.06]" transition={{ type: 'spring', stiffness: 400, damping: 34 }} />}
                  <Icon className="relative size-4 shrink-0" />
                  <span className="relative truncate">{a.name}</span>
                </button>
              )
            })}
          </div>
        </div>
      ))}
    </nav>
  )
}

function TAccount({ entries, accountId, nameOf }) {
  const debits = entries.filter((e) => e.debitAccountId === accountId)
  const credits = entries.filter((e) => e.creditAccountId === accountId)
  const posted = (list) => list.filter((e) => e.kind === 'POSTED' || e.kind === 'POST_PENDING').reduce((s, e) => s + e.amount, 0)

  const column = (title, subtitle, list, Icon, tone) => (
    <div className="min-w-0">
      <div className="flex items-baseline justify-between border-b border-line-strong pb-2">
        <p className="flex items-center gap-1.5 text-sm font-semibold text-ink"><Icon className={cn('size-4', tone)} /> {title}</p>
        <p className="text-xs text-faint">{subtitle}</p>
      </div>
      <ul className="divide-y divide-line">
        {list.length === 0 && <li className="py-3 text-xs text-faint">None</li>}
        {list.map((e) => {
          const other = e.debitAccountId === accountId ? e.creditAccountId : e.debitAccountId
          const pending = e.kind === 'PENDING' || e.kind === 'VOID_PENDING'
          return (
            <li key={e.id} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p className="truncate text-sm text-ink">{CODE[e.code] ?? e.code} <span className="text-faint">· {nameOf(other)}</span></p>
                <p className="text-[11px] text-faint">{KIND[e.kind].label} · {new Date(e.timestamp).toLocaleTimeString()}</p>
              </div>
              <span className={cn('shrink-0 text-sm tabular', pending ? 'text-hold italic' : 'text-ink')}>{formatPaise(e.amount)}</span>
            </li>
          )
        })}
      </ul>
      <div className="flex justify-between border-t border-line-strong pt-2 text-sm">
        <span className="text-muted">Posted total</span>
        <span className="font-semibold text-ink tabular">{formatPaise(posted(list))}</span>
      </div>
    </div>
  )

  return (
    <div className="grid gap-8 md:grid-cols-2 md:gap-0 md:divide-x md:divide-line-strong [&>*]:md:px-5">
      {column('Debits', 'money out', debits, ArrowUpRight, 'text-decline')}
      {column('Credits', 'money in', credits, ArrowDownLeft, 'text-brand')}
    </div>
  )
}

function AccountLedger({ accountId, nameOf }) {
  const balance = useApi(() => api.ledgerTransfers(accountId, 100).then(async (entries) => ({
    entries,
    account: await api.getAccount(accountId).then((a) => a.balance).catch(() => null),
  })), [accountId])
  const entries = balance.data?.entries
  const b = balance.data?.account

  return (
    <motion.div key={accountId} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs text-muted">Account</p>
          <h2 className="text-2xl font-semibold tracking-tight text-ink">{nameOf(accountId)}</h2>
          <CopyId id={accountId} className="mt-1" />
        </div>
        {b && (
          <div className="flex gap-6 text-right">
            <div><p className="text-xs text-muted">Available</p><p className="text-xl font-semibold text-ink tabular">{formatPaise(b.available)}</p></div>
            <div><p className="text-xs text-muted">On hold</p><p className="text-xl font-semibold text-hold tabular">{formatPaise(b.debitsPending || b.creditsPending)}</p></div>
          </div>
        )}
      </div>

      {!entries ? (
        <Skeleton className="h-64 w-full rounded-2xl" />
      ) : entries.length === 0 ? (
        <EmptyState icon={BookOpen} title="No transfers yet" description="Top up this account or make a payment and its ledger entries will appear here." />
      ) : (
        <>
          <Card className="p-5">
            <p className="mb-4 text-xs text-muted">T-account view: how an accountant would draw it. Holds are shown in amber; they reserve money but only captured transfers count as posted.</p>
            <TAccount entries={entries} accountId={accountId} nameOf={nameOf} />
          </Card>

          <Card className="overflow-hidden">
            <div className="border-b border-line px-5 py-3 text-sm font-semibold text-ink">Raw TigerBeetle transfers <span className="font-normal text-faint">· newest first</span></div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-[11px] tracking-wide text-faint uppercase">
                  <tr className="border-b border-line">
                    <th className="px-5 py-2.5 font-medium">Transfer</th>
                    <th className="px-3 py-2.5 font-medium">Kind</th>
                    <th className="px-3 py-2.5 font-medium">Debit → Credit</th>
                    <th className="px-5 py-2.5 text-right font-medium">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  <AnimatePresence initial={false}>
                    {entries.map((e, i) => (
                      <motion.tr key={e.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: Math.min(i * 0.02, 0.4) }}>
                        <td className="px-5 py-2.5 font-mono text-xs text-muted">{shortId(e.id)}</td>
                        <td className="px-3 py-2.5"><Badge tone={KIND[e.kind].tone}>{KIND[e.kind].label}</Badge></td>
                        <td className="px-3 py-2.5 text-xs whitespace-nowrap text-muted">
                          <span className={e.debitAccountId === accountId ? 'text-ink' : ''}>{nameOf(e.debitAccountId)}</span>
                          <span className="mx-1.5 text-faint">→</span>
                          <span className={e.creditAccountId === accountId ? 'text-ink' : ''}>{nameOf(e.creditAccountId)}</span>
                        </td>
                        <td className={cn('px-5 py-2.5 text-right tabular', e.creditAccountId === accountId ? 'text-brand' : 'text-ink')}>
                          {e.creditAccountId === accountId ? '+' : '−'}{formatPaise(e.amount)}
                        </td>
                      </motion.tr>
                    ))}
                  </AnimatePresence>
                </tbody>
              </table>
            </div>
          </Card>
        </>
      )}
    </motion.div>
  )
}

export default function Explorer() {
  const { customers, merchants, labAccounts: lab } = useSandbox()
  const accounts = useMemo(() => [...customers, ...merchants], [customers, merchants])
  const overview = useApi(() => api.ledgerOverview(), [])
  const [selected, setSelected] = useState(null)

  const system = overview.data ? [
    { id: overview.data.externalFundingAccountId, name: 'External funding', type: 'SYSTEM' },
    { id: overview.data.platformFeeAccountId, name: 'PayCore fees', type: 'SYSTEM' },
  ] : []

  const groups = [
    { title: 'Your accounts', icon: Wallet, accounts },
    { title: 'Lab runs', icon: FlaskConical, accounts: lab },
    { title: 'PayCore system', icon: Building2, accounts: system },
  ]
  const known = [...accounts, ...lab, ...system]
  const nameOf = (id) => known.find((a) => a.id === id)?.name ?? shortId(id)
  const current = selected ?? accounts[0]?.id ?? lab[0]?.id ?? system[1]?.id

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
      <PageHeader
        eyebrow="Ledger Explorer"
        title="See every paisa move"
        description="These are the actual transfers stored in TigerBeetle, the same data the balances are computed from. Pick an account to inspect its debits and credits."
      />
      <div className="mt-8"><Invariant /></div>

      <div className="mt-8">
        <AuthGate title="Sign in to inspect your accounts" description="The invariant above is public. Account-level ledger entries are private to their owner.">
      <div className="mt-8 grid gap-8 lg:grid-cols-[260px_1fr]">
            <div className="lg:sticky lg:top-24 lg:self-start">
              <AccountList groups={groups} selected={current} onSelect={setSelected} />
            </div>
            <div className="min-w-0">
              {current ? <AccountLedger accountId={current} nameOf={nameOf} /> : <Skeleton className="h-64 w-full rounded-2xl" />}
            </div>
          </div>
        </AuthGate>
      </div>
    </div>
  )
}
