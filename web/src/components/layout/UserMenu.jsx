import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { LogIn, LogOut, UserPlus } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth } from '../../hooks/useAuth'
import { Tip } from './Navbar'

/** Avatar with a small menu when signed in; a "Sign in" pill otherwise. */
export function UserMenu() {
  const { user, loading, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    if (!open) return undefined
    const close = (e) => { if (!ref.current?.contains(e.target)) setOpen(false) }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (loading) return <span className="size-9" />
  if (!user) {
    return (
      <Link to="/signin" aria-label="Sign in" className="group relative grid size-9 place-items-center rounded-full bg-ink text-canvas transition-colors hover:bg-white">
        <LogIn className="size-4" />
        <Tip>Sign in</Tip>
      </Link>
    )
  }

  const initial = user.guest ? 'G' : user.name.trim()[0]?.toUpperCase()

  const signOut = async () => {
    setOpen(false)
    await logout()
    toast('Signed out')
    navigate('/')
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label="Account menu"
        aria-expanded={open}
        className="group relative grid size-9 place-items-center rounded-full border border-brand/40 bg-brand/15 text-sm font-semibold text-brand transition-colors hover:bg-brand/25"
      >
        {initial}
        {!open && <Tip>Account</Tip>}
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.97 }}
            transition={{ duration: 0.15 }}
            className="absolute top-11 right-0 z-50 w-64 rounded-2xl border border-line-strong bg-raised p-2 shadow-2xl"
          >
            <div className="px-2.5 py-2">
              <p className="text-sm font-medium text-ink">{user.guest ? 'Guest' : user.name}</p>
              <p className="truncate text-xs text-faint">{user.guest ? 'Temporary guest session' : user.email}</p>
            </div>
            <div className="my-1 h-px bg-line" />
            {user.guest && (
              <Link to="/signin?mode=signup" onClick={() => setOpen(false)} className="flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm text-muted hover:bg-white/[0.05] hover:text-ink">
                <UserPlus className="size-4" /> Create a real account
              </Link>
            )}
            <button type="button" onClick={signOut} className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm text-muted hover:bg-white/[0.05] hover:text-ink">
              <LogOut className="size-4" /> Sign out
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
