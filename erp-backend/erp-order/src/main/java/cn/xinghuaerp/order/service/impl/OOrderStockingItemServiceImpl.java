package cn.xinghuaerp.order.service.impl;

import cn.xinghuaerp.inventory.entity.ErpStockOut;
import cn.xinghuaerp.inventory.facade.InventoryFacade;
import cn.xinghuaerp.inventory.entity.ErpStockOutItem;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.entity.OOrderStocking;
import cn.xinghuaerp.inventory.entity.OOrderStockingItem;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.inventory.bo.ShipStockUpCompleteBo;
import cn.xinghuaerp.inventory.bo.StockingOrderItemBo;
import cn.xinghuaerp.inventory.bo.SupplierShipOrderItemListBo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.xinghuaerp.inventory.service.OOrderStockingItemService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

import cn.xinghuaerp.inventory.service.OOrderStockingItemService;
import cn.xinghuaerp.order.service.impl.OOrderStockingItemServiceImpl;
import cn.xinghuaerp.inventory.mapper.OOrderStockingItemMapper;
/**
* @author qilip
* @description 针对表【o_supplier_ship_order_item】的数据库操作Service实现
* @createDate 2025-02-18 13:48:13
*/
@AllArgsConstructor
@Service
public class OOrderStockingItemServiceImpl extends ServiceImpl<OOrderStockingItemMapper, OOrderStockingItem>
    implements OOrderStockingItemService {
    private final InventoryFacade stockingMapper;
    private final InventoryFacade stockingItemMapper;
    private final InventoryFacade stockOutItemMapper;
    private final InventoryFacade stockOutMapper;
    private final InventoryFacade warehouseMapper;

    /**
     * 查询供应商备货商品清单
     * @param bo
     * @param pageQuery
     * @return
     */
    @Override
    public PageResult<OOrderStockingItem> queryVendorStockingPageList(SupplierShipOrderItemListBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<OOrderStockingItem> queryWrapper = new LambdaQueryWrapper<OOrderStockingItem>()
                .eq(OOrderStockingItem::getSupplierId,bo.getSupplierId())
                .eq(StringUtils.hasText(bo.getOrderNum()), OOrderStockingItem::getOrderNum,bo.getOrderNum())
                .eq(StringUtils.hasText(bo.getSkuCode()), OOrderStockingItem::getSkuCode,bo.getSkuCode())
                .eq(bo.getSendStatus()!=null, OOrderStockingItem::getSendStatus,bo.getSendStatus())
                .eq(bo.getRefundStatus()!=null, OOrderStockingItem::getRefundStatus,bo.getRefundStatus());

        pageQuery.setOrderByColumn("id");
        pageQuery.setIsAsc("desc");
        Page<OOrderStockingItem> pages = stockingItemMapper.pageOOrderStockingItem(pageQuery.build(), queryWrapper);

        return PageResult.build(pages);
    }

    @Override
    public List<OOrderStockingItem> queryAllList2(SupplierShipOrderItemListBo bo ) {
        LambdaQueryWrapper<OOrderStockingItem> queryWrapper = new LambdaQueryWrapper<OOrderStockingItem>()
                .eq(OOrderStockingItem::getSupplierId,bo.getSupplierId())
                .eq(StringUtils.hasText(bo.getOrderNum()), OOrderStockingItem::getOrderNum,bo.getOrderNum())
                .eq(StringUtils.hasText(bo.getSkuCode()), OOrderStockingItem::getSkuCode,bo.getSkuCode())
                .eq(bo.getSendStatus()!=null, OOrderStockingItem::getSendStatus,bo.getSendStatus())
                .eq(bo.getRefundStatus()!=null, OOrderStockingItem::getRefundStatus,bo.getRefundStatus());

        return stockingItemMapper.listOOrderStockingItem(queryWrapper);
    }

    /**
     * 查询仓库备货商品清单
     * @param bo
     * @param pageQuery
     * @return
     */
    @Override
    public PageResult<OOrderStockingItem> queryStockingPageList(StockingOrderItemBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<OOrderStockingItem> queryWrapper = new LambdaQueryWrapper<OOrderStockingItem>()
                .eq(OOrderStockingItem::getSupplierId,0)
                .eq(OOrderStockingItem::getSendStatus,2)
                .eq(bo.getMerchantId()!=null, OOrderStockingItem::getMerchantId,bo.getMerchantId())
                .eq(StringUtils.hasText(bo.getOrderNum()), OOrderStockingItem::getOrderNum,bo.getOrderNum())
                .eq(StringUtils.hasText(bo.getGoodsNum()), OOrderStockingItem::getGoodsNum,bo.getGoodsNum())
                .eq(StringUtils.hasText(bo.getSkuCode()), OOrderStockingItem::getSkuCode,bo.getSkuCode())
                .eq(bo.getStockingStatus()!=null, OOrderStockingItem::getStockingStatus,bo.getStockingStatus())
//                .eq(bo.getStockingStatus()==null, OOrderStockingItem::getStockingStatus,0)
                ;

        pageQuery.setOrderByColumn("id");
        pageQuery.setIsAsc("desc");
        Page<OOrderStockingItem> pages = stockingItemMapper.pageOOrderStockingItem(pageQuery.build(), queryWrapper);

        return PageResult.build(pages);
    }

    /** 按商品生成出库单，同一订单先锁主单，再锁明细，避免重复生成。 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo generateStockOutByItem(ShipStockUpCompleteBo bo) {
        if (bo == null || bo.getIds() == null || bo.getIds().length == 0)
            return ResultVo.error("请选择备货明细");
        List<Long> ids = Arrays.stream(bo.getIds()).filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.size() != bo.getIds().length) return ResultVo.error("备货明细ID无效或重复");
        List<OOrderStockingItem> selected = stockingItemMapper.listOOrderStockingItem(
                new LambdaQueryWrapper<OOrderStockingItem>().in(OOrderStockingItem::getId, ids));
        if (selected.size() != ids.size()) return ResultVo.error("备货明细不存在");
        List<OOrderStocking> orders = new ArrayList<>();
        for (Long orderId : selected.stream().map(OOrderStockingItem::getShipOrderId).distinct().sorted().toList()) {
            OOrderStocking order = lockOrder(orderId);
            if (order == null) return ResultVo.error("备货单不存在");
            orders.add(order);
        }
        selected = stockingItemMapper.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>()
                .in(OOrderStockingItem::getId, ids).orderByAsc(OOrderStockingItem::getId).last("FOR UPDATE"));
        return generate(bo, orders, selected);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo generateStockOutByOrder(ShipStockUpCompleteBo bo) {
        if (bo == null || bo.getStockingId() == null) return ResultVo.error("请选择备货单");
        OOrderStocking order = lockOrder(bo.getStockingId());
        if (order == null) return ResultVo.error("备货单不存在");
        List<OOrderStockingItem> items = stockingItemMapper.listOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>()
                .eq(OOrderStockingItem::getShipOrderId, order.getId())
                .orderByAsc(OOrderStockingItem::getId).last("FOR UPDATE"));
        return generate(bo, List.of(order), items);
    }

    private OOrderStocking lockOrder(Long id) {
        return stockingMapper.getOOrderStockingOne(new LambdaQueryWrapper<OOrderStocking>()
                .eq(OOrderStocking::getId, id).last("FOR UPDATE"));
    }

    private ResultVo generate(ShipStockUpCompleteBo bo, List<OOrderStocking> orders, List<OOrderStockingItem> items) {
        if (items.isEmpty()) return ResultVo.error("没有可备货的商品");
        if (bo.getStockOutNum() != null && bo.getStockOutNum().trim().length() > 20)
            return ResultVo.error("出库单号不能超过20个字符");
        OOrderStocking first = orders.get(0);
        Long warehouseId = bo.getWarehouseId() != null ? bo.getWarehouseId() : first.getWarehouseId();
        ErpWarehouse warehouse = warehouseId == null ? null : warehouseMapper.getErpWarehouseById(warehouseId);
        if (warehouse == null || !"1".equals(warehouse.getStatus())
                || !"LOCAL".equals(warehouse.getWarehouseType())) return ResultVo.error("请选择启用的本地仓库");
        for (OOrderStocking order : orders) {
            if (!Integer.valueOf(0).equals(order.getType())) return ResultVo.error("只能为本地发货生成出库单");
            if (!Integer.valueOf(2).equals(order.getSendStatus())) return ResultVo.error("请先登记本地任务发货，再生成出库单");
            if (!Objects.equals(first.getMerchantId(), order.getMerchantId())
                    || !Objects.equals(first.getShopId(), order.getShopId()))
                return ResultVo.error("请选择同一商户、同一店铺的备货记录");
            if (order.getWarehouseId() != null && order.getWarehouseId() > 0
                    && !order.getWarehouseId().equals(warehouseId)) return ResultVo.error("出库仓库必须与发货仓库一致");
        }
        long total = 0;
        for (OOrderStockingItem item : items) {
            if (!Integer.valueOf(0).equals(item.getStockingStatus())) return ResultVo.error("所选商品已生成出库单，请勿重复操作");
            if (!Long.valueOf(0).equals(item.getSupplierId()) || !Integer.valueOf(1).equals(item.getRefundStatus()))
                return ResultVo.error("所选商品不是本地备货商品或存在售后");
            if (item.getGoodsId() == null || item.getGoodsId() <= 0 || item.getGoodsSkuId() == null || item.getGoodsSkuId() <= 0)
                return ResultVo.error("商品尚未关联商品库SKU");
            if (item.getQuantity() == null || item.getQuantity() <= 0) return ResultVo.error("出库数量必须大于0");
            if (item.getWarehouseId() != null && item.getWarehouseId() > 0 && !item.getWarehouseId().equals(warehouseId))
                return ResultVo.error("所选商品的发货仓库不一致");
            total += item.getQuantity();
        }
        if (total > Integer.MAX_VALUE) return ResultVo.error("出库总数量超出范围");
        LocalDateTime now = LocalDateTime.now();
        ErpStockOut out = new ErpStockOut();
        out.setOutNum(StringUtils.hasText(bo.getStockOutNum()) ? bo.getStockOutNum().trim()
                : "DDCK" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        out.setType(1);
        out.setWarehouseId(warehouseId);
        out.setWarehouseName(warehouse.getWarehouseName());
        if (orders.size() == 1) {
            out.setSourceId(first.getId());
            out.setSourceNum(first.getOrderNum());
        }
        out.setMerchantId(first.getMerchantId());
        out.setShopId(first.getShopId());
        out.setGoodsUnit((int) items.stream().map(OOrderStockingItem::getGoodsId).distinct().count());
        out.setSpecUnit(items.size());
        out.setSpecUnitTotal((int) total);
        out.setOutTotal(0);
        out.setStatus(0);
        out.setPrintStatus(0);
        out.setCreateTime(now);
        requireWrite(stockOutMapper.saveErpStockOut(out));
        for (OOrderStockingItem item : items) {
            ErpStockOutItem detail = new ErpStockOutItem();
            detail.setEntryId(out.getId());
            detail.setType(1);
            detail.setSourceOrderId(item.getShipOrderId());
            detail.setSourceOrderItemId(item.getId());
            detail.setSourceOrderNum(item.getOrderNum());
            detail.setOriginalQuantity(item.getQuantity());
            detail.setOutQuantity(0);
            detail.setStatus(0);
            detail.setWarehouseId(warehouseId);
            detail.setPositionId(0L);
            detail.setGoodsId(item.getGoodsId());
            detail.setSkuId(item.getGoodsSkuId());
            detail.setGoodsNum(item.getGoodsNum());
            detail.setGoodsName(item.getGoodsName());
            detail.setGoodsImage(item.getGoodsImg());
            detail.setSkuCode(item.getSkuCode());
            detail.setSkuName(item.getSkuName());
            detail.setMerchantId(item.getMerchantId());
            detail.setShopId(first.getShopId());
            detail.setPurPrice(0.0);
            detail.setCreateTime(now);
            detail.setUpdateTime(now);
            requireWrite(stockOutItemMapper.saveErpStockOutItem(detail));
            OOrderStockingItem update = new OOrderStockingItem();
            update.setId(item.getId());
            update.setStockingStatus(1);
            update.setWarehouseId(warehouseId);
            update.setWarehouseType(warehouse.getWarehouseType());
            update.setWarehouseName(warehouse.getWarehouseName());
            update.setUpdateTime(now);
            update.setUpdateBy("生成出库单");
            requireWrite(stockingItemMapper.updateOOrderStockingItem(update));
        }
        for (OOrderStocking order : orders) {
            long remaining = stockingItemMapper.countOOrderStockingItem(new LambdaQueryWrapper<OOrderStockingItem>()
                    .eq(OOrderStockingItem::getShipOrderId, order.getId()).eq(OOrderStockingItem::getStockingStatus, 0));
            OOrderStocking update = new OOrderStocking();
            update.setId(order.getId());
            update.setOrderType(order.getOrderType());
            update.setStockingStatus(remaining == 0 ? 2 : 1);
            update.setWarehouseId(warehouseId);
            update.setWarehouseType(warehouse.getWarehouseType());
            update.setWarehouseName(warehouse.getWarehouseName());
            update.setWarehouseNo(warehouse.getWarehouseNo());
            update.setUpdateTime(now);
            update.setUpdateBy("生成出库单");
            requireWrite(stockingMapper.updateOOrderStocking(update));
        }
        return ResultVo.success(out.getId());
    }

    private static void requireWrite(int affected) {
        if (affected != 1) throw new IllegalStateException("生成出库单失败，请重试");
    }
}
