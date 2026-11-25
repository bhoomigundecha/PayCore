// Thin wrapper around fetch for the PayCore API.
// - Errors come back as RFC 9457 problem JSON ({ title, status, detail }); we throw them as ApiError.
// - Auth is a session cookie set by the server (httpOnly, so this code never sees it).
// - State-changing requests echo the XSRF-TOKEN cookie back as the X-XSRF-TOKEN header (CSRF protection).

export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `Request failed (${status})`)
    this.status = status
    this.problem = problem
  }
}

/** Fired when the server says we're not signed in (e.g. the session expired). */
export const UNAUTHORIZED_EVENT = 'paycore:unauthorized'

/** A fresh Idempotency-Key. Reuse the same key when retrying the same request. */
export const newIdempotencyKey = () => crypto.randomUUID()

const csrfToken = () => document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)?.[1]

async function request(method, path, { body, idempotencyKey } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
  if (method !== 'GET') {
    // The CSRF cookie is set by GET /auth/me; fetch it first if this browser doesn't have one yet.
    if (!csrfToken()) await fetch('/auth/me', { credentials: 'same-origin' }).catch(() => {})
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
  }

  let res
  try {
    res = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, { detail: 'Cannot reach the PayCore API' })
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)

  if (res.status === 401 && !path.startsWith('/auth/')) {
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }
  // A declined payment is a valid result (HTTP 422 with the payment body), not an exception.
  if (!res.ok && !(data && data.status === 'FAILED' && data.id)) {
    throw new ApiError(res.status, data ?? (res.status === 401 ? { detail: 'Please sign in' } : null))
  }
  return data
}

export const api = {
  // auth
  me: () => request('GET', '/auth/me'),
  signUp: (name, email, password) => request('POST', '/auth/signup', { body: { name, email, password } }),
  login: (email, password) => request('POST', '/auth/login', { body: { email, password } }),
  guest: () => request('POST', '/auth/guest'),
  logout: () => request('POST', '/auth/logout'),

  // public
  health: () => request('GET', '/actuator/health'),
  ledgerOverview: () => request('GET', '/ledger/overview'),
  ledgerTotals: () => request('GET', '/ledger/totals'),
  activity: () => request('GET', '/activity'),

  // signed in
  ledgerTransfers: (accountId, limit = 50) => request('GET', `/ledger/accounts/${accountId}/transfers?limit=${limit}`),
  listAccounts: () => request('GET', '/accounts'),
  createAccount: (type, name) => request('POST', '/accounts', { body: { type, name } }),
  getAccount: (id) => request('GET', `/accounts/${id}`),
  topUp: (id, amount, idempotencyKey = newIdempotencyKey()) =>
    request('POST', `/accounts/${id}/top-ups`, { body: { amount }, idempotencyKey }),

  listCards: () => request('GET', '/cards'),
  revealCard: (id) => request('POST', `/cards/${id}/reveal`),

  listPayments: () => request('GET', '/payments'),
  createPayment: ({ customerAccountId, merchantAccountId, amount, capture = true },
                  idempotencyKey = newIdempotencyKey()) =>
    request('POST', '/payments', {
      body: { customerAccountId, merchantAccountId, amount, capture },
      idempotencyKey,
    }),
  getPayment: (id) => request('GET', `/payments/${id}`),
  authenticatePayment: (id, code) => request('POST', `/payments/${id}/authenticate`, { body: { code } }),
  resendPaymentCode: (id) => request('POST', `/payments/${id}/resend-code`),
  notifications: () => request('GET', '/notifications'),

  // payment links / hosted checkout
  createCheckout: (merchantAccountId, amount, description) =>
    request('POST', '/checkout-sessions', { body: { merchantAccountId, amount, description } }),
  listCheckouts: () => request('GET', '/checkout-sessions'),
  getCheckout: (id) => request('GET', `/checkout-sessions/${id}`),
  payCheckout: (id, card, idempotencyKey = newIdempotencyKey()) =>
    request('POST', `/checkout-sessions/${id}/pay`, { body: card, idempotencyKey }),
  authenticateCheckout: (id, paymentId, code) =>
    request('POST', `/checkout-sessions/${id}/authenticate`, { body: { paymentId, code } }),
  listWebhooks: () => request('GET', '/webhooks/endpoints'),
  createWebhook: (accountId, { url, testReceiver }) =>
    request('POST', '/webhooks/endpoints', { body: { accountId, url, testReceiver } }),
  deleteWebhook: (id) => request('DELETE', `/webhooks/endpoints/${id}`),
  webhookDeliveries: (id) => request('GET', `/webhooks/endpoints/${id}/deliveries`),
  webhookReceived: (id) => request('GET', `/webhooks/endpoints/${id}/received`),
  sendTestWebhook: (id) => request('POST', `/webhooks/endpoints/${id}/test`),
  setReceiverFailing: (id, failing) => request('PUT', `/webhooks/endpoints/${id}/receiver`, { body: { failing } }),
  retryDelivery: (id) => request('POST', `/webhooks/deliveries/${id}/retry`),
  capturePayment: (id) => request('POST', `/payments/${id}/capture`),
  cancelPayment: (id) => request('POST', `/payments/${id}/cancel`),
  refundPayment: (id) => request('POST', `/payments/${id}/refund`),
}
