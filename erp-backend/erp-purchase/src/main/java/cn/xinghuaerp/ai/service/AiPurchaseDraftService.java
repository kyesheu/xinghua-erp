package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.purchase.request.PurchaseOrderAddItemBo;
import cn.xinghuaerp.purchase.request.PurchaseOrderAddRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.xinghuaerp.purchase.service.ErpPurchaseOrderService;
/** 人工确认的单 SKU 采购草稿；幂等记录与采购单在同一事务中提交。 */
@Service
@RequiredArgsConstructor
public class AiPurchaseDraftService {
    private final JdbcTemplate jdbc;
    private final ErpPurchaseOrderService orders;

    public record Request(String requestKey, Long skuId, Long supplierId, Long quantity, BigDecimal unitPrice) {}
    public record Result(Long orderId, boolean replayed) {}
    public record Supplier(String id, String name) {}
    public record Recent(String orderId, String skuId, long quantity, BigDecimal unitPrice, String orderNum, Integer status) {}
    private record Existing(long userId, long skuId, long supplierId, long quantity, BigDecimal price, Long orderId) {}
    private record ReviewedExisting(long userId, long supplierId, BigDecimal price, Long orderId) {}

    @Transactional(readOnly = true)
    public Result findReviewedDraft(String taskId, Long userId, Long supplierId, BigDecimal unitPrice) {
        var rows = jdbc.query("SELECT user_id,supplier_id,unit_price,purchase_order_id FROM ai_purchase_draft_request WHERE request_key=?",
                (rs,n) -> new ReviewedExisting(rs.getLong(1),rs.getLong(2),rs.getBigDecimal(3),rs.getObject(4,Long.class)),taskId);
        if (rows.isEmpty()) return null;
        var saved = rows.get(0);
        if (userId == null || saved.userId() != userId || supplierId == null || saved.supplierId() != supplierId || unitPrice == null || saved.price().compareTo(unitPrice) != 0)
            throw new IllegalArgumentException("审核任务已用于其他采购内容");
        return saved.orderId() == null ? null : new Result(saved.orderId(),true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result createFromReview(String taskId, int version, Long supplierId, BigDecimal unitPrice, Long userId, String actor) {
        var rows = jdbc.query("SELECT sku_id,approved_quantity,status,version FROM ai_inventory_review_task WHERE id=? FOR UPDATE",
                (rs,n) -> new Object[]{rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getInt(4)}, taskId);
        if (rows.isEmpty()) throw new IllegalArgumentException("审核任务不存在");
        var task = rows.get(0);
        if (!"APPROVED".equals(task[2]) || (int) task[3] != version)
            throw new IllegalStateException("审核任务未通过或版本已变化");
        return create(new Request(taskId,(Long) task[0],supplierId,(Long) task[1],unitPrice),userId,actor);
    }

    @Transactional(readOnly = true)
    public List<Recent> recent(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("用户无效");
        return jdbc.query("SELECT r.purchase_order_id,r.sku_id,r.quantity,r.unit_price,p.order_num,p.status "
                        + "FROM ai_purchase_draft_request r LEFT JOIN erp_purchase_order p ON p.id=r.purchase_order_id "
                        + "WHERE r.user_id=? AND r.purchase_order_id IS NOT NULL "
                        + "AND (p.id IS NULL OR (p.merchant_id=0 AND p.shop_id=0)) "
                        + "ORDER BY r.purchase_order_id DESC LIMIT 20",
                (rs,n) -> new Recent(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getBigDecimal(4),rs.getString(5),rs.getObject(6,Integer.class)), userId);
    }

    @Transactional(readOnly = true)
    public List<Supplier> suppliers(Long skuId) {
        if (skuId == null || skuId <= 0) throw new IllegalArgumentException("SKU 无效");
        return jdbc.query("SELECT DISTINCT s.id,s.name FROM erp_supplier s JOIN erp_supplier_product_item i ON i.supplier_id=s.id "
                        + "JOIN erp_supplier_product p ON p.id=i.supplier_product_id AND p.supplier_id=i.supplier_id "
                        + "WHERE i.erp_goods_sku_id=? AND s.disable=0 AND s.is_delete=0 AND i.status<>2 AND p.status<>2 ORDER BY s.id LIMIT 100",
                (rs,n) -> new Supplier(rs.getString(1),rs.getString(2)), skuId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result create(Request request, Long userId, String actor) {
        if (request == null || userId == null || userId <= 0 || actor == null || actor.isBlank())
            throw new IllegalArgumentException("采购请求或用户无效");
        String key;
        try { key = UUID.fromString(request.requestKey()).toString(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("请求标识无效"); }
        if (request.skuId() == null || request.skuId() <= 0 || request.supplierId() == null || request.supplierId() <= 0
                || request.quantity() == null || request.quantity() <= 0 || request.quantity() > Integer.MAX_VALUE
                || request.unitPrice() == null || request.unitPrice().signum() < 0 || request.unitPrice().scale() > 2
                || request.unitPrice().compareTo(new BigDecimal("999999.99")) > 0)
            throw new IllegalArgumentException("采购商品、供应商、数量或单价无效");
        BigDecimal total = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        if (total.compareTo(new BigDecimal("99999999.99")) > 0) throw new IllegalArgumentException("采购总金额超过系统上限");
        jdbc.update("INSERT INTO ai_purchase_draft_request(request_key,user_id,sku_id,supplier_id,quantity,unit_price) VALUES(?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE request_key=request_key",
                key,userId,request.skuId(),request.supplierId(),request.quantity(),request.unitPrice());
        Existing saved = jdbc.queryForObject("SELECT user_id,sku_id,supplier_id,quantity,unit_price,purchase_order_id FROM ai_purchase_draft_request WHERE request_key=? FOR UPDATE",
                (rs, n) -> new Existing(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getBigDecimal(5),rs.getObject(6,Long.class)), key);
        if (saved == null || saved.userId() != userId || saved.skuId() != request.skuId() || saved.supplierId() != request.supplierId()
                || saved.quantity() != request.quantity() || saved.price().compareTo(request.unitPrice()) != 0)
            throw new IllegalArgumentException("请求标识已用于其他采购内容");
        if (saved.orderId() != null) return new Result(saved.orderId(), true);
        var item = new PurchaseOrderAddItemBo(); item.setId(request.skuId()); item.setQuantity(request.quantity()); item.setPurPrice(request.unitPrice());
        var order = new PurchaseOrderAddRequest(); order.setContactId(request.supplierId()); order.setOrderDate(LocalDate.now().toString());
        order.setOrderAmount(total); order.setCreateBy(actor); order.setMerchantId(0L); order.setShopId(0L); order.setStatus(0); order.setGoodsList(List.of(item));
        var result = orders.createPurchaseOrder(order);
        if (result.getCode() != 0 || result.getData() == null) throw new IllegalArgumentException(result.getMsg());
        if (jdbc.update("UPDATE ai_purchase_draft_request SET purchase_order_id=? WHERE request_key=? AND purchase_order_id IS NULL", result.getData(),key) != 1)
            throw new IllegalStateException("采购草稿记录保存失败");
        return new Result(result.getData(), false);
    }
}
