// Tiny event bridge between payment actions and the animation layer (PaymentFlightLayer).
export const FLIGHT_EVENT = 'paycore:flight'

let nextId = 0

/**
 * Asks the animation layer to fly a payment card between two account cards.
 * Resolves when the card lands (or quickly if nothing is listening / motion is reduced).
 * kind: captured | held | declined | released | refunded
 */
export function launchFlight(detail) {
  return new Promise((resolve) => {
    window.dispatchEvent(new CustomEvent(FLIGHT_EVENT, { detail: { ...detail, id: ++nextId, resolve } }))
    setTimeout(resolve, 1400)
  })
}
