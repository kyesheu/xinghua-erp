package cn.xinghuaerp.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import cn.xinghuaerp.system.service.BusinessSubjectScopeService;
/**
 * 风险与售后资料只读服务。
 *
 * <p>在数据库授权的业务主体范围内，对内部售后记录做一致性核对，输出固定顺序的风险发现项。
 * 本类只做统计，不修改任何业务数据，统计口径不代表已确认风险或财务结算结果。</p>
 */
@Service
@RequiredArgsConstructor
public class AiRiskReadService {
    /** 统计口径与资料边界说明，固定随结果返回，供智能体与人工核对。 */
    public static final String LIMITATION =
            "仅统计当前授权范围内内部售后记录；记录当前状态与申请金额为原始数据，不等于已确认风险、退款完成或财务结算；跟卖、合规及平台异常订单未接入，处置需人工完成；不执行售后操作，不返回买家个人信息。";

    private static final String TABLE = "o_refund";
    /**
     * 售后终态集合，用于「未关闭」与「重复未关闭」两个口径：终态记录不再占用处理队列。
     *
     * <p><b>注意：{@code o_refund.status} 同时存在两套状态词表，本常量必须同时覆盖，不是脏数据，请勿删减。</b></p>
     * <ul>
     *   <li><b>平台词表</b>（平台同步记录，10001–10094）：
     *       14000 拒绝、10011 关闭、10010 完成、10020 售后成功、10021 售后失败、
     *       10091 换货成功、10092 换货失败、10093 维修关闭、10094 维修成功。
     *       10090 退款中属进行中，<b>不在</b>本集合内。</li>
     *   <li><b>本地词表</b>（{@code LocalAfterSaleService} 写入，{@code refund_num} 形如 {@code LOCAL-AF-…}）：
     *       0 待处理（进行中）、1 已关闭（终态）、4 已办结（终态）。
     *       依据：创建时 {@code LocalAfterSaleService.java:111} 置 0；办结时 {@code :274} 置 4；
     *       关闭时 {@code :414} 置 1；该服务自身判定「是否还有活跃售后」也是
     *       {@code notIn(ORefund::getStatus, 1, 4)}（{@code :280}、{@code :418}），
     *       取消入口亦以 {@code Set.of(1,4)} 判「已关闭或已办结」（{@code :410}）。</li>
     * </ul>
     *
     * <p>若只写平台词表，本地已办结/已关闭的记录会整片被算成未关闭（真实库曾出现 19 条本地
     * {@code status=4} 全部误判为未关闭），{@code DUPLICATE_OPEN} 也会把「已办结的本地售后 + 新申请」
     * 误判成重复。后续若任一词表新增终态，必须同步本常量，否则 open 与 DUPLICATE_OPEN 会高估。</p>
     */
    private static final String CLOSED_STATUS =
            "1,4,14000,10011,10010,10020,10021,10091,10092,10093,10094";
    /**
     * 「流程终止且未完成」集合，<b>只用于「需退货但无退货单号」</b>，同时覆盖两套词表。
     *
     * <p>与 {@link #CLOSED_STATUS} 刻意不同，请勿合并：售后成功(10020)、维修成功(10094)、
     * 完成(10010)、换货成功(10091) 都属于「钱已退/货已处理但退货可能没回来」，恰恰是最需要
     * 提示缺失退货单号的场景，因此必须继续检出；只有拒绝(14000)、关闭(10011)、
     * 售后失败(10021)、换货失败(10092)、维修关闭(10093) 是流程终止且未完成，
     * 不再追退货单号。</p>
     *
     * <p>本地词表同理：只收 1（本地已关闭，{@code LocalAfterSaleService.java:414}，流程终止且未完成）。
     * <b>刻意不含 4（本地已办结）</b>——办结走的是「退货入库确认后办结」流程（{@code :274}），
     * 此时若仍缺退货单号，恰恰是「货没回却已办结」的高风险场景，必须继续检出，
     * 与 10020/10094/10010/10091 同侧。</p>
     */
    private static final String REJECTED_OR_CLOSED = "1,14000,10011,10021,10092,10093";
    /** 待处理超期阈值：3 天。 */
    private static final long STALE_DAYS = 3L;

    private final JdbcTemplate jdbc;

    /** 单条风险发现项；count 为该规则在当前授权范围内的命中条数。 */
    public record Finding(String code, String level, String title, long count, String advice) {}

    /** 授权范围内的售后记录总览。 */
    public record Overview(String scope, long total, long open, List<Finding> findings, String limitation) {}

    /** 主体范围过滤片段：SQL 条件、占位参数与范围标签。 */
    private record ScopeFilter(String sql, List<Object> args, String label) {}

    /**
     * 统计当前授权范围内的售后记录总览与风险发现项。
     *
     * @param scope 服务端解析出的业务主体范围，不接受前端传入
     * @return 总览数据与按固定顺序排列、仅含 count&gt;0 的发现项
     */
    @Transactional(readOnly = true)
    public Overview overview(BusinessSubjectScopeService.Scope scope) {
        ScopeFilter filter = filter("t", scope);
        LocalDateTime staleBefore = LocalDateTime.now().minusDays(STALE_DAYS);
        long total = count(filter, "");
        long open = count(filter, " AND t.status NOT IN (" + CLOSED_STATUS + ")");

        List<Finding> findings = new ArrayList<>(6);
        add(findings, "STALE_PENDING", "HIGH", "待处理超3天",
                count(filter, " AND t.erp_status=0 AND t.create_time<?", staleBefore),
                "核对原单与售后进度，及时处理或转人工跟进。");
        // 订单金额为 0 或更小时，任何正退款都需人工核实，因此不要求 order_amount>0；
        // refund_fee>0 用于排除零元/负值退款，避免与 AMOUNT_INVALID 重复报同一记录；
        // order_amount 为 NULL 时两条金额规则都不报，这是有意的静默：
        // AMOUNT_OVER_ORDER 因 order_amount IS NOT NULL 不判定；AMOUNT_INVALID 只看 refund_fee，
        // 所以「订单金额缺失但退款金额正常」的记录当前不会被任何金额规则检出。
        // 缺失金额不作为超限证据，不是漏洞；若要覆盖必须先补全订单金额，此处不改 SQL。
        add(findings, "AMOUNT_OVER_ORDER", "HIGH", "退款金额超过订单金额",
                count(filter, " AND t.order_amount IS NOT NULL AND t.refund_fee>0 AND t.refund_fee>t.order_amount"),
                "核对金额与订单实付，必要时驳回或调整。");
        add(findings, "DUPLICATE_OPEN", "HIGH", "同一订单明细存在重复未关闭售后",
                duplicateOpen(scope, filter),
                "核对是否重复申请，避免重复退款。");
        add(findings, "STUCK_SELLER", "HIGH", "等待卖家处理超3天",
                count(filter, " AND t.status=10005 AND t.create_time<?", staleBefore),
                "及时处理或说明原因，避免平台超时。");
        add(findings, "MISSING_RETURN_LOGISTICS", "MEDIUM", "需退货但无退货物流单号",
                count(filter, " AND t.has_good_return=1"
                        + " AND (t.return_logistics_code IS NULL OR TRIM(t.return_logistics_code)='')"
                        + " AND t.status NOT IN (" + REJECTED_OR_CLOSED + ")"),
                "核对退货物流，避免货未回款先退。");
        add(findings, "AMOUNT_INVALID", "MEDIUM", "退款金额缺失或非正",
                count(filter, " AND (t.refund_fee IS NULL OR t.refund_fee<=0)"),
                "核对申请金额，零元或负值不能作为退款依据。");

        return new Overview(filter.label(), total, open, List.copyOf(findings), LIMITATION);
    }

    /**
     * 统计参与「同订单同明细重复未关闭」的售后记录条数。
     *
     * <p>外层用别名 t、子查询用别名 o，两侧各自套用同一授权范围条件（参数单独传）。</p>
     */
    private long duplicateOpen(BusinessSubjectScopeService.Scope scope, ScopeFilter filter) {
        ScopeFilter inner = filter("o", scope);
        String sql = "SELECT COUNT(*) FROM " + TABLE + " t WHERE 1=1" + filter.sql()
                + " AND t.status NOT IN (" + CLOSED_STATUS + ")"
                + " AND EXISTS (SELECT 1 FROM " + TABLE + " o WHERE o.status NOT IN (" + CLOSED_STATUS + ")"
                + " AND o.id<>t.id AND o.order_num=t.order_num"
                + " AND COALESCE(o.order_item_num,'')=COALESCE(t.order_item_num,'')"
                + inner.sql() + ")";
        return countSql(sql, filter.args(), inner.args());
    }

    /** 统计满足授权范围与附加条件的售后记录条数。 */
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
        if (scope == null || scope.type() == null) throw new IllegalArgumentException("当前账号不支持售后资料查询");
        return switch (scope.type()) {
            case "HEADQUARTERS" -> new ScopeFilter("", List.of(), "总部");
            case "MERCHANT" -> {
                if (scope.merchantId() == null || scope.merchantId() <= 0) throw new IllegalArgumentException("商户范围无效");
                yield new ScopeFilter(" AND " + alias + ".merchant_id=?", List.of(scope.merchantId()), "绑定商户");
            }
            case "SHOP" -> {
                if (scope.merchantId() == null || scope.merchantId() < 0 || scope.shopId() == null || scope.shopId() <= 0)
                    throw new IllegalArgumentException("店铺范围无效");
                yield new ScopeFilter(" AND " + alias + ".merchant_id=? AND " + alias + ".shop_id=?",
                        List.of(scope.merchantId(), scope.shopId()), "绑定店铺");
            }
            default -> throw new IllegalArgumentException("当前账号不支持售后资料查询");
        };
    }
}
