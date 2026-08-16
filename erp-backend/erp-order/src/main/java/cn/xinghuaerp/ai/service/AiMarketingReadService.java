package cn.xinghuaerp.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

/**
 * 广告与营销资料只读服务。
 *
 * <p>在数据库授权的业务主体范围内，对系统内手动订单折扣规则做可用性核对，输出固定顺序的风险发现项。
 * 本类只做统计，不修改任何业务数据，也不把全表数据拉到内存后再筛选。</p>
 */
@Service
@RequiredArgsConstructor
public class AiMarketingReadService {
    /** 统计口径与资料边界说明，固定随结果返回，供智能体与人工核对。 */
    public static final String LIMITATION =
            "仅统计当前授权范围内本系统手动订单折扣规则；广告投放、竞品、广告效果及平台活动数据未接入，规则异常不等于实际资损，处置需人工在营销模块完成；营销建议和文案须人工审核，不自动发布或修改平台设置。";

    private static final String TABLE = "o_marketing_discount_rule";
    /** 即将到期窗口：7 天（秒）。 */
    private static final long EXPIRING_WINDOW_SECONDS = 604800L;
    /** 待审核超期阈值：3 天。 */
    private static final long PENDING_STALE_DAYS = 3L;

    private final JdbcTemplate jdbc;

    /** 单条风险发现项；count 为该规则在当前授权范围内的命中条数。 */
    public record Finding(String code, String level, String title, long count, String advice) {}

    /** 授权范围内的营销规则总览。 */
    public record Overview(String scope, long total, long active, long pendingReview, List<Finding> findings, String limitation) {}

    /** 主体范围过滤片段：SQL 条件、占位参数与范围标签。 */
    private record ScopeFilter(String sql, List<Object> args, String label) {}

    /**
     * 统计当前授权范围内的营销折扣规则总览与风险发现项。
     *
     * @param scope 服务端解析出的业务主体范围，不接受前端传入
     * @return 总览数据与按固定顺序排列、仅含 count&gt;0 的发现项
     */
    @Transactional(readOnly = true)
    public Overview overview(BusinessSubjectScopeService.Scope scope) {
        ScopeFilter filter = filter("t", scope);
        long now = Instant.now().getEpochSecond();
        LocalDateTime staleBefore = LocalDateTime.now().minusDays(PENDING_STALE_DAYS);
        long total = count(filter, "");
        long active = count(filter, " AND t.status=1");
        long pendingReview = count(filter, " AND t.status=0");

        List<Finding> findings = new ArrayList<>(7);
        add(findings, "EXPIRED_ENABLED", "HIGH", "已过期但仍启用",
                count(filter, " AND t.status=1 AND t.end_time<?", now),
                "核对结束时间，停用或续期后再对外使用。");
        add(findings, "INVALID_VALUE", "HIGH", "折扣值不合法",
                count(filter, " AND ((t.discount_type=1 AND (t.discount_value<=0 OR t.discount_value>100))"
                        + " OR (t.discount_type=2 AND t.discount_value<=0))"),
                "折扣百分比须在0到100之间，固定金额须大于0；请核对并修正。");
        add(findings, "FIXED_OVER_MIN", "HIGH", "固定折扣不低于订单下限",
                count(filter, " AND t.discount_type=2 AND t.discount_value>0 AND t.min_order_amount>0"
                        + " AND t.discount_value>=t.min_order_amount"),
                "固定金额折扣大于等于订单下限时，实付可能为零或负数，请核对门槛与折扣。");
        add(findings, "OVERLAP_CONFLICT", "HIGH", "同范围同优先级有效期重叠",
                overlapConflicts(scope, filter),
                "重叠规则的适用顺序不确定，请调整优先级或有效期。");
        add(findings, "QUOTA_EXHAUSTED", "MEDIUM", "启用但额度已用尽",
                count(filter, " AND t.status=1 AND t.total_quota>0 AND t.used_quota>=t.total_quota"),
                "额度用尽的规则不会生效，请补充额度或停用。");
        add(findings, "EXPIRING_SOON", "MEDIUM", "7天内到期",
                count(filter, " AND t.status=1 AND t.end_time>=? AND t.end_time<?", now, now + EXPIRING_WINDOW_SECONDS),
                "确认是否续期，避免活动中断。");
        add(findings, "PENDING_STALE", "MEDIUM", "待审核超3天",
                count(filter, " AND t.status=0 AND t.created_time<?", staleBefore),
                "及时审核或驳回，避免规则长期悬置。");

        return new Overview(filter.label(), total, active, pendingReview, List.copyOf(findings), LIMITATION);
    }

    /**
     * 统计参与「同范围、同优先级、有效期重叠」冲突的启用规则条数。
     *
     * <p>外层用别名 t、子查询用别名 o，两侧各自套用同一授权范围条件（参数单独传）。</p>
     *
     * <p>可空列一律先归一再比较：{@code priority} 在真实库上是 {@code int NULL DEFAULT 0}，
     * {@code NULL=NULL} 在 SQL 中不成立，若直接写 {@code o.priority=t.priority} 会把
     * 「两条都未设置优先级且有效期重叠」的规则漏掉，与项目「多报不漏报」的口径冲突。</p>
     */
    private long overlapConflicts(BusinessSubjectScopeService.Scope scope, ScopeFilter filter) {
        ScopeFilter inner = filter("o", scope);
        String sql = "SELECT COUNT(*) FROM " + TABLE + " t WHERE 1=1" + filter.sql()
                + " AND t.status=1 AND EXISTS (SELECT 1 FROM " + TABLE + " o WHERE o.status=1 AND o.id<>t.id"
                // priority 可空，用 COALESCE(...,0) 归一；apply_scope 为 tinyint NOT NULL，无需处理。
                + " AND COALESCE(o.priority,0)=COALESCE(t.priority,0) AND o.apply_scope=t.apply_scope"
                + " AND COALESCE(o.apply_merchant_id,0)=COALESCE(t.apply_merchant_id,0)"
                + " AND COALESCE(o.apply_shop_id,0)=COALESCE(t.apply_shop_id,0)"
                + " AND o.start_time<t.end_time AND t.start_time<o.end_time"
                + inner.sql() + ")";
        return countSql(sql, filter.args(), inner.args());
    }

    /** 统计满足授权范围与附加条件的规则条数。 */
    private long count(ScopeFilter filter, String condition, Object... extra) {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " t WHERE 1=1" + filter.sql() + condition;
        List<Object> args = new ArrayList<>(filter.args());
        for (Object value : extra) args.add(value);
        return countSql(sql, args);
    }

    @SafeVarargs
    private final long countSql(String sql, List<Object>... argGroups) {
        List<Object> args = new ArrayList<>();
        for (List<Object> group : argGroups) args.addAll(group);
        Long value = jdbc.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0L : value;
    }

    private static void add(List<Finding> findings, String code, String level, String title, long count, String advice) {
        if (count > 0) findings.add(new Finding(code, level, title, count, advice));
    }

    /** 按主体类型构造范围过滤条件；标签用于回显数据可见范围。 */
    private ScopeFilter filter(String alias, BusinessSubjectScopeService.Scope scope) {
        if (scope == null || scope.type() == null) throw new IllegalArgumentException("当前账号不支持营销资料查询");
        return switch (scope.type()) {
            case "HEADQUARTERS" -> new ScopeFilter("", List.of(), "总部");
            case "MERCHANT" -> {
                if (scope.merchantId() == null || scope.merchantId() <= 0) throw new IllegalArgumentException("商户范围无效");
                yield new ScopeFilter(" AND (" + alias + ".apply_scope=1 OR ("
                        + alias + ".apply_scope IN (2,3) AND " + alias + ".apply_merchant_id=?))",
                        List.of(scope.merchantId()), "绑定商户");
            }
            case "SHOP" -> {
                if (scope.merchantId() == null || scope.merchantId() < 0 || scope.shopId() == null || scope.shopId() <= 0)
                    throw new IllegalArgumentException("店铺范围无效");
                yield new ScopeFilter(" AND (" + alias + ".apply_scope=1 OR ("
                        + alias + ".apply_scope=2 AND " + alias + ".apply_merchant_id=?) OR ("
                        + alias + ".apply_scope=3 AND " + alias + ".apply_merchant_id=? AND " + alias + ".apply_shop_id=?))",
                        List.of(scope.merchantId(), scope.merchantId(), scope.shopId()), "绑定店铺");
            }
            default -> throw new IllegalArgumentException("当前账号不支持营销资料查询");
        };
    }
}
