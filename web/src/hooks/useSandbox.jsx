import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { api } from '../api/client'
import { useLocalStorage } from './useLocalStorage'

/**
 * Every visitor gets a private sandbox: the accounts and payments they created are remembered
 * in localStorage. `version` bumps after every mutation so balances and lists refetch.
 */
const SandboxContext = createContext(null)

export const DEMO_CUSTOMER = 'Asha Rao'
export const DEMO_MERCHANT = 'Monsoon Café'

export function SandboxProvider({ children }) {
  const [accounts, setAccounts] = useLocalStorage('paycore.accounts', [])
  const [payments, setPayments] = useLocalStorage('paycore.payments', [])
  const [version, setVersion] = useState(0)

  const refresh = useCallback(() => setVersion((v) => v + 1), [])

  const addAccount = useCallback(async (type, name) => {
    const account = await api.createAccount(type, name)
    setAccounts((prev) => [{ id: account.id, name: account.name, type: account.type }, ...prev])
    refresh()
    return account
  }, [setAccounts, refresh])

  const rememberPayment = useCallback((id) => {
    setPayments((prev) => [id, ...prev.filter((p) => p !== id)])
    refresh()
  }, [setPayments, refresh])

  const quickStart = useCallback(async () => {
    const customer = await addAccount('CUSTOMER_WALLET', DEMO_CUSTOMER)
    const merchant = await addAccount('MERCHANT', DEMO_MERCHANT)
    await api.topUp(customer.id, 500_000) // ₹5,000
    refresh()
    return { customer, merchant }
  }, [addAccount, refresh])

  const forgetAccount = useCallback((id) => {
    setAccounts((prev) => prev.filter((a) => a.id !== id))
  }, [setAccounts])

  const forgetPayment = useCallback((id) => {
    setPayments((prev) => prev.filter((p) => p !== id))
  }, [setPayments])

  const reset = useCallback(() => {
    setAccounts([])
    setPayments([])
    refresh()
  }, [setAccounts, setPayments, refresh])

  const value = useMemo(() => {
    const customers = accounts.filter((a) => a.type === 'CUSTOMER_WALLET')
    const merchants = accounts.filter((a) => a.type === 'MERCHANT')
    const nameOf = (id) => accounts.find((a) => a.id === id)?.name
    return {
      accounts, customers, merchants, payments, version,
      refresh, addAccount, rememberPayment, quickStart, reset, nameOf, forgetAccount, forgetPayment,
    }
  }, [accounts, payments, version, refresh, addAccount, rememberPayment, quickStart, reset, forgetAccount, forgetPayment])

  return <SandboxContext.Provider value={value}>{children}</SandboxContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSandbox() {
  const ctx = useContext(SandboxContext)
  if (!ctx) throw new Error('useSandbox must be used inside <SandboxProvider>')
  return ctx
}
