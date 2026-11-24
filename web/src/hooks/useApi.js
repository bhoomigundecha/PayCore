import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Runs an API call, re-running it when `deps` change and optionally every `interval` ms.
 * Keeps the previous data while reloading so numbers animate instead of flashing.
 */
export function useApi(fetcher, deps = [], { interval, enabled = true } = {}) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(enabled)
  const fetcherRef = useRef(fetcher)
  // Always call the latest fetcher without restarting the polling effect. Declared first so it runs first.
  useEffect(() => {
    fetcherRef.current = fetcher
  })

  const load = useCallback(async () => {
    try {
      const result = await fetcherRef.current()
      setData(result)
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!enabled) return undefined
    load()
    if (!interval) return undefined
    const timer = setInterval(load, interval)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, interval, load, ...deps])

  return { data, error, loading, reload: load }
}
