package cn.xinghuaerp.system.service;

/**
 * 销售与售后数据范围。
 *
 * @param merchantId 商户ID，null 表示总部不受限
 * @param shopId     店铺ID，null 表示商户级或总部级范围
 */
public record SalesScope(Long merchantId, Long shopId) {

    /** null 表示总部；非总部必须由 Controller 传入登录身份范围。 */
    public void check(Long merchant, Long shop) {
        require((merchantId == null || merchantId.equals(merchant)) && (shopId == null || shopId.equals(shop)), "无权处理该店铺的售后");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
