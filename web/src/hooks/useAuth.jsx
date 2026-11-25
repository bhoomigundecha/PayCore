import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { api, UNAUTHORIZED_EVENT } from '../api/client'

/**
 * Who is signed in. `user` is undefined while loading, null when signed out.
 * The session itself is a server-side cookie; this only mirrors /auth/me.
 */
const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(undefined)

  useEffect(() => {
    let cancelled = false
    const check = () => api.me()
      .then((session) => !cancelled && setUser(session.authenticated ? session.user : null))
      .catch(() => !cancelled && setUser(null))
    check()
    // Data from the old anonymous sandbox (before accounts lived on the server).
    try {
      ['paycore.accounts', 'paycore.payments', 'paycore.labAccounts'].forEach((k) => localStorage.removeItem(k))
    } catch {
      // storage unavailable
    }
    // A 401 can come from a request sent before the latest sign-in, so ask the server
    // instead of assuming the session is gone.
    const onUnauthorized = () => { check() }
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => {
      cancelled = true
      window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    }
  }, [])

  const value = useMemo(() => ({
    user,
    loading: user === undefined,
    signedIn: !!user,
    signUp: async (name, email, password) => setUser((await api.signUp(name, email, password)).user),
    login: async (email, password) => setUser((await api.login(email, password)).user),
    guest: async () => setUser((await api.guest()).user),
    logout: async () => {
      await api.logout().catch(() => {})
      setUser(null)
    },
  }), [user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
