import { Button } from '../components/ui/Button'

export default function NotFound() {
  return (
    <div className="mx-auto max-w-xl px-6 py-32 text-center">
      <p className="font-mono text-sm text-brand">404</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight">This page moved no money.</h1>
      <p className="mt-3 text-muted">There is nothing at this address.</p>
      <Button to="/" className="mt-8">Back home</Button>
    </div>
  )
}
