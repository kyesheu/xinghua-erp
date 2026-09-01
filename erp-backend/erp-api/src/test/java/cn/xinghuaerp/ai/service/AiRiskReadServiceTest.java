package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import cn.xinghuaerp.ai.service.AiFinanceReadService.Overview;
import cn.xinghuaerp.ai.service.AiMarketingReadService.Finding;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

/** 售后风险只读统计的口径、状态隔离与发现项顺序测试。 */
class AiRiskReadServiceTest {
    private JdbcTemplate db;
    private AiRiskReadService service;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(dataSource);
        db.execute("CREATE TABLE o_refund("
                + "id BIGINT,refund_num VARCHAR(50),order_num VARCHAR(50),order_item_num VARCHAR(50),"
                + "merchant_id BIGINT,shop_id BIGINT,refund_type INT,refund_fee DECIMAL(10,2),order_amount DECIMAL(10,2),"
                + "has_good_return INT,return_logistics_code VARCHAR(100),status INT,erp_status INT,create_time TIMESTAMP)");
        service = new AiRiskReadService(db);
    }

    @AfterEach
    void tearDown() {
        db.execute("SHUTDOWN");
    }

    @Test
    void scopeIsolationMatchesRefundOwner() {
        var now = LocalDateTime.now();
        insert("SO1", "IT1", 10, 100, 50, 100, 0, "SF1", 10001, 1, now);
        insert("SO2", "IT2", 10, 101, 50, 100, 0, "SF1", 10001, 1, now);
        insert("SO3", "IT3", 20, 200, 50, 100, 0, "SF1", 10001, 1, now);

        var headquarters = service.overview(new BusinessSubjectScopeService.Scope("HEADQUARTERS", null, null, null));
        assertEquals(3, headquarters.total());
        assertEquals("总部", headquarters.scope());
        assertTrue(headquarters.findings().isEmpty());

        var merchant = service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 10L, 10L, null));
        assertEquals(2, merchant.total());
        assertEquals("绑定商户", merchant.scope());

        var shop = service.overview(new BusinessSubjectScopeService.Scope("SHOP", 100L, 10L, 100L));
        assertEquals(1, shop.total());
        assertEquals("绑定店铺", shop.scope());

        assertThrows(IllegalArgumentException.class,
                () -> service.overview(new BusinessSubjectScopeService.Scope("WAREHOUSE", 1L, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.overview(new BusinessSubjectScopeService.Scope("SUPPLIER", 1L, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.overview(null));
    }

    @Test
    void openRefundExcludesClosedRejectedAndFinished() {
        var now = LocalDateTime.now();
        insert("SO1", "IT1", 10, 100, 50, 100, 0, "SF1", 14000, 1, now);
        insert("SO2", "IT2", 10, 100, 50, 100, 0, "SF1", 10011, 1, now);
        insert("SO3", "IT3", 10, 100, 50, 100, 0, "SF1", 10010, 1, now);
        insert("SO4", "IT4", 10, 100, 50, 100, 0, "SF1", 10001, 1, now);
        var overview = overview();
        assertEquals(4, overview.total());
        assertEquals(1, overview.open(), "拒绝/关闭/完成均不计入未关闭");
    }

    /**
     * 本地售后词表（0 待处理 / 1 已关闭 / 4 已办结）同样适用终态口径。
     *
     * <p>{@code o_refund.status} 混用两套词表：平台记录用 10001–10094，本地售后
     * （{@code LocalAfterSaleService}，{@code refund_num} 形如 {@code LOCAL-AF-…}）用 0/1/4。
     * 三条记录同订单同明细，只有 status=0 待处理算未关闭，因此重复判断也必须为 0。</p>
     */
    @Test
    void localAfterSaleTerminalStatusesExcludedFromOpen() {
        var now = LocalDateTime.now();
        insert("LOCAL-AF-ORDER", "IT1", 10, 100, 10, 100, 0, "SF1", 0, 1, now);
        insert("LOCAL-AF-ORDER", "IT1", 10, 100, 10, 100, 0, "SF1", 1, 1, now);
        insert("LOCAL-AF-ORDER", "IT1", 10, 100, 10, 100, 0, "SF1", 4, 1, now);

        var overview = overview();
        assertEquals(3, overview.total(), "三条本地售后均计入总量");
        assertEquals(1, overview.open(), "仅 status=0 待处理算未关闭；1 已关闭、4 已办结均为终态");
        assertEquals(0, count(overview, "DUPLICATE_OPEN"),
                "终态本地售后不参与重复未关闭判断，不构成与待处理记录的重复");
    }

    /**
     * 本地词表下「已关闭」不追退货单号，「已办结」仍要追。
     *
     * <p>本地 1 已关闭属流程终止且未完成，与平台 10011/14000 同侧；本地 4 已办结走
     * 「退货入库确认后办结」，若仍缺退货单号恰恰是「货没回却已办结」，必须检出。</p>
     */
    @Test
    void localClosedSkipsReturnLogisticsButFinishedStillChecked() {
        var now = LocalDateTime.now();
        long closed = insert("LOCAL-AF-C", "IT1", 10, 100, 10, 100, 1, "SF1", 1, 1, now);
        clearColumn(closed, "return_logistics_code");
        long finished = insert("LOCAL-AF-F", "IT2", 10, 100, 10, 100, 1, "SF1", 4, 1, now);
        clearColumn(finished, "return_logistics_code");

        assertEquals(1, count(overview(), "MISSING_RETURN_LOGISTICS"),
                "只报 status=4 已办结那条；status=1 已关闭不再追退货单号");
    }

    /** 售后成功/失败、维修关闭/成功与拒绝/关闭/完成同属终态，不得计入未关闭或重复未关闭。 */
    @Test
    void terminalStatusesExcludedFromOpenAndDuplicate() {
        var now = LocalDateTime.now();
        insert("ST1", "IT1", 10, 100, 10, 100, 0, "SF1", 10020, 1, now);
        insert("ST1", "IT1", 10, 100, 10, 100, 0, "SF1", 10021, 1, now);
        insert("ST1", "IT1", 10, 100, 10, 100, 0, "SF1", 10093, 1, now);
        insert("ST1", "IT1", 10, 100, 10, 100, 0, "SF1", 10094, 1, now);

        var overview = overview();
        assertEquals(4, overview.total(), "终态记录仍计入总量");
        assertEquals(0, overview.open(), "售后成功/失败、维修关闭/成功均为终态，不计入未关闭");
        assertEquals(0, count(overview, "DUPLICATE_OPEN"), "终态记录不参与重复未关闭判断");
    }

    /** 换货成功/换货失败同属终态；退款中（10090）仍是进行中，必须计入未关闭。 */
    @Test
    void exchangeStatusesAreTerminalButRefundingIsNot() {
        var now = LocalDateTime.now();
        insert("SX1", "IT1", 10, 100, 10, 100, 0, "SF1", 10091, 1, now);
        insert("SX1", "IT1", 10, 100, 10, 100, 0, "SF1", 10092, 1, now);
        insert("SX2", "IT2", 10, 100, 10, 100, 0, "SF1", 10090, 1, now);

        var overview = overview();
        assertEquals(3, overview.total(), "终态与进行中记录都计入总量");
        assertEquals(1, overview.open(), "仅有退款中计未关闭；换货成功/失败为终态");
        assertEquals(0, count(overview, "DUPLICATE_OPEN"), "换货终态记录不参与重复未关闭判断");
    }

    /** 换货成功=货已处理但退货可能没回，必须提示；换货失败=流程终止未完成，不再追单号。 */
    @Test
    void exchangeResultDecidesReturnLogisticsCheck() {
        var now = LocalDateTime.now();
        long exchanged = insert("SX1", "IT1", 10, 100, 10, 100, 1, "SF1", 10091, 1, now);
        clearColumn(exchanged, "return_logistics_code");
        long exchangeFailed = insert("SX2", "IT2", 10, 100, 10, 100, 1, "SF1", 10092, 1, now);
        clearColumn(exchangeFailed, "return_logistics_code");
        assertEquals(1, count(overview(), "MISSING_RETURN_LOGISTICS"),
                "换货成功需追退货单号，换货失败属流程终止且未完成");
    }

    /** 售后成功/维修成功/完成：钱已退但货可能没回，必须继续提示缺失退货单号。 */
    @Test
    void missingReturnLogisticsStillFlagsSuccessfulRefunds() {
        var now = LocalDateTime.now();
        long success = insert("SO1", "IT1", 10, 100, 10, 100, 1, "SF1", 10020, 1, now);
        clearColumn(success, "return_logistics_code");
        long repairSuccess = insert("SO2", "IT2", 10, 100, 10, 100, 1, "SF1", 10094, 1, now);
        clearColumn(repairSuccess, "return_logistics_code");
        long finished = insert("SO3", "IT3", 10, 100, 10, 100, 1, "SF1", 10010, 1, now);
        clearColumn(finished, "return_logistics_code");
        assertEquals(3, count(overview(), "MISSING_RETURN_LOGISTICS"),
                "这三条虽是终态，但属于已退款却无退货单号，必须报警");
    }

    /** 拒绝/关闭/售后失败/维修关闭：流程终止且未完成，不再追退货单号。 */
    @Test
    void missingReturnLogisticsSkipsTerminatedUnfinishedRefunds() {
        var now = LocalDateTime.now();
        long rejected = insert("SO1", "IT1", 10, 100, 10, 100, 1, "SF1", 14000, 1, now);
        clearColumn(rejected, "return_logistics_code");
        long closed = insert("SO2", "IT2", 10, 100, 10, 100, 1, "SF1", 10011, 1, now);
        clearColumn(closed, "return_logistics_code");
        long failed = insert("SO3", "IT3", 10, 100, 10, 100, 1, "SF1", 10021, 1, now);
        clearColumn(failed, "return_logistics_code");
        long repairClosed = insert("SO4", "IT4", 10, 100, 10, 100, 1, "SF1", 10093, 1, now);
        clearColumn(repairClosed, "return_logistics_code");
        assertEquals(0, count(overview(), "MISSING_RETURN_LOGISTICS"),
                "流程终止且未完成的四种状态不应再提示退货单号；发现项应整体缺席");
        assertTrue(overview().findings().stream()
                        .noneMatch(finding -> finding.code().equals("MISSING_RETURN_LOGISTICS")),
                "count=0 的发现项不应出现在输出列表中");
    }

    @Test
    void refundAmountOverOrderAmount() {
        var now = LocalDateTime.now();
        insert("SO1", "IT1", 10, 100, 100, 100, 0, "SF1", 10001, 1, now);
        insert("SO2", "IT2", 10, 100, 100.01, 100, 0, "SF1", 10001, 1, now);
        long noOrderAmount = insert("SO3", "IT3", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        clearColumn(noOrderAmount, "order_amount");
        assertEquals(1, count(overview(), "AMOUNT_OVER_ORDER"), "等于订单金额不计，超过才计；订单金额为空不计");
    }

    /** 订单金额为 0 时任何正退款都需核实；订单金额缺失不判定；零值退款不与 AMOUNT_INVALID 重复报。 */
    @Test
    void refundOverZeroOrderAmountIsFlagged() {
        var now = LocalDateTime.now();
        insert("SO1", "IT1", 10, 100, 100, 0, 0, "SF1", 10001, 1, now);
        long missing = insert("SO2", "IT2", 10, 100, 100, 100, 0, "SF1", 10001, 1, now);
        clearColumn(missing, "order_amount");
        insert("SO3", "IT3", 10, 100, 0, 0, 0, "SF1", 10001, 1, now);
        insert("SO4", "IT4", 10, 100, 0, -5, 0, "SF1", 10001, 1, now);
        assertEquals(1, count(overview(), "AMOUNT_OVER_ORDER"),
                "0 元订单 + 正退款计入；订单金额缺失不判定；零退款（含负订单金额场景）不在此报");
        assertEquals(2, count(overview(), "AMOUNT_INVALID"),
                "零退款与负订单金额下的零退款交由 AMOUNT_INVALID 覆盖，避免重复报同一记录");
    }

    /**
     * 锁定「订单金额缺失但退款金额正常」的有意静默。
     *
     * <p>order_amount 为 NULL 时：AMOUNT_OVER_ORDER 因 order_amount IS NOT NULL 不判定；
     * AMOUNT_INVALID 只看 refund_fee，因此该记录不会被任何金额规则检出。缺失金额不作为超限证据，
     * 这是有意设计而非漏洞；本用例防止后续误把 NULL 当成超限或擅自补判定。</p>
     */
    @Test
    void missingOrderAmountIsIntentionallySilent() {
        var now = LocalDateTime.now();
        long id = insert("SO1", "IT1", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        clearColumn(id, "order_amount");

        var overview = overview();
        assertEquals(1, overview.total(), "记录仍计入总量");
        assertEquals(1, overview.open(), "10001 属进行中，计入未关闭");
        assertEquals(0, count(overview, "AMOUNT_OVER_ORDER"), "订单金额为 NULL 时不做超限判定");
        assertEquals(0, count(overview, "AMOUNT_INVALID"), "退款金额 10 正常，不属金额缺失或非正");
    }

    @Test
    void duplicateOpenRefundCountsParticipatingRecords() {
        var now = LocalDateTime.now();
        insert("SO1", "IT1", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        insert("SO1", "IT1", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        insert("SO1", "IT1", 10, 100, 10, 100, 0, "SF1", 10011, 1, now);
        insert("SO3", "IT9", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        assertEquals(2, count(overview(), "DUPLICATE_OPEN"),
                "同一订单同一明细的两条未关闭记录各计一次；已关闭记录与不同明细不参与");
    }

    @Test
    void duplicateOpenTreatsMissingItemNumberAsSameLine() {
        var now = LocalDateTime.now();
        long first = insert("SO9", "", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        clearColumn(first, "order_item_num");
        long second = insert("SO9", "", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        clearColumn(second, "order_item_num");
        assertEquals(2, count(overview(), "DUPLICATE_OPEN"), "订单明细号为空时同样参与重复判断");
    }

    @Test
    void stuckSellerOnlyOverThreeDays() {
        var now = LocalDateTime.now();
        insert("SO1", "IT1", 10, 100, 10, 100, 0, "SF1", 10005, 1, now.minusDays(4));
        insert("SO2", "IT2", 10, 100, 10, 100, 0, "SF1", 10005, 1, now.minusDays(1));
        assertEquals(1, count(overview(), "STUCK_SELLER"));
    }

    @Test
    void missingReturnLogisticsForGoodsReturnRequired() {
        var now = LocalDateTime.now();
        long blankNull = insert("SO1", "IT1", 10, 100, 10, 100, 1, "SF1", 10001, 1, now);
        clearColumn(blankNull, "return_logistics_code");
        insert("SO2", "IT2", 10, 100, 10, 100, 1, "   ", 10001, 1, now);
        insert("SO3", "IT3", 10, 100, 10, 100, 1, "SF999", 10001, 1, now);
        long noReturn = insert("SO4", "IT4", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        clearColumn(noReturn, "return_logistics_code");
        long rejected = insert("SO5", "IT5", 10, 100, 10, 100, 1, "SF1", 14000, 1, now);
        clearColumn(rejected, "return_logistics_code");
        assertEquals(2, count(overview(), "MISSING_RETURN_LOGISTICS"),
                "需退货且单号为空白计；已拒绝与非需退货不计");
    }

    @Test
    void invalidRefundAmount() {
        var now = LocalDateTime.now();
        long missing = insert("SO1", "IT1", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        clearColumn(missing, "refund_fee");
        insert("SO2", "IT2", 10, 100, 0, 100, 0, "SF1", 10001, 1, now);
        insert("SO3", "IT3", 10, 100, -1, 100, 0, "SF1", 10001, 1, now);
        insert("SO4", "IT4", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        assertEquals(3, count(overview(), "AMOUNT_INVALID"), "缺失、零元与负值均计");
    }

    @Test
    void stalePendingIgnoresRecentlyHandledRecords() {
        var now = LocalDateTime.now();
        insert("SO1", "IT1", 10, 100, 10, 100, 0, "SF1", 10001, 0, now.minusDays(4));
        insert("SO2", "IT2", 10, 100, 10, 100, 0, "SF1", 10001, 0, now.minusDays(1));
        insert("SO3", "IT3", 10, 100, 10, 100, 0, "SF1", 10001, 1, now.minusDays(4));
        assertEquals(1, count(overview(), "STALE_PENDING"), "仅 erp_status=0 且超过3天计");
    }

    @Test
    void findingsKeepFixedOrderAndLimitation() {
        var now = LocalDateTime.now();
        insert("S1", "I1", 10, 100, 10, 100, 0, "SF1", 10001, 0, now.minusDays(4));
        insert("S2", "I2", 10, 100, 200, 100, 0, "SF1", 10001, 1, now);
        insert("S3", "I3", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        insert("S3", "I3", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        insert("S5", "I5", 10, 100, 10, 100, 0, "SF1", 10005, 1, now.minusDays(4));
        long noLogistics = insert("S6", "I6", 10, 100, 10, 100, 1, "SF1", 10001, 1, now);
        clearColumn(noLogistics, "return_logistics_code");
        insert("S7", "I7", 10, 100, 0, 100, 0, "SF1", 10001, 1, now);

        var overview = overview();
        assertEquals(List.of("STALE_PENDING", "AMOUNT_OVER_ORDER", "DUPLICATE_OPEN", "STUCK_SELLER",
                        "MISSING_RETURN_LOGISTICS", "AMOUNT_INVALID"),
                overview.findings().stream().map(AiRiskReadService.Finding::code).toList());
        assertEquals(List.of(1L, 1L, 2L, 1L, 1L, 1L),
                overview.findings().stream().map(AiRiskReadService.Finding::count).toList());
        assertEquals(List.of("HIGH", "HIGH", "HIGH", "HIGH", "MEDIUM", "MEDIUM"),
                overview.findings().stream().map(AiRiskReadService.Finding::level).toList());
        assertEquals(AiRiskReadService.LIMITATION, overview.limitation());
        assertTrue(overview.limitation().contains("未接入"));
        assertFalse(overview.findings().isEmpty());
    }

    /** 相同订单号但不同商户的未关闭售后不能算作重复，也不能跨店铺泄漏。 */
    @Test
    void crossMerchantDuplicateStaysInsideMerchantScope() {
        var now = LocalDateTime.now();
        insert("SO1", "IT1", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        insert("SO1", "IT1", 20, 200, 10, 100, 0, "SF1", 10001, 1, now);

        var merchant10 = service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 10L, 10L, null));
        var merchant20 = service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 20L, 20L, null));
        assertEquals(1, merchant10.total());
        assertEquals(0, count(merchant10, "DUPLICATE_OPEN"), "商户10 不能把商户20 的同号售后算成重复");
        assertEquals(0, count(merchant20, "DUPLICATE_OPEN"));
        assertEquals(2, count(overview(), "DUPLICATE_OPEN"), "总部范围内同一订单号仍视为重复");

        insert("SO1", "IT1", 10, 100, 10, 100, 0, "SF1", 10001, 1, now);
        assertEquals(2, count(service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 10L, 10L, null)),
                "DUPLICATE_OPEN"), "商户内两条未关闭售后各计一次");
        assertEquals(2, count(service.overview(new BusinessSubjectScopeService.Scope("SHOP", 100L, 10L, 100L)),
                "DUPLICATE_OPEN"), "店铺范围只统计本店记录");
        assertEquals(0, count(service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 20L, 20L, null)),
                "DUPLICATE_OPEN"), "商户20 不受商户10 新增记录影响");
    }

    private AiRiskReadService.Overview overview() {
        return service.overview(new BusinessSubjectScopeService.Scope("HEADQUARTERS", null, null, null));
    }

    private long count(AiRiskReadService.Overview overview, String code) {
        return overview.findings().stream().filter(finding -> finding.code().equals(code))
                .mapToLong(AiRiskReadService.Finding::count).findFirst().orElse(0L);
    }

    private long insert(String orderNum, String orderItemNum, long merchantId, long shopId, double refundFee,
                        double orderAmount, int hasGoodReturn, String returnCode, int status, int erpStatus,
                        LocalDateTime createTime) {
        long id = nextId++;
        db.update("INSERT INTO o_refund(id,refund_num,order_num,order_item_num,merchant_id,shop_id,refund_type,"
                        + "refund_fee,order_amount,has_good_return,return_logistics_code,status,erp_status,create_time)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, "R" + id, orderNum, orderItemNum, merchantId, shopId, 1,
                refundFee, orderAmount, hasGoodReturn, returnCode, status, erpStatus, createTime);
        return id;
    }

    private void clearColumn(long id, String column) {
        db.update("UPDATE o_refund SET " + column + "=NULL WHERE id=?", id);
    }
}
