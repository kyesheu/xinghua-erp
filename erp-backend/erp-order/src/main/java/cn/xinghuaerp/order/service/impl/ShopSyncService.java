package cn.xinghuaerp.order.service.impl;

import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.order.mq.OrderEventPublisher;
import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.order.service.ORefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopSyncService {

    private final OOrderService oOrderService;
    private final ORefundService oRefundService;

    /** MQ 开启（erp.mq.enabled=true）时注入；关闭时为 null，同步主流程不受影响。 */
    @Autowired(required = false)
    private OrderEventPublisher orderEventPublisher;

    public ResultVo<Long> syncOrder(Long shopOrderId) {
        if (shopOrderId == null || shopOrderId <= 0) {
            return ResultVo.error("shopOrderId 无效");
        }
        try {
            ResultVo<Long> r = oOrderService.shopOrderMessage(shopOrderId);
            if (r.getCode() == 0) {
                log.info("订单同步成功：shopOrderId={}", shopOrderId);
                if (orderEventPublisher != null) {
                    orderEventPublisher.publishOrderCreated(shopOrderId, r.getData());
                }
            } else {
                log.warn("订单同步失败：shopOrderId={}, 原因={}", shopOrderId, r.getMsg());
            }
            return r;
        } catch (Exception e) {
            log.error("订单同步异常：shopOrderId={}", shopOrderId, e);
            return ResultVo.error("同步异常：" + e.getMessage());
        }
    }

    public ResultVo<Long> syncRefund(Long shopRefundId) {
        if (shopRefundId == null || shopRefundId <= 0) {
            return ResultVo.error("shopRefundId 无效");
        }
        try {
            ResultVo<Long> r = oRefundService.shopRefundMessage(shopRefundId);
            if (r.getCode() == 0) {
                log.info("售后同步成功：shopRefundId={}", shopRefundId);
            } else {
                log.warn("售后同步失败：shopRefundId={}, 原因={}", shopRefundId, r.getMsg());
            }
            return r;
        } catch (Exception e) {
            log.error("售后同步异常：shopRefundId={}", shopRefundId, e);
            return ResultVo.error("同步异常：" + e.getMessage());
        }
    }
}
