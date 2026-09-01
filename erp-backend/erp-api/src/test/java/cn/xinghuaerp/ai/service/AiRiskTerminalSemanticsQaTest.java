package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import cn.xinghuaerp.ai.service.AiMarketingReadService.Finding;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

/**
 * 售后终态两套口径的 QA 复核用例（独立于工程师用例，便于交叉验证）。
 *
 * <p>口径 A（{@code open} / {@code DUPLICATE_OPEN}）：9 个终态全部排除，
 * 即 14000/10011/10010/10020/10021/10091/10092/10093/10094；10090 退款中属进行中，不排除。</p>
 * <p>口径 B（{@code MISSING_RETURN_LOGISTICS}）：只排除 14000/10011/10021/10092/10093，
 * 售后成功(10020)、退款完成(10010)、维修成功(10094)、换货成功(10091) 仍需检查退货物流——
 * 这几类恰恰是「钱已退/货已处理、货未回」的高风险场景。</p>
 */
class AiRiskTerminalSemanticsQaTest {
    private JdbcTemplate db;
    private AiRiskReadService service;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:qa" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(dataSource);
        db.execute("CREATE TABLE o_refund(id BIGINT,refund_num VARCHAR(50),order_num VARCHAR(50),order_item_num VARCHAR(50),"
                + "merchant_id BIGINT,shop_id BIGINT,refund_type INT,refund_fee DECIMAL(10,2),order_amount DECIMAL(10,2),"
                + "has_good_return INT,return_logistics_code VARCHAR(100),status INT,erp_status INT,create_time TIMESTAMP)");
        service = new AiRiskReadService(db);
    }

    @AfterEach
    void tearDown() {
        db.execute("SHUTDOWN");
    }

    /** 售后成功但无退货单号：钱已退、货未回，必须提示。 */
    @Test
    void successfulAfterSaleWithoutReturnLogisticsIsFlagged() {
        insert(10020, 1, null, 100.0, 100.0);
        assertEquals(1, count("MISSING_RETURN_LOGISTICS"),
                "10020 售后成功 + 需退货 + 无退货单号必须计入高风险提示");
    }

    /** 维修成功但无退货单号：同样属于货未回，必须提示。 */
    @Test
    void repairSuccessWithoutReturnLogisticsIsFlagged() {
        insert(10094, 1, null, 100.0, 100.0);
        assertEquals(1, count("MISSING_RETURN_LOGISTICS"), "10094 维修成功 + 需退货 + 无退货单号必须计入");
    }

    /**
     * 退款完成(10010) 也必须提示：它是最容易漏掉的边界——10010 在 {@code CLOSED_STATUS} 里，
     * 但在 {@code REJECTED_OR_CLOSED} 里没有，属于「钱已退、货可能未回」。
     */
    @Test
    void completedAfterSaleWithoutReturnLogisticsIsFlagged() {
        insert(10010, 1, null, 100.0, 100.0);
        assertEquals(1, count("MISSING_RETURN_LOGISTICS"), "10010 退款完成 + 需退货 + 无退货单号必须计入");
        var overview = service.overview(new BusinessSubjectScopeService.Scope("HEADQUARTERS", null, null, null));
        assertEquals(0, overview.open(), "10010 同时属终态，不计入未关闭");
    }

    /** 拒绝/关闭/售后失败/维修关闭 不提示缺退货物流。 */
    @Test
    void rejectedClosedFailedRepairClosedAreNotFlagged() {
        insert(14000, 1, null, 100.0, 100.0);
        insert(10011, 1, null, 100.0, 100.0);
        insert(10021, 1, null, 100.0, 100.0);
        insert(10093, 1, null, 100.0, 100.0);
        assertEquals(0, count("MISSING_RETURN_LOGISTICS"), "14000/10011/10021/10093 不应提示缺退货物流");
    }

    /** 10020 仍属「未关闭」口径的终态：open 与 DUPLICATE_OPEN 不得把它算进去，证明两套常量没有被合并。 */
    @Test
    void successfulAfterSaleStaysClosedForOpenAndDuplicate() {
        insert(10020, 1, null, 100.0, 100.0);
        insert(10020, 1, null, 100.0, 100.0);
        var overview = service.overview(new BusinessSubjectScopeService.Scope("HEADQUARTERS", null, null, null));
        assertEquals(2, overview.total());
        assertEquals(0, overview.open(), "10020 属终态，不计入未关闭");
        assertEquals(0, count("DUPLICATE_OPEN"), "终态记录不参与重复未关闭");
        assertEquals(2, count("MISSING_RETURN_LOGISTICS"), "但两条都要提示缺退货物流，两套口径必须不同");
    }

    /** 负订单金额 + 零退款：只由 AMOUNT_INVALID 报一次，不重复进 AMOUNT_OVER_ORDER。 */
    @Test
    void zeroRefundOnNegativeOrderAmountReportedOnlyByAmountInvalid() {
        insert(10001, 0, null, -5.0, 0.0);
        assertEquals(0, count("AMOUNT_OVER_ORDER"), "退款金额为 0 不应判定超过订单金额");
        assertEquals(1, count("AMOUNT_INVALID"), "零元退款由 AMOUNT_INVALID 报一次");
    }

    /** 回归守门：0 元订单 + 正退款仍要计入（工程师已修的语义不能被后来的 refund_fee>0 条件误伤）。 */
    @Test
    void positiveRefundOnZeroOrderAmountStillFlaggedAsOverOrder() {
        insert(10001, 100, null, 0.0, 100.0);
        assertEquals(1, count("AMOUNT_OVER_ORDER"), "0 元订单退 100 必须计入");
    }

    /** 订单金额缺失时不做超过判定（交给退款金额侧口径）。 */
    @Test
    void missingOrderAmountIsNotOverOrder() {
        insert(10001, 100, null, null, 100.0);
        assertEquals(0, count("AMOUNT_OVER_ORDER"), "order_amount 为 NULL 不判定超过订单金额");
    }

    private long count(String code) {
        return service.overview(new BusinessSubjectScopeService.Scope("HEADQUARTERS", null, null, null))
                .findings().stream()
                .filter(finding -> finding.code().equals(code))
                .mapToLong(AiRiskReadService.Finding::count)
                .findFirst().orElse(0L);
    }

    private void insert(int status, int hasGoodReturn, String returnCode, Double orderAmount, Double refundFee) {
        long id = nextId++;
        db.update("INSERT INTO o_refund(id,refund_num,order_num,order_item_num,merchant_id,shop_id,refund_type,"
                        + "refund_fee,order_amount,has_good_return,return_logistics_code,status,erp_status,create_time)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, "R" + id, "SO" + id, "IT" + id, 10, 100, 1,
                refundFee, orderAmount, hasGoodReturn, returnCode, status, 1, LocalDateTime.now());
    }
}
