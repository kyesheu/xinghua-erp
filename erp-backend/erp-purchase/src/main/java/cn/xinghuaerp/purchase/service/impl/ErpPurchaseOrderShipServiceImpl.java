package cn.xinghuaerp.purchase.service.impl;

import cn.xinghuaerp.inventory.entity.ErpStockIn;
import cn.xinghuaerp.inventory.facade.InventoryFacade;
import cn.xinghuaerp.inventory.entity.ErpStockInItem;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrder;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderItem;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderShip;
import cn.xinghuaerp.common.DateHelper;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.purchase.request.PurchaseOrderStockInBo;
import cn.xinghuaerp.purchase.request.SearchRequest;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderShipService;

import cn.xinghuaerp.utils.DateUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


import java.util.List;

import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderItemMapper;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderMapper;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderShipMapper;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderShipService;
import cn.xinghuaerp.purchase.service.impl.ErpPurchaseOrderShipServiceImpl;
/**
* @author qilip
* @description 针对表【scm_purchase_order_ship(采购订单物流表)】的数据库操作Service实现
* @createDate 2024-10-20 17:18:53
*/
@AllArgsConstructor
@Service
public class ErpPurchaseOrderShipServiceImpl extends ServiceImpl<ErpPurchaseOrderShipMapper, ErpPurchaseOrderShip>
    implements ErpPurchaseOrderShipService {
    private final ErpPurchaseOrderShipMapper shipMapper;
    private final ErpPurchaseOrderMapper orderMapper;
    private final ErpPurchaseOrderItemMapper orderItemMapper;
    private final InventoryFacade warehouseMapper;
    private final InventoryFacade stockInMapper;
    private final InventoryFacade stockInItemMapper;


    @Override
    public PageResult<ErpPurchaseOrderShip> queryPageList(SearchRequest bo, PageQuery pageQuery) {
        if(org.springframework.util.StringUtils.hasText(bo.getStartTime())){

            boolean b = DateHelper.isValidDate(bo.getStartTime());
            if(!b){
//                bo.setStartTime(bo.getStartTime()+" 00:00:00");
                bo.setStartTime("");
            }
        }
        if(org.springframework.util.StringUtils.hasText(bo.getEndTime())){
            boolean b =DateHelper.isValidDate(bo.getEndTime());
            if(!b){
//                bo.setEndTime(bo.getEndTime()+" 23:59:59");
                bo.setEndTime("");
            }
        }else{
            bo.setEndTime(bo.getShipTime());
        }

        LambdaQueryWrapper<ErpPurchaseOrderShip> queryWrapper = new LambdaQueryWrapper<ErpPurchaseOrderShip>()
                .eq(bo.getMerchantId()!=null, ErpPurchaseOrderShip::getMerchantId,bo.getMerchantId())
                .eq(bo.getShopId()!=null, ErpPurchaseOrderShip::getShopId,bo.getShopId())
                .eq(bo.getSupplierId()!=null, ErpPurchaseOrderShip::getSupplierId,bo.getSupplierId())
                .eq(org.springframework.util.StringUtils.hasText(bo.getOrderNum()), ErpPurchaseOrderShip::getOrderNum,bo.getOrderNum())
                .eq(bo.getOrderStatus()!=null, ErpPurchaseOrderShip::getStatus,bo.getOrderStatus())
                .ge(org.springframework.util.StringUtils.hasText(bo.getStartTime()), ErpPurchaseOrderShip::getShipTime,bo.getStartTime()+" 00:00:00")
                .le(org.springframework.util.StringUtils.hasText(bo.getEndTime()), ErpPurchaseOrderShip::getShipTime,bo.getEndTime()+" 23:59:59")
                ;
        if(org.springframework.util.StringUtils.hasText(bo.getShipTime())){
            boolean b = DateHelper.isValidDate(bo.getShipTime());
            if(b){
                queryWrapper.ge( ErpPurchaseOrderShip::getShipTime,bo.getShipTime()+" 00:00:00")
                        .le( ErpPurchaseOrderShip::getShipTime,bo.getShipTime()+" 23:59:59");
            }
        }

        Page<ErpPurchaseOrderShip> pages = shipMapper.selectPage(pageQuery.build(), queryWrapper);

        return PageResult.build(pages);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateScmPurchaseOrderShip(ErpPurchaseOrderShip erpPurchaseOrderShip)
    {
        if (erpPurchaseOrderShip == null || erpPurchaseOrderShip.getId() == null) return -1;
        ErpPurchaseOrderShip snapshot = shipMapper.selectById(erpPurchaseOrderShip.getId());
        if (snapshot == null || snapshot.getOrderId() == null) return -1;
        ErpPurchaseOrder source = orderMapper.selectOne(new LambdaQueryWrapper<ErpPurchaseOrder>()
                .eq(ErpPurchaseOrder::getId, snapshot.getOrderId()).last("FOR UPDATE"));
        if (source == null) return -1;
        if (!Integer.valueOf(102).equals(source.getStatus())) return -2;
        ErpPurchaseOrderShip ship = shipMapper.selectOne(new LambdaQueryWrapper<ErpPurchaseOrderShip>()
                .eq(ErpPurchaseOrderShip::getId, erpPurchaseOrderShip.getId()).last("FOR UPDATE"));
        if (ship == null || !java.util.Objects.equals(ship.getOrderId(), source.getId())) return -1;
        if (!Integer.valueOf(0).equals(ship.getStatus())) return -2;
        // 采购单归属以服务端物流记录为准，不使用客户端传入的订单ID。
        ErpPurchaseOrder order = new ErpPurchaseOrder();
        order.setId(ship.getOrderId());
        order.setStatus(2);
        order.setReceivedTime(erpPurchaseOrderShip.getReceiptTime());
        order.setUpdateTime(DateUtils.getNowDate());
        order.setUpdateBy(erpPurchaseOrderShip.getUpdateBy());
        requireWrite(orderMapper.updateById(order));
        //更新
        ErpPurchaseOrderShip update = new ErpPurchaseOrderShip();
        update.setId(ship.getId());
        update.setUpdateTime(DateUtils.getNowDate());
        update.setUpdateBy(erpPurchaseOrderShip.getUpdateBy());
        update.setStatus(1);
        update.setRemark(erpPurchaseOrderShip.getRemark());
        update.setReceiptTime(erpPurchaseOrderShip.getReceiptTime());
//        update.setReceiptTime(DateUtils.getNowDate());
        update.setId(erpPurchaseOrderShip.getId());
        requireWrite(shipMapper.updateById(update));
        return 1;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> createStockInEntry(PurchaseOrderStockInBo bo,Long userId,String userName) {
        if(bo == null || bo.getId()==null) return ResultVo.error("缺少参数：ID");
        if(bo.getWarehouseId()==null) return ResultVo.error("缺少参数：warehouseId");
        ErpPurchaseOrderShip snapshot = shipMapper.selectById(bo.getId());
        if (snapshot == null || snapshot.getOrderId() == null) return ResultVo.error("采购物流不存在");
        // 按采购单串行化：多个物流记录引用同一采购单时也只能创建一次整单入库任务。
        ErpPurchaseOrder erpPurchaseOrder = orderMapper.selectOne(new LambdaQueryWrapper<ErpPurchaseOrder>()
                .eq(ErpPurchaseOrder::getId, snapshot.getOrderId()).last("FOR UPDATE"));
        if (erpPurchaseOrder == null) return ResultVo.error("采购单不存在");
        if (Integer.valueOf(99).equals(erpPurchaseOrder.getStatus())) return ResultVo.error("已取消的采购单不能生成入库任务");
        ErpPurchaseOrderShip ship = shipMapper.selectOne(new LambdaQueryWrapper<ErpPurchaseOrderShip>()
                .eq(ErpPurchaseOrderShip::getId, bo.getId()).last("FOR UPDATE"));
        if (ship == null || !java.util.Objects.equals(ship.getOrderId(), erpPurchaseOrder.getId())) return ResultVo.error("采购物流归属已变化");
        if (Integer.valueOf(2).equals(ship.getStatus())) return ResultVo.error("已处理过了请勿重复操作");
        // MySQL 默认可重复读下，普通 select 可能看不到锁等待期间新提交的任务；使用当前读。
        ErpStockIn existing = stockInMapper.getErpStockInOne(new LambdaQueryWrapper<ErpStockIn>()
                .select(ErpStockIn::getId).eq(ErpStockIn::getStockInType, 1)
                .eq(ErpStockIn::getSourceId, erpPurchaseOrder.getId()).last("LIMIT 1 FOR UPDATE"));
        if (existing != null) return ResultVo.error("该采购单已有入库任务，请到入库管理继续处理");


        ErpWarehouse warehouse = warehouseMapper.getErpWarehouseById(bo.getWarehouseId());
        if (warehouse == null) return ResultVo.error("仓库不存在");
        // 子表（使用采购单item数据）
        List<ErpPurchaseOrderItem> purchaseOrderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<ErpPurchaseOrderItem>().eq(ErpPurchaseOrderItem::getOrderId, ship.getOrderId())
                        .and(w -> w.eq(ErpPurchaseOrderItem::getIsDelete, 0).or().isNull(ErpPurchaseOrderItem::getIsDelete)));
        if(purchaseOrderItems.isEmpty()) return ResultVo.error("没有找到有效采购明细");
        long totalQuantity = 0;
        for (var item : purchaseOrderItems) {
            if (item.getId() == null || item.getSpecId() == null || item.getSpecId() <= 0 || item.getGoodsId() == null || item.getGoodsId() <= 0
                    || item.getQuantity() == null || item.getQuantity() <= 0 || item.getQuantity() > Integer.MAX_VALUE
                    || item.getInventoryMode() == null || (item.getInventoryMode() != 0 && item.getInventoryMode() != 1))
                return ResultVo.error("采购明细的商品、库存模式或数量无效");
            totalQuantity += item.getQuantity();
            if (totalQuantity > Integer.MAX_VALUE) return ResultVo.error("采购总数量过大");
        }
        // 更新采购物流表状态
        ErpPurchaseOrderShip update = new ErpPurchaseOrderShip();
        update.setUpdateTime(DateUtils.getNowDate());
        update.setStockInTime(DateUtils.getNowDate());
        if (bo.getReceiptTime() != null && !bo.getReceiptTime().isEmpty()) {
            update.setReceiptTime(java.time.LocalDateTime.of(
                java.time.LocalDate.parse(bo.getReceiptTime(), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                java.time.LocalTime.MIN));
        } else {
            update.setReceiptTime(DateUtils.getNowDate());
        }
        update.setRemark(bo.getRemark());
        update.setUpdateBy(bo.getCreateBy());
        update.setWarehouseId(warehouse.getId());
        update.setWarehouseType(warehouse.getWarehouseType());
        update.setWarehouseName(warehouse.getWarehouseName());
        update.setStatus(2);
        update.setId(ship.getId());
        requireWrite(shipMapper.updateById(update));

        // 更新采购订单状态
        ErpPurchaseOrder order = new ErpPurchaseOrder();
        order.setId(ship.getOrderId());
        order.setStatus(3);
        if (bo.getReceiptTime() != null && !bo.getReceiptTime().isEmpty()) {
            order.setReceivedTime(java.time.LocalDateTime.of(
                java.time.LocalDate.parse(bo.getReceiptTime(), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                java.time.LocalTime.MIN));
        } else {
            order.setReceivedTime(DateUtils.getNowDate());
        }
        order.setWarehouseId(warehouse.getId());
        order.setWarehouseType(warehouse.getWarehouseType());
        order.setWarehouseName(warehouse.getWarehouseName());
        order.setStockInTime(DateUtils.getNowDate());
        order.setUpdateTime(DateUtils.getNowDate());
        order.setUpdateBy("生成入库单");
        requireWrite(orderMapper.updateById(order));

        // 创建入库单
        ErpStockIn stockIn = new ErpStockIn();
        stockIn.setStockInNum("SI" + ship.getOrderNum());
        stockIn.setStockInType(1); // 采购入库
        stockIn.setSourceNo(ship.getOrderNum());
        stockIn.setSourceId(ship.getOrderId());
        stockIn.setSourceGoodsUnit(purchaseOrderItems.size());
        int totalQty = (int) totalQuantity;
        stockIn.setSourceSpecUnitTotal(totalQty);
        stockIn.setSourceSpecUnit(purchaseOrderItems.size());
        stockIn.setWarehouseId(warehouse.getId());
        stockIn.setWarehouseName(warehouse.getWarehouseName());
        stockIn.setWarehouseType(warehouse.getWarehouseType());
        stockIn.setStatus(0); // 待入库
        stockIn.setCreateBy(userName);
        stockIn.setCreateTime(DateUtils.getNowDate());
        stockIn.setMerchantId(erpPurchaseOrder.getMerchantId());
        stockIn.setShopId(erpPurchaseOrder.getShopId());
        requireWrite(stockInMapper.saveErpStockIn(stockIn));

        // 创建入库单明细
        for (ErpPurchaseOrderItem item : purchaseOrderItems) {
            ErpStockInItem stockInItem = new ErpStockInItem();
            stockInItem.setStockInId(stockIn.getId());
            stockInItem.setStockInType(1);
            stockInItem.setSourceNo(ship.getOrderNum());
            stockInItem.setSourceId(ship.getOrderId());
            stockInItem.setSourceItemId(Long.parseLong(item.getId()));
            stockInItem.setGoodsId(item.getGoodsId() != null ? String.valueOf(item.getGoodsId()) : null);
            stockInItem.setGoodsNum(item.getGoodsNum());
            stockInItem.setGoodsName(item.getGoodsName());
            stockInItem.setGoodsImage(item.getColorImage());
            stockInItem.setSkuId(item.getSpecId() != null ? String.valueOf(item.getSpecId()) : null);
            stockInItem.setSkuCode(item.getSpecNum());
            stockInItem.setSkuName(buildSkuName(item));
            stockInItem.setQuantity(item.getQuantity() != null ? item.getQuantity().intValue() : 0);
            stockInItem.setInQuantity(0);
            stockInItem.setInventoryMode(item.getInventoryMode());
            stockInItem.setPurPrice(item.getPrice() != null ? item.getPrice().doubleValue() : 0);
            stockInItem.setWarehouseId(warehouse.getId());
            stockInItem.setStatus(0); // 待入库
            stockInItem.setCreateBy(userName);
            stockInItem.setCreateTime(DateUtils.getNowDate());
            stockInItem.setMerchantId(erpPurchaseOrder.getMerchantId());
            stockInItem.setShopId(erpPurchaseOrder.getShopId());
            requireWrite(stockInItemMapper.saveErpStockInItem(stockInItem));
        }

        return ResultVo.success(ship.getId());
    }

    private static void requireWrite(int count) {
        if (count != 1) throw new IllegalStateException("生成采购入库任务失败，已回滚");
    }

    private String buildSkuName(ErpPurchaseOrderItem item) {
        String skuName = "";
        if (StringUtils.hasText(item.getColorValue())) {
            skuName += item.getColorValue();
        }
        if (StringUtils.hasText(item.getSizeValue())) {
            skuName += " " + item.getSizeValue();
        }
        if (StringUtils.hasText(item.getStyleValue())) {
            skuName += " " + item.getStyleValue();
        }
        return skuName;
    }
}




