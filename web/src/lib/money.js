// The API works in paise (integers). Convert only at the edges: input and display.

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 })
const inrCompact = new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', notation: 'compact', maximumFractionDigits: 1,
})

/** 12345 paise -> "₹123.45" */
export const formatPaise = (paise) => inr.format((paise ?? 0) / 100)

/** 12345678 paise -> "₹1.2L" */
export const formatPaiseCompact = (paise) => inrCompact.format((paise ?? 0) / 100)

/** "123.45" rupees typed by a user -> 12345 paise (integer), or NaN. */
export const rupeesToPaise = (rupees) => {
  const n = Number(rupees)
  return Number.isFinite(n) ? Math.round(n * 100) : NaN
}

/** PayCore's platform fee: 200 basis points (2%), rounded down, integer maths like the backend. */
export const FEE_BPS = 200
export const feeFor = (paise) => Math.floor((paise * FEE_BPS) / 10_000)

/** Mirrors paycore.step-up.threshold: payments of ₹2,000 or more need a one-time code. */
export const STEP_UP_THRESHOLD = 200_000

export const shortId = (id) => (id ? `${id.slice(0, 8)}…${id.slice(-4)}` : '')
