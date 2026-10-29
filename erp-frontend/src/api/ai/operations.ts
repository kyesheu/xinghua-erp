import request from '@/utils/request'

export interface AiOperationsSummary {
  usage: { requests: number; inputTokens: number; outputTokens: number; unknownRequests: number | null }
  circuit: { failures: number; open_until: string | null; probe_until: string | null; manual_disabled: boolean | number }[]
  outbox: { pending: number; oldestPending: string | null; maxAttempts: number }
}
export function getAiOperations() { return request({ url: 'api/ai/operations/summary', method: 'get' }) }
export function setAiOperationsEnabled(enabled: boolean) { return request({ url: 'api/ai/operations/enabled', method: 'post', data: { enabled } }) }
