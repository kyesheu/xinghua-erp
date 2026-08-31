package cn.xinghuaerp.product.constant;

/**
 * 商品与规格的上下架状态常量。
 *
 * <p>历史上这些状态值以字面量形式散落在创建商品、生成规格与供应商商品审核等多处，
 * 语义只能靠字段注释猜；统一收敛到这里，后续新增状态只需改一处。</p>
 *
 * @author xinghua
 */
public final class GoodsStatusConstants {

    /** 销售中（已上架） */
    public static final int ON_SALE = 1;

    /** 已下架 */
    public static final int OFF_SALE = 2;

    /** 待审核（供应商商品） */
    public static final int PENDING_AUDIT = 0;

    private GoodsStatusConstants() {
    }
}
