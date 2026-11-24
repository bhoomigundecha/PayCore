import { useEffect, useState } from 'react'

// The API has no "list my accounts" endpoint, so the UI remembers the ids it created in this browser.
export function useLocalStorage(key, initialValue) {
  const [value, setValue] = useState(() => {
    try {
      const stored = localStorage.getItem(key)
      return stored === null ? initialValue : JSON.parse(stored)
    } catch {
      return initialValue
    }
  })

  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(value))
    } catch {
      // storage unavailable (private mode): keep working in memory
    }
  }, [key, value])

  return [value, setValue]
}
