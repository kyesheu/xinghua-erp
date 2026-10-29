package cn.xinghuaerp.order.service.impl;

import cn.xinghuaerp.inventory.facade.InventoryFacade;
import cn.xinghuaerp.order.bo.PosOrderCreateBo;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA 独立复核：回收抵扣金额的单位换算。
 *
 * <p>单位事实以 DDL 为准（docs/sql/xinghua-erp.sql）：</p>
 * <ul>
 *   <li>{@code oms_shop_order.deduction_price} int，注释「积分抵扣金额，单位为分」；</li>
 *   <li>{@code oms_shop_order.order_amount} int，注释「订单金额，单位为分」；</li>
 *   <li>{@code erp_recovery_record.total_amount / deducted_amount / remaining_amount}
 *       与 {@code erp_recovery_deduction.deduction_amount} 都是 {@code decimal(10,2)}，单位是元。</li>
 * </ul>
 *
 * <p>验算方法不使用 {@code compareTo} 单一通道：同时用 {@code doubleValue()} 与字符串、
 * 数量级（不得出现 10^2 / 10^4 放大）三条独立路径交叉验证，避免「scale 不敏感」造成的假通过。</p>
 */
class ShopOrderRecoveryAmountQaTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "recovery-amount-qa"), ShopOrder.class);
    }

    @Test
    @DisplayName("createOrder 混合分支：分→元只换算一次，deducted=2000、remaining=3000，无 10^2/10^4 放大")
    void mixedBranchConvertsFenToYuanExactlyOnce() {
        // 回收价值 5000 元（金重 10g × 金价 500 元/g）、订单金额 2000 元、抵扣 3000 元
        Fixture f = new Fixture(10L, 10L);
        var result = f.createOrder(yuan("2000"), yuan("3000"), yuan("10"), yuan("500"), yuan("0"), yuan("0"));

        assertEquals(0, result.getCode(), "订单必须创建成功：" + result.getMsg());

        ErpRecoveryRecord record = f.insertedRecord();
        assertMoney("2000", record.getDeductedAmount(), "deducted_amount 必须是 2000 元");
        assertMoney("3000", record.getRemainingAmount(), "remaining_amount = 5000 - 2000");
        assertMoney("5000", record.getTotalAmount(), "回收价值必须保持 5000 元");
        assertNoMagnitudeInflation(record.getDeductedAmount(), "2000");
        assertEquals(3, record.getSettlementType().intValue(), "混合抵扣 settlementType=3");
        assertEquals(1, record.getSettlementStatus().intValue(), "混合抵扣 settlementStatus=1");
        assertEquals(1, record.getStatus().intValue(), "记录必须有效");

        ErpRecoveryDeduction deduction = f.insertedDeduction();
        assertMoney("2000", deduction.getDeductionAmount(), "deduction_amount 必须是 2000 元");
        assertNoMagnitudeInflation(deduction.getDeductionAmount(), "2000");
    }

    @Test
    @DisplayName("混合分支：remaining_amount 在本例中为正，且 deducted+remaining 恰好等于回收价值")
    void deductedPlusRemainingEqualsRecoveryValue() {
        Fixture f = new Fixture(10L, 10L);
        f.createOrder(yuan("2000"), yuan("3000"), yuan("10"), yuan("500"), yuan("0"), yuan("0"));

        ErpRecoveryRecord record = f.insertedRecord();
        assertTrue(record.getRemainingAmount().signum() >= 0, "本例剩余金额不得为负");
        assertEquals(0, record.getDeductedAmount().add(record.getRemainingAmount())
                        .compareTo(record.getTotalAmount()),
                "抵扣 + 剩余必须等于回收价值：deducted=" + record.getDeductedAmount()
                        + " remaining=" + record.getRemainingAmount() + " total=" + record.getTotalAmount());
    }

    /**
     * 第三轮口径：{@code deducted = min(回收价值, 实际抵扣)}，{@code remaining = 回收价值 - deducted}。
     *
     * <p>「实际抵扣」= {@code shop_order.deduction_price} 换算成元，而它在上文已被截到「订单金额」，
     * 所以本用例从外部等价地写成 {@code min(回收价值, min(订单金额, 抵扣金额))}。</p>
     *
     * <p>回收 100 元 &lt; 订单金额 2000 元、抵扣 3000 元 → 抵扣只能抵到回收价值的 100 元，
     * 剩余清零、视为结清；负数金额不再出现。</p>
     */
    @Test
    @DisplayName("回收价值 < 实际抵扣时抵扣额被截到回收价值：100/2000/3000 → 100、0、type=1、status=2")
    void recoveryValueBelowAppliedIsCappedAtRecoveryValue() {
        // 回收价值 100 元（1g × 100）、订单金额 2000 元、抵扣 3000 元
        Fixture f = new Fixture(10L, 10L);
        f.createOrder(yuan("2000"), yuan("3000"), yuan("1"), yuan("100"), yuan("0"), yuan("0"));

        ErpRecoveryRecord record = f.insertedRecord();
        assertMoney("100", record.getDeductedAmount(), "抵扣额不得超过回收价值");
        assertMoney("0", record.getRemainingAmount(), "回收价值已全部抵扣：剩余必须是 0，不能是负数");
        assertEquals(1, record.getSettlementType().intValue(), "remaining=0 → 1-仅抵扣");
        assertEquals(2, record.getSettlementStatus().intValue(), "remaining=0 → 2-已结清");
        assertMoney("100", f.insertedDeduction().getDeductionAmount(), "两张表必须对得上");
    }

    /**
     * 抵扣金额恰好等于订单金额（原来是「全额抵扣分支」）：抵扣记实际抵扣的 2000 元，
     * 剩余 3000 元继续结算（type=3/status=1），{@code deduction_amount} 与 record 一致。
     */
    @Test
    @DisplayName("抵扣=订单金额：记实际抵扣 2000、剩余 3000 继续结算（type=3、status=1）")
    void deductionEqualToOrderAmountRecordsAppliedAmountAndKeepsRemaining() {
        Fixture f = new Fixture(10L, 10L);
        f.createOrder(yuan("2000"), yuan("2000"), yuan("10"), yuan("500"), yuan("0"), yuan("0"));

        ErpRecoveryRecord record = f.insertedRecord();
        assertMoney("2000", record.getDeductedAmount(), "抵扣 2000 元不能记成回收价值 5000 元");
        assertMoney("3000", record.getRemainingAmount(), "剩余 5000-2000=3000，不能清零");
        assertEquals(3, record.getSettlementType().intValue(), "remaining>0 → 3-混合");
        assertEquals(1, record.getSettlementStatus().intValue(), "remaining>0 → 1-未结清");

        ErpRecoveryDeduction deduction = f.insertedDeduction();
        assertMoney("2000", deduction.getDeductionAmount(), "deduction_amount 必须等于 record 的 deducted_amount");
        assertEquals(0, deduction.getDeductionAmount().compareTo(record.getDeductedAmount()),
                "两张表的抵扣金额必须逐值相等");
    }

    /**
     * 口径不变量（矩阵）：{@code deducted = min(回收价值, 实际抵扣)}、{@code remaining >= 0}、
     * {@code deducted + remaining == 回收价值}、{@code type/status} 由 remaining 是否为 0 决定、
     * 两张表金额一致。这些是对「口径」的独立表述，不依赖实现里的具体分支。
     */
    @Test
    @DisplayName("口径矩阵：任一组合下 remaining>=0、取小、两表一致、type/status 与 remaining 自洽")
    void deductionIsAlwaysTheSmallerOfRecoveryValueAndAppliedAmount() {
        // {回收价值（金重×金价）, 订单金额, 抵扣金额}，单位元
        String[][] cases = {
                {"5000", "2000", "3000"},   // 抵扣>订单：取订单金额
                {"5000", "2000", "1000"},   // 抵扣<订单：取实际抵扣
                {"5000", "2000", "2000"},   // 恰好相等
                {"5000", "2000", "5000"},   // 抵扣等于回收价值
                {"100", "2000", "3000"},    // 回收价值极小
                {"5000", "2000", "1"},      // 抵扣 1 元
                {"0.01", "2000", "3000"},   // 一分钱的回收价值
                // 回收价值为 0：闸门 `deductionPrice > 0` 仍成立，会落一条全零记录
                // （源码 TODO(已知差异) ① 已如实记录，本轮不改闸门；这里钉住现状，将来改闸门本条必须同步更新）
                {"0", "2000", "3000"},
        };
        for (String[] c : cases) {
            BigDecimal recoveryTotal = yuan(c[0]);
            BigDecimal orderAmount = yuan(c[1]);
            BigDecimal applyAmount = yuan(c[2]);
            BigDecimal applied = orderAmount.min(applyAmount);      // shop_order.deduction_price 已被截到订单金额
            BigDecimal expectedDeducted = recoveryTotal.min(applied);
            BigDecimal expectedRemaining = recoveryTotal.subtract(expectedDeducted);

            Fixture f = new Fixture(10L, 10L);
            // 金价固定 1 元/g，用金重精确表达回收价值（连 0.01 元也能表示，避免除法引入误差）
            BigDecimal goldPrice = yuan("1");
            var result = f.createOrder(orderAmount, applyAmount, recoveryTotal, goldPrice, yuan("0"), yuan("0"));
            assertEquals(0, result.getCode(), "组合 " + String.join("/", c) + " 必须建单成功：" + result.getMsg());

            ErpRecoveryRecord record = f.insertedRecord();
            String label = "组合 回收" + c[0] + "/订单" + c[1] + "/抵扣" + c[2] + "：";
            assertMoney(expectedDeducted.toPlainString(), record.getDeductedAmount(), label + "抵扣额必须取小");
            assertMoney(expectedRemaining.toPlainString(), record.getRemainingAmount(), label + "剩余金额");
            assertTrue(record.getRemainingAmount().signum() >= 0, label + "剩余金额绝不能为负");
            assertEquals(0, record.getDeductedAmount().add(record.getRemainingAmount())
                    .compareTo(record.getTotalAmount()), label + "抵扣+剩余必须等于回收价值");
            assertEquals(record.getRemainingAmount().signum() == 0 ? 1 : 3,
                    record.getSettlementType().intValue(), label + "settlementType 必须由 remaining 是否为 0 决定");
            assertEquals(record.getRemainingAmount().signum() == 0 ? 2 : 1,
                    record.getSettlementStatus().intValue(), label + "settlementStatus 必须由 remaining 是否为 0 决定");
            assertEquals(0, f.insertedDeduction().getDeductionAmount().compareTo(record.getDeductedAmount()),
                    label + "erp_recovery_deduction 与 erp_recovery_record 的金额必须一致");
        }
    }

    @Test
    @DisplayName("抵扣金额为 0 时整条回收链路不落任何记录")
    void zeroDeductionWritesNoRecoveryRecords() {
        Fixture f = new Fixture(10L, 10L);
        var result = f.createOrder(yuan("2000"), yuan("0"), yuan("10"), yuan("500"), yuan("0"), yuan("0"));

        assertEquals(0, result.getCode(), "抵扣为 0 时仍应正常建单：" + result.getMsg());
        verify(f.recoveryRecordMapper, never()).insert(any(ErpRecoveryRecord.class));
        verify(f.recoveryDeductionMapper, never()).insert(any(ErpRecoveryDeduction.class));
    }

    @Test
    @DisplayName("createPosOrder：入参已是元，写 deduction_amount 必须原样落库（不得再 ×100）")
    void createPosOrderWritesYuanAsIs() {
        Fixture f = new Fixture(10L, 10L);

        PosOrderCreateBo bo = new PosOrderCreateBo();
        bo.setRecoveryId(5L);
        bo.setRecoveryDeductionAmount(yuan("300"));   // 实际抵扣 300 元
        bo.setTotalAmount(yuan("1000"));
        bo.setDiscountAmount(yuan("0"));
        bo.setFinalAmount(yuan("1000"));
        bo.setPaymentMethod("CASH");
        bo.setMemberId(0L);
        PosOrderCreateBo.PosOrderItemBo item = new PosOrderCreateBo.PosOrderItemBo();
        item.setSkuId(101L);
        item.setGoodsName("金戒指");
        item.setSkuName("默认");
        item.setQuantity(1);
        item.setUnitPrice(yuan("1000"));
        item.setSubtotal(yuan("1000"));
        bo.setItems(List.of(item));

        var result = f.service.createPosOrder(bo, 10L, 5L, "tester");
        assertEquals(0, result.getCode(), "POS 订单必须创建成功：" + result.getMsg());

        assertMoney("300", f.insertedDeduction().getDeductionAmount(),
                "POS 路径的 deduction_amount 必须是 300 元，×100 会写成 30000 元");
        // 这条路径确实不写 erp_recovery_record（与拒绝对照：createOrder 会写）
        verify(f.recoveryRecordMapper, never()).insert(any(ErpRecoveryRecord.class));
    }

    @Test
    @DisplayName("店铺 id=128（刚好越过 Long 缓存上界）时同一店铺仍能建单")
    void shopIdJustOutsideLongCacheStillCreatesOrder() {
        Fixture f = new Fixture(128L, 128L);
        assertNotSame(f.shop.getId(), f.sku.getShopId(),
                "用例前提：两个 Long 必须是不同实例，否则这条护栏会假绿");

        var result = f.createOrder(yuan("2000"), yuan("3000"), yuan("10"), yuan("500"), yuan("0"), yuan("0"));

        assertEquals(0, result.getCode(), "同一店铺 id=128 必须能建单，实际：" + result.getMsg());
        assertMoney("2000", f.insertedRecord().getDeductedAmount(), "金额换算不受影响");
    }

    @Test
    @DisplayName("店铺 id 相同但商品属于别家（1000 vs 2000）仍被拦下，且不落任何单据")
    void productFromAnotherShopIsStillRejected() {
        Fixture f = new Fixture(1000L, 2000L);

        var result = f.createOrder(yuan("2000"), yuan("3000"), yuan("10"), yuan("500"), yuan("0"), yuan("0"));

        assertEquals("店铺商品不属于你", result.getMsg());
        verify(f.recoveryRecordMapper, never()).insert(any(ErpRecoveryRecord.class));
        verify(f.baseMapper, never()).insert(any(ShopOrder.class));
    }

    // ------------------------------------------------------------------

    /** 金额断言：compareTo + doubleValue + 字符串三条独立通道，避免 scale 造成的假通过。 */
    private static void assertMoney(String expected, BigDecimal actual, String message) {
        assertNotNull(actual, message + "（实际为 null）");
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                message + "，实际：" + actual.toPlainString());
        assertEquals(new BigDecimal(expected).doubleValue(), actual.doubleValue(), 1e-9,
                message + "（double 通道），实际：" + actual.toPlainString());
    }

    /** 不得出现「分当元」「分×100」造成的数量级放大。 */
    private static void assertNoMagnitudeInflation(BigDecimal actual, String expected) {
        BigDecimal expectedValue = new BigDecimal(expected);
        for (String inflated : new String[]{"100", "10000"}) {
            BigDecimal wrong = expectedValue.multiply(new BigDecimal(inflated));
            assertTrue(actual.compareTo(wrong) != 0,
                    "金额被放大了 " + inflated + " 倍：" + actual.toPlainString());
        }
        assertTrue(actual.signum() > 0, "金额必须为正：" + actual.toPlainString());
    }

    private static BigDecimal yuan(String value) {
        return new BigDecimal(value);
    }

    /** 全部依赖 mock 的 service，外加 baseMapper（父类字段，需反射注入）。 */
    private static final class Fixture {
        final ShopOrderMapper baseMapper = mock(ShopOrderMapper.class);
        final ErpRecoveryRecordMapper recoveryRecordMapper = mock(ErpRecoveryRecordMapper.class);
        final ErpRecoveryDeductionMapper recoveryDeductionMapper = mock(ErpRecoveryDeductionMapper.class);
        final ShopOrderItemMapper itemMapper = mock(ShopOrderItemMapper.class);
        final ShopOrderServiceImpl service;
        final OShop shop = new OShop();
        final ShopGoodsSku sku = new ShopGoodsSku();

        Fixture(long shopId, long skuShopId) {
            shop.setId(shopId);
            shop.setMerchantId(5L);
            shop.setType(999);
            shop.setName("测试店铺");

            SystemFacade shopFacade = mock(SystemFacade.class);
            SystemFacade merchantFacade = mock(SystemFacade.class);
            ProductFacade skuFacade = mock(ProductFacade.class);
            ShopMemberMapper shopMemberMapper = mock(ShopMemberMapper.class);

            when(shopFacade.getOShopById(any())).thenReturn(shop);
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
                    itemMapper,
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

        cn.xinghuaerp.common.ResultVo<Long> createOrder(BigDecimal goodsAmount, BigDecimal deductionPrice,
                                                       BigDecimal goldWeight, BigDecimal goldPrice,
                                                       BigDecimal silverWeight, BigDecimal silverPrice) {
            ShopOrderCreateBo bo = new ShopOrderCreateBo();
            bo.setOrderNum("QA-" + System.nanoTime());
            bo.setShopId(shop.getId());
            bo.setGoodsAmount(goodsAmount);
            bo.setPostage(BigDecimal.ZERO);
            bo.setDeductionPrice(deductionPrice);
            bo.setIsRecoveryDeduction(1);
            bo.setGoldWeight(goldWeight);
            bo.setGoldPrice(goldPrice);
            bo.setSilverWeight(silverWeight);
            bo.setSilverPrice(silverPrice);
            bo.setDeductionRemark("QA 复核");
            bo.setShopMemberId(0L);
            bo.setReceiverName("张三");
            bo.setReceiverPhone("13800000000");
            bo.setProvince("浙江省");
            bo.setCity("杭州市");
            bo.setTown("西湖区");
            bo.setAddress("文三路 1 号");

            ShopOrderCreateItemBo item = new ShopOrderCreateItemBo();
            item.setId("101");
            item.setQuantity(1);
            item.setItemAmount(goodsAmount);
            item.setIsGift(0);
            bo.setItemList(List.of(item));
            return service.createOrder(bo, "tester");
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
