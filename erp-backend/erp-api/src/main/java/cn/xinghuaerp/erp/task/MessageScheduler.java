package cn.xinghuaerp.erp.task;

import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.product.service.OGoodsSkuService;
import cn.xinghuaerp.system.notify.NotifierService;
import cn.xinghuaerp.order.entity.ShopRefund;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.order.vo.SalesDailyVo;
import cn.xinghuaerp.system.service.ISysMessageService;
import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.order.service.ShopRefundService;
import cn.xinghuaerp.sse.SseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 消息与预警定时任务。
 *
 * <p>这里的检查全部是确定性规则（阈值、计数），不调用模型：
 * 模型侧的异常解读由 Python 服务负责，本类只在规则命中时落消息并推送。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "erp.notifications.scheduler-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MessageScheduler {

    private final OOrderService orderService;
    private final ShopRefundService refundService;
    private final ISysMessageService messageService;
    private final NotifierService notifierService;
    private final SseService sseService;
    private final OGoodsInventoryService inventoryService;
    private final OGoodsSkuService goodsSkuService;

    private static final List<String> NEED_NOTIFY_TYPES = List.of("stock_low", "order_timeout", "ai_analysis");

    @Scheduled(fixedRate = 1800000)
    public void run() {
        log.info("======自动分析====");
        checkSalesZero();
        checkShipPending();
        checkRefundExcess();
        checkStockLow();
        checkOrderTimeout();
        // checkAiAnalysis() 未实现：模型侧异常解读由 Python 服务负责，本类只有确定性规则。
    }

    @Scheduled(fixedRate = 600000)
    public void retryFailedNotify() {
        List<SysMessage> failed = messageService.getFailedNotify();
        for (SysMessage msg : failed) {
            if(Thread.currentThread().isInterrupted())return;
            try {
                // 多实例并发：先抢占投递权（条件更新，影响行数=1 才算抢到），
                // 否则两个实例都会读到同一批待补偿消息，导致同一 messageId 重复触达渠道。
                // 令牌用于结算时确认“这次发送中确实是我抢的”，见 finishNotify 的围栏说明。
                String token = messageService.claimNotify(msg.getId(), msg.getNotifyStatus());
                if (token == null) continue;
                boolean allOk=notifierService.notifyAll(msg.getTitle(),msg.getContent());
                // 只条件结算本次投递结果，不能对读取时的旧快照调 save：
                // 推送阻塞期间用户可能已标记已读，整实体回写会把 is_read/read_time/title 覆盖回旧值。
                messageService.finishNotify(msg.getId(),allOk?1:2,token);
            } catch(RuntimeException failure) {
                if(Thread.currentThread().isInterrupted())return;
                log.warn("消息补偿失败 id={} errorType={}",msg.getId(),failure.getClass().getSimpleName());
            }
        }
        if (!failed.isEmpty()) log.info("重试推送 {} 条消息", failed.size());
    }

    private void checkSalesZero() {
        if (!firstTimeToday("sales_zero")) return;
        SalesDailyVo today = orderService.getTodaySalesDaily(null);
        if (today != null && today.getAmount() != null && today.getAmount() > 0) return;
        save("sales_zero", "high", "今日销售额为零", "今日暂无销售额，请检查运营情况", "/sale/shop_order_list", "system");
    }

    private void checkShipPending() {
        if (!firstTimeToday("ship_pending")) return;
        Integer count = orderService.getWaitShipOrderAllCount(null);
        if (count == null || count < 50) return;
        save("ship_pending", "medium", "发货积压提醒", "待发货订单" + count + "单，请尽快安排发货", "/sale/shop_order_list", "system");
    }

    private void checkRefundExcess() {
        if (!firstTimeToday("refund_excess")) return;
        LambdaQueryWrapper<ShopRefund> w = new LambdaQueryWrapper<>();
        w.ge(ShopRefund::getCreateOn, LocalDateTime.now().minusDays(30));
        long count = refundService.count(w);
        if (count < 20) return;
        save("refund_excess", "medium", "退款过多提醒", "近30天退款" + count + "单，请关注退款原因", "/sale/shop_refund", "system");
    }

    /**
     * 库存不足预警。
     *
     * <p>阈值取商品库 SKU 上手填的 {@code low_qty}（最低库存）。没有采用「日均销量 × 补货提前期 ×
     * 波动系数」那套安全库存公式：本系统只有几百行销售日报，算出来的日均销量不足信，硬套公式
     * 等于用编出来的数字报警。{@code low_qty} 为空或 &lt;=0 的 SKU 直接跳过，否则「没维护阈值」
     * 会变成每天几十条噪音。</p>
     *
     * <p>分级：可用量为 0 及以下算「已缺货」走 high，低于阈值但还有货算 medium。两者都会进
     * {@code NEED_NOTIFY_TYPES} 走渠道推送——这是原有的类型白名单，库存告警按设计就该推给仓管。
     * 一天最多一条，靠 dedup_key 兜住。</p>
     */
    private void checkStockLow() {
        if (!firstTimeToday("stock_low")) return;

        List<OGoodsInventory> invList = inventoryService.list(new LambdaQueryWrapper<OGoodsInventory>()
                .eq(OGoodsInventory::getIsDelete, 0)
                .eq(OGoodsInventory::getStockStatus, 1)
                .orderByAsc(OGoodsInventory::getAvailableQuantity)
                .last("LIMIT 500"));
        if (invList.isEmpty()) return;

        List<String> skuIds = invList.stream()
                .map(OGoodsInventory::getSkuId).filter(Objects::nonNull).distinct()
                .map(String::valueOf).toList();
        if (skuIds.isEmpty()) return;
        // o_goods_sku 的主键在实体里是 String（库里是 bigint），这里统一按 Long 建索引，
        // 与 o_goods_inventory.sku_id 的 Long 对齐，避免「200 和 200L」当成两个 key。
        Map<Long, Integer> lowQtyBySku = goodsSkuService.listByIds(skuIds).stream()
                .filter(s -> s.getId() != null && s.getLowQty() != null && s.getLowQty() > 0)
                .collect(Collectors.toMap(s -> Long.valueOf(s.getId()), OGoodsSku::getLowQty, (a, b) -> a));
        if (lowQtyBySku.isEmpty()) return;

        List<OGoodsInventory> hits = invList.stream()
                .filter(i -> {
                    Integer low = lowQtyBySku.get(i.getSkuId());
                    return low != null && i.getAvailableQuantity() != null && i.getAvailableQuantity() <= low;
                })
                .toList();
        if (hits.isEmpty()) return;

        boolean outOfStock = hits.stream().anyMatch(i -> i.getAvailableQuantity() <= 0);
        int show = Math.min(hits.size(), 5);
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < show; i++) {
            OGoodsInventory inv = hits.get(i);
            String goodsName = inv.getGoodsName() == null ? "" : inv.getGoodsName();
            String skuName = inv.getSkuName() == null ? "" : inv.getSkuName();
            detail.append("「").append(goodsName)
                    .append(skuName.isEmpty() ? "" : " " + skuName)
                    .append("」可用 ").append(inv.getAvailableQuantity())
                    .append(" 件（预警线 ").append(lowQtyBySku.get(inv.getSkuId())).append("）");
            if (i < show - 1) detail.append("；");
        }
        if (hits.size() > show) detail.append("……共 ").append(hits.size()).append(" 个 SKU");
        else detail.append("。");

        String title = (outOfStock ? "有 " + hits.size() + " 个 SKU 已缺货"
                : "有 " + hits.size() + " 个 SKU 低于预警线");
        save("stock_low", outOfStock ? "high" : "medium", title, detail.toString(), "/stock/index.vue", "system");
    }

    /**
     * 发货超时预警。
     *
     * <p>口径与 {@code getWaitShipOrderAllCount} 一致，只统计 {@code order_status=1}（待发货）。
     * 超时线 4 小时：平台发货时限普遍 24~48 小时，内部提醒要留出一个班次的处理时间，
     * 原方法注释里写的也是 4h。超过 24 小时的升级为 high，会走渠道推送。</p>
     */
    private void checkOrderTimeout() {
        if (!firstTimeToday("order_timeout")) return;

        LocalDateTime now = LocalDateTime.now();
        long overdue = orderService.count(new LambdaQueryWrapper<OOrder>()
                .eq(OOrder::getOrderStatus, 1)
                .lt(OOrder::getOrderTime, now.minusHours(4)));
        if (overdue == 0) return;

        long urgent = orderService.count(new LambdaQueryWrapper<OOrder>()
                .eq(OOrder::getOrderStatus, 1)
                .lt(OOrder::getOrderTime, now.minusHours(24)));
        long normal = overdue - urgent;

        String content = "待发货订单中，" + overdue + " 单已超过 4 小时未发货"
                + (urgent > 0 ? "，其中 " + urgent + " 单超过 24 小时" : "")
                + "。超时发货会被平台扣分或罚款，请尽快处理。";
        if (normal == 0) content = "待发货订单中有 " + urgent + " 单已超过 24 小时未发货，请立即处理。";
        save("order_timeout", urgent > 0 ? "high" : "medium",
                "发货超时：" + overdue + " 单待处理", content, "/sale/shop_order_list", "system");
    }

    /**
     * 同类型预警当天是否已经发过。定时任务每 30 分钟跑一次，不加这道闸门会变成刷屏。
     *
     * <p>用 {@code dedup_key = type + ":" + 日期} 判定，写入时也带同一个键，靠唯一索引兜住
     * 多实例并发的窗口——这里只是提前短路，省掉后续的库存/订单查询。</p>
     */
    private boolean firstTimeToday(String type) {
        return !messageService.existsByDedupKey(dedupKey(type));
    }

    private String dedupKey(String type) {
        return type + ":" + LocalDate.now();
    }

    private void save(String type, String level, String title, String content, String link, String source) {
        save(type, level, title, content, link, source, dedupKey(type));
    }

    /**
     * @param dedupKey 去重键，同上则同一条。定时预警按「类型 + 日期」去重，一天一条；
     *                 靠 sys_message.dedup_key 的唯一索引兜住多实例并发。
     */
    private void save(String type, String level, String title, String content, String link, String source,
                      String dedupKey) {
        boolean needNotify = "high".equals(level) || NEED_NOTIFY_TYPES.contains(type);

        SysMessage m = new SysMessage();
        m.setType(type);
        m.setLevel(level);
        m.setTitle(title);
        m.setContent(content);
        m.setLink(link);
        m.setSource(source);
        m.setIsRead(0);
        m.setNeedNotify(needNotify ? 1 : 0);
        m.setNotifyStatus(0);
        m.setDedupKey(dedupKey);
        m.setCreatedTime(LocalDateTime.now());
        messageService.save(m);

        // 插入被 dedup_key 唯一索引挡回来时，实体不会拿到 id。此时消息今天已经发过，
        // 必须就地返回：继续往下走会再用 id=null 广播一条 SSE，前端收到一条点不开的重复提醒。
        if (m.getId() == null) {
            log.debug("预警去重命中，跳过重复推送 type={} dedupKey={}", type, dedupKey);
            return;
        }

        if (needNotify && m.getId() != null) {
            // 先抢占投递权再推送：插入时状态是「待推送」，抢占后才是「发送中」，
            // 后面才能用 notify_status=发送中 + 租约令牌 的围栏做条件结算；
            // expectedNotifyStatus=null 即从「待推送」抢占。
            String token = messageService.claimNotify(m.getId(), null);
            if (token != null) {
                boolean allOk = notifierService.notifyAll(title, content);
                // 同理：新消息在推送期间也可能被用户标记已读，只能条件结算投递结果，不能整实体回写。
                messageService.finishNotify(m.getId(), allOk ? 1 : 2, token);
            }
        }

        sseService.broadcastMessage("message", Map.of(
                "type", type, "level", level,
                "title", title, "content", content, "id", m.getId()));

        log.info("消息: [{}] {} (needNotify={})", level, title, needNotify);
    }
}
