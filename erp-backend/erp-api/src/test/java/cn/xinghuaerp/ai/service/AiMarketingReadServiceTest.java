package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import cn.xinghuaerp.ai.service.AiFinanceReadService.Overview;
import cn.xinghuaerp.ai.service.AiMarketingReadService.Finding;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

/** 营销规则只读统计的口径、范围隔离与发现项顺序测试。 */
class AiMarketingReadServiceTest {
    private static final long DAY = 86400L;

    private JdbcTemplate db;
    private AiMarketingReadService service;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(dataSource);
        db.execute("CREATE TABLE o_marketing_discount_rule("
                + "id BIGINT,rule_name VARCHAR(50),discount_type INT,discount_value DECIMAL(10,2),apply_scope INT,"
                + "apply_merchant_id BIGINT,apply_shop_id BIGINT,total_quota INT,used_quota INT,min_order_amount DECIMAL(10,2),"
                + "start_time BIGINT,end_time BIGINT,status INT,priority INT,remark VARCHAR(50),created_time TIMESTAMP)");
        service = new AiMarketingReadService(db);
    }

    @AfterEach
    void tearDown() {
        db.execute("SHUTDOWN");
    }

    @Test
    void scopeIsolationMatchesRuleOwner() {
        long now = Instant.now().getEpochSecond();
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 0, 1);
        insert(1, 10, 2, 10, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 0, 2);
        insert(1, 10, 3, 10, 100, 0, 0, 0, now - 1000, now + 30 * DAY, 0, 3);
        insert(1, 10, 3, 10, 101, 0, 0, 0, now - 1000, now + 30 * DAY, 0, 4);
        insert(1, 10, 2, 20, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 0, 5);
        insert(1, 10, 3, 20, 200, 0, 0, 0, now - 1000, now + 30 * DAY, 0, 6);

        var headquarters = service.overview(new BusinessSubjectScopeService.Scope("HEADQUARTERS", null, null, null));
        assertEquals(6, headquarters.total());
        assertEquals("总部", headquarters.scope());
        assertTrue(headquarters.findings().isEmpty());

        var merchant = service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 10L, 10L, null));
        assertEquals(4, merchant.total(), "商户可见全部规则 + 本商户的商户/店铺规则");
        assertEquals("绑定商户", merchant.scope());

        var shop = service.overview(new BusinessSubjectScopeService.Scope("SHOP", 100L, 10L, 100L));
        assertEquals(3, shop.total(), "店铺可见全部规则 + 本商户商户规则 + 本店规则");
        assertEquals("绑定店铺", shop.scope());

        assertThrows(IllegalArgumentException.class,
                () -> service.overview(new BusinessSubjectScopeService.Scope("WAREHOUSE", 1L, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.overview(new BusinessSubjectScopeService.Scope("SUPPLIER", 1L, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.overview(null));
    }

    @Test
    void expiredRuleCountsOnlyWhenStillEnabled() {
        long now = Instant.now().getEpochSecond();
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 10 * DAY, now - 3600, 1, 1);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 10 * DAY, now - 3600, 0, 2);
        var overview = overview();
        assertEquals(1, count(overview, "EXPIRED_ENABLED"), "已过期但停用的规则不计");
        assertEquals(0, count(overview, "EXPIRING_SOON"));
    }

    @Test
    void expiringSoonOnlyInsideSevenDays() {
        long now = Instant.now().getEpochSecond();
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 1000, now + 3 * DAY, 1, 1);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 1000, now + 8 * DAY, 1, 2);
        var overview = overview();
        assertEquals(1, count(overview, "EXPIRING_SOON"), "仅7天内到期的启用规则计");
        assertEquals(0, count(overview, "EXPIRED_ENABLED"));
    }

    @Test
    void quotaExhaustedIgnoresUnlimitedQuota() {
        long now = Instant.now().getEpochSecond();
        insert(1, 10, 1, 0, 0, 10, 10, 0, now - 1000, now + 30 * DAY, 1, 1);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 1, 2);
        assertEquals(1, count(overview(), "QUOTA_EXHAUSTED"), "total_quota=0 表示不限额度，不计");
    }

    @Test
    void invalidDiscountValue() {
        long now = Instant.now().getEpochSecond();
        insert(1, 120, 1, 0, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 1, 1);
        insert(1, 0, 1, 0, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 0, 2);
        insert(2, -1, 1, 0, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 1, 3);
        insert(1, 100, 1, 0, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 1, 4);
        insert(2, 1, 1, 0, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 1, 5);
        assertEquals(3, count(overview(), "INVALID_VALUE"), "百分比超100、百分比为0、固定金额为负均计");
    }

    @Test
    void fixedDiscountNotBelowOrderMinimum() {
        long now = Instant.now().getEpochSecond();
        insert(2, 50, 1, 0, 0, 0, 0, 50, now - 1000, now + 30 * DAY, 1, 1);
        insert(2, 50, 1, 0, 0, 0, 0, 60, now - 1000, now + 30 * DAY, 1, 2);
        insert(2, 70, 1, 0, 0, 0, 0, 60, now - 1000, now + 30 * DAY, 1, 3);
        insert(2, 50, 1, 0, 0, 0, 0, 0, now - 1000, now + 30 * DAY, 1, 4);
        insert(1, 50, 1, 0, 0, 0, 0, 10, now - 1000, now + 30 * DAY, 1, 5);
        assertEquals(2, count(overview(), "FIXED_OVER_MIN"), "等于下限与超过下限计入，低于下限不计");
    }

    @Test
    void overlapConflictRequiresSamePriorityAndOverlappingWindow() {
        long now = Instant.now().getEpochSecond();
        insert(1, 10, 1, 0, 0, 0, 0, 0, now, now + 10 * DAY, 1, 5);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now + 1000, now + 10 * DAY, 1, 5);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now + 1000, now + 10 * DAY, 1, 6);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now + 10 * DAY, now + 20 * DAY, 1, 5);
        assertEquals(2, count(overview(), "OVERLAP_CONFLICT"),
                "仅同优先级且时间区间真正重叠的两条参与冲突；优先级不同或首尾相接不计");
    }

    /**
     * 两条 priority 均为 NULL 的规则，有效期重叠时必须判为冲突。
     *
     * <p>真实库为 {@code priority int NULL DEFAULT 0}，SQL 中 {@code NULL=NULL} 不成立，
     * 若直接写 {@code o.priority=t.priority} 会把这类规则整片漏掉（违反「多报不漏报」）；
     * 服务端已用 {@code COALESCE(...,0)} 归一。计数口径与
     * {@link #overlapConflictRequiresSamePriorityAndOverlappingWindow()} 一致：互为冲突的两条各计一次。</p>
     */
    @Test
    void overlapDetectsNullPriorityRules() {
        long now = Instant.now().getEpochSecond();
        long first = insert(1, 10, 1, 0, 0, 0, 0, 0, now + 1000, now + 10 * DAY, 1, 0);
        clearColumn(first, "priority");
        long second = insert(1, 10, 1, 0, 0, 0, 0, 0, now + 5 * DAY, now + 20 * DAY, 1, 0);
        clearColumn(second, "priority");

        var overview = overview();
        assertEquals(2, overview.active(), "两条均为启用状态");
        assertEquals(2, count(overview, "OVERLAP_CONFLICT"),
                "priority 均为 NULL 且区间重叠的两条规则互为冲突，各计一次");
    }

    @Test
    void pendingReviewStaleOnlyAfterThreeDays() {
        insert(1, 10, 1, 0, 0, 0, 0, 0, Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 30 * DAY,
                0, 1, LocalDateTime.now().minusDays(4));
        insert(1, 10, 1, 0, 0, 0, 0, 0, Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 30 * DAY,
                0, 2, LocalDateTime.now().minusDays(1));
        assertEquals(1, count(overview(), "PENDING_STALE"), "仅待审核超过3天计");
    }

    @Test
    void findingsKeepFixedOrderAndOnlyIncludePositiveCounts() {
        long now = Instant.now().getEpochSecond();
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 100000, now - 50, 1, 1);
        insert(1, 120, 1, 0, 0, 0, 0, 0, now + 1000, now + 10 * DAY, 1, 2);
        insert(2, 50, 1, 0, 0, 0, 0, 50, now + 1000, now + 10 * DAY, 1, 3);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now + 1000, now + 10 * DAY, 1, 9);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now + 1000, now + 10 * DAY, 1, 9);
        insert(1, 10, 1, 0, 0, 5, 5, 0, now + 1000, now + 10 * DAY, 1, 4);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 1000, now + DAY, 1, 5);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now + 1000, now + 10 * DAY, 0, 6, LocalDateTime.now().minusDays(4));

        var overview = overview();
        assertEquals(8, overview.total());
        assertEquals(7, overview.active(), "status=1 视为启用");
        assertEquals(1, overview.pendingReview(), "status=0 视为待审核");
        assertEquals(List.of("EXPIRED_ENABLED", "INVALID_VALUE", "FIXED_OVER_MIN", "OVERLAP_CONFLICT",
                        "QUOTA_EXHAUSTED", "EXPIRING_SOON", "PENDING_STALE"),
                overview.findings().stream().map(AiMarketingReadService.Finding::code).toList());
        assertEquals(List.of(1L, 1L, 1L, 2L, 1L, 1L, 1L),
                overview.findings().stream().map(AiMarketingReadService.Finding::count).toList());
        assertEquals(List.of("HIGH", "HIGH", "HIGH", "HIGH", "MEDIUM", "MEDIUM", "MEDIUM"),
                overview.findings().stream().map(AiMarketingReadService.Finding::level).toList());
        assertEquals(AiMarketingReadService.LIMITATION, overview.limitation());
        assertTrue(overview.limitation().contains("未接入"));
        assertFalse(overview.findings().isEmpty());
    }

    /** 跨商户同优先级重叠必须留在各自商户内，不能因 EXISTS 子查询缺范围条件而互相计数。 */
    @Test
    void crossMerchantOverlapStaysInsideMerchantScope() {
        long now = Instant.now().getEpochSecond();
        insert(1, 10, 2, 10, 0, 0, 0, 0, now + DAY, now + 10 * DAY, 1, 5);
        insert(1, 10, 2, 20, 0, 0, 0, 0, now + DAY, now + 10 * DAY, 1, 5);

        var merchant10 = service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 10L, 10L, null));
        var merchant20 = service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 20L, 20L, null));
        assertEquals(1, merchant10.total(), "商户10 只能看到自己的1条规则");
        assertEquals(0, count(merchant10, "OVERLAP_CONFLICT"), "不同商户的同优先级规则不算冲突");
        assertEquals(1, merchant20.total());
        assertEquals(0, count(merchant20, "OVERLAP_CONFLICT"));
        assertEquals(0, count(overview(), "OVERLAP_CONFLICT"), "总部范围内不同适用商户也不构成重叠");

        insert(1, 10, 2, 10, 0, 0, 0, 0, now + DAY, now + 10 * DAY, 1, 5);
        assertEquals(2, count(service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 10L, 10L, null)),
                "OVERLAP_CONFLICT"), "同一商户内两条重叠规则各计一次");
        assertEquals(0, count(service.overview(new BusinessSubjectScopeService.Scope("MERCHANT", 20L, 20L, null)),
                "OVERLAP_CONFLICT"), "商户20 不受商户10 新增规则影响");
    }

    /** 已过期与7天内到期互斥，且窗口外（超过7天）两端都不计。 */
    @Test
    void expiredAndExpiringSoonAreMutuallyExclusive() {
        long now = Instant.now().getEpochSecond();
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 10 * DAY, now - DAY, 1, 1);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 10 * DAY, now + 60, 1, 2);
        insert(1, 10, 1, 0, 0, 0, 0, 0, now - 10 * DAY, now + 8 * DAY, 1, 3);

        var overview = overview();
        assertEquals(1, count(overview, "EXPIRED_ENABLED"), "仅已过期的启用规则计");
        assertEquals(1, count(overview, "EXPIRING_SOON"), "仅7天内到期的启用规则计");
        assertEquals(3, overview.active(), "三条均为启用状态");
        assertEquals(3, overview.total());
        // 无法断言 end_time 恰等于服务端 now 的那一秒：now 在服务内取，测试插入的时间只能做到秒级近似。
    }

    private AiMarketingReadService.Overview overview() {
        return service.overview(new BusinessSubjectScopeService.Scope("HEADQUARTERS", null, null, null));
    }

    private long count(AiMarketingReadService.Overview overview, String code) {
        return overview.findings().stream().filter(finding -> finding.code().equals(code))
                .mapToLong(AiMarketingReadService.Finding::count).findFirst().orElse(0L);
    }

    private long insert(int discountType, double discountValue, int applyScope, long merchantId, long shopId,
                        int totalQuota, int usedQuota, double minOrderAmount, long startTime, long endTime,
                        int status, int priority) {
        return insert(discountType, discountValue, applyScope, merchantId, shopId, totalQuota, usedQuota,
                minOrderAmount, startTime, endTime, status, priority, LocalDateTime.now());
    }

    private long insert(int discountType, double discountValue, int applyScope, long merchantId, long shopId,
                        int totalQuota, int usedQuota, double minOrderAmount, long startTime, long endTime,
                        int status, int priority, LocalDateTime createdTime) {
        long id = nextId++;
        db.update("INSERT INTO o_marketing_discount_rule(id,discount_type,discount_value,apply_scope,apply_merchant_id,"
                        + "apply_shop_id,total_quota,used_quota,min_order_amount,start_time,end_time,status,priority,created_time)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, discountType, discountValue, applyScope, merchantId, shopId, totalQuota, usedQuota,
                minOrderAmount, startTime, endTime, status, priority, createdTime);
        return id;
    }

    /** 把指定列置 NULL，用于还原真实库中可空列（如 priority、apply_shop_id）的取值。 */
    private void clearColumn(long id, String column) {
        db.update("UPDATE o_marketing_discount_rule SET " + column + "=NULL WHERE id=?", id);
    }
}
