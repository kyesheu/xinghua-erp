package cn.xinghuaerp.erp.task;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    private static final List<String> NEED_NOTIFY_TYPES = List.of("stock_low", "order_timeout", "ai_analysis");

    @Scheduled(fixedRate = 1800000)
    public void run() {
        log.info("======自动分析====");
//        checkSalesZero();
//        checkShipPending();
//        checkRefundExcess();
//        checkStockLow();
//        checkOrderTimeout();
//        checkAiAnalysis();
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
        SalesDailyVo today = orderService.getTodaySalesDaily(null);
        if (today != null && today.getAmount() != null && today.getAmount() > 0) return;
        save("sales_zero", "high", "今日销售额为零", "今日暂无销售额，请检查运营情况", "/sale/shop_order_list", "system");
    }

    private void checkShipPending() {
        Integer count = orderService.getWaitShipOrderAllCount(null);
        if (count == null || count < 50) return;
        save("ship_pending", "medium", "发货积压提醒", "待发货订单" + count + "单，请尽快安排发货", "/sale/shop_order_list", "system");
    }

    private void checkRefundExcess() {
        LambdaQueryWrapper<ShopRefund> w = new LambdaQueryWrapper<>();
        w.ge(ShopRefund::getCreateOn, LocalDateTime.now().minusDays(30));
        long count = refundService.count(w);
        if (count < 20) return;
        save("refund_excess", "medium", "退款过多提醒", "近30天退款" + count + "单，请关注退款原因", "/sale/shop_refund", "system");
    }

    private void checkStockLow() {
        // TODO: query OGoodsSku with lowQty > availableQty
        // save("stock_low", "high", "库存不足: 商品名", "SKU xxx 库存仅剩 n 件")
    }

    private void checkOrderTimeout() {
        // TODO: query OOrder with status 0/1 and createTime > 4h
        // save("order_timeout", "medium", "发货超时提醒", "n 个订单超过 4 小时未发货")
    }

    private void save(String type, String level, String title, String content, String link, String source) {
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
        m.setCreatedTime(LocalDateTime.now());
        messageService.save(m);

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
