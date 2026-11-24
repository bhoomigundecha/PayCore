import { api } from '../../api/client'

const LAB_KEY = 'paycore.labAccounts'

/** Creates a fresh customer (topped up) and merchant for one experiment run, and remembers them for the Explorer. */
export async function freshAccounts(label, walletPaise) {
  const customer = await api.createAccount('CUSTOMER_WALLET', `Lab · ${label} wallet`)
  const merchant = await api.createAccount('MERCHANT', `Lab · ${label} merchant`)
  if (walletPaise > 0) await api.topUp(customer.id, walletPaise)
  try {
    const prev = JSON.parse(localStorage.getItem(LAB_KEY) ?? '[]')
    const next = [
      { id: customer.id, name: customer.name, type: customer.type },
      { id: merchant.id, name: merchant.name, type: merchant.type },
      ...prev.filter((a) => a.name !== customer.name && a.name !== merchant.name),
    ].slice(0, 12)
    localStorage.setItem(LAB_KEY, JSON.stringify(next))
  } catch {
    // ignore storage errors
  }
  return { customer, merchant }
}

export function readLabAccounts() {
  try {
    return JSON.parse(localStorage.getItem(LAB_KEY) ?? '[]')
  } catch {
    return []
  }
}

export const available = async (id) => (await api.getAccount(id)).balance.available
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
