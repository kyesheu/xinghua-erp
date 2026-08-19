package cn.xinghuaerp.purchase.service;

import cn.xinghuaerp.system.entity.ErpLogisticsCompany;
import cn.xinghuaerp.product.facade.ProductFacade;
import cn.xinghuaerp.inventory.facade.InventoryFacade;
import cn.xinghuaerp.system.facade.SystemFacade;
import cn.xinghuaerp.purchase.facade.PurchaseFacade;
import cn.xinghuaerp.order.facade.OrderFacade;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.entity.OOrderStocking;
import cn.xinghuaerp.inventory.entity.OOrderStockingItem;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.entity.OOrderItem;
import cn.xinghuaerp.order.entity.ShopOrder;
import cn.xinghuaerp.order.entity.ShopOrderItem;
import cn.xinghuaerp.product.entity.ErpSupplierProductItem;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.purchase.entity.ErpSupplierProduct;
import cn.xinghuaerp.purchase.request.SupplierDispatchConfirmRequest;
import cn.xinghuaerp.purchase.request.SupplierDispatchRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

import cn.xinghuaerp.purchase.service.SupplierDispatchService;
/** 本地登记供应商代发任务；不推送第三方、不扣本地仓库存。 */
@Service
@RequiredArgsConstructor
public class SupplierDispatchService {
    private final OrderFacade orders;
    private final OrderFacade items;
    private final InventoryFacade tasks;
    private final InventoryFacade taskItems;
    private final PurchaseFacade suppliers;
    private final InventoryFacade warehouses;
    private final ProductFacade supplyItems;
    private final PurchaseFacade supplyProducts;
    private final ProductFacade skus;
    private final SystemFacade logistics;
    private final OrderFacade sourceOrders;
    private final OrderFacade sourceItems;

    /** 批量整单采用同一事务，任意一单校验失败时全部不分配。 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> allocate(SupplierDispatchRequest request, String actor) {
        return allocateInternal(request, actor, false, false);
    }

    /** 按明细分配：一次选择同一订单的一个或多个完整明细。 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> allocateItems(SupplierDispatchRequest request, String actor) {
        return allocateInternal(request, actor, true, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Long> allocateLocalItems(SupplierDispatchRequest request, String actor) {
        return allocateInternal(request, actor, true, true);
    }

    private List<Long> allocateInternal(SupplierDispatchRequest request, String actor, boolean byItems, boolean local) {
        require(request != null && (local ? request.getWarehouseId() != null && request.getWarehouseId() > 0 : request.getSupplierId() != null && request.getSupplierId() > 0), local ? "请选择本地仓库" : "请选择供应商");
        require(request.getIds() != null && !request.getIds().isEmpty() && request.getIds().size() <= 100, "请选择1至100个订单");
        List<Long> requestedIds = request.getIds();
        require(requestedIds.stream().allMatch(id -> id != null && id > 0) && new HashSet<>(requestedIds).size() == requestedIds.size(), "ID无效或重复");
        Set<String> selectedItems = new HashSet<>();
        List<Long> ids;
        if (byItems) {
            List<OOrderItem> selected = items.listOOrderItemByIds(requestedIds);
            require(selected.size() == requestedIds.size() && selected.stream().allMatch(i -> i.getOrderId() != null), "订单商品不存在");
            Set<String> orderIds = new HashSet<>();
            selected.forEach(i -> { selectedItems.add(i.getId()); orderIds.add(i.getOrderId()); });
            require(orderIds.size() == 1, "一次只能分配同一订单的商品");
            try { ids = List.of(Long.valueOf(orderIds.iterator().next())); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("订单ID无效"); }
        } else ids = requestedIds;
        ErpSupplier supplier = local ? null : suppliers.getErpSupplierById(request.getSupplierId());
        ErpWarehouse warehouse = local ? warehouses.getErpWarehouseById(request.getWarehouseId()) : null;
        if (local) require(warehouse != null && "LOCAL".equals(warehouse.getWarehouseType()) && "1".equals(warehouse.getStatus()), "请选择启用的本地仓库");
        else require(supplier != null && Integer.valueOf(1).equals(supplier.getIsShipper())
                && !Integer.valueOf(1).equals(supplier.getDisable()) && !Integer.valueOf(1).equals(supplier.getIsDelete()), "供应商不存在、已停用或不支持订单代发");
        List<Long> created = new ArrayList<>();
        // 与手动发货及本地售后保持相同的原订单优先锁顺序。
        for (Long id : ids.stream().sorted().toList()) {
            OOrder order = orders.getOOrderOne(new LambdaQueryWrapper<OOrder>().eq(OOrder::getId, id).last("FOR UPDATE"));
            require(order != null && Integer.valueOf(1).equals(order.getOrderMode()), "仅支持已同步的本地手工订单");
            boolean pendingOrder = Integer.valueOf(1).equals(order.getOrderStatus()) && empty(order.getShipStatus());
            boolean partiallyShipped = byItems && Integer.valueOf(101).equals(order.getOrderStatus()) && Integer.valueOf(1).equals(order.getShipStatus());
            require((pendingOrder || partiallyShipped) && empty(order.getWaybillStatus()), "订单状态不允许分配商品");
            require(local ? authorizedWarehouse(warehouse, order.getMerchantId()) : authorized(supplier, order.getMerchantId()), local ? "仓库未授权给该订单所属商户" : "供应商未授权给该订单所属商户");
            List<OOrderStocking> activeTasks = tasks.listOOrderStocking(new LambdaQueryWrapper<OOrderStocking>().eq(OOrderStocking::getOOrderId, id).and(w -> w.isNull(OOrderStocking::getOrderStatus).or().ne(OOrderStocking::getOrderStatus, 11)));
            require(byItems ? activeTasks.stream().allMatch(t -> (Integer.valueOf(300).equals(t.getType()) || Integer.valueOf(0).equals(t.getType())) && t.getOrderType() == 0 && (Integer.valueOf(0).equals(t.getSendStatus()) || Integer.valueOf(2).equals(t.getSendStatus()))) : activeTasks.isEmpty() && empty(order.getDistStatus()), "订单已有不兼容的发货任务");
            Map<String, Integer> allocated = quantities(activeTasks, false);
            List<OOrderItem> allDetails = items.listOOrderItem(new LambdaQueryWrapper<OOrderItem>().eq(OOrderItem::getOrderId, id).orderByAsc(OOrderItem::getId).last("FOR UPDATE"));
            List<OOrderItem> details = byItems ? allDetails.stream().filter(i -> selectedItems.contains(i.getId())).toList() : allDetails;
            require(!details.isEmpty() && (!byItems || details.size() == selectedItems.size()), "订单商品不存在或已变更");
            Map<String, Integer> requested = new HashMap<>();
            for (OOrderItem item : details) {
                require(Integer.valueOf(1).equals(item.getRefundStatus()) && item.getQuantity() != null
                        && item.getQuantity() > 0 && empty(item.getRefundCount()), "订单商品存在售后或数量无效");
                int remaining = item.getQuantity() - allocated.getOrDefault(item.getId(), 0);
                Integer amount = byItems && request.getQuantities() != null ? request.getQuantities().get(item.getId()) : remaining;
                require(amount != null && amount > 0 && amount <= remaining, "分配数量必须大于0且不能超过未分配数量");
                requested.put(item.getId(), amount);
                OGoodsSku sku = item.getGoodsSkuId() == null ? null : skus.getOGoodsSkuById(item.getGoodsSkuId());
                require(sku != null && Objects.equals(sku.getGoodsId(), String.valueOf(item.getGoodsId())), "订单商品未正确关联商品库SKU");
                if (!local) {
                List<ErpSupplierProductItem> offers = supplyItems.listErpSupplierProductItem(new LambdaQueryWrapper<ErpSupplierProductItem>()
                        .eq(ErpSupplierProductItem::getSupplierId, supplier.getId()).eq(ErpSupplierProductItem::getErpGoodsSkuId, item.getGoodsSkuId())
                        .eq(ErpSupplierProductItem::getErpGoodsId, item.getGoodsId()).eq(ErpSupplierProductItem::getStatus, 1));
                boolean validOffer = offers.stream().anyMatch(offer -> {
                    if (offer.getSupplierProductId() == null) return false;
                    ErpSupplierProduct product = supplyProducts.getErpSupplierProductById(offer.getSupplierProductId());
                    return product != null && Integer.valueOf(1).equals(product.getStatus())
                            && Objects.equals(product.getSupplierId(), supplier.getId())
                            && Objects.equals(product.getErpGoodsId(), item.getGoodsId());
                });
                require(validOffer, "供应商未关联已启用的商品及供货SKU");
                }
            }
            LocalDateTime now = LocalDateTime.now();
            OOrderStocking task = new OOrderStocking();
            task.setType(local ? 0 : 300); task.setOrderType(0); task.setShipMode(0); task.setShipperId(local ? 0L : supplier.getId()); task.setShipperNo(local ? warehouse.getWarehouseNo() : supplier.getNumber());
            if (local) { task.setWarehouseId(warehouse.getId()); task.setWarehouseType("LOCAL"); task.setWarehouseName(warehouse.getWarehouseName()); task.setWarehouseNo(warehouse.getWarehouseNo()); }
            task.setOOrderId(id); task.setOrderNum(order.getOrderNum()); task.setOrderTime(order.getOrderTime()); task.setMerchantId(order.getMerchantId());
            task.setShopId(order.getShopId()); task.setShopType(order.getShopType()); task.setOrderStatus(1); task.setSendStatus(0); task.setStockingStatus(0); task.setWaybillStatus(0);
            task.setReceiverName(order.getReceiverName()); task.setReceiverMobile(order.getReceiverMobile()); task.setProvince(order.getProvince()); task.setCity(order.getCity());
            task.setTown(order.getTown()); task.setAddress(order.getAddress()); task.setBuyerMemo(order.getBuyerMemo()); task.setSellerMemo(order.getSellerMemo());
            task.setRemark(local ? "本地仓待发任务，未扣库存" : "本地登记供应商代发任务，未推送外部系统"); task.setCreateTime(now); task.setCreateBy(actor);
            write(tasks.saveOOrderStocking(task));
            for (OOrderItem item : details) {
                OOrderStockingItem detail = new OOrderStockingItem();
                detail.setShipOrderId(task.getId()); detail.setOOrderId(id); detail.setOOrderItemId(Long.valueOf(item.getId())); detail.setSupplierId(local ? 0L : supplier.getId());
                if (local) { detail.setWarehouseId(warehouse.getId()); detail.setWarehouseType("LOCAL"); detail.setWarehouseName(warehouse.getWarehouseName()); }
                detail.setMerchantId(order.getMerchantId()); detail.setOrderNum(order.getOrderNum()); detail.setSubOrderNum(item.getSubOrderNum()); detail.setOrderTime(order.getOrderTime());
                detail.setProductId(item.getProductId()); detail.setSkuId(item.getSkuId()); detail.setGoodsId(item.getGoodsId()); detail.setGoodsSkuId(item.getGoodsSkuId());
                detail.setGoodsName(item.getGoodsTitle()); detail.setGoodsImg(item.getGoodsImg()); detail.setGoodsNum(item.getGoodsNum()); detail.setSkuCode(item.getSkuNum()); detail.setSkuName(item.getGoodsSpec()); detail.setBarcode(item.getBarcode());
                detail.setQuantity(requested.get(item.getId())); detail.setUnshippedQuantity(requested.get(item.getId())); detail.setSendStatus(0); detail.setRefundStatus(1); detail.setStockingStatus(0);
                detail.setCreateTime(now); detail.setCreateBy(actor); write(taskItems.saveOOrderStockingItem(detail));
                OOrderItem update = new OOrderItem(); update.setId(item.getId()); update.setShipType(local ? 0 : 300);
                int previouslyAllocated = allocated.getOrDefault(item.getId(), 0);
                update.setShipperType(previouslyAllocated == 0 ? (local ? "LOCAL" : "SUPPLIER") : "MIXED");
                update.setShipperId(previouslyAllocated == 0 && !local ? supplier.getId() : 0L);
                update.setShipperName(previouslyAllocated == 0 ? (local ? warehouse.getWarehouseName() : supplier.getName()) : "多个发货任务"); update.setShipperNo(previouslyAllocated == 0 ? (local ? warehouse.getWarehouseNo() : supplier.getNumber()) : "");
                update.setShipStatus(1); update.setHasPushErp(previouslyAllocated + requested.get(item.getId()) == item.getQuantity() ? 1 : 0);
                update.setUpdateBy(actor); update.setUpdateTime(now); write(items.updateOOrderItem(update));
            }
            OOrder update = new OOrder(); update.setId(order.getId()); update.setOrderMode(order.getOrderMode()); update.setDistStatus(allDetails.stream().allMatch(i -> i.getQuantity() != null && allocated.getOrDefault(i.getId(), 0) + requested.getOrDefault(i.getId(), 0) == i.getQuantity()) ? 2 : 1); update.setUpdateBy(actor); update.setUpdateTime(now); write(orders.updateOOrder(update));
            created.add(task.getId());
        }
        return created;
    }

    /** 登记供应商已发货，整张任务一个物流单，重复登记会被拒绝。 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(SupplierDispatchConfirmRequest request, String actor) { confirmTask(request, actor, 300); }

    @Transactional(rollbackFor = Exception.class)
    public void confirmLocal(SupplierDispatchConfirmRequest request, String actor) { confirmTask(request, actor, 0); }

    private void confirmTask(SupplierDispatchConfirmRequest request, String actor, int expectedType) {
        require(request != null && request.getId() != null && request.getLogisticsCompany() != null && StringUtils.hasText(request.getWaybillCode()), "请选择物流并填写运单号");
        String code = request.getWaybillCode().trim(); require(code.length() <= 50, "运单号不能超过50个字符");
        OOrderStocking snapshot = tasks.getOOrderStockingById(request.getId());
        require(snapshot != null && snapshot.getOOrderId() != null, "代发任务不存在");
        OOrder order = orders.getOOrderOne(new LambdaQueryWrapper<OOrder>().eq(OOrder::getId, snapshot.getOOrderId()).last("FOR UPDATE"));
        OOrderStocking task = tasks.getOOrderStockingOne(new LambdaQueryWrapper<OOrderStocking>().eq(OOrderStocking::getId, request.getId()).last("FOR UPDATE"));
        require(order != null && task != null && Integer.valueOf(1).equals(order.getOrderMode()) && (Integer.valueOf(1).equals(order.getOrderStatus()) || Integer.valueOf(101).equals(order.getOrderStatus()))
                && Integer.valueOf(expectedType).equals(task.getType()) && Integer.valueOf(0).equals(task.getSendStatus()) && Integer.valueOf(0).equals(task.getOrderType()), "任务不存在、已发货或订单状态不允许发货");
        if (expectedType == 0) {
            ErpWarehouse warehouse = task.getWarehouseId() == null ? null : warehouses.getErpWarehouseById(task.getWarehouseId());
            require(warehouse != null && "LOCAL".equals(warehouse.getWarehouseType()) && "1".equals(warehouse.getStatus())
                    && authorizedWarehouse(warehouse, order.getMerchantId()), "本地仓库已停用或不再授权给该商户");
        }
        ErpLogisticsCompany company = logistics.getErpLogisticsCompanyById(request.getLogisticsCompany());
        require(company != null && Integer.valueOf(1).equals(company.getStatus()), "所选物流公司不存在或已停用");
        List<OOrderStockingItem> details = taskItems.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>().eq(OOrderStockingItem::getShipOrderId, task.getId()).orderByAsc(OOrderStockingItem::getId).last("FOR UPDATE"));
        require(!details.isEmpty(), "代发任务没有商品");
        List<OOrderItem> originals = items.listOOrderItem(new LambdaQueryWrapper<OOrderItem>().eq(OOrderItem::getOrderId, order.getId()).orderByAsc(OOrderItem::getId).last("FOR UPDATE"));
        List<OOrderStocking> activeTasks = activeTasks(order.getId());
        Map<String, Integer> allocated = quantities(activeTasks, false);
        Map<String, Integer> shipped = quantities(activeTasks, true);
        require(originals.stream().allMatch(i -> i.getQuantity() != null && allocated.getOrDefault(i.getId(), 0) == i.getQuantity()), "请先分配订单全部商品，再登记发货");
        ShopOrder source = sourceOrders.getShopOrderOne(new LambdaQueryWrapper<ShopOrder>().eq(ShopOrder::getOrderId, order.getOrderNum()).eq(ShopOrder::getShopId, order.getShopId()).eq(ShopOrder::getMerchantId, order.getMerchantId()).last("FOR UPDATE"));
        require(source != null && Integer.valueOf(1).equals(source.getOrderMode()) && (Integer.valueOf(1).equals(source.getOrderStatus()) || Integer.valueOf(101).equals(source.getOrderStatus())), "本地店铺来源订单不存在或状态不一致");
        List<ShopOrderItem> sourceDetails = sourceItems.listShopOrderItem(new LambdaQueryWrapper<ShopOrderItem>().eq(ShopOrderItem::getShopOrderId, source.getId()).last("FOR UPDATE"));
        require(sourceDetails.size() == originals.size() && originals.stream().map(OOrderItem::getSubOrderNum).allMatch(StringUtils::hasText)
                && originals.stream().map(OOrderItem::getSubOrderNum).distinct().count() == originals.size(), "店铺订单商品关联不完整或重复");
        Set<Long> detailIds = new HashSet<>();
        for (OOrderStockingItem detail : details) {
            require(detail.getOOrderItemId() != null && detailIds.add(detail.getOOrderItemId()), "任务商品关联重复");
            OOrderItem item = originals.stream().filter(i -> Objects.equals(i.getId(), String.valueOf(detail.getOOrderItemId()))).findFirst().orElse(null);
            require(item != null && Objects.equals(detail.getOOrderId(), task.getOOrderId()) && Objects.equals(detail.getSupplierId(), task.getShipperId())
                    && Integer.valueOf(1).equals(item.getRefundStatus()) && Integer.valueOf(1).equals(item.getShipStatus())
                    && detail.getQuantity() != null && detail.getQuantity() > 0
                    && shipped.getOrDefault(item.getId(), 0) + detail.getQuantity() <= item.getQuantity()
                    && Integer.valueOf(0).equals(detail.getSendStatus()) && Integer.valueOf(0).equals(detail.getStockingStatus()), "原订单商品已变更、存在售后或已备货发货");
            require(sourceDetails.stream().filter(i -> Objects.equals(i.getSubOrderId(), item.getSubOrderNum())).count() == 1, "店铺订单商品关联不完整");
        }
        LocalDateTime now = LocalDateTime.now();
        for (OOrderStockingItem detail : details) {
            OOrderItem original = originals.stream().filter(i -> Objects.equals(i.getId(), String.valueOf(detail.getOOrderItemId()))).findFirst().orElseThrow();
            ShopOrderItem sourceItem = sourceDetails.stream().filter(i -> Objects.equals(i.getSubOrderId(), original.getSubOrderNum())).findFirst().orElseThrow();
            OOrderStockingItem d = new OOrderStockingItem(); d.setId(detail.getId()); d.setSendStatus(2); d.setUnshippedQuantity(0); d.setWaybillCode(code); d.setWaybillStatus(10); d.setUpdateBy(actor); d.setUpdateTime(now); write(taskItems.updateOOrderStockingItem(d));
            boolean itemComplete = shipped.getOrDefault(original.getId(), 0) + detail.getQuantity() == original.getQuantity();
            boolean singleItemPackage = detail.getQuantity().equals(original.getQuantity());
            OOrderItem i = new OOrderItem(); i.setId(original.getId()); i.setShipStatus(itemComplete ? 2 : 1);
            if (singleItemPackage) { i.setWaybillCode(code); i.setWaybillCompany(company.getName()); }
            i.setUpdateBy(actor); i.setUpdateTime(now); write(items.updateOOrderItem(i));
            ShopOrderItem s = new ShopOrderItem(); s.setId(sourceItem.getId()); s.setShipStatus(itemComplete ? 2 : 1); s.setOrderStatus(itemComplete ? 2 : 101);
            if (singleItemPackage) { s.setLogisticsCompany(company.getName()); s.setLogisticsCode(code); }
            s.setUpdateBy(actor); s.setUpdateOn(now); write(sourceItems.updateShopOrderItem(s));
        }
        OOrderStocking t = new OOrderStocking(); t.setId(task.getId()); t.setSendStatus(2); t.setOrderStatus(2); t.setWaybillCode(code); t.setWaybillCompany(company.getName()); t.setWaybillStatus(10);
        t.setShippingNumber(code); t.setShippingCompany(company.getName()); t.setShippingCompanyCode(company.getCode()); t.setShippingTime(now); t.setShippingMan(actor); t.setUpdateBy(actor); t.setUpdateTime(now); write(tasks.updateOOrderStocking(t));
        Map<String, Integer> current = new HashMap<>();
        details.forEach(d -> current.merge(String.valueOf(d.getOOrderItemId()), d.getQuantity(), Integer::sum));
        boolean allShipped = originals.stream().allMatch(i -> shipped.getOrDefault(i.getId(), 0) + current.getOrDefault(i.getId(), 0) == i.getQuantity());
        boolean singlePackage = details.size() == originals.size() && originals.stream().allMatch(i -> current.getOrDefault(i.getId(), 0).equals(i.getQuantity()));
        OOrder o = new OOrder(); o.setId(order.getId()); o.setOrderMode(order.getOrderMode()); o.setOrderStatus(allShipped ? 2 : 101); o.setShipStatus(allShipped ? 2 : 1);
        if (singlePackage) { o.setWaybillStatus(10); o.setWaybillCode(code); o.setWaybillCompany(company.getName()); }
        o.setUpdateBy(actor); o.setUpdateTime(now); write(orders.updateOOrder(o));
        ShopOrder s = new ShopOrder(); s.setId(source.getId()); s.setOrderStatus(allShipped ? 2 : 101); s.setErpShipStatus(allShipped ? 2 : 1); s.setErpShipTime(now);
        if (singlePackage) { s.setErpShipCompany(company.getName()); s.setErpShipCode(code); }
        s.setUpdateOn(now); write(sourceOrders.updateShopOrder(s));
    }

    /** 撤销尚未发货的本地代发任务，保留取消记录以便追查。 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long taskId, String actor) { cancelTask(taskId, actor, 300); }

    @Transactional(rollbackFor = Exception.class)
    public void cancelLocal(Long taskId, String actor) { cancelTask(taskId, actor, 0); }

    private void cancelTask(Long taskId, String actor, int expectedType) {
        require(taskId != null && taskId > 0, "请选择代发任务");
        OOrderStocking snapshot = tasks.getOOrderStockingById(taskId);
        require(snapshot != null && snapshot.getOOrderId() != null, "代发任务不存在");
        OOrder order = orders.getOOrderOne(new LambdaQueryWrapper<OOrder>().eq(OOrder::getId, snapshot.getOOrderId()).last("FOR UPDATE"));
        OOrderStocking task = tasks.getOOrderStockingOne(new LambdaQueryWrapper<OOrderStocking>().eq(OOrderStocking::getId, taskId).last("FOR UPDATE"));
        require(order != null && task != null && Integer.valueOf(1).equals(order.getOrderMode())
                && (Integer.valueOf(1).equals(order.getOrderStatus()) || Integer.valueOf(101).equals(order.getOrderStatus())) && Integer.valueOf(expectedType).equals(task.getType())
                && task.getOrderType() == 0 && Integer.valueOf(0).equals(task.getSendStatus())
                && Integer.valueOf(1).equals(task.getOrderStatus()) && Integer.valueOf(0).equals(task.getStockingStatus()), "任务不存在、已发货、已备货或不可撤销");
        List<OOrderStockingItem> details = taskItems.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>()
                .eq(OOrderStockingItem::getShipOrderId, taskId).orderByAsc(OOrderStockingItem::getId).last("FOR UPDATE"));
        List<OOrderItem> originals = items.listOOrderItem(new LambdaQueryWrapper<OOrderItem>()
                .eq(OOrderItem::getOrderId, order.getId()).orderByAsc(OOrderItem::getId).last("FOR UPDATE"));
        require(!details.isEmpty(), "任务明细不完整，不能撤销");
        Set<String> ids = new HashSet<>();
        for (OOrderStockingItem detail : details) {
            String id = String.valueOf(detail.getOOrderItemId());
            OOrderItem original = originals.stream().filter(i -> Objects.equals(i.getId(), id)).findFirst().orElse(null);
            require(ids.add(id) && original != null && Objects.equals(detail.getOOrderId(), task.getOOrderId())
                    && Objects.equals(detail.getSupplierId(), task.getShipperId()) && Integer.valueOf(0).equals(detail.getSendStatus())
                    && Integer.valueOf(0).equals(detail.getStockingStatus()) && Integer.valueOf(1).equals(original.getRefundStatus()) && empty(original.getRefundCount())
                    && Integer.valueOf(1).equals(original.getShipStatus()) && detail.getQuantity() != null && detail.getQuantity() > 0, "订单商品已变更或存在售后，不能撤销");
        }
        LocalDateTime now = LocalDateTime.now();
        List<OOrderStocking> remainingTasks = activeTasks(order.getId()).stream().filter(t -> !Objects.equals(t.getId(), taskId)).toList();
        Map<String, Integer> remaining = quantities(remainingTasks, false);
        for (OOrderItem original : originals.stream().filter(i -> ids.contains(i.getId())).toList()) {
            int count = remaining.getOrDefault(original.getId(), 0);
            require(original.getQuantity() != null && count <= original.getQuantity(), "剩余任务数量异常");
            LambdaUpdateWrapper<OOrderItem> change = new LambdaUpdateWrapper<OOrderItem>().eq(OOrderItem::getId, original.getId())
                    .set(OOrderItem::getShipStatus, count == 0 ? 0 : 1).set(OOrderItem::getHasPushErp, count == original.getQuantity() ? 1 : 0)
                    .set(OOrderItem::getUpdateBy, actor).set(OOrderItem::getUpdateTime, now);
            if (count == 0) change.set(OOrderItem::getShipType, 0).set(OOrderItem::getShipperType, null).set(OOrderItem::getShipperId, 0L).set(OOrderItem::getShipperName, null).set(OOrderItem::getShipperNo, null);
            else {
                List<Long> remainingIds = remainingTasks.stream().map(OOrderStocking::getId).toList();
                Set<Long> supplierIds = new HashSet<>();
                List<OOrderStockingItem> remainingDetails = taskItems.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>()
                        .in(OOrderStockingItem::getShipOrderId, remainingIds).eq(OOrderStockingItem::getOOrderItemId, original.getId()));
                remainingDetails.forEach(d -> supplierIds.add(d.getSupplierId()));
                if (supplierIds.size() == 1 && supplierIds.contains(0L)) {
                    Set<Long> warehouseIds = new HashSet<>(); remainingDetails.forEach(d -> warehouseIds.add(d.getWarehouseId()));
                    ErpWarehouse owner = warehouseIds.size() == 1 && warehouseIds.iterator().next() != null ? warehouses.getErpWarehouseById(warehouseIds.iterator().next()) : null;
                    change.set(OOrderItem::getShipType, 0).set(OOrderItem::getShipperType, "LOCAL").set(OOrderItem::getShipperId, 0L)
                            .set(OOrderItem::getShipperName, owner == null ? "多个本地仓" : owner.getWarehouseName()).set(OOrderItem::getShipperNo, owner == null ? "" : owner.getWarehouseNo());
                } else if (supplierIds.size() == 1 && supplierIds.iterator().next() != null && supplierIds.iterator().next() > 0) {
                    change.set(OOrderItem::getShipType, 300).set(OOrderItem::getShipperType, "SUPPLIER");
                    ErpSupplier owner = suppliers.getErpSupplierById(supplierIds.iterator().next());
                    if (owner != null) change.set(OOrderItem::getShipperId, owner.getId()).set(OOrderItem::getShipperName, owner.getName()).set(OOrderItem::getShipperNo, owner.getNumber());
                    else change.set(OOrderItem::getShipperId, 0L).set(OOrderItem::getShipperName, "供应商已删除").set(OOrderItem::getShipperNo, "");
                } else change.set(OOrderItem::getShipperType, "MIXED").set(OOrderItem::getShipperId, 0L).set(OOrderItem::getShipperName, "多个发货任务").set(OOrderItem::getShipperNo, "");
            }
            write(items.updateOOrderItem(null, change));
        }
        OOrder update = new OOrder(); update.setId(order.getId()); update.setOrderMode(order.getOrderMode());
        update.setDistStatus(remaining.values().stream().anyMatch(n -> n > 0) ? 1 : 0); update.setUpdateBy(actor); update.setUpdateTime(now); write(orders.updateOOrder(update));
        OOrderStocking cancelled = new OOrderStocking(); cancelled.setId(taskId); cancelled.setOrderType(task.getOrderType());
        cancelled.setOrderStatus(11); cancelled.setSendStatus(-1); cancelled.setRemark("发货分配已撤销，未推送外部系统");
        cancelled.setUpdateBy(actor); cancelled.setUpdateTime(now); write(tasks.updateOOrderStocking(cancelled));
    }

    /** 提供页面的剩余数量预览；实际分配仍在事务锁内重新校验。 */
    public Map<String, Integer> remainingItems(List<Long> ids) {
        require(ids != null && !ids.isEmpty() && ids.size() <= 100 && ids.stream().allMatch(i -> i != null && i > 0)
                && new HashSet<>(ids).size() == ids.size(), "请选择有效的订单商品");
        List<OOrderItem> selected = items.listOOrderItemByIds(ids);
        require(selected.size() == ids.size() && selected.stream().allMatch(i -> i.getOrderId() != null), "订单商品不存在");
        Set<String> orders = new HashSet<>(); selected.forEach(i -> orders.add(i.getOrderId()));
        require(orders.size() == 1, "一次只能分配同一订单的商品");
        Map<String, Integer> allocated = quantities(activeTasks(orders.iterator().next()), false);
        Map<String, Integer> remaining = new HashMap<>();
        for (OOrderItem item : selected) {
            require(item.getQuantity() != null && item.getQuantity() >= allocated.getOrDefault(item.getId(), 0), "订单商品数量异常");
            remaining.put(item.getId(), item.getQuantity() - allocated.getOrDefault(item.getId(), 0));
        }
        return remaining;
    }

    private List<OOrderStocking> activeTasks(String orderId) {
        List<OOrderStocking> active = tasks.listOOrderStocking(new LambdaQueryWrapper<OOrderStocking>().eq(OOrderStocking::getOOrderId, orderId)
                .and(w -> w.isNull(OOrderStocking::getOrderStatus).or().ne(OOrderStocking::getOrderStatus, 11)));
        require(active.stream().allMatch(t -> (Integer.valueOf(300).equals(t.getType()) || Integer.valueOf(0).equals(t.getType())) && t.getOrderType() == 0
                && (Integer.valueOf(0).equals(t.getSendStatus()) || Integer.valueOf(2).equals(t.getSendStatus()))), "订单存在不兼容的发货任务");
        return active;
    }
    private Map<String, Integer> quantities(List<OOrderStocking> active, boolean shippedOnly) {
        List<Long> ids = active.stream().filter(t -> !shippedOnly || Integer.valueOf(2).equals(t.getSendStatus())).map(OOrderStocking::getId).toList();
        Map<String, Integer> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (OOrderStockingItem detail : taskItems.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>().in(OOrderStockingItem::getShipOrderId, ids))) {
            require(detail.getOOrderItemId() != null && detail.getQuantity() != null && detail.getQuantity() > 0, "发货任务数量异常");
            result.merge(String.valueOf(detail.getOOrderItemId()), detail.getQuantity(), Math::addExact);
        }
        return result;
    }

    private static boolean authorizedWarehouse(ErpWarehouse warehouse, Long merchantId) {
        if (merchantId == null) return false;
        if (Objects.equals(warehouse.getMerchantId(), merchantId)) return true;
        return warehouse.getMerchantIds() != null && Arrays.stream(warehouse.getMerchantIds().split(",")).map(String::trim).anyMatch(id -> id.equals(merchantId.toString()));
    }
    private static boolean authorized(ErpSupplier supplier, Long merchantId) {
        if (merchantId == null) return false;
        if (Objects.equals(supplier.getMerchantId(), merchantId)) return true;
        return supplier.getMerchantIds() != null && Arrays.stream(supplier.getMerchantIds().split(",")).map(String::trim).anyMatch(id -> id.equals(merchantId.toString()));
    }
    private static boolean empty(Integer value) {return value == null || value == 0;}
    private static void require(boolean condition, String message) {if (!condition) throw new IllegalArgumentException(message);}
    private static void write(int count) {if (count != 1) throw new IllegalStateException("保存失败，供应商任务已回滚");}
}
