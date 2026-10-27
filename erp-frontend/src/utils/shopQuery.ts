/**
 * 商户 / 店铺筛选条件的默认值与下拉选项加载。
 *
 * ## 两条前提
 *
 * 1. `MerchantController.list()`（erp-system 模块）会在商户列表头部固定插入一条
 *    虚拟记录 `{ id: 0, name: '总部自营' }`，用于表示"总部"这个业务主体。它名下
 *    **没有任何真实店铺**，也不会出现在 `o_shop.merchant_id` 里。这条记录只服务于
 *    下拉展示，**永远不进查询条件** —— 一旦进查询条件就变成 `where merchant_id = 0`，
 *    结果是 0 条，这正是「店铺商品」「店铺售后」「手动发货」等页面首屏空白的根因。
 *
 * 2. 商户 / 店铺的可见范围由**后端** `access.salesScope()` 决定。前端不预设、也不猜：
 *    首屏默认只带平台 `shopType`，`merchantId` / `shopId` 保持为空，让后端按登录账号的
 *    数据范围返回数据。前端一旦自己"猜一个商户"，后端补上 scope 之后就会跟前端打架
 *    （商户账号只有一个商户 → 被锁死 → 没开该平台店铺的 tab 直接全空）。
 *
 * 用户在下拉里**手动选择**商户 / 店铺后，由各页面自己的 `merchantChange()` /
 * `shopChange()` 写入 `merchantId` / `shopId`，本文件不参与。
 */

/** 默认查询条件。字段为 `undefined` 时不参与请求，与原行为一致。 */
export interface DefaultShopQuery {
  /** 平台类型；未指定时不带该条件 */
  shopType?: number
  /** 商户 ID；仅当调用方显式传入时才带（路由参数、页面 props 等） */
  merchantId?: number
}

/** 加载店铺下拉选项的可选参数。 */
export interface ShopOptionOptions {
  /** 平台类型，对应 `o_shop.type` */
  shopType?: number | string | null
  /** 已选定的商户；未指定时按平台查全部店铺 */
  merchantId?: number | string | null
}

/**
 * 把平台类型 / ID 类入参规整成正整数，非法值（null、''、0、负数、NaN）返回 `null`。
 *
 * 之所以把 `0` 也视为非法：商户 0 是虚拟总部，平台 0（`EnumShipType.LOCAL`）在本项目里
 * 表示"ERP 内销"，二者都不该作为首屏筛选条件出现。
 *
 * @param value 原始值，可能来自 props、路由 query 或接口返回
 * @returns 正整数，或 `null` 表示"不带该条件"
 */
function toPositiveInt(value: any): number | null {
  if (value === null || value === undefined || value === '') return null
  const num = Number(value)
  if (!Number.isFinite(num)) return null
  const int = Math.trunc(num)
  return int > 0 ? int : null
}

/**
 * 计算页面首屏的默认查询条件：**只带平台，不带商户 / 店铺**。
 *
 * @param shopType 当前页面对应的平台类型，例如店铺商品页的 `props.shopType`
 * @param merchantId 仅当调用方有**明确来源**（路由参数、页面 props、登录账号的 deptId）
 *        时才传；不要从 `merchantList` 里"挑一个"传进来
 * @returns 可直接 `Object.assign` 到页面 `queryParams` 的对象
 *
 * @example
 * // 总部视角的店铺商品页：请求只带 shopType=100
 * Object.assign(queryParams, getDefaultShopQuery(100))
 *
 * @example
 * // 从工作台跳转过来，路由里明确指定了商户 2
 * Object.assign(queryParams, getDefaultShopQuery(100, Number(route.query.merchantId)))
 */
export function getDefaultShopQuery(
  shopType?: number | string | null,
  merchantId?: number | string | null,
): DefaultShopQuery {
  const query: DefaultShopQuery = {}
  const type = toPositiveInt(shopType)
  if (type !== null) query.shopType = type
  const merchant = toPositiveInt(merchantId)
  if (merchant !== null) query.merchantId = merchant
  return query
}

/**
 * 剔除 `id <= 0` 的虚拟商户「总部自营」，返回真实商户列表。
 *
 * 只用于渲染下拉、以及判断"是否需要隐藏商户选择框"这类展示逻辑，
 * **不要**用它的长度去推断当前账号属于哪个商户。
 *
 * @param merchantList 接口返回的商户列表
 * @returns 真实商户数组（不修改入参）
 */
export function pickRealMerchants(merchantList: any[]): any[] {
  if (!Array.isArray(merchantList)) return []
  return merchantList.filter((merchant: any) => toPositiveInt(merchant && merchant.id) !== null)
}

/**
 * 加载店铺下拉选项。
 *
 * 只负责把店铺列表取回来喂给页面的 `shopList`，**不写任何查询条件**，
 * 因此不会影响首屏的列表结果。用户手动选择店铺后由页面自己的 `shopChange()` 处理。
 *
 * 请求失败时**向上抛出**，由调用方决定是否提示；调用方务必接 `.catch()`，
 * 否则会变成 unhandled rejection，页面停在 loading 且用户没有任何提示。
 *
 * @param fetchShops 拉取店铺列表的方法，一般为 `api/shop/shop` 的 `listShop`
 * @param options 见 {@link ShopOptionOptions}
 * @returns 店铺数组
 */
export async function loadShopOptions(
  fetchShops: (params: Record<string, any>) => Promise<any>,
  options: ShopOptionOptions = {},
): Promise<any[]> {
  const params: Record<string, any> = {}
  const type = toPositiveInt(options.shopType)
  if (type !== null) params.type = type
  const merchant = toPositiveInt(options.merchantId)
  if (merchant !== null) params.merchantId = merchant

  const response = await fetchShops(params)
  return (response && response.rows) || []
}
