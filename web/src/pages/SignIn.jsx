import { useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowRight, CreditCard, Lock, ShieldCheck, Wallet } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth } from '../hooks/useAuth'
import { Button } from '../components/ui/Button'
import { Field, Segmented, TextInput } from '../components/ui/Form'
import { LogoMark } from '../components/ui/misc'

const PERKS = [
  { icon: Wallet, text: 'A wallet with ₹5,000 of play money' },
  { icon: CreditCard, text: 'A virtual PayCore card, ready to pay with' },
  { icon: ShieldCheck, text: 'Your own store to receive payments' },
]

export default function SignIn() {
  const { signedIn, login, signUp, guest } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [mode, setMode] = useState(params.get('mode') === 'signup' ? 'signup' : 'signin')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)

  if (signedIn) return <Navigate to="/playground" replace />

  const done = (message) => {
    toast.success(message)
    navigate('/playground')
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy('form')
    setError(null)
    try {
      if (mode === 'signup') {
        await signUp(name.trim(), email.trim(), password)
        done('Account created. Your card and ₹5,000 of play money are ready.')
      } else {
        await login(email.trim(), password)
        done('Welcome back')
      }
    } catch (err) {
      setError(err.status === 400 ? 'Please check the fields: passwords need at least 8 characters.' : err.message)
    } finally {
      setBusy(null)
    }
  }

  const asGuest = async () => {
    setBusy('guest')
    try {
      await guest()
      done('Guest session started with ₹5,000 of play money')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="mx-auto grid max-w-5xl gap-10 px-4 py-14 sm:px-6 lg:grid-cols-2 lg:items-center lg:py-20">
      <div className="hidden lg:block">
        <LogoMark className="size-10" />
        <h1 className="mt-6 text-4xl font-semibold tracking-tight text-ink">Your test-mode payments account</h1>
        <p className="mt-3 max-w-md text-muted">Pay merchants, hold and capture, refund, and inspect every ledger entry. All money here is play money.</p>
        <ul className="mt-8 space-y-3">
          {PERKS.map(({ icon: Icon, text }) => (
            <li key={text} className="flex items-center gap-3 text-sm text-ink">
              <span className="grid size-8 place-items-center rounded-lg border border-line bg-surface text-brand"><Icon className="size-4" /></span>
              {text}
            </li>
          ))}
        </ul>
      </div>

      <div className="rounded-3xl border border-line bg-surface p-6 sm:p-8">
        <Segmented
          layoutId="auth-mode"
          className="w-full"
          value={mode}
          onChange={(m) => { setMode(m); setError(null) }}
          options={[{ value: 'signin', label: 'Sign in' }, { value: 'signup', label: 'Create account' }]}
        />

        <form onSubmit={submit} className="mt-6 space-y-4">
          <AnimatePresence initial={false}>
            {mode === 'signup' && (
              <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} className="overflow-hidden">
                <Field label="Name">
                  {(id) => <TextInput id={id} value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required maxLength={100} placeholder="Asha Rao" />}
                </Field>
              </motion.div>
            )}
          </AnimatePresence>
          <Field label="Email">
            {(id) => <TextInput id={id} type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required placeholder="you@example.com" />}
          </Field>
          <Field label="Password" hint={mode === 'signup' ? 'At least 8 characters.' : undefined}>
            {(id) => (
              <TextInput
                id={id}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                required
                minLength={mode === 'signup' ? 8 : undefined}
                maxLength={72}
              />
            )}
          </Field>

          {error && <p role="alert" className="rounded-lg border border-decline/25 bg-decline/[0.07] px-3 py-2 text-sm text-decline">{error}</p>}

          <Button type="submit" variant="primary" size="lg" className="w-full" loading={busy === 'form'} disabled={busy !== null}>
            {mode === 'signup' ? 'Create account' : 'Sign in'} <ArrowRight className="size-4" />
          </Button>
        </form>

        <div className="my-6 flex items-center gap-3 text-xs text-faint">
          <span className="h-px flex-1 bg-line" /> or <span className="h-px flex-1 bg-line" />
        </div>

        <Button variant="secondary" size="lg" className="w-full" onClick={asGuest} loading={busy === 'guest'} disabled={busy !== null}>
          Continue as guest
        </Button>
        <p className="mt-4 flex items-center justify-center gap-1.5 text-xs text-faint">
          <Lock className="size-3" /> Passwords are hashed with BCrypt. Sessions use a secure cookie.
        </p>
      </div>
    </div>
  )
}
