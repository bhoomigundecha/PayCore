// PayCore load test: realistic mixed traffic against the real API.
//
//   70% create a payment (authorize + capture: 2 ledger calls, 3 DB transactions, 2 outbox events)
//   20% list my payments (Postgres read)
//   10% read my accounts (balances from TigerBeetle)
//
// Pick a scenario with SCENARIO=smoke|load|stress|spike (default smoke). RATE sets the load test's
// requests per second. The app must run with raised rate limits (see load/README.md).
import http from 'k6/http'
import { check } from 'k6'
import { Counter, Trend } from 'k6/metrics'
import exec from 'k6/execution'
import { BASE, as, newGuest, uuid } from './lib.js'

const SCENARIO = __ENV.SCENARIO || 'smoke'
const RATE = Number(__ENV.RATE || 100)
const USERS = Number(__ENV.USERS || 50)

const SCENARIOS = {
  smoke: { executor: 'constant-vus', vus: 2, duration: '20s' },
  // Steady traffic at a fixed arrival rate: measures latency at a known throughput.
  load: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: '2m', preAllocatedVUs: 100, maxVUs: 400 },
  // Keep raising the rate until latency or errors break the thresholds: finds the breaking point.
  stress: {
    executor: 'ramping-arrival-rate', startRate: 50, timeUnit: '1s', preAllocatedVUs: 200, maxVUs: 800,
    stages: [
      { target: 200, duration: '1m' },
      { target: 400, duration: '1m' },
      { target: 800, duration: '1m' },
      { target: 1200, duration: '1m' },
      { target: 0, duration: '20s' },
    ],
  },
  // A sudden burst (a flash sale): does it recover once the burst passes?
  spike: {
    executor: 'ramping-arrival-rate', startRate: 50, timeUnit: '1s', preAllocatedVUs: 200, maxVUs: 800,
    stages: [
      { target: 50, duration: '30s' },
      { target: 800, duration: '10s' },
      { target: 800, duration: '30s' },
      { target: 50, duration: '10s' },
      { target: 50, duration: '30s' },
    ],
  },
}

export const options = {
  setupTimeout: '5m',
  scenarios: { [SCENARIO]: { ...SCENARIOS[SCENARIO], exec: 'traffic' } },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:POST /payments}': ['p(95)<250', 'p(99)<500'],
    'http_req_duration{name:GET /payments}': ['p(95)<150'],
    'http_req_duration{name:GET /accounts}': ['p(95)<150'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

const captured = new Counter('payments_captured')
const declined = new Counter('payments_declined')
const paymentTime = new Trend('payment_create_ms', true)

/** Creates the users once; every VU then picks one per iteration. */
export function setup() {
  const users = []
  for (let i = 0; i < USERS; i++) users.push(newGuest())
  return { users }
}

export function traffic({ users }) {
  const me = users[exec.scenario.iterationInTest % users.length]
  const roll = Math.random()

  if (roll < 0.7) {
    // Pay another user's store, ₹1 each, so wallets last for thousands of payments.
    const other = users[(exec.scenario.iterationInTest + 1) % users.length]
    const res = http.post(`${BASE}/payments`, JSON.stringify({
      customerAccountId: me.wallet, merchantAccountId: other.store, amount: 100, capture: true,
    }), as(me, 'POST /payments', { 'Idempotency-Key': uuid() }))
    paymentTime.add(res.timings.duration)
    const body = res.json()
    const ok = check(res, {
      'payment 201': (r) => r.status === 201,
      'payment captured': () => body && body.status === 'CAPTURED',
    })
    if (ok) captured.add(1)
    else declined.add(1)
  } else if (roll < 0.9) {
    const res = http.get(`${BASE}/payments`, as(me, 'GET /payments'))
    check(res, { 'list 200': (r) => r.status === 200 })
  } else {
    const res = http.get(`${BASE}/accounts`, as(me, 'GET /accounts'))
    check(res, { 'accounts 200': (r) => r.status === 200 })
  }
}
