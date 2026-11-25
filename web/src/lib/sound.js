// Tiny synthesized sound effects (Web Audio, no audio files). Off unless the visitor turns sound on.

const KEY = 'paycore.sound'
let ctx = null

export const soundEnabled = () => {
  try {
    return localStorage.getItem(KEY) === 'true'
  } catch {
    return false
  }
}

function audio() {
  if (!soundEnabled()) return null
  try {
    ctx ??= new (window.AudioContext || window.webkitAudioContext)()
    if (ctx.state === 'suspended') ctx.resume()
    return ctx
  } catch {
    return null
  }
}

function tone(ac, { freq, start = 0, duration = 0.25, type = 'sine', volume = 0.12, slideTo }) {
  const t = ac.currentTime + start
  const osc = ac.createOscillator()
  const gain = ac.createGain()
  osc.type = type
  osc.frequency.setValueAtTime(freq, t)
  if (slideTo) osc.frequency.exponentialRampToValueAtTime(slideTo, t + duration)
  gain.gain.setValueAtTime(0.0001, t)
  gain.gain.exponentialRampToValueAtTime(volume, t + 0.008)
  gain.gain.exponentialRampToValueAtTime(0.0001, t + duration)
  osc.connect(gain).connect(ac.destination)
  osc.start(t)
  osc.stop(t + duration + 0.05)
}

/** Bright two-note chime: payment captured. */
export function playPaid() {
  const ac = audio()
  if (!ac) return
  tone(ac, { freq: 1318.5, duration: 0.35, volume: 0.1 })
  tone(ac, { freq: 1975.5, start: 0.07, duration: 0.5, volume: 0.09 })
}

/** Single soft note: money placed on hold or released. */
export function playHold() {
  const ac = audio()
  if (!ac) return
  tone(ac, { freq: 880, duration: 0.3, type: 'triangle', volume: 0.08 })
}

/** Short metallic tick: one fee coin landing. */
export function playCoin() {
  const ac = audio()
  if (!ac) return
  tone(ac, { freq: 2800 + Math.random() * 600, duration: 0.09, volume: 0.05 })
}

/** Low falling buzz: payment declined. */
export function playDeclined() {
  const ac = audio()
  if (!ac) return
  tone(ac, { freq: 330, slideTo: 196, duration: 0.32, type: 'triangle', volume: 0.1 })
}
