package cn.xinghuaerp.purchase.service.impl;

import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.product.facade.ProductFacade;
import cn.xinghuaerp.product.entity.ErpSupplierProductItem;

import cn.xinghuaerp.common.DateHelper;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrder;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderItem;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderShip;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.purchase.request.PurchaseOrderAddItemBo;
import cn.xinghuaerp.purchase.request.PurchaseOrderAddRequest;
import cn.xinghuaerp.purchase.request.PurchaseOrderOptionRequest;
import cn.xinghuaerp.purchase.request.SearchRequest;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderItemMapper;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderMapper;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderShipMapper;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderService;
import cn.xinghuaerp.utils.DateUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
* @author qilip
* @description 针对表【scm_purchase_order(采购订单)】的数据库操作Service实现
* @createDate 2024-10-20 15:36:33
*/
@AllArgsConstructor
@Service
public class ErpPurchaseOrderServiceImpl extends ServiceImpl<ErpPurchaseOrderMapper, ErpPurchaseOrder>
    implements ErpPurchaseOrderService {
    private final ErpPurchaseOrderMapper erpPurchaseOrderMapper;
    private final ErpPurchaseOrderItemMapper erpPurchaseOrderItemMapper;
    private final ErpPurchaseOrderShipMapper shipMapper;
    private final ProductFacade goodsSkuMapper;
    private final ProductFacade supplierProductItemMapper;
    private final ErpSupplierMapper supplierMapper;

    
    @Override
    public PageResult<ErpPurchaseOrder> queryPageList(SearchRequest bo, PageQuery pageQuery) {
        if(org.springframework.util.StringUtils.hasText(bo.getStartTime())){
            boolean b = DateHelper.isValidDate(bo.getStartTime());
            if(!b){
//                bo.setStartTime(bo.getStartTime()+" 00:00:00");
                bo.setStartTime("");
            }
        }
        if(org.springframework.util.StringUtils.hasText(bo.getEndTime())){
            boolean b = DateHelper.isValidDate(bo.getEndTime());
            if(!b){
//                bo.setEndTime(bo.getEndTime()+" 23:59:59");
                bo.setEndTime("");
            }
        }else {
            bo.setEndTime(bo.getStartTime());
        }

        LambdaQueryWrapper<ErpPurchaseOrder> queryWrapper = new LambdaQueryWrapper<ErpPurchaseOrder>()
                .eq(bo.getMerchantId()!=null, ErpPurchaseOrder::getMerchantId,bo.getMerchantId())
                .eq(bo.getShopId()!=null, ErpPurchaseOrder::getShopId,bo.getShopId())
                .eq(bo.getSupplierId()!=null, ErpPurchaseOrder::getSupplierId,bo.getSupplierId())
                .eq(org.springframework.util.StringUtils.hasText(bo.getOrderNum()), ErpPurchaseOrder::getOrderNum,bo.getOrderNum())
                .eq(bo.getOrderStatus()!=null, ErpPurchaseOrder::getStatus,bo.getOrderStatus())
                .ge(org.springframework.util.StringUtils.hasText(bo.getStartTime()), ErpPurchaseOrder::getOrderTime,bo.getStartTime()+" 00:00:00")
                .le(org.springframework.util.StringUtils.hasText(bo.getEndTime()), ErpPurchaseOrder::getOrderTime,bo.getEndTime()+" 23:59:59")
                ;

        if(org.springframework.util.StringUtils.hasText(bo.getOrderDate())){

            boolean b = DateHelper.isValidDate(bo.getOrderDate());
            if(b){
//                queryWrapper.eq(ErpPurchaseOrder::getOrderDate,bo.getOrderDate());
                queryWrapper.last(" and order_date = '"+bo.getOrderDate()+"'");
            }
        }
        pageQuery.setOrderByColumn("order_time");
        pageQuery.setIsAsc("desc");
        Page<ErpPurchaseOrder> pages = erpPurchaseOrderMapper.selectPage(pageQuery.build(), queryWrapper);

        // 查询子订单
        if(pages.getRecords()!=null){
            for (ErpPurchaseOrder order:pages.getRecords()) {
                order.setItemList(erpPurchaseOrderItemMapper.selectList(new LambdaQueryWrapper<ErpPurchaseOrderItem>().eq(ErpPurchaseOrderItem::getOrderId, order.getId())));

            }
        }

        return PageResult.build(pages);
    }

    @Override
    public ErpPurchaseOrder getDetailById(Long id) {
        ErpPurchaseOrder order = erpPurchaseOrderMapper.selectById(id);
        if(order!=null){
            order.setItemList(erpPurchaseOrderItemMapper.selectList(new LambdaQueryWrapper<ErpPurchaseOrderItem>().eq(ErpPurchaseOrderItem::getOrderId, order.getId())));
            return order;
        }else
            return null;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> createPurchaseOrder(PurchaseOrderAddRequest addBo) {
        if(addBo == null || addBo.getGoodsList() == null || addBo.getGoodsList().isEmpty()) return ResultVo.error("商品明细不能为空");
        if (addBo.getContactId() == null || addBo.getContactId() <= 0) return ResultVo.error("请选择供应商");
        var supplier = supplierMapper.selectById(addBo.getContactId());
        if (supplier == null || !Integer.valueOf(0).equals(supplier.getDisable()) || !Integer.valueOf(0).equals(supplier.getIsDelete()))
            return ResultVo.error("供应商不存在或已停用");
        try { LocalDate.parse(addBo.getOrderDate()); }
        catch (RuntimeException e) { return ResultVo.error("采购日期无效"); }
        if (addBo.getStatus() != null && addBo.getStatus() != 0) return ResultVo.error("创建采购单只能是待审核状态");
        var seen = new HashSet<Long>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalQuantity = 0;
        for (PurchaseOrderAddItemBo item : addBo.getGoodsList()) {
            if (item == null || item.getId() == null || item.getId() <= 0) return ResultVo.error("采购商品SKU不能为空");
            if (!seen.add(item.getId())) return ResultVo.error("采购商品SKU不能重复");
            if (item.getQuantity() == null || item.getQuantity() <= 0 || item.getQuantity() > Integer.MAX_VALUE)
                return ResultVo.error("采购数量必须为正整数且不能超过系统上限");
            totalQuantity += item.getQuantity();
            if (totalQuantity > Integer.MAX_VALUE) return ResultVo.error("采购总数量超过系统上限");
            if (item.getPurPrice() == null || item.getPurPrice().signum() < 0 || item.getPurPrice().scale() > 2
                    || item.getPurPrice().compareTo(new BigDecimal("999999.99")) > 0)
                return ResultVo.error("采购单价无效");
            totalAmount = totalAmount.add(item.getPurPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        if (totalAmount.compareTo(new BigDecimal("99999999.99")) > 0) return ResultVo.error("采购总金额超过系统上限");
        if (addBo.getOrderAmount() == null || addBo.getOrderAmount().compareTo(totalAmount) != 0)
            return ResultVo.error("采购总金额与商品明细不一致");
        var skuIds = addBo.getGoodsList().stream().map(PurchaseOrderAddItemBo::getId).distinct().toList();
        var suppliedIds = supplierProductItemMapper.listErpSupplierProductItem(
                new LambdaQueryWrapper<ErpSupplierProductItem>()
                        .eq(ErpSupplierProductItem::getSupplierId, addBo.getContactId())
                        .ne(ErpSupplierProductItem::getStatus, 2)
                        .apply("EXISTS (SELECT 1 FROM erp_supplier_product p WHERE p.id = erp_supplier_product_item.supplier_product_id "
                                + "AND p.supplier_id = erp_supplier_product_item.supplier_id AND p.status <> 2)")
                        .in(ErpSupplierProductItem::getErpGoodsSkuId, skuIds))
                .stream().map(ErpSupplierProductItem::getErpGoodsSkuId)
                .collect(Collectors.toSet());
        if (!suppliedIds.containsAll(skuIds)) return ResultVo.error("采购商品未关联当前供应商，请先在供应商产品中维护供货关系");

        // 添加主表
        ErpPurchaseOrder erpPurchaseOrder = new ErpPurchaseOrder();
        erpPurchaseOrder.setMerchantId(0L);
        erpPurchaseOrder.setShopId(0L);
        erpPurchaseOrder.setShopId(addBo.getShopId() == null ? 0L : addBo.getShopId());
        erpPurchaseOrder.setOrderNum("PUR" + UUID.randomUUID().toString().replace("-", "").substring(0, 27));
        erpPurchaseOrder.setOrderAmount(totalAmount);
        erpPurchaseOrder.setCreateTime(DateUtils.getNowDate());
        erpPurchaseOrder.setOrderDate(addBo.getOrderDate());
        erpPurchaseOrder.setSupplierId(addBo.getContactId());
        erpPurchaseOrder.setOrderTime(System.currentTimeMillis()/1000);
        erpPurchaseOrder.setCreateBy(addBo.getCreateBy());
        erpPurchaseOrder.setStatus(0);
        erpPurchaseOrder.setShipAmount(BigDecimal.ZERO);
//        erpPurchaseOrderMapper.insert(erpPurchaseOrder);
        List<ErpPurchaseOrderItem> items = new ArrayList<>();
        // 添加子表
        for (PurchaseOrderAddItemBo item:addBo.getGoodsList()) {
            OGoodsSku oGoodsSku = goodsSkuMapper.getOGoodsSkuById(item.getId());
            if(oGoodsSku == null) {
                return ResultVo.error("找不到商品信息："+item.getId());
            }
            Long goodsId;
            try { goodsId = Long.valueOf(oGoodsSku.getGoodsId()); }
            catch (RuntimeException e) { return ResultVo.error("商品关联无效：" + item.getId()); }
            if (goodsId <= 0 || oGoodsSku.getInventoryMode() == null || (oGoodsSku.getInventoryMode() != 0 && oGoodsSku.getInventoryMode() != 1))
                return ResultVo.error("商品或库存模式无效：" + item.getId());
            ErpPurchaseOrderItem orderItem = new ErpPurchaseOrderItem();
            orderItem.setOrderDate(addBo.getOrderDate());
            orderItem.setOrderId(erpPurchaseOrder.getId());
            orderItem.setMerchantId(erpPurchaseOrder.getMerchantId());
            orderItem.setShopId(erpPurchaseOrder.getShopId());
            orderItem.setOrderNum(erpPurchaseOrder.getOrderNum());
            orderItem.setAmount(item.getPurPrice().multiply(BigDecimal.valueOf(item.getQuantity())).doubleValue());
            orderItem.setGoodsId(goodsId);
            orderItem.setGoodsNum(oGoodsSku.getGoodsNum());
            orderItem.setIsDelete(0);
            orderItem.setPrice(item.getPurPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setSpecId(item.getId());
            orderItem.setInventoryMode(oGoodsSku.getInventoryMode());
            orderItem.setSpecNum(oGoodsSku.getSkuCode());
            orderItem.setStatus(0);
            orderItem.setTransType("Purchase");
            orderItem.setGoodsName(oGoodsSku.getGoodsName());
            orderItem.setColorValue(oGoodsSku.getColorValue());
            orderItem.setColorImage(oGoodsSku.getColorImage());
            orderItem.setSizeValue(oGoodsSku.getSizeValue());
            orderItem.setStyleValue(oGoodsSku.getStyleValue());
            items.add(orderItem);
        }

        if (erpPurchaseOrderMapper.insert(erpPurchaseOrder) != 1 || erpPurchaseOrder.getId() == null)
            throw new IllegalStateException("采购单保存失败");
        for(ErpPurchaseOrderItem item:items){
            item.setOrderId(erpPurchaseOrder.getId());
            if (erpPurchaseOrderItemMapper.insert(item) != 1) throw new IllegalStateException("采购明细保存失败");
        }

        return ResultVo.success(erpPurchaseOrder.getId());
    }

    @Override
    public int updateScmPurchaseOrder(PurchaseOrderOptionRequest request) {
        if (request == null || request.getId() == null || request.getOptionType() == null
                || !List.of("audit", "confirm", "SupplierShip", "cancel").contains(request.getOptionType())) return -1;
        ErpPurchaseOrder order = erpPurchaseOrderMapper.selectById(request.getId());
        if(order == null) return -1;


        if(request.getOptionType().equals("audit")){
            if(order.getStatus() !=0 || !org.springframework.util.StringUtils.hasText(request.getUpdateBy())){
                // 状态不是待审核的
                return -1;
            }
            ErpPurchaseOrder erpPurchaseOrder = new ErpPurchaseOrder();
            erpPurchaseOrder.setId(order.getId());
            erpPurchaseOrder.setUpdateBy(request.getUpdateBy());
            erpPurchaseOrder.setUpdateTime(DateUtils.getNowDate());
            // updateBy 由 Controller 从登录上下文赋值，不能采信客户端指定的审核人。
            erpPurchaseOrder.setAuditUser(request.getUpdateBy());
            erpPurchaseOrder.setAuditTime(System.currentTimeMillis()/1000);
            erpPurchaseOrder.setRemark(request.getRemark());
            erpPurchaseOrder.setStatus(1);
            // 并发审核时仅允许一个待审核请求成功，避免覆盖已记录的审核人。
            return erpPurchaseOrderMapper.update(erpPurchaseOrder,
                    new LambdaQueryWrapper<ErpPurchaseOrder>()
                            .eq(ErpPurchaseOrder::getId, request.getId())
                            .eq(ErpPurchaseOrder::getStatus, 0));
        }
        else if (request.getOptionType().equals("confirm")) {
            if(order.getStatus() !=1){
                // 状态不是已审核的不能确认
                return -1;
            }
            // 查询数据
            List<ErpPurchaseOrderItem> items = erpPurchaseOrderItemMapper.selectList(new LambdaQueryWrapper<ErpPurchaseOrderItem>().eq(ErpPurchaseOrderItem::getOrderId, request.getId()));

            Map<Long, List<ErpPurchaseOrderItem>> goodsGroup = items.stream().collect(Collectors.groupingBy(x -> x.getGoodsId()));
            Long total = items.stream().mapToLong(ErpPurchaseOrderItem::getQuantity).sum();
            // 生成费用信息
//            ScmPurchaseOrderCost cost = new ScmPurchaseOrderCost();
//            cost.setId(order.getId());
//            cost.setOrderId(order.getId());
//            cost.setSupplierId(order.getSupplierId());
//            cost.setOrderNum(order.getOrderNum());
//            cost.setOrderDate(order.getOrderDate());
//            cost.setOrderGoodsUnit(goodsGroup.size());
//            cost.setOrderSpecUnit(items.size());
//            cost.setOrderSpecUnitTotal(total.intValue());
//            cost.setOrderAmount(order.getOrderAmount());
//            cost.setActualAmount(request.getTotalAmount());
//            cost.setFreight(BigDecimal.ZERO);
//            cost.setConfirmUser(request.getConfirmUser());
//            cost.setConfirmTime(LocalDateTime.now());
//            cost.setCreateBy(request.getUpdateBy());
//            cost.setPayAmount(BigDecimal.ZERO);
//            cost.setPayCount(0);
//            cost.setStatus(0);
//            costMapper.insert(cost);

            // 更新主表
            ErpPurchaseOrder erpPurchaseOrder = new ErpPurchaseOrder();
            erpPurchaseOrder.setId(order.getId());
            erpPurchaseOrder.setUpdateBy(request.getUpdateBy());
            erpPurchaseOrder.setUpdateTime(DateUtils.getNowDate());
            erpPurchaseOrder.setStatus(101);
            erpPurchaseOrder.setRemark(request.getRemark());
            erpPurchaseOrder.setSupplierConfirmTime(LocalDateTime.now());
            return erpPurchaseOrderMapper.update(erpPurchaseOrder,new LambdaQueryWrapper<ErpPurchaseOrder>()
                    .eq(ErpPurchaseOrder::getId,order.getId()).eq(ErpPurchaseOrder::getStatus,order.getStatus()));
        }
        else if (request.getOptionType().equals("SupplierShip")) {
            if(order.getStatus() !=101 && order.getStatus()!=1){
                // 状态不是已确认的不能发货
                return -1;
            }
            // 查询数据
            List<ErpPurchaseOrderItem> items = erpPurchaseOrderItemMapper.selectList(new LambdaQueryWrapper<ErpPurchaseOrderItem>().eq(ErpPurchaseOrderItem::getOrderId, request.getId()));

            Map<Long, List<ErpPurchaseOrderItem>> goodsGroup = items.stream().collect(Collectors.groupingBy(x -> x.getGoodsId()));
            Long total = items.stream().mapToLong(ErpPurchaseOrderItem::getQuantity).sum();

            // 生成物流信息
            ErpPurchaseOrderShip ship = new ErpPurchaseOrderShip();
            ship.setMerchantId(order.getMerchantId());
            ship.setShopId(order.getShopId());
            ship.setId(order.getId());
            ship.setOrderId(order.getId());
            ship.setSupplierId(order.getSupplierId());
            ship.setOrderNum(order.getOrderNum());
            ship.setOrderDate(order.getOrderDate());
            ship.setOrderGoodsUnit(goodsGroup.size());
            ship.setOrderSpecUnit(items.size());
            ship.setOrderSpecUnitTotal(total.intValue());
            ship.setShipCompany(request.getShipCompany());
            ship.setShipNum(request.getShipNo());
            ship.setFreight(request.getShipCost());
            ship.setShipTime(request.getSupplierDeliveryTime() != null && !request.getSupplierDeliveryTime().isEmpty()
                    ? LocalDateTime.of(LocalDate.parse(request.getSupplierDeliveryTime(), DateTimeFormatter.ISO_LOCAL_DATE), LocalTime.MIN)
                    : LocalDateTime.now());
            ship.setCreateBy(request.getUpdateBy());
            ship.setRemark(request.getRemark());
            ship.setCreateTime(LocalDateTime.now());
            ship.setStatus(0);
            ship.setBackCount(0);
            ship.setStockInCount(0);
            shipMapper.insert(ship);
            // 更新费用表


//            ScmSupplier scmSupplier = supplierMapper.selectScmSupplierById(order.getContactId());
            // 生成应付信息fms_payable_purchase
//            ScmPurchaseOrderPayable payable = new ScmPurchaseOrderPayable();
//            payable.setSupplierId(order.getSupplierId());
////            fmsPP.setSupplierName(scmSupplier!=null ? scmSupplier.getName():"数据库未找到供应商信息");
//            payable.setAmount(order.getOrderAmount().add(request.getShipCost()));
//            payable.setDate(LocalDateTime.now());
//            payable.setPurchaseOrderNum(order.getOrderNum());
//            payable.setPurchaseDesc("{采购商品总数量:"+total+",不同款式:"+goodsGroup.size()+",不同SKU:"+items.size()+",商品总价:"+order.getOrderAmount()+",运费:"+request.getShipCost()+"}");
//            payable.setStatus(0);
//            payable.setCreateTime(LocalDateTime.now());
//            payable.setCreateBy(request.getUpdateBy());
//            payableMapper.insert(payable);

            // 更新主表
            ErpPurchaseOrder erpPurchaseOrder = new ErpPurchaseOrder();
            erpPurchaseOrder.setId(order.getId());
            erpPurchaseOrder.setUpdateBy(request.getUpdateBy());
            erpPurchaseOrder.setUpdateTime(DateUtils.getNowDate());
            erpPurchaseOrder.setStatus(102);
            erpPurchaseOrder.setRemark(request.getRemark());
            erpPurchaseOrder.setSupplierDeliveryTime(LocalDateTime.now());
            erpPurchaseOrder.setShipAmount(request.getShipCost());
            return erpPurchaseOrderMapper.update(erpPurchaseOrder,new LambdaQueryWrapper<ErpPurchaseOrder>()
                    .eq(ErpPurchaseOrder::getId,order.getId()).eq(ErpPurchaseOrder::getStatus,order.getStatus()));
        }
        else if (request.getOptionType().equals("cancel")) {
            if(order.getStatus() !=0 && order.getStatus()!=1){
                // 状态不是已确认的不能发货
                return -1;
            }
            ErpPurchaseOrder erpPurchaseOrder = new ErpPurchaseOrder();
            erpPurchaseOrder.setId(order.getId());
            erpPurchaseOrder.setUpdateBy(request.getUpdateBy());
            erpPurchaseOrder.setUpdateTime(DateUtils.getNowDate());
            erpPurchaseOrder.setRemark(request.getRemark());
            erpPurchaseOrder.setStatus(99);
            return erpPurchaseOrderMapper.update(erpPurchaseOrder,new LambdaQueryWrapper<ErpPurchaseOrder>()
                    .eq(ErpPurchaseOrder::getId,order.getId()).eq(ErpPurchaseOrder::getStatus,order.getStatus()));
        }
        return 1;
    }
}




