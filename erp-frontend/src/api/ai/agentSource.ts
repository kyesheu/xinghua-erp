/**
 * 兜底映射：后端 sections[].name 已给中文，本表仅用于旧数据或字段缺失时回退。
 *
 * 后端 sections[].source 使用内部英文标识（如 marketing-expired_enabled、notice），
 * name 缺失时直接展示 source 可读性差，这里统一转换为中文标签；未知来源回退为原始值，便于排查。
 */

/** 规则码到中文标题的映射，营销与售后共用（code 为 source 去掉前缀并小写后的值）。 */
const SOURCE_LABELS: Record<string, string> = {
  // 营销规则（对应营销页 findings 的 code 小写形式）
  expired_enabled: '已过期仍启用',
  invalid_value: '折扣值不合法',
  fixed_over_min: '固定折扣不低于订单下限',
  overlap_conflict: '同范围同优先级重叠',
  quota_exhausted: '额度已用尽',
  expiring_soon: '7天内到期',
  pending_stale: '待审核超3天',
  // 售后规则（对应风险页 findings 的 code 小写形式）
  stale_pending: '待处理超3天',
  amount_over_order: '退款超订单金额',
  duplicate_open: '重复未关闭售后',
  stuck_seller: '等待卖家处理超3天',
  missing_return_logistics: '缺退货物流单号',
  amount_invalid: '金额缺失或非正'
}

/**
 * 把 section.source 转为中文标签。
 *
 * @param source 后端返回的来源标识，如 marketing、risk、notice、marketing-quota_exhausted。
 * @returns 已知来源返回中文标签；未知来源回退为原文，便于排查。
 */
export function sourceLabel(source: string): string {
  if (source === 'marketing') return '折扣规则资料'
  if (source === 'risk') return '售后记录资料'
  if (source === 'notice') return '口径说明'
  // 空 findings 的两个提示节必须在剥离前缀之前拦住：
  // marketing-risk 剥前缀后恰好是 risk，否则会被误判成售后基础节。
  if (source === 'marketing-risk' || source === 'risk-check') return '未发现异常项'
  const code = source.replace(/^(marketing|risk)-/, '')
  return SOURCE_LABELS[code] ?? source
}
