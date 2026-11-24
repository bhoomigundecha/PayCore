// Thin wrapper around fetch for the PayCore API.
// Errors come back as RFC 9457 problem JSON ({ title, status, detail }); we throw them as ApiError.

export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `Request failed (${status})`)
    this.status = status
    this.problem = problem
  }
}

/** A fresh Idempotency-Key. Reuse the same key when retrying the same request. */
export const newIdempotencyKey = () => crypto.randomUUID()

async function request(method, path, { body, idempotencyKey } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey

  let res
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, { detail: 'Cannot reach the PayCore API' })
  }
  const data = await res.json().catch(() => null)

  // A declined payment is a valid result (HTTP 422 with the payment body), not an exception.
  if (!res.ok && !(data && data.status === 'FAILED' && data.id)) {
    throw new ApiError(res.status, data)
  }
  return data
}

export const api = {
  health: () => request('GET', '/actuator/health'),
  ledgerOverview: () => request('GET', '/ledger/overview'),
  ledgerTotals: () => request('GET', '/ledger/totals'),
  ledgerTransfers: (accountId, limit = 50) => request('GET', `/ledger/accounts/${accountId}/transfers?limit=${limit}`),

  createAccount: (type, name) => request('POST', '/accounts', { body: { type, name } }),
  getAccount: (id) => request('GET', `/accounts/${id}`),
  topUp: (id, amount, idempotencyKey = newIdempotencyKey()) =>
    request('POST', `/accounts/${id}/top-ups`, { body: { amount }, idempotencyKey }),

  createPayment: ({ customerAccountId, merchantAccountId, amount, capture = true },
                  idempotencyKey = newIdempotencyKey()) =>
    request('POST', '/payments', {
      body: { customerAccountId, merchantAccountId, amount, capture },
      idempotencyKey,
    }),
  getPayment: (id) => request('GET', `/payments/${id}`),
  capturePayment: (id) => request('POST', `/payments/${id}/capture`),
  cancelPayment: (id) => request('POST', `/payments/${id}/cancel`),
  refundPayment: (id) => request('POST', `/payments/${id}/refund`),
}
