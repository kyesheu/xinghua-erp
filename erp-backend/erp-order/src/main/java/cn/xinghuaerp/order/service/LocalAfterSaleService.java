package cn.xinghuaerp.order.service;

import cn.xinghuaerp.system.entity.ErpLogisticsCompany;
import cn.xinghuaerp.purchase.facade.PurchaseFacade;
import cn.xinghuaerp.product.facade.ProductFacade;
import cn.xinghuaerp.system.facade.SystemFacade;
import cn.xinghuaerp.inventory.facade.InventoryFacade;
import cn.xinghuaerp.inventory.entity.ErpStockIn;
import cn.xinghuaerp.inventory.entity.ErpStockInItem;
import cn.xinghuaerp.inventory.entity.ErpStockOut;
import cn.xinghuaerp.inventory.entity.ErpStockOutItem;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.entity.OOrderStocking;
import cn.xinghuaerp.inventory.entity.OOrderStockingItem;
import cn.xinghuaerp.system.service.SalesScope;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.entity.OOrderItem;
import cn.xinghuaerp.order.entity.ORefund;
import cn.xinghuaerp.order.entity.ORefundAfterSale;
import cn.xinghuaerp.order.entity.ShopOrder;
import cn.xinghuaerp.order.entity.ShopRefund;
import cn.xinghuaerp.product.entity.ErpSupplierProductItem;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.purchase.entity.ErpSupplierProduct;
import cn.xinghuaerp.order.request.LocalAfterSaleRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import cn.xinghuaerp.order.mapper.OOrderItemMapper;
import cn.xinghuaerp.order.mapper.OOrderMapper;
import cn.xinghuaerp.order.mapper.ORefundAfterSaleMapper;
import cn.xinghuaerp.order.mapper.ORefundMapper;
import cn.xinghuaerp.order.mapper.ShopOrderMapper;
import cn.xinghuaerp.order.mapper.ShopRefundMapper;
import cn.xinghuaerp.order.service.LocalAfterSaleService;
/** 本地手工售后。仓库任务复用实际入/出库；不调用支付或电商平台接口。 */
@Service
@AllArgsConstructor
public class LocalAfterSaleService {
    private final OOrderMapper orders;
    private final OOrderItemMapper orderItems;
    private final ProductFacade skus;
    private final ShopRefundMapper shopRefunds;
    private final ORefundMapper refunds;
    private final ORefundAfterSaleMapper afterSales;
    private final InventoryFacade warehouses;
    private final SystemFacade logistics;
    private final InventoryFacade stockIns;
    private final InventoryFacade stockInItems;
    private final InventoryFacade stockOuts;
    private final InventoryFacade stockOutItems;
    private final InventoryFacade stockings;
    private final InventoryFacade stockingItems;
    private final ShopOrderMapper sourceOrders;
    private final PurchaseFacade suppliers;
    private final PurchaseFacade supplierProducts;
    private final ProductFacade supplierItems;

    /** null 表示总部；非总部必须由 Controller 传入登录身份范围。 */
    public List<OOrderItem> findItems(Long shopId, String orderNum, SalesScope scope) {
        require(shopId != null && StringUtils.hasText(orderNum), "请选择店铺并输入完整订单号");
        OOrder order = orders.selectOne(new LambdaQueryWrapper<OOrder>().eq(OOrder::getShopId, shopId).eq(OOrder::getOrderNum, orderNum.trim()));
        require(order != null, "订单库中没有找到该店铺的订单，请先完成订单同步");
        scope.check(order.getMerchantId(), order.getShopId());
        require(Integer.valueOf(1).equals(order.getOrderMode()), "此入口仅支持本地手工订单；平台售后请使用平台授权流程");
        return orderItems.selectList(new LambdaQueryWrapper<OOrderItem>().eq(OOrderItem::getOrderId, order.getId()).orderByAsc(OOrderItem::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(LocalAfterSaleRequest request, SalesScope scope, String actor) {
        require(request != null && request.getShopId() != null && request.getOrderItemId() != null, "请选择原订单商品");
        OOrderItem snapshot = orderItems.selectById(request.getOrderItemId());
        require(snapshot != null, "原订单商品不存在");
        // 与手动发货使用相同的主单→明细锁顺序，串行校验累计数量与退款额。
        OOrder order = orders.selectOne(new LambdaQueryWrapper<OOrder>().eq(OOrder::getId, snapshot.getOrderId()).last("FOR UPDATE"));
        require(order != null && request.getShopId().equals(order.getShopId()), "订单商品不属于所选店铺");
        scope.check(order.getMerchantId(), order.getShopId());
        require(Integer.valueOf(1).equals(order.getOrderMode()), "此入口仅支持本地手工订单");
        OOrderItem item = orderItems.selectOne(new LambdaQueryWrapper<OOrderItem>().eq(OOrderItem::getId, snapshot.getId()).last("FOR UPDATE"));
        int type = request.getType() == null ? -1 : request.getType();
        require(Set.of(1, 11, 10, 20, 80).contains(type), "请选择售前退款、仅退款、退货退款、换货或补发");
        int qty = request.getQuantity() == null ? 0 : request.getQuantity();
        require(qty > 0 && item.getQuantity() != null, "售后数量必须大于0");
        require(StringUtils.hasText(request.getReason()) && request.getReason().length() <= 200, "请填写200字以内的售后原因");
        BigDecimal amount = request.getRefundAmount() == null ? BigDecimal.ZERO : request.getRefundAmount();
        require(amount.signum() >= 0 && amount.scale() <= 2, "退款金额不能为负数，最多保留两位小数");
        if (type == 20 || type == 80) require(amount.signum() == 0, "换货、补发不在此单登记退款金额");
        List<ORefund> previous = refunds.selectList(new LambdaQueryWrapper<ORefund>().eq(ORefund::getShopId, order.getShopId())
                .eq(ORefund::getOrderNum, order.getOrderNum()).eq(ORefund::getOrderItemNum, item.getId()).likeRight(ORefund::getRefundNum, "LOCAL-AF-").ne(ORefund::getStatus, 1));
        int used = previous.stream().mapToInt(r -> r.getQuantity() == null ? 0 : r.getQuantity()).sum();
        require(qty <= item.getQuantity() - used, "累计售后数量不能超过原购买数量");
        BigDecimal paid = BigDecimal.valueOf(item.getPayment() != null ? item.getPayment() : item.getItemAmount() != null ? item.getItemAmount() : 0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refunded = previous.stream().map(r -> BigDecimal.valueOf(r.getRefundFee() == null ? 0 : r.getRefundFee()).setScale(2, RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO, BigDecimal::add);
        require(amount.add(refunded).compareTo(paid) <= 0, "累计退款金额不能超过该商品实付金额");
        require(item.getQuantity() > 0 && amount.compareTo(paid.multiply(BigDecimal.valueOf(qty)).divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.DOWN)) <= 0, "退款金额不能超过本次数量对应的商品实付金额");
        OGoodsSku sku = skus.getOGoodsSkuById(item.getGoodsSkuId());
        require(sku != null && Objects.equals(sku.getGoodsId(), String.valueOf(item.getGoodsId())), "原商品未正确关联商品库SKU");
        require(Integer.valueOf(0).equals(sku.getInventoryMode()), "当前本地售后仅支持传统SKU；一物一码需按单件来源核对后处理");
        if (type == 1) {
            require(Integer.valueOf(1).equals(order.getOrderStatus()) && stockings.countOOrderStocking(new LambdaQueryWrapper<OOrderStocking>().eq(OOrderStocking::getOOrderId, order.getId()).and(w -> w.isNull(OOrderStocking::getOrderStatus).or().ne(OOrderStocking::getOrderStatus, 11))) == 0
                    && (order.getWaybillStatus() == null || order.getWaybillStatus() == 0), "售前退款仅限尚未分配、未取号的待发货订单");
            require(orderItems.selectCount(new LambdaQueryWrapper<OOrderItem>().eq(OOrderItem::getOrderId, order.getId())) == 1 && qty == item.getQuantity(), "本地售前退款当前仅支持单商品整单退款，部分退款请先核对剩余发货任务");
        } else {
            require(Set.of(2, 3).contains(order.getOrderStatus()), "售后退款、退货、换货或补发需原订单已发货");
            int vendorShipped = supplierShipments(order, item).values().stream().mapToInt(Integer::intValue).sum();
            if (vendorShipped > 0) {
                require(scope.merchantId() == null && scope.shopId() == null, "供应商或混合发货售后目前仅支持总部账号");

            }
            require(qty + used <= shippedQuantity(order, item), "累计售后数量不能超过已发货数量；本地仓商品需先实际出库");
        }
        OGoodsSku exchange = type == 20 ? skus.getOGoodsSkuById(request.getExchangeSkuId()) : null;
        if (type == 20) require(exchange != null && Integer.valueOf(0).equals(exchange.getInventoryMode())
                && (Objects.equals(exchange.getMerchantId(), 0L) || Objects.equals(exchange.getMerchantId(), order.getMerchantId())), "请选择本商户可用的传统SKU作为换出商品");
        LocalDateTime now = LocalDateTime.now();
        String number = "LOCAL-AF-" + UUID.randomUUID().toString().replace("-", "").substring(0,24);
        ORefund refund = new ORefund();
        refund.setRefundNum(number); refund.setRefundType(type); refund.setShopId(order.getShopId()); refund.setShopType(order.getShopType());
        refund.setMerchantId(order.getMerchantId()); refund.setOrderNum(order.getOrderNum()); refund.setOrderItemNum(item.getId());
        refund.setOrderAmount(order.getAmount()); refund.setRefundFee(amount.doubleValue()); refund.setRefundReason(request.getReason().trim());
        refund.setSkuId(item.getSkuId()); refund.setGoodsId(item.getGoodsId()); refund.setGoodsSkuId(item.getGoodsSkuId());
        refund.setGoodsName(item.getGoodsTitle()); refund.setGoodsSku(item.getGoodsSpec()); refund.setGoodsImage(item.getGoodsImg()); refund.setSkuNum(sku.getSkuCode());
        refund.setQuantity(qty); refund.setHasGoodReturn(type == 10 || type == 20 ? 1 : 0); refund.setShippingStatus(type == 1 ? 0 : 1);
        refund.setStatus(0); refund.setErpStatus(0); refund.setHasProcessing(0); refund.setCreateTime(now); refund.setCreateBy(actor);
        if (exchange != null) { refund.setExchangeErpGoodsSkuId(Long.valueOf(exchange.getId())); refund.setExchangeSkuId(exchange.getId()); refund.setExchangeGoodsName(exchange.getGoodsName()); refund.setExchangeGoodsNum(qty); }
        write(refunds.insert(refund));
        ShopRefund source = new ShopRefund();
        source.setAfterId(number); source.setShopId(order.getShopId()); source.setMerchantId(order.getMerchantId()); source.setShopType(order.getShopType());
        source.setOrderId(order.getOrderNum()); source.setSubOrderId(item.getSubOrderNum()); source.setType(type); source.setCount(qty); source.setSellCount(item.getQuantity());
        source.setOrderAmount(BigDecimal.valueOf(order.getAmount() == null ? 0 : order.getAmount()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact());
        source.setProductId(item.getProductId() == null ? "" : item.getProductId()); source.setSkuId(item.getSkuId() == null ? "" : item.getSkuId()); source.setSkuName(item.getGoodsSpec()); source.setGoodsName(item.getGoodsTitle()); source.setGoodsImage(item.getGoodsImg());
        source.setOrderStatus(type == 1 ? "NOT_SHIPPED" : "SHIPPED"); source.setUserShipStatus(type == 1 ? 0 : 2); source.setDisputeRefundStatus(0); source.setRefundPhase(type == 1 ? "ON_SALE" : "AFTER_SALE");
        source.setRefundAmount(amount.movePointRight(2).intValueExact()); source.setStatus(0); source.setConfirmStatus(0); source.setRefundStatus(1);
        source.setReason(request.getReason().trim()); source.setOrderShipStatus(type == 1 ? 0 : 1); source.setCreateOn(now); source.setCreateTime(System.currentTimeMillis() / 1000);
        write(shopRefunds.insert(source));
        OOrderItem update = new OOrderItem(); update.setId(item.getId()); update.setRefundStatus(2); write(orderItems.updateById(update));
        if (type == 1) preSaleStatus(order, 12, actor);
        return source.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public String process(String number, SalesScope scope, String actor) {
        ORefund refund = refund(number, scope, true);
        require(Integer.valueOf(0).equals(refund.getStatus()), "该售后已关闭或已办结");
        require(!Integer.valueOf(1).equals(refund.getHasProcessing()), "该售后已处理，请查看已有处理记录");
        OOrderItem item = orderItems.selectById(refund.getOrderItemNum());
        require(item != null, "原订单商品不存在");
        ORefundAfterSale sale = new ORefundAfterSale();
        sale.setRefundId(refund.getId()); sale.setRefundNum(number); sale.setType(refund.getRefundType()); sale.setShopId(refund.getShopId());
        sale.setMerchantId(refund.getMerchantId()); sale.setShopType(refund.getShopType()); sale.setOrderNum(refund.getOrderNum()); sale.setSubOrderNum(item.getSubOrderNum());
        sale.setOOrderId(Long.valueOf(item.getOrderId())); sale.setOOrderItemId(Long.valueOf(item.getId())); sale.setOGoodsId(refund.getGoodsId()); sale.setOGoodsSkuId(refund.getGoodsSkuId());
        sale.setSkuId(refund.getSkuId()); sale.setTitle(refund.getGoodsName()); sale.setSkuInfo(refund.getGoodsSku()); sale.setSkuCode(refund.getSkuNum()); sale.setImg(refund.getGoodsImage());
        sale.setQuantity(refund.getQuantity()); sale.setExchangeErpGoodsSkuId(refund.getExchangeErpGoodsSkuId()); sale.setExchangeGoodsNum(refund.getQuantity());
        sale.setSupplierId(0L); sale.setReturnType(Set.of(10,20).contains(refund.getRefundType()) ? 0 : -1); sale.setReissueType(Set.of(20,80).contains(refund.getRefundType()) ? 0 : -1);
        sale.setSendWarehouseId(0L);
        var originalShipments = stockings.listOOrderStocking(new LambdaQueryWrapper<OOrderStocking>().eq(OOrderStocking::getOOrderId, item.getOrderId()).eq(OOrderStocking::getOrderType, 0).and(w -> w.isNull(OOrderStocking::getOrderStatus).or().ne(OOrderStocking::getOrderStatus, 11)));
        sale.setSendShipType(0);
        if (!originalShipments.isEmpty()) {
            var originalDetails = stockingItems.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>().in(OOrderStockingItem::getShipOrderId, originalShipments.stream().map(OOrderStocking::getId).toList()).eq(OOrderStockingItem::getOOrderItemId, item.getId()));
            Set<Long> referenced = new HashSet<>(); originalDetails.forEach(d -> referenced.add(d.getShipOrderId()));
            var related = originalShipments.stream().filter(t -> referenced.contains(t.getId()) && Integer.valueOf(2).equals(t.getSendStatus())).toList();
            Set<Integer> types = new HashSet<>(); related.forEach(t -> types.add(t.getType()));
            if (types.size() > 1) sale.setSendShipType(-1);
            else if (types.contains(300)) {
                sale.setSendShipType(300);
                Set<Long> vendors = new HashSet<>(); related.forEach(t -> vendors.add(t.getShipperId()));
                if (vendors.size() == 1) sale.setSupplierId(vendors.iterator().next());
            } else if (types.contains(0)) {
                Set<Long> warehouseIds = new HashSet<>(); related.forEach(t -> warehouseIds.add(t.getWarehouseId()));
                if (warehouseIds.size() == 1) { var sent = related.get(0); sale.setSendWarehouseId(sent.getWarehouseId()); sale.setSendWarehouseType(sent.getWarehouseType()); sale.setSendWarehouseName(sent.getWarehouseName()); }
            }
        }
        sale.setHasGoodsSend(refund.getShippingStatus()); sale.setStatus(0); sale.setCreateBy(actor); sale.setCreateTime(LocalDateTime.now());
        write(afterSales.insert(sale));
        refund.setHasProcessing(1); refund.setProcessType(refund.getRefundType()); refund.setAfterSaleId(sale.getId()); refund.setErpStatus(refund.getRefundType() == 10 ? 21 : refund.getRefundType() == 20 ? 31 : refund.getRefundType() == 80 ? 41 : 0);
        refund.setUpdateBy(actor); refund.setUpdateTime(LocalDateTime.now()); write(refunds.updateById(refund));
        ShopRefund source = shopRefunds.selectOne(new LambdaQueryWrapper<ShopRefund>().eq(ShopRefund::getShopId, refund.getShopId()).eq(ShopRefund::getAfterId, number));
        require(source != null, "店铺售后来源记录缺失"); source.setConfirmStatus(1); source.setStatus(2); source.setConfirmTime(LocalDateTime.now()); write(shopRefunds.updateById(source));
        return sale.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirm(LocalAfterSaleRequest request, SalesScope scope, String actor) {
        ORefund snapshot = refund(request.getRefundNum(), scope, false);
        OOrderItem sourceItem = orderItems.selectById(snapshot.getOrderItemNum()); require(sourceItem != null, "原订单商品不存在");
        OOrder order = orders.selectOne(new LambdaQueryWrapper<OOrder>().eq(OOrder::getId, sourceItem.getOrderId()).last("FOR UPDATE"));
        require(order != null, "原订单不存在");
        ORefund refund = refund(request.getRefundNum(), scope, true);
        ORefundAfterSale sale = sale(refund, true);
        require(Integer.valueOf(0).equals(sale.getStatus()), "已生成售后仓库任务，请勿重复确认");
        int type = sale.getType(); require(Set.of(10, 20, 80).contains(type), "仅退款无需生成仓库任务");
        boolean vendorReturn = (type == 10 || type == 20) && Integer.valueOf(300).equals(request.getReturnType());
        boolean vendorReissue = (type == 20 || type == 80) && Integer.valueOf(300).equals(request.getReissueType());
        if (vendorReissue) require(scope.merchantId() == null && scope.shopId() == null, "供应商补发目前仅支持总部账号");
        if (vendorReturn) {
            require(scope.merchantId() == null && scope.shopId() == null, "退回供应商目前仅支持总部账号");
            Long supplierId = request.getReturnSupplierId();
            require(supplierId != null && supplierId > 0, "请选择原发货供应商");
            int sent = supplierShipments(order, sourceItem).getOrDefault(supplierId, 0);
            int used = 0;
            for (ORefundAfterSale other : afterSales.selectList(new LambdaQueryWrapper<ORefundAfterSale>()
                    .eq(ORefundAfterSale::getOOrderItemId, sale.getOOrderItemId()).eq(ORefundAfterSale::getReturnType, 300)
                    .eq(ORefundAfterSale::getSupplierId, supplierId))) {
                ORefund prior = refunds.selectById(other.getRefundId());
                if (prior != null && !Integer.valueOf(1).equals(prior.getStatus())) used += other.getQuantity();
            }
            require(supplierId != null && sent > 0 && sale.getQuantity() <= sent - used, "退回数量超过该供应商已发货且尚未退回的数量");
            sale.setReturnType(300); sale.setSupplierId(supplierId);
        } else if (type == 10 || type == 20) require(request.getReturnType() == null || request.getReturnType() == 0, "退货去向无效");
        if (type == 20 || type == 80) require(request.getReissueType() == null || request.getReissueType() == 0 || vendorReissue, "补发方式无效");
        ErpWarehouse returned = type == 80 || vendorReturn ? null : warehouse(request.getReturnWarehouseId(), scope, refund);
        ErpWarehouse outbound = type == 10 || vendorReissue ? null : warehouse(request.getWarehouseId(), scope, refund);
        OGoodsSku original = skus.getOGoodsSkuById(sale.getOGoodsSkuId());
        OGoodsSku replacement = type == 20 ? skus.getOGoodsSkuById(sale.getExchangeErpGoodsSkuId()) : original;
        require(original != null && Integer.valueOf(0).equals(original.getInventoryMode()) && (type == 10 || replacement != null && Integer.valueOf(0).equals(replacement.getInventoryMode())
                && (Objects.equals(replacement.getMerchantId(), 0L) || Objects.equals(replacement.getMerchantId(), refund.getMerchantId()))), "售后商品SKU不存在、库存模式已改变或不属于当前商户");
        ErpLogisticsCompany carrier = type == 10 || vendorReissue ? null : logistics.getErpLogisticsCompanyById(request.getShippingCompanyId());
        if (type != 10) {
            require(text(request.getReceiverName(), 50) && text(request.getReceiverTel(), 30) && text(request.getReceiverAddress(), 200), "请填写收货信息");
            if (!vendorReissue) require(carrier != null && Integer.valueOf(1).equals(carrier.getStatus()) && text(request.getShippingNumber(), 50), "请填写有效的补发物流和运单号");
        }
        if (type != 80) require(text(request.getReturnLogisticsCompany(), 100) && text(request.getReturnLogisticsCode(), 50), "请填写退回物流公司及50字以内的运单号");
        if (returned != null) createInbound(refund, sale, original, returned, actor);
        if (outbound != null) sale.setExchangeErpOrderId(createReissue(refund, sale, replacement, outbound, carrier, request, actor));
        if (vendorReissue) {
            ErpSupplier vendor = validReissueSupplier(request.getReissueSupplierId(), refund.getMerchantId(), replacement);
            sale.setReissueType(300); sale.setExchangeErpOrderId(createVendorReissue(refund, sale, replacement, vendor, request, actor));
        }
        sale.setReturnLogisticsCompany(request.getReturnLogisticsCompany()); sale.setReturnLogisticsCode(request.getReturnLogisticsCode());
        if (returned != null) { sale.setReturnWarehouseId(returned.getId()); sale.setReturnWarehouseName(returned.getWarehouseName()); }
        if (outbound != null) { sale.setReissueWarehouseId(outbound.getId()); sale.setReissueWarehouseName(outbound.getWarehouseName()); sale.setReissueLogisticsCompany(carrier.getName()); sale.setReissueLogisticsCode(request.getShippingNumber().trim()); }
        sale.setStatus(1); sale.setResult(vendorReissue ? "已登记供应商补发任务，尚未通知供应商或发货" : vendorReturn ? "已登记退回供应商，待确认对方实际收货" : "已生成仓库任务，待实际入库/出库"); sale.setUpdateBy(actor); sale.setUpdateTime(LocalDateTime.now()); write(afterSales.updateById(sale));
    }

    public Map<String, Object> detail(String number, SalesScope scope) {
        ORefund refund = refund(number, scope, false);
        ORefundAfterSale sale = refund.getAfterSaleId() == null ? null : afterSales.selectById(refund.getAfterSaleId());
        Map<String, Object> data = new LinkedHashMap<>(); data.put("refund", refund); data.put("afterSale", sale);
        data.put("inbound", stockIns.listErpStockIn(new LambdaQueryWrapper<ErpStockIn>().eq(ErpStockIn::getSourceNo, number).eq(ErpStockIn::getStockInType, 2)));
        var details = outboundItems(sale);
        data.put("outbound", details.isEmpty() ? List.of() : stockOuts.listErpStockOut(new LambdaQueryWrapper<ErpStockOut>().in(ErpStockOut::getId, details.stream().map(ErpStockOutItem::getEntryId).distinct().toList())));
        data.put("stockingId", sale == null || Integer.valueOf(300).equals(sale.getReissueType()) ? null : sale.getExchangeErpOrderId());
        data.put("supplierReissue", sale != null && Integer.valueOf(300).equals(sale.getReissueType()) && sale.getExchangeErpOrderId() != null ? stockings.getOOrderStockingById(sale.getExchangeErpOrderId()) : null);
        OOrderItem item = orderItems.selectById(refund.getOrderItemNum());
        OOrder order = item == null ? null : orders.selectById(item.getOrderId());
        data.put("returnSuppliers", order == null ? Map.of() : supplierShipments(order, item));
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(LocalAfterSaleRequest request, SalesScope scope, String actor) {
        ORefund snapshot = refund(request.getRefundNum(), scope, false);
        OOrderItem original = orderItems.selectById(snapshot.getOrderItemNum());
        require(original != null, "原订单商品不存在");
        orders.selectOne(new LambdaQueryWrapper<OOrder>().eq(OOrder::getId, original.getOrderId()).last("FOR UPDATE"));
        ORefund refund = refund(request.getRefundNum(), scope, true); ORefundAfterSale sale = sale(refund, true);
        require(!Integer.valueOf(10).equals(sale.getStatus()), "售后已办结，请勿重复操作");
        int type = sale.getType();
        if ((type == 10 || type == 20) && Integer.valueOf(300).equals(sale.getReturnType())) {
            require(Integer.valueOf(1).equals(sale.getStatus()) && Boolean.TRUE.equals(request.getReturnReceived()) && text(request.getRemark(), 200),
                    "请先确认供应商实际收到退货，并填写收货凭据或说明");
        } else if (type == 10 || type == 20) {
            var entries = stockIns.listErpStockIn(new LambdaQueryWrapper<ErpStockIn>().eq(ErpStockIn::getSourceNo, refund.getRefundNum()).eq(ErpStockIn::getStockInType, 2));
            require(entries.size() == 1 && Integer.valueOf(2).equals(entries.get(0).getStatus()), "退货尚未实际全部入库");
        }
        if (type == 20 || type == 80) {
            require(sale.getExchangeErpOrderId() != null, "尚未生成换货或补发任务");
            if (Integer.valueOf(300).equals(sale.getReissueType())) {
                OOrderStocking task = stockings.getOOrderStockingById(sale.getExchangeErpOrderId());
                require(task != null && Integer.valueOf(300).equals(task.getType()) && task.getOrderType() == type
                        && Objects.equals(task.getOrderNum(), refund.getRefundNum()) && Objects.equals(task.getOOrderId(), sale.getOOrderId()) && Integer.valueOf(2).equals(task.getSendStatus())
                        && text(task.getWaybillCode(), 50), "供应商换货或补发尚未登记发货");
                var details = stockingItems.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>().eq(OOrderStockingItem::getShipOrderId, task.getId()));
                require(details.size() == 1 && Integer.valueOf(2).equals(details.get(0).getSendStatus())
                        && Objects.equals(details.get(0).getQuantity(), sale.getQuantity()) && Objects.equals(details.get(0).getOOrderItemId(), sale.getOOrderItemId())
                        && Objects.equals(details.get(0).getGoodsSkuId(), type == 20 ? sale.getExchangeErpGoodsSkuId() : sale.getOGoodsSkuId())
                        && Objects.equals(details.get(0).getSupplierId(), task.getShipperId()), "供应商换货或补发明细不一致");
            } else {
                var entries = outboundItems(sale);
                require(!entries.isEmpty() && entries.stream().allMatch(e -> Integer.valueOf(2).equals(e.getStatus()) && Objects.equals(e.getOriginalQuantity(), e.getOutQuantity()))
                        && entries.stream().mapToInt(ErpStockOutItem::getOutQuantity).sum() == sale.getQuantity(), "换货或补发尚未实际全部出库");
            }
        }
        if (Set.of(1, 11, 10).contains(type) && refund.getRefundFee() != null && refund.getRefundFee() > 0)
            require(Boolean.TRUE.equals(request.getRefundPaid()) && text(request.getRemark(), 200), "请先在原收款渠道退款，再勾选确认并填写退款凭据或说明");
        sale.setStatus(10); sale.setResult(Integer.valueOf(300).equals(sale.getReturnType()) ? "已人工确认供应商收货并办结；退款如有发生由人工登记" : "已核对仓库任务并办结；退款为人工登记"); sale.setRemark(request.getRemark()); sale.setUpdateBy(actor); sale.setUpdateTime(LocalDateTime.now()); write(afterSales.updateById(sale));
        refund.setErpStatus(type == 20 ? 32 : type == 80 ? 42 : type == 10 ? 22 : 10); refund.setStatus(4); refund.setUpdateBy(actor); refund.setUpdateTime(LocalDateTime.now()); write(refunds.updateById(refund));
        ShopRefund source = shopRefunds.selectOne(new LambdaQueryWrapper<ShopRefund>().eq(ShopRefund::getShopId, refund.getShopId()).eq(ShopRefund::getAfterId, refund.getRefundNum()));
        require(source != null, "店铺售后来源记录缺失"); source.setConfirmStatus(1); source.setConfirmTime(LocalDateTime.now()); source.setStatus(4); source.setRefundStatus(refund.getRefundFee() != null && refund.getRefundFee() > 0 ? 3 : 0); source.setRemark(request.getRemark()); write(shopRefunds.updateById(source));
        OOrderItem item = orderItems.selectOne(new LambdaQueryWrapper<OOrderItem>().eq(OOrderItem::getId, sale.getOOrderItemId()).last("FOR UPDATE"));
        if (type == 1) { preSaleStatus(orders.selectById(sale.getOOrderId()), 11, actor); item.setRefundCount(item.getQuantity()); }
        if (type == 10) item.setRefundCount((item.getRefundCount() == null ? 0 : item.getRefundCount()) + refund.getQuantity());
        long active = refunds.selectCount(new LambdaQueryWrapper<ORefund>().eq(ORefund::getShopId, refund.getShopId()).eq(ORefund::getOrderNum, refund.getOrderNum()).eq(ORefund::getOrderItemNum, item.getId()).likeRight(ORefund::getRefundNum, "LOCAL-AF-").notIn(ORefund::getStatus, 1, 4));
        item.setRefundStatus(active == 0 ? 1 : 2); write(orderItems.updateById(item));
    }

    private void preSaleStatus(OOrder order, int status, String actor) {
        order.setOrderStatus(status); order.setUpdateBy(actor); order.setUpdateTime(LocalDateTime.now()); write(orders.updateById(order));
        var source = sourceOrders.selectOne(new LambdaQueryWrapper<ShopOrder>().eq(ShopOrder::getShopId, order.getShopId()).eq(ShopOrder::getOrderId, order.getOrderNum()));
        if (source != null) { source.setOrderStatus(status); source.setRefundStatus(status == 12 ? 2 : status == 11 ? 4 : 1); source.setUpdateOn(LocalDateTime.now()); write(sourceOrders.updateById(source)); }
    }

    private void createInbound(ORefund refund, ORefundAfterSale sale, OGoodsSku sku, ErpWarehouse warehouse, String actor) {
        ErpStockIn entry = new ErpStockIn(); entry.setStockInNum("THRK-" + UUID.randomUUID().toString().replace("-", "").substring(0,24)); entry.setStockInType(2); entry.setSourceNo(refund.getRefundNum()); entry.setSourceId(Long.valueOf(refund.getId()));
        entry.setWarehouseId(warehouse.getId()); entry.setWarehouseType("LOCAL"); entry.setWarehouseName(warehouse.getWarehouseName()); entry.setWarehouseNo(warehouse.getWarehouseNo()); entry.setMerchantId(refund.getMerchantId()); entry.setShopId(refund.getShopId());
        entry.setSourceGoodsUnit(1); entry.setSourceSpecUnit(1); entry.setSourceSpecUnitTotal(sale.getQuantity()); entry.setStatus(0); entry.setCreateBy(actor); entry.setCreateTime(LocalDateTime.now()); write(stockIns.saveErpStockIn(entry));
        ErpStockInItem item = new ErpStockInItem(); item.setStockInId(entry.getId()); item.setStockInType(2); item.setSourceId(Long.valueOf(refund.getId())); item.setSourceItemId(Long.valueOf(sale.getId())); item.setSourceNo(refund.getRefundNum());
        item.setGoodsId(sku.getGoodsId()); item.setGoodsNum(sku.getGoodsNum()); item.setGoodsName(sku.getGoodsName()); item.setGoodsImage(sku.getColorImage()); item.setSkuId(sku.getId()); item.setSkuCode(sku.getSkuCode()); item.setSkuName(sku.getSkuName()); item.setInventoryMode(0);
        item.setQuantity(sale.getQuantity()); item.setInQuantity(0); item.setStatus(0); item.setPurPrice(sku.getPurPrice() == null ? 0 : sku.getPurPrice().doubleValue()); item.setWarehouseId(warehouse.getId()); item.setMerchantId(refund.getMerchantId()); item.setShopId(refund.getShopId()); item.setCreateBy(actor); item.setCreateTime(LocalDateTime.now()); write(stockInItems.saveErpStockInItem(item));
    }

    private Long createReissue(ORefund refund, ORefundAfterSale sale, OGoodsSku sku, ErpWarehouse warehouse, ErpLogisticsCompany carrier, LocalAfterSaleRequest req, String actor) {
        OOrder original = orders.selectById(sale.getOOrderId());
        OOrderStocking entry = new OOrderStocking(); entry.setOrderType(sale.getType()); entry.setOOrderId(sale.getOOrderId()); entry.setOrderNum(refund.getRefundNum()); entry.setShopType(refund.getShopType()); entry.setShopId(refund.getShopId()); entry.setMerchantId(refund.getMerchantId());
        entry.setOrderTime(LocalDateTime.now()); entry.setType(0); entry.setShipperId(0L); entry.setShipMode(0); entry.setSendStatus(2); entry.setOrderStatus(2); entry.setStockingStatus(0); entry.setWaybillStatus(10);
        entry.setWarehouseId(warehouse.getId()); entry.setWarehouseName(warehouse.getWarehouseName()); entry.setWarehouseType("LOCAL"); entry.setWarehouseNo(warehouse.getWarehouseNo()); entry.setReceiverName(req.getReceiverName()); entry.setReceiverMobile(req.getReceiverTel()); entry.setAddress(req.getReceiverAddress());
        entry.setProvince(original.getProvince()); entry.setCity(original.getCity()); entry.setTown(original.getTown()); entry.setShippingCompany(carrier.getName()); entry.setShippingCompanyCode(carrier.getCode()); entry.setShippingNumber(req.getShippingNumber().trim()); entry.setWaybillCode(req.getShippingNumber().trim()); entry.setWaybillCompany(carrier.getName()); entry.setShippingTime(LocalDateTime.now()); entry.setShippingMan(actor); entry.setRemark("售后" + refund.getRefundNum()); entry.setCreateBy(actor); entry.setCreateTime(LocalDateTime.now()); write(stockings.saveOOrderStocking(entry));
        OOrderStockingItem item = new OOrderStockingItem(); item.setShipOrderId(entry.getId()); item.setOOrderId(sale.getOOrderId()); item.setOOrderItemId(sale.getOOrderItemId()); item.setMerchantId(refund.getMerchantId()); item.setOrderNum(entry.getOrderNum()); item.setSubOrderNum(sale.getSubOrderNum()); item.setOrderTime(entry.getOrderTime()); item.setSupplierId(0L);
        item.setProductId(""); item.setSkuId(sku.getId()); item.setGoodsId(Long.valueOf(sku.getGoodsId())); item.setGoodsSkuId(Long.valueOf(sku.getId())); item.setGoodsNum(sku.getGoodsNum()); item.setGoodsName(sku.getGoodsName()); item.setGoodsImg(sku.getColorImage()); item.setSkuName(sku.getSkuName()); item.setSkuCode(sku.getSkuCode());
        item.setQuantity(sale.getQuantity()); item.setUnshippedQuantity(0); item.setRefundStatus(1); item.setSendStatus(2); item.setStockingStatus(0); item.setWarehouseId(warehouse.getId()); item.setWarehouseType("LOCAL"); item.setWarehouseName(warehouse.getWarehouseName()); item.setWaybillStatus(10); item.setWaybillCode(req.getShippingNumber().trim()); item.setCreateBy(actor); item.setCreateTime(LocalDateTime.now()); write(stockingItems.saveOOrderStockingItem(item));
        return entry.getId();
    }

    private ErpSupplier validReissueSupplier(Long id, Long merchantId, OGoodsSku sku) {
        ErpSupplier supplier = id == null ? null : suppliers.getErpSupplierById(id);
        require(supplier != null && Integer.valueOf(1).equals(supplier.getIsShipper()) && !Integer.valueOf(1).equals(supplier.getDisable())
                && !Integer.valueOf(1).equals(supplier.getIsDelete()) && merchantId != null
                && (Objects.equals(supplier.getMerchantId(), merchantId) || supplier.getMerchantIds() != null
                && Arrays.stream(supplier.getMerchantIds().split(",")).map(String::trim).anyMatch(v -> v.equals(merchantId.toString()))), "供应商不可用或未授权给商户");
        List<ErpSupplierProductItem> offers = supplierItems.listErpSupplierProductItem(new LambdaQueryWrapper<ErpSupplierProductItem>()
                .eq(ErpSupplierProductItem::getSupplierId, id).eq(ErpSupplierProductItem::getErpGoodsSkuId, sku.getId())
                .eq(ErpSupplierProductItem::getErpGoodsId, sku.getGoodsId()).eq(ErpSupplierProductItem::getStatus, 1));
        require(offers.stream().anyMatch(offer -> {
            ErpSupplierProduct product = offer.getSupplierProductId() == null ? null : supplierProducts.getErpSupplierProductById(offer.getSupplierProductId());
            return product != null && Integer.valueOf(1).equals(product.getStatus()) && Objects.equals(product.getSupplierId(), id)
                    && Objects.equals(String.valueOf(product.getErpGoodsId()), sku.getGoodsId());
        }), "供应商未关联已启用的换出或补发商品SKU");
        return supplier;
    }

    private Long createVendorReissue(ORefund refund, ORefundAfterSale sale, OGoodsSku sku, ErpSupplier supplier, LocalAfterSaleRequest req, String actor) {
        OOrder original = orders.selectById(sale.getOOrderId()); LocalDateTime now = LocalDateTime.now();
        OOrderStocking task = new OOrderStocking(); task.setOrderType(sale.getType()); task.setOOrderId(sale.getOOrderId()); task.setOrderNum(refund.getRefundNum());
        task.setShopType(refund.getShopType()); task.setShopId(refund.getShopId()); task.setMerchantId(refund.getMerchantId()); task.setOrderTime(now);
        task.setType(300); task.setShipperId(supplier.getId()); task.setShipperNo(supplier.getNumber()); task.setShipMode(0); task.setSendStatus(0); task.setOrderStatus(1); task.setStockingStatus(0); task.setWaybillStatus(0);
        task.setReceiverName(req.getReceiverName()); task.setReceiverMobile(req.getReceiverTel()); task.setAddress(req.getReceiverAddress());
        task.setProvince(original.getProvince()); task.setCity(original.getCity()); task.setTown(original.getTown());
        task.setRemark("售后供应商任务，仅本地登记，未推送外部系统"); task.setCreateBy(actor); task.setCreateTime(now); write(stockings.saveOOrderStocking(task));
        OOrderStockingItem d = new OOrderStockingItem(); d.setShipOrderId(task.getId()); d.setOOrderId(sale.getOOrderId()); d.setOOrderItemId(sale.getOOrderItemId());
        d.setMerchantId(refund.getMerchantId()); d.setOrderNum(task.getOrderNum()); d.setSubOrderNum(sale.getSubOrderNum()); d.setOrderTime(now); d.setSupplierId(supplier.getId());
        d.setProductId(""); d.setSkuId(sku.getId()); d.setGoodsId(Long.valueOf(sku.getGoodsId())); d.setGoodsSkuId(Long.valueOf(sku.getId())); d.setGoodsNum(sku.getGoodsNum());
        d.setGoodsName(sku.getGoodsName()); d.setGoodsImg(sku.getColorImage()); d.setSkuName(sku.getSkuName()); d.setSkuCode(sku.getSkuCode()); d.setQuantity(sale.getQuantity()); d.setUnshippedQuantity(sale.getQuantity());
        d.setRefundStatus(1); d.setSendStatus(0); d.setStockingStatus(0); d.setCreateBy(actor); d.setCreateTime(now); write(stockingItems.saveOOrderStockingItem(d)); return task.getId();
    }

    /** 线下核实供应商发货后登记物流，不修改原销售订单，也不扣本地库存。 */
    @Transactional(rollbackFor = Exception.class)
    public void confirmVendorReissue(LocalAfterSaleRequest request, SalesScope scope, String actor) {
        require(scope.merchantId() == null && scope.shopId() == null, "供应商补发目前仅支持总部账号");
        ORefund snapshot = refund(request.getRefundNum(), scope, false);
        OOrderItem original = orderItems.selectById(snapshot.getOrderItemNum()); require(original != null, "原订单商品不存在");
        OOrder order = orders.selectOne(new LambdaQueryWrapper<OOrder>().eq(OOrder::getId, original.getOrderId()).last("FOR UPDATE")); require(order != null, "原订单不存在");
        ORefund refund = refund(request.getRefundNum(), scope, true); ORefundAfterSale sale = sale(refund, true);
        require(Integer.valueOf(1).equals(sale.getStatus()) && Integer.valueOf(300).equals(sale.getReissueType()) && sale.getExchangeErpOrderId() != null, "没有可登记的供应商补发任务");
        OOrderStocking task = stockings.getOOrderStockingOne(new LambdaQueryWrapper<OOrderStocking>().eq(OOrderStocking::getId, sale.getExchangeErpOrderId()).last("FOR UPDATE"));
        require(task != null && Integer.valueOf(300).equals(task.getType()) && task.getOrderType() == sale.getType()
                && Objects.equals(task.getOrderNum(), refund.getRefundNum()) && Objects.equals(task.getOOrderId(), sale.getOOrderId()) && Integer.valueOf(0).equals(task.getSendStatus()), "供应商补发任务不存在或已发货");
        ErpLogisticsCompany carrier = request.getShippingCompanyId() == null ? null : logistics.getErpLogisticsCompanyById(request.getShippingCompanyId());
        require(carrier != null && Integer.valueOf(1).equals(carrier.getStatus()) && text(request.getShippingNumber(), 50), "请选择物流并填写运单号");
        List<OOrderStockingItem> details = stockingItems.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>().eq(OOrderStockingItem::getShipOrderId, task.getId()).last("FOR UPDATE"));
        require(details.size() == 1 && Integer.valueOf(0).equals(details.get(0).getSendStatus()) && Objects.equals(details.get(0).getQuantity(), sale.getQuantity())
                && Objects.equals(details.get(0).getSupplierId(), task.getShipperId()) && Objects.equals(details.get(0).getOOrderItemId(), sale.getOOrderItemId())
                && Objects.equals(details.get(0).getGoodsSkuId(), sale.getType() == 20 ? sale.getExchangeErpGoodsSkuId() : sale.getOGoodsSkuId()), "供应商补发商品不一致");
        LocalDateTime now = LocalDateTime.now(); String code = request.getShippingNumber().trim();
        OOrderStockingItem d = new OOrderStockingItem(); d.setId(details.get(0).getId()); d.setSendStatus(2); d.setUnshippedQuantity(0); d.setWaybillCode(code); d.setWaybillStatus(10); d.setUpdateBy(actor); d.setUpdateTime(now); write(stockingItems.updateOOrderStockingItem(d));
        OOrderStocking t = new OOrderStocking(); t.setId(task.getId()); t.setOrderType(task.getOrderType()); t.setSendStatus(2); t.setOrderStatus(2); t.setWaybillCode(code); t.setWaybillCompany(carrier.getName()); t.setWaybillStatus(10); t.setShippingCompany(carrier.getName()); t.setShippingCompanyCode(carrier.getCode()); t.setShippingNumber(code); t.setShippingMan(actor); t.setShippingTime(now); t.setUpdateBy(actor); t.setUpdateTime(now); write(stockings.updateOOrderStocking(t));
        sale.setReissueLogisticsCompany(carrier.getName()); sale.setReissueLogisticsCode(code); sale.setUpdateBy(actor); sale.setUpdateTime(now); write(afterSales.updateById(sale));
    }

    private List<OOrderStocking> originalShipments(OOrder order) {
        return stockings.listOOrderStocking(new LambdaQueryWrapper<OOrderStocking>().eq(OOrderStocking::getOOrderId, order.getId())
                .eq(OOrderStocking::getOrderType, 0).and(w -> w.isNull(OOrderStocking::getOrderStatus).or().ne(OOrderStocking::getOrderStatus, 11)));
    }
    private Map<Long, Integer> supplierShipments(OOrder order, OOrderItem item) {
        List<OOrderStocking> sent = originalShipments(order).stream().filter(t -> Integer.valueOf(300).equals(t.getType()) && Integer.valueOf(2).equals(t.getSendStatus())).toList();
        Map<Long, Integer> result = new HashMap<>();
        if (sent.isEmpty()) return result;
        Map<Long, OOrderStocking> taskMap = new HashMap<>(); sent.forEach(t -> taskMap.put(t.getId(), t));
        for (OOrderStockingItem d : stockingItems.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>()
                .in(OOrderStockingItem::getShipOrderId, taskMap.keySet()).eq(OOrderStockingItem::getOOrderItemId, item.getId()))) {
            OOrderStocking task = taskMap.get(d.getShipOrderId());
            require(task.getShipperId() != null && task.getShipperId() > 0 && Objects.equals(task.getShipperId(), d.getSupplierId())
                    && Integer.valueOf(2).equals(d.getSendStatus()) && d.getQuantity() != null && d.getQuantity() > 0, "供应商发货明细不一致");
            result.merge(task.getShipperId(), d.getQuantity(), Math::addExact);
        }
        return result;
    }
    private int shippedQuantity(OOrder order, OOrderItem item) {
        List<OOrderStocking> tasks = originalShipments(order);
        require(tasks.stream().allMatch(t -> Integer.valueOf(0).equals(t.getType()) || Integer.valueOf(300).equals(t.getType())), "当前入口不处理云仓售后");
        int vendor = supplierShipments(order, item).values().stream().mapToInt(Integer::intValue).sum();
        List<Long> local = tasks.stream().filter(t -> Integer.valueOf(0).equals(t.getType()) && Integer.valueOf(2).equals(t.getSendStatus())).map(OOrderStocking::getId).toList();
        if (local.isEmpty()) return vendor;
        var sources = stockingItems.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>().in(OOrderStockingItem::getShipOrderId, local).eq(OOrderStockingItem::getOOrderItemId, item.getId()));
        require(sources.stream().allMatch(d -> "LOCAL".equals(d.getWarehouseType())), "本地发货仓库信息不一致");
        if (sources.isEmpty()) return vendor;
        int localOut = stockOutItems.listErpStockOutItem(new LambdaQueryWrapper<ErpStockOutItem>().eq(ErpStockOutItem::getType, 1).in(ErpStockOutItem::getSourceOrderItemId, sources.stream().map(OOrderStockingItem::getId).toList()))
                .stream().mapToInt(r -> r.getOutQuantity() == null ? 0 : r.getOutQuantity()).sum();
        return Math.addExact(vendor, localOut);
    }

    private List<ErpStockOutItem> outboundItems(ORefundAfterSale sale) {
        if (sale == null || sale.getExchangeErpOrderId() == null || Integer.valueOf(300).equals(sale.getReissueType())) return List.of();
        return stockOutItems.listErpStockOutItem(new LambdaQueryWrapper<ErpStockOutItem>().eq(ErpStockOutItem::getType, 1).eq(ErpStockOutItem::getSourceOrderId, sale.getExchangeErpOrderId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(String number, SalesScope scope, String actor) {
        ORefund snapshot = refund(number, scope, false);
        OOrderItem original = orderItems.selectById(snapshot.getOrderItemNum()); require(original != null, "原订单商品不存在");
        orders.selectOne(new LambdaQueryWrapper<OOrder>().eq(OOrder::getId, original.getOrderId()).last("FOR UPDATE"));
        ORefund refund = refund(number, scope, true);
        require(!Set.of(1,4).contains(refund.getStatus()), "售后已关闭或已办结");
        ORefundAfterSale sale = refund.getAfterSaleId() == null ? null : sale(refund, true);
        require(sale == null || Integer.valueOf(0).equals(sale.getStatus()), "已生成仓库任务的售后不能直接取消，请先核对实物与仓库单据");
        if (sale != null) { sale.setStatus(10); sale.setResult("申请已取消，未生成仓库任务"); sale.setUpdateBy(actor); sale.setUpdateTime(LocalDateTime.now()); write(afterSales.updateById(sale)); }
        refund.setStatus(1); refund.setHasProcessing(9); refund.setUpdateBy(actor); refund.setUpdateTime(LocalDateTime.now()); write(refunds.updateById(refund));
        ShopRefund source = shopRefunds.selectOne(new LambdaQueryWrapper<ShopRefund>().eq(ShopRefund::getAfterId, number).eq(ShopRefund::getShopId, refund.getShopId()));
        require(source != null, "店铺售后来源记录缺失"); source.setStatus(1); source.setConfirmStatus(1); write(shopRefunds.updateById(source));
        OOrderItem item = orderItems.selectOne(new LambdaQueryWrapper<OOrderItem>().eq(OOrderItem::getId, original.getId()).last("FOR UPDATE"));
        long active = refunds.selectCount(new LambdaQueryWrapper<ORefund>().eq(ORefund::getOrderNum, refund.getOrderNum()).eq(ORefund::getShopId, refund.getShopId()).eq(ORefund::getOrderItemNum, item.getId()).likeRight(ORefund::getRefundNum, "LOCAL-AF-").notIn(ORefund::getStatus, 1, 4));
        item.setRefundStatus(active == 0 ? 1 : 2); write(orderItems.updateById(item));
        if (refund.getRefundType() == 1) preSaleStatus(orders.selectById(item.getOrderId()), 1, actor);
    }

    private ORefund refund(String number, SalesScope scope, boolean lock) {
        require(number != null && number.startsWith("LOCAL-AF-"), "此操作仅支持本地手工售后，平台售后不能在此登记为已完成");
        var query = new LambdaQueryWrapper<ORefund>().eq(ORefund::getRefundNum, number); if (lock) query.last("FOR UPDATE");
        ORefund refund = refunds.selectOne(query); require(refund != null, "售后单不存在"); scope.check(refund.getMerchantId(), refund.getShopId());
        if (scope.merchantId() != null || scope.shopId() != null) {
            OOrderItem item = orderItems.selectById(refund.getOrderItemNum());
            OOrder order = item == null ? null : orders.selectById(item.getOrderId());
            require(order != null && supplierShipments(order, item).isEmpty(), "供应商或混合发货售后目前仅支持总部账号");
        }
        return refund;
    }
    private ORefundAfterSale sale(ORefund refund, boolean lock) {
        require(refund.getAfterSaleId() != null, "请先处理售后单");
        var query = new LambdaQueryWrapper<ORefundAfterSale>().eq(ORefundAfterSale::getId, refund.getAfterSaleId()); if (lock) query.last("FOR UPDATE");
        ORefundAfterSale sale = afterSales.selectOne(query); require(sale != null, "售后处理记录不存在"); return sale;
    }
    private ErpWarehouse warehouse(Long id, SalesScope scope, ORefund refund) {
        ErpWarehouse warehouse = id == null ? null : warehouses.getErpWarehouseById(id);
        require(warehouse != null && "LOCAL".equals(warehouse.getWarehouseType()) && "1".equals(warehouse.getStatus()), "请选择启用的本地仓库");
        require(Objects.equals(warehouse.getMerchantId(), 0L) || Objects.equals(warehouse.getMerchantId(), refund.getMerchantId()), "仓库不属于当前商户"); return warehouse;
    }
    private static boolean text(String value, int max) { return StringUtils.hasText(value) && value.trim().length() <= max; }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
    private static void write(int count) { if (count != 1) throw new IllegalStateException("售后资料保存失败，请重试"); }
}
