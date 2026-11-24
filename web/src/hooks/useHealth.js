import { api } from '../api/client'
import { useApi } from './useApi'

export const SERVICES = [
  { key: 'tigerBeetle', label: 'TigerBeetle', role: 'Ledger' },
  { key: 'db', label: 'PostgreSQL', role: 'Payments' },
  { key: 'redis', label: 'Redis', role: 'Rate limits' },
]

/** Polls the actuator health endpoint. Status per service: 'UP' | 'DOWN' | 'UNKNOWN'. */
export function useHealth() {
  const { data, error } = useApi(() => api.health(), [], { interval: 15_000 })
  const statusOf = (key) => {
    if (error && !data) return 'DOWN'
    return data?.components?.[key]?.status ?? 'UNKNOWN'
  }
  const services = SERVICES.map((s) => ({ ...s, status: statusOf(s.key) }))
  return {
    services,
    allUp: services.every((s) => s.status === 'UP'),
    reachable: !!data,
  }
}
