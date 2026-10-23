import request from '@/utils/request'

/** 确定性规则核对输出的异常项，由后端规则计算，不是模型判断。 */
export interface RiskFinding {
  code: string
  level: string
  title: string
  count: number
  advice: string
}

/** 售后资料概览，范围由当前账号权限决定。 */
export interface RiskOverview {
  scope: string
  total: number
  open: number
  findings: RiskFinding[]
  limitation: string
}

/** 智能体汇总的逐条资料，source 用于标识来源，name 为后端给出的中文标题。 */
export interface AgentSection {
  source: string
  /** 后端已给中文标题；旧数据可能缺失，页面回退为 sourceLabel(source)。 */
  name?: string
  summary: string
  limitation: string
}

/** not_requested：未请求模型；disabled：模型未启用；ok：成功；unavailable：调用失败。 */
export type AgentModelStatus = 'not_requested' | 'disabled' | 'ok' | 'unavailable'

/** 智能体汇总结果，规则汇总始终返回，模型解读为可选。 */
export interface RiskAnalysis {
  scope: string
  sections: AgentSection[]
  modelStatus: AgentModelStatus
  /** 后端序列化可能省略该字段（NON_NULL），undefined 与 null 同样视为无模型解读。 */
  explanation?: string | null
}

/** 汇总请求参数，question 仅在 useModel 为 true 时有意义。 */
export interface RiskAnalysisRequest {
  question?: string
  useModel: boolean
}

export function getRiskOverview() {
  return request<any, { data: RiskOverview }>({ url: '/api/erp-api/ai-data/risk/overview' })
}

export function analyzeRisk(data: RiskAnalysisRequest) {
  return request<any, { data: RiskAnalysis }>({
    url: '/api/erp-api/ai-agent/risk/analysis',
    method: 'post',
    data,
    timeout: 65000
  })
}
