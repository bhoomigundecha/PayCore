import { useState } from 'react'
import { Link } from 'react-router'
import { ArrowRight, LogIn, UserRound } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth } from '../../hooks/useAuth'
import { Button } from '../ui/Button'
import { Skeleton } from '../ui/misc'

/** Shows children only when signed in; otherwise a one-click guest option and a link to sign in. */
export function AuthGate({ children, title = 'Sign in to continue', description }) {
  const { loading, signedIn } = useAuth()
  if (loading) return <Skeleton className="h-72 w-full rounded-2xl" />
  if (signedIn) return children
  return <SignInPrompt title={title} description={description} />
}

export function SignInPrompt({ title, description }) {
  const { guest } = useAuth()
  const [busy, setBusy] = useState(false)

  const tryAsGuest = async () => {
    setBusy(true)
    try {
      await guest()
      toast.success('Welcome! Your wallet has ₹5,000 of play money and a virtual card.')
    } catch (e) {
      toast.error(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto flex max-w-lg flex-col items-center rounded-3xl border border-line bg-surface px-6 py-14 text-center">
      <span className="grid size-12 place-items-center rounded-2xl border border-line bg-canvas text-brand">
        <UserRound className="size-5" />
      </span>
      <h2 className="mt-5 text-xl font-semibold tracking-tight text-ink">{title}</h2>
      <p className="mt-2 max-w-sm text-sm text-pretty text-muted">
        {description ?? 'Every account comes with a wallet, a virtual PayCore card and ₹5,000 of play money.'}
      </p>
      <div className="mt-8 flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
        <Button variant="brand" size="lg" onClick={tryAsGuest} loading={busy}>
          Try as guest <ArrowRight className="size-4" />
        </Button>
        <Button to="/signin" variant="secondary" size="lg">
          <LogIn className="size-4" /> Sign in or sign up
        </Button>
      </div>
      <p className="mt-4 text-xs text-faint">Guest mode needs no email. You can <Link to="/signin" className="underline hover:text-muted">create an account</Link> to keep your data.</p>
    </div>
  )
}
