package cn.xinghuaerp.fulfillment.service;

import cn.xinghuaerp.system.entity.ErpLogisticsCompany;
import cn.xinghuaerp.system.facade.SystemFacade;
import cn.xinghuaerp.order.facade.OrderFacade;
import cn.xinghuaerp.product.facade.ProductFacade;
import cn.xinghuaerp.inventory.facade.InventoryFacade;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.entity.OOrderStocking;
import cn.xinghuaerp.inventory.entity.OOrderStockingItem;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.entity.OOrderItem;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.order.bo.OrderShipRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import cn.xinghuaerp.fulfillment.service.ManualShipmentService;
/** 本地手动发货登记；只生成备货单，库存由实际出库操作扣减。 */
@Service
@AllArgsConstructor
public class ManualShipmentService {
    private final OrderFacade orderMapper;
    private final OrderFacade itemMapper;
    private final InventoryFacade stockingMapper;
    private final InventoryFacade stockingItemMapper;
    private final InventoryFacade warehouseMapper;
    private final SystemFacade logisticsMapper;
    private final ProductFacade skuMapper;

    @Transactional(rollbackFor = Exception.class)
    public ResultVo<Long> ship(OrderShipRequest request, String operator) {
        if (request == null || !StringUtils.hasText(request.getId()) || !request.getId().matches("[1-9]\\d*"))
            return ResultVo.error("请选择有效的订单");
        if (request.getWarehouseId() == null) return ResultVo.error("请选择发货仓库");
        if (!StringUtils.hasText(request.getShippingCompany()) || !StringUtils.hasText(request.getShippingNumber()))
            return ResultVo.error("请填写物流公司和物流单号");
        String waybill = request.getShippingNumber().trim();
        if (waybill.length() > 50) return ResultVo.error("物流单号不能超过50个字符");
        if (request.getShippingCost() != null && request.getShippingCost().signum() < 0)
            return ResultVo.error("物流费用不能为负数");

        // 同一订单串行处理：第二次提交在拿到锁后能看到最新发货状态。
        OOrder order = orderMapper.getOOrderOne(new LambdaQueryWrapper<OOrder>()
                .eq(OOrder::getId, request.getId()).last("FOR UPDATE"));
        if (order == null) return ResultVo.error("订单不存在");
        if (!Integer.valueOf(1).equals(order.getOrderStatus())) return ResultVo.error("只有待发货订单可以手动发货");
        if ((order.getDistStatus() != null && order.getDistStatus() != 0)
                || (order.getShipStatus() != null && order.getShipStatus() != 0)
                || (order.getWaybillStatus() != null && order.getWaybillStatus() != 0)
                || stockingMapper.countOOrderStocking(new LambdaQueryWrapper<OOrderStocking>()
                    .eq(OOrderStocking::getOOrderId, order.getId())
                    .and(w -> w.isNull(OOrderStocking::getOrderStatus).or().ne(OOrderStocking::getOrderStatus, 11))) > 0)
            return ResultVo.error("订单已分配、已取号或已发货，请勿重复操作");

        ErpWarehouse warehouse = warehouseMapper.getErpWarehouseById(request.getWarehouseId());
        if (warehouse == null || !"1".equals(warehouse.getStatus())
                || !"LOCAL".equals(warehouse.getWarehouseType())) return ResultVo.error("请选择启用的本地仓库");
        ErpLogisticsCompany logistics = logisticsMapper.getErpLogisticsCompanyById(request.getShippingCompany());
        if (logistics == null || !Integer.valueOf(1).equals(logistics.getStatus()))
            return ResultVo.error("所选物流公司不存在或已停用");

        List<OOrderItem> items = itemMapper.listOOrderItem(new LambdaQueryWrapper<OOrderItem>()
                .eq(OOrderItem::getOrderId, order.getId()).orderByAsc(OOrderItem::getId).last("FOR UPDATE"));
        if (items.isEmpty()) return ResultVo.error("订单没有商品明细");
        List<OOrderStockingItem> details = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (OOrderItem item : items) {
            // 本入口整单处理，不绕过售后、部分分配、电子面单等其他发货流程。
            if (!Integer.valueOf(1).equals(item.getRefundStatus())) return ResultVo.error("订单存在售后，请先处理售后");
            if ((item.getShipStatus() != null && item.getShipStatus() != 0)
                    || Integer.valueOf(1).equals(item.getHasPushErp())) return ResultVo.error("订单明细已分配或已发货");
            int refunded = item.getRefundCount() == null ? 0 : item.getRefundCount();
            if (item.getQuantity() == null || refunded < 0 || item.getQuantity() <= refunded)
                return ResultVo.error("订单商品可发货数量必须大于0");
            OGoodsSku sku = item.getGoodsSkuId() == null ? null : skuMapper.getOGoodsSkuById(item.getGoodsSkuId());
            if (sku == null || item.getGoodsId() == null || !item.getGoodsId().toString().equals(sku.getGoodsId()))
                return ResultVo.error("订单商品尚未正确关联商品库SKU，请先完成关联");

            OOrderStockingItem detail = new OOrderStockingItem();
            detail.setOOrderId(Long.valueOf(order.getId()));
            detail.setOOrderItemId(Long.valueOf(item.getId()));
            detail.setMerchantId(order.getMerchantId());
            detail.setOrderNum(order.getOrderNum());
            detail.setSubOrderNum(item.getSubOrderNum());
            detail.setOrderTime(order.getOrderTime());
            detail.setSupplierId(0L);
            detail.setProductId(item.getProductId());
            detail.setSkuId(item.getSkuId());
            detail.setGoodsId(item.getGoodsId());
            detail.setGoodsSkuId(item.getGoodsSkuId());
            detail.setGoodsName(item.getGoodsTitle());
            detail.setGoodsNum(item.getGoodsNum());
            detail.setGoodsImg(item.getGoodsImg());
            detail.setSkuName(item.getGoodsSpec());
            detail.setSkuCode(sku.getSkuCode());
            detail.setBarcode(item.getBarcode());
            detail.setQuantity(item.getQuantity() - refunded);
            detail.setUnshippedQuantity(0);
            detail.setRefundStatus(1);
            detail.setSendStatus(2);
            detail.setStockingStatus(0);
            detail.setWaybillStatus(10);
            detail.setWaybillCode(waybill);
            detail.setWarehouseId(warehouse.getId());
            detail.setWarehouseType(warehouse.getWarehouseType());
            detail.setWarehouseName(warehouse.getWarehouseName());
            detail.setCreateTime(now);
            detail.setCreateBy(operator);
            details.add(detail);
        }

        OOrderStocking stocking = new OOrderStocking();
        stocking.setOOrderId(Long.valueOf(order.getId()));
        stocking.setType(0);
        stocking.setShipperId(0L);
        stocking.setShipMode(0);
        stocking.setOrderType(0);
        stocking.setOrderNum(order.getOrderNum());
        stocking.setOrderTime(order.getOrderTime());
        stocking.setMerchantId(order.getMerchantId());
        stocking.setShopId(order.getShopId());
        stocking.setShopType(order.getShopType());
        stocking.setRemark(order.getRemark());
        stocking.setBuyerMemo(order.getBuyerMemo());
        stocking.setSellerMemo(order.getSellerMemo());
        stocking.setReceiverName(order.getReceiverName());
        stocking.setReceiverMobile(order.getReceiverMobile());
        stocking.setProvince(order.getProvince());
        stocking.setCity(order.getCity());
        stocking.setTown(order.getTown());
        stocking.setAddress(order.getAddress());
        stocking.setWarehouseId(warehouse.getId());
        stocking.setWarehouseType(warehouse.getWarehouseType());
        stocking.setWarehouseName(warehouse.getWarehouseName());
        stocking.setWarehouseNo(warehouse.getWarehouseNo());
        stocking.setSendStatus(2);
        stocking.setOrderStatus(2);
        stocking.setStockingStatus(0);
        stocking.setWaybillStatus(10);
        stocking.setShippingCompany(logistics.getName());
        stocking.setShippingCompanyCode(logistics.getCode());
        stocking.setShippingNumber(waybill);
        stocking.setWaybillCompany(logistics.getName());
        stocking.setWaybillCode(waybill);
        stocking.setShippingMan(operator);
        stocking.setShippingTime(now);
        stocking.setShippingCost(request.getShippingCost());
        stocking.setCreateBy(operator);
        stocking.setCreateTime(now);
        requireWrite(stockingMapper.saveOOrderStocking(stocking));
        for (int i = 0; i < items.size(); i++) {
            OOrderStockingItem detail = details.get(i);
            detail.setShipOrderId(stocking.getId());
            requireWrite(stockingItemMapper.saveOOrderStockingItem(detail));
            OOrderItem update = new OOrderItem();
            update.setId(items.get(i).getId());
            update.setShipType(0);
            update.setShipperId(0L);
            update.setShipperType("LOCAL");
            update.setShipperName(warehouse.getWarehouseName());
            update.setShipperNo(warehouse.getWarehouseNo());
            update.setShipStatus(2);
            update.setHasPushErp(1);
            update.setWaybillCode(waybill);
            update.setWaybillCompany(logistics.getName());
            update.setUpdateBy(operator);
            update.setUpdateTime(now);
            requireWrite(itemMapper.updateOOrderItem(update));
        }
        OOrder update = new OOrder();
        update.setId(order.getId());
        update.setOrderMode(order.getOrderMode());
        update.setOrderStatus(2);
        update.setDistStatus(2);
        update.setShipStatus(2);
        update.setWaybillStatus(10);
        update.setWaybillCode(waybill);
        update.setWaybillCompany(logistics.getName());
        update.setUpdateBy(operator);
        update.setUpdateTime(now);
        requireWrite(orderMapper.updateOOrder(update));
        return ResultVo.success(stocking.getId());
    }

    private static void requireWrite(int affected) {
        if (affected != 1) throw new IllegalStateException("保存发货信息失败，请重试");
    }
}
