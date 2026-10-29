package cn.xinghuaerp.order.service.impl;

import cn.xinghuaerp.inventory.facade.InventoryFacade;
import cn.xinghuaerp.order.bo.ShopOrderCreateBo;
import cn.xinghuaerp.order.bo.ShopOrderCreateItemBo;
import cn.xinghuaerp.order.entity.ErpRecoveryDeduction;
import cn.xinghuaerp.order.entity.ErpRecoveryRecord;
import cn.xinghuaerp.order.entity.ShopMember;
import cn.xinghuaerp.order.entity.ShopOrder;
import cn.xinghuaerp.order.mapper.ErpRecoveryDeductionMapper;
import cn.xinghuaerp.order.mapper.ErpRecoveryRecordMapper;
import cn.xinghuaerp.order.mapper.OMarketingDiscountRuleMapper;
import cn.xinghuaerp.order.mapper.OOrderMapper;
import cn.xinghuaerp.order.mapper.ShopMemberMapper;
import cn.xinghuaerp.order.mapper.ShopOrderItemMapper;
import cn.xinghuaerp.order.mapper.ShopOrderMapper;
import cn.xinghuaerp.order.mapper.ShopOrderPromotionMapper;
import cn.xinghuaerp.product.entity.ShopGoodsSku;
import cn.xinghuaerp.product.facade.ProductFacade;
import cn.xinghuaerp.product.facade.ShopGoodsSkuShipItemFacade;
import cn.xinghuaerp.system.entity.ErpMerchant;
import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.system.facade.SystemFacade;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回收抵扣的金额口径与单位验证。
 *
 * <p>单位事实（见 docs/sql/xinghua-erp.sql）：{@code oms_shop_order.deduction_price} 是<b>分</b>
 * （{@code paymentAmount = orderAmount - deductionPrice}，orderAmount 已是分），
 * 而 {@code erp_recovery_record.deducted_amount / remaining_amount} 与
 * {@code erp_recovery_deduction.deduction_amount} 都是 {@code decimal(10,2)} 的<b>元</b>。
 * 历史写法是「分 × 100」，金额被放大 10^4 倍（2000 元写成 20000000）。</p>
 *
 * <p>口径事实（本轮定稿）：{@code deducted = min(回收价值, 实际抵扣)}、
 * {@code remaining = 回收价值 - deducted}（恒 &gt;= 0），
 * {@code settlementType / settlementStatus} 由「是否还有剩余」决定，
 * 且 {@code erp_recovery_deduction.deduction_amount} 必须等于同一笔 {@code deducted}。</p>
 *
 * <p>断言的都是<b>真正传给 mapper 的入参实体</b>，不是中间变量。</p>
 */
class ShopOrderRecoveryDeductionUnitTest {

    /** 默认回收价值 5000 元 = 金重 10g × 金价 500 元/g。 */
    private static final BigDecimal GOLD_WEIGHT = new BigDecimal("10");
    private static final BigDecimal GOLD_PRICE = new BigDecimal("500");

    @BeforeAll
    static void initTableInfo() {
        // createOrder 内部用 LambdaQueryWrapper 查重订单号，Lambda 列名解析需要先注册表信息
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "recovery-unit-test"), ShopOrder.class);
    }

    @Test
    @DisplayName("算例 1：回收 5000 / 订单 2000 / 抵扣 3000 → 抵扣 2000、剩余 3000、type=3、status=1")
    void appliedAmountIsCappedAtOrderAmount() {
        // 抵扣 3000 > 订单金额 2000，本方法上文已把 shop_order.deduction_price 截到 2000 元
        var mappers = run(GOLD_WEIGHT, GOLD_PRICE, "2000", "3000");

        ErpRecoveryRecord record = mappers.insertedRecord();
        assertAmount("5000", record.getTotalAmount(), "回收价值保持 5000 元");
        assertAmount("2000", record.getDeductedAmount(), "deducted = min(5000, 2000)");
        assertAmount("3000", record.getRemainingAmount(), "remaining = 5000 - 2000");
        assertEquals(3, record.getSettlementType().intValue(), "还有剩余 → 混合");
        assertEquals(1, record.getSettlementStatus().intValue(), "还有剩余 → 未结清");
        assertEquals(1, record.getStatus().intValue());
        // 单位护栏：不得写成「分」（200000）或 10^4 放大值（20000000）
        assertNotEquals(0, record.getDeductedAmount().compareTo(new BigDecimal("200000")), "不得把「分」当「元」写入");
        assertNotEquals(0, record.getDeductedAmount().compareTo(new BigDecimal("20000000")), "不得出现 10^4 放大后的值");
        assertAmount("2000", mappers.insertedDeduction().getDeductionAmount(), "deduction_amount 必须与 deducted 一致");
    }

    @Test
    @DisplayName("算例 2：回收 5000 / 订单 2000 / 抵扣 1000 → 抵扣 1000、剩余 4000、type=3、status=1")
    void appliedBelowRecoveryValueIsRecordedAsIs() {
        // 行为变更：改前走全额分支，写成 5000 / 0 / type=1 / status=2（抵扣额虚高 4000 并把剩余清零）
        var mappers = run(GOLD_WEIGHT, GOLD_PRICE, "2000", "1000");

        ErpRecoveryRecord record = mappers.insertedRecord();
        assertAmount("1000", record.getDeductedAmount(), "deducted = min(5000, 1000)");
        assertAmount("4000", record.getRemainingAmount(), "remaining = 5000 - 1000");
        assertEquals(3, record.getSettlementType().intValue());
        assertEquals(1, record.getSettlementStatus().intValue(), "剩余 4000 待继续结算");
        assertAmount("1000", mappers.insertedDeduction().getDeductionAmount(), "deduction_amount 必须与 deducted 一致");
    }

    @Test
    @DisplayName("算例 3：回收 100 / 订单 2000 / 抵扣 3000 → 抵扣 100、剩余 0、type=1、status=2")
    void recoveryValueSmallerThanAppliedIsCappedAtRecoveryValue() {
        // 行为变更：改前混合分支把 deducted 记成订单金额 2000，remaining = 100 - 2000 = -1900（客户倒欠平台）
        var mappers = run(new BigDecimal("1"), new BigDecimal("100"), "2000", "3000");

        ErpRecoveryRecord record = mappers.insertedRecord();
        assertAmount("100", record.getTotalAmount(), "回收价值 1g × 100 元/g = 100 元");
        assertAmount("100", record.getDeductedAmount(), "抵扣不得超过回收价值");
        assertAmount("0", record.getRemainingAmount(), "remaining = 100 - 100 = 0");
        assertTrue(record.getRemainingAmount().signum() >= 0, "剩余金额恒不得为负");
        assertEquals(1, record.getSettlementType().intValue(), "无剩余 → 仅抵扣");
        assertEquals(2, record.getSettlementStatus().intValue(), "无剩余 → 已结清");
        assertAmount("100", mappers.insertedDeduction().getDeductionAmount(), "deduction_amount 必须与 deducted 一致");
    }

    @Test
    @DisplayName("抵扣金额恰好等于订单金额：按取小口径记实际抵扣，剩余继续结算")
    void deductionEqualToOrderAmountRecordsAppliedAmount() {
        // 原名 fullDeductionBranchKeepsCurrentSemantics；口径改为取小后行为变了，
        // 改前是 5000 / 0 / type=1 / status=2（按回收价值全额抵扣、剩余清零）
        var mappers = run(GOLD_WEIGHT, GOLD_PRICE, "2000", "2000");

        ErpRecoveryRecord record = mappers.insertedRecord();
        assertAmount("2000", record.getDeductedAmount(), "deducted = min(5000, 2000)");
        assertAmount("3000", record.getRemainingAmount(), "remaining = 5000 - 2000");
        assertEquals(3, record.getSettlementType().intValue());
        assertEquals(1, record.getSettlementStatus().intValue());
        assertAmount("2000", mappers.insertedDeduction().getDeductionAmount(), "deduction_amount 必须与 deducted 一致");
    }

    @Test
    @DisplayName("不变量：deducted + remaining == 回收价值，且 remaining 恒 >= 0")
    void deductedPlusRemainingAlwaysEqualsRecoveryValue() {
        BigDecimal[][] cases = {
                {GOLD_WEIGHT, GOLD_PRICE, new BigDecimal("2000"), new BigDecimal("3000")},
                {GOLD_WEIGHT, GOLD_PRICE, new BigDecimal("2000"), new BigDecimal("1000")},
                {new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("2000"), new BigDecimal("3000")},
                {GOLD_WEIGHT, GOLD_PRICE, new BigDecimal("2000"), new BigDecimal("2000")},
        };
        for (BigDecimal[] c : cases) {
            var mappers = run(c[0], c[1], c[2].toPlainString(), c[3].toPlainString());
            ErpRecoveryRecord record = mappers.insertedRecord();
            assertEquals(0, record.getDeductedAmount().add(record.getRemainingAmount())
                            .compareTo(record.getTotalAmount()),
                    "抵扣 + 剩余必须等于回收价值，实际：" + record.getDeductedAmount()
                            + " + " + record.getRemainingAmount() + " vs " + record.getTotalAmount());
            assertTrue(record.getRemainingAmount().signum() >= 0, "剩余金额不得为负");
            assertEquals(0, mappers.insertedDeduction().getDeductionAmount()
                    .compareTo(record.getDeductedAmount()), "两张表的抵扣金额必须相等");
        }
    }

    @Test
    @DisplayName("店铺 id 超出 Long 缓存区间时同一店铺仍可正常下单（Long 值比较回归护栏）")
    void sameShopOutsideLongCacheStillCreatesOrder() {
        // 1000 超出 Long 缓存区间（-128~127）：shop.setId(1000L) 与 sku.setShopId(1000L) 各自装箱，
        // 得到两个不同引用的 Long 实例。修复前这里用 `!=` 做引用比较，必然误判为「不属于同一店铺」，
        // 于是店铺 id > 127 的订单创建整体不可用（「上线到第 128 家店才炸」）。
        var mappers = new Mappers(1000L, 1000L);
        assertNotSame(mappers.shop.getId(), mappers.sku.getShopId(),
                "用例前提：两个 Long 必须是不同实例，否则这条护栏会假绿");

        var result = mappers.call(order("2000", "3000", GOLD_WEIGHT, GOLD_PRICE));

        assertEquals(0, result.getCode(), "同一店铺（id=1000）必须能创建订单，实际返回：" + result.getMsg());
        assertNotNull(result.getData(), "订单应创建成功并返回主键");
        assertAmount("2000", mappers.insertedRecord().getDeductedAmount(), "金额换算不受影响");
    }

    @Test
    @DisplayName("不同店铺的商品仍然被拦下（证明校验没有被删掉）")
    void productFromAnotherShopIsStillRejected() {
        // 店铺 1000 与商品所属店铺 2000，同样都在 Long 缓存区间之外
        var mappers = new Mappers(1000L, 2000L);

        var result = mappers.call(order("2000", "3000", GOLD_WEIGHT, GOLD_PRICE));

        assertNotEquals(0, result.getCode(), "不同店铺的商品必须被拒绝");
        assertEquals("店铺商品不属于你", result.getMsg(), "拒绝原因保持不变");
        verify(mappers.recoveryRecordMapper, never()).insert(any(ErpRecoveryRecord.class));
        verify(mappers.baseMapper, never()).insert(any(ShopOrder.class));
    }

    // ------------------------------------------------------------------

    /** 跑一次 createOrder 并返回可回读入参的 fixture；顺带断言建单成功。 */
    private Mappers run(BigDecimal goldWeight, BigDecimal goldPrice, String orderYuan, String deductionYuan) {
        var mappers = new Mappers();
        var result = mappers.call(order(orderYuan, deductionYuan, goldWeight, goldPrice));
        assertEquals(0, result.getCode(), "订单必须创建成功：" + result.getMsg());
        return mappers;
    }

    private void assertAmount(String expected, BigDecimal actual, String message) {
        assertNotNull(actual, message + "（实际为 null）");
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                message + "，实际：" + actual.toPlainString());
    }

    private ShopOrderCreateBo order(String orderYuan, String deductionYuan,
                                    BigDecimal goldWeight, BigDecimal goldPrice) {
        var goodsAmount = new BigDecimal(orderYuan);
        var bo = new ShopOrderCreateBo();
        bo.setOrderNum("RECOVERY-" + System.nanoTime());
        bo.setShopId(10L);
        bo.setGoodsAmount(goodsAmount);
        bo.setPostage(BigDecimal.ZERO);
        bo.setDeductionPrice(new BigDecimal(deductionYuan));
        bo.setIsRecoveryDeduction(1);
        bo.setGoldWeight(goldWeight);
        bo.setGoldPrice(goldPrice);
        bo.setSilverWeight(BigDecimal.ZERO);
        bo.setSilverPrice(BigDecimal.ZERO);
        bo.setDeductionRemark("回收抵扣单位验证");
        bo.setShopMemberId(0L);
        bo.setReceiverName("张三");
        bo.setReceiverPhone("13800000000");
        bo.setProvince("浙江省");
        bo.setCity("杭州市");
        bo.setTown("西湖区");
        bo.setAddress("文三路 1 号");

        var item = new ShopOrderCreateItemBo();
        item.setId("101");
        item.setQuantity(1);
        item.setItemAmount(goodsAmount);
        item.setIsGift(0);
        bo.setItemList(List.of(item));
        return bo;
    }

    /** 13 个依赖全 Mock 的 service，外加 baseMapper（继承字段，需反射注入）。 */
    private static final class Mappers {
        final ShopOrderMapper baseMapper = mock(ShopOrderMapper.class);
        final ShopMemberMapper shopMemberMapper = mock(ShopMemberMapper.class);
        final SystemFacade shopFacade = mock(SystemFacade.class);
        final SystemFacade merchantFacade = mock(SystemFacade.class);
        final ProductFacade skuFacade = mock(ProductFacade.class);
        final ErpRecoveryRecordMapper recoveryRecordMapper = mock(ErpRecoveryRecordMapper.class);
        final ErpRecoveryDeductionMapper recoveryDeductionMapper = mock(ErpRecoveryDeductionMapper.class);
        final ShopOrderServiceImpl service;
        final OShop shop;
        final ShopGoodsSku sku;

        Mappers() {
            this(10L, 10L);
        }

        /**
         * @param shopId    店铺主键
         * @param skuShopId 商品所属店铺主键（用于验证两者的比较方式）
         */
        Mappers(long shopId, long skuShopId) {
            shop = new OShop();
            // 注意：下面两次 long→Long 装箱各自独立。id 超过 127 时（不在 Long 缓存区间内）
            // 得到的一定是两个不同引用的 Long 实例，正好用来暴露 `!=` 引用比较的缺陷。
            shop.setId(shopId);
            shop.setMerchantId(5L);
            shop.setType(999);
            shop.setName("测试店铺");
            when(shopFacade.getOShopById(any())).thenReturn(shop);

            sku = new ShopGoodsSku();
            sku.setShopId(skuShopId);
            sku.setPrice(200000);
            sku.setProductId("P-1");
            sku.setSkuId("S-1");
            when(skuFacade.getShopGoodsSkuById(any())).thenReturn(sku);

            ErpMerchant merchant = new ErpMerchant();
            merchant.setName("测试商户");
            when(merchantFacade.getErpMerchantById(any())).thenReturn(merchant);

            when(shopMemberMapper.insert(any(ShopMember.class))).thenAnswer(call -> {
                ShopMember member = call.getArgument(0);
                member.setId(7L);
                return 1;
            });
            when(baseMapper.selectList(any())).thenReturn(List.of());
            when(baseMapper.insert(any(ShopOrder.class))).thenAnswer(call -> {
                ShopOrder saved = call.getArgument(0);
                saved.setId(99L);
                return 1;
            });

            service = new ShopOrderServiceImpl(
                    mock(OOrderMapper.class),
                    mock(InventoryFacade.class),
                    mock(ShopOrderItemMapper.class),
                    mock(ShopGoodsSkuShipItemFacade.class),
                    skuFacade,
                    mock(ProductFacade.class),
                    shopMemberMapper,
                    shopFacade,
                    merchantFacade,
                    mock(OMarketingDiscountRuleMapper.class),
                    mock(ShopOrderPromotionMapper.class),
                    recoveryRecordMapper,
                    recoveryDeductionMapper);
            injectBaseMapper(service, baseMapper);
        }

        cn.xinghuaerp.common.ResultVo<Long> call(ShopOrderCreateBo order) {
            return service.createOrder(order, "tester");
        }

        ErpRecoveryRecord insertedRecord() {
            ArgumentCaptor<ErpRecoveryRecord> captor = ArgumentCaptor.forClass(ErpRecoveryRecord.class);
            verify(recoveryRecordMapper).insert(captor.capture());
            return captor.getValue();
        }

        ErpRecoveryDeduction insertedDeduction() {
            ArgumentCaptor<ErpRecoveryDeduction> captor = ArgumentCaptor.forClass(ErpRecoveryDeduction.class);
            verify(recoveryDeductionMapper).insert(captor.capture());
            return captor.getValue();
        }
    }

    private static void injectBaseMapper(ShopOrderServiceImpl service, ShopOrderMapper mapper) {
        for (Class<?> type = service.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField("baseMapper");
                field.setAccessible(true);
                field.set(service, mapper);
                return;
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("baseMapper 注入失败", failure);
            }
        }
        throw new IllegalStateException("未找到 baseMapper 字段");
    }
}
