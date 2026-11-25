import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { api } from '../api/client'
import { useApi } from './useApi'
import { useAuth } from './useAuth'
import { LAB_PREFIX } from '../components/lab/labApi'

/**
 * The signed-in user's accounts and payments, loaded from the server.
 * `version` bumps after every mutation so balances and lists refetch.
 */
const SandboxContext = createContext(null)

export function SandboxProvider({ children }) {
  const { user } = useAuth()
  const [version, setVersion] = useState(0)
  const refresh = useCallback(() => setVersion((v) => v + 1), [])

  const enabled = !!user
  const accountsQuery = useApi(() => api.listAccounts(), [version, user?.id], { enabled })
  const paymentsQuery = useApi(() => api.listPayments(), [version, user?.id], { enabled })

  const addAccount = useCallback(async (type, name) => {
    const account = await api.createAccount(type, name)
    refresh()
    return account
  }, [refresh])

  const value = useMemo(() => {
    const all = enabled ? accountsQuery.data ?? [] : []
    // Accounts made by Lab experiments are kept out of the Playground but stay inspectable in the Explorer.
    const labAccounts = all.filter((a) => a.name.startsWith(LAB_PREFIX))
    const accounts = all.filter((a) => !a.name.startsWith(LAB_PREFIX))
    const customers = accounts.filter((a) => a.type === 'CUSTOMER_WALLET')
    const merchants = accounts.filter((a) => a.type === 'MERCHANT')
    const nameOf = (id) => all.find((a) => a.id === id)?.name
    const owns = (id) => all.some((a) => a.id === id)
    return {
      accounts, customers, merchants, labAccounts,
      payments: enabled ? (paymentsQuery.data ?? []).filter((p) => !p.customerName.startsWith(LAB_PREFIX)) : [],
      loading: enabled && (accountsQuery.loading || paymentsQuery.loading),
      version, refresh, addAccount, nameOf, owns,
    }
  }, [enabled, accountsQuery.data, accountsQuery.loading, paymentsQuery.data, paymentsQuery.loading, version, refresh, addAccount])

  return <SandboxContext.Provider value={value}>{children}</SandboxContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSandbox() {
  const ctx = useContext(SandboxContext)
  if (!ctx) throw new Error('useSandbox must be used inside <SandboxProvider>')
  return ctx
}
