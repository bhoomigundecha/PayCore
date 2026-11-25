// Client-side card helpers. The server re-checks everything; these only give instant feedback.

export const PAYCORE_BIN = '990001'
export const TEST_DECLINE_CARD = '9900 0100 0000 0001'

export const digitsOnly = (s) => (s ?? '').replace(/\D/g, '')

/** "9900011234" -> "9900 0112 34" */
export const formatCardNumber = (s) => digitsOnly(s).slice(0, 16).replace(/(.{4})/g, '$1 ').trim()

/** "1228" or "12/28" -> "12/28" */
export const formatExpiry = (s) => {
  const d = digitsOnly(s).slice(0, 4)
  return d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d
}

export function luhnValid(number) {
  const d = digitsOnly(number)
  if (d.length < 12) return false
  let sum = 0
  let double = false
  for (let i = d.length - 1; i >= 0; i--) {
    let n = d.charCodeAt(i) - 48
    if (double) {
      n *= 2
      if (n > 9) n -= 9
    }
    sum += n
    double = !double
  }
  return sum % 10 === 0
}

export function parseExpiry(value) {
  const [mm, yy] = formatExpiry(value).split('/')
  const month = Number(mm)
  const year = Number(yy)
  if (!month || month > 12 || !yy || yy.length !== 2) return null
  return { month, year: 2000 + year }
}
