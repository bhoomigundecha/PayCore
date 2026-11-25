// Shared helpers: sign users in through the real API, then send requests as them.
import http from 'k6/http'

export const BASE = __ENV.BASE_URL || 'http://localhost:8080'

/** Signs in a fresh guest (wallet with ₹5,000 + a store) and returns what a VU needs to act as them. */
export function newGuest() {
  const jar = new http.CookieJar()
  http.get(`${BASE}/auth/me`, { jar }) // issues the XSRF-TOKEN cookie
  let xsrf = jar.cookiesForURL(BASE)['XSRF-TOKEN'][0]
  const res = http.post(`${BASE}/auth/guest`, null, { jar, headers: { 'X-XSRF-TOKEN': xsrf } })
  if (res.status !== 201) throw new Error(`guest sign-in failed: ${res.status} ${res.body}`)
  http.get(`${BASE}/auth/me`, { jar }) // signing in rotates the CSRF token
  const cookies = jar.cookiesForURL(BASE)
  xsrf = cookies['XSRF-TOKEN'][0]
  const session = cookies.SESSION[0]
  const accounts = http.get(`${BASE}/accounts`, { jar }).json()
  return {
    session,
    xsrf,
    wallet: accounts.find((a) => a.type === 'CUSTOMER_WALLET').id,
    store: accounts.find((a) => a.type === 'MERCHANT').id,
  }
}

/** Request params for acting as a signed-in user (session cookie + CSRF header), tagged by endpoint name. */
export function as(user, name, extraHeaders = {}) {
  return {
    cookies: { SESSION: user.session, 'XSRF-TOKEN': user.xsrf },
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': user.xsrf, ...extraHeaders },
    tags: { name },
  }
}

export function uuid() {
  // RFC 4122 v4 from Math.random: fine for idempotency keys in a load test.
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}
