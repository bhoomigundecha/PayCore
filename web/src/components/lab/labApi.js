import { api } from '../../api/client'

/** Accounts created by Lab experiments are named with this prefix, so the Playground can hide them. */
export const LAB_PREFIX = 'Lab · '

/** Creates a fresh customer (topped up) and merchant, owned by the signed-in user, for one experiment run. */
export async function freshAccounts(label, walletPaise) {
  const customer = await api.createAccount('CUSTOMER_WALLET', `${LAB_PREFIX}${label} wallet`)
  const merchant = await api.createAccount('MERCHANT', `${LAB_PREFIX}${label} store`)
  if (walletPaise > 0) await api.topUp(customer.id, walletPaise)
  return { customer, merchant }
}

export const available = async (id) => (await api.getAccount(id)).balance.available
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
