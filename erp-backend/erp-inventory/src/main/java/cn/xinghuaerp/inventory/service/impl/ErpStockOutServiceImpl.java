package cn.xinghuaerp.inventory.service.impl;

import cn.xinghuaerp.inventory.entity.ErpStockOut;
import cn.xinghuaerp.inventory.entity.ErpStockOutItem;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.entity.OGoodsInventoryBatch;
import cn.xinghuaerp.inventory.entity.OOrderStockingItem;
import cn.xinghuaerp.inventory.entity.OOrderStockingItemBatch;
import cn.xinghuaerp.common.*;
import cn.xinghuaerp.inventory.request.StockOutItemRequest;
import cn.xinghuaerp.inventory.mapper.ErpStockOutMapper;
import cn.xinghuaerp.inventory.mapper.OOrderStockingMapper;
import cn.xinghuaerp.utils.DateUtils;
import cn.xinghuaerp.inventory.vo.GoodsSkuInventoryVo;
import cn.xinghuaerp.inventory.request.StockOutCreateRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.xinghuaerp.inventory.service.OGoodsInventoryBatchService;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import cn.xinghuaerp.inventory.service.ErpStockOutItemService;
import cn.xinghuaerp.inventory.service.ErpStockOutService;
import cn.xinghuaerp.inventory.service.ErpWarehouseService;
import cn.xinghuaerp.inventory.service.OOrderStockingItemBatchService;
import cn.xinghuaerp.inventory.service.OOrderStockingItemService;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.ResultVoEnum;
/**
* @author qilip
* @description 针对表【wms_stock_out(出库单)】的数据库操作Service实现
* @createDate 2024-09-22 11:13:23
*/
@AllArgsConstructor
@Service
public class ErpStockOutServiceImpl extends ServiceImpl<ErpStockOutMapper, ErpStockOut>
    implements ErpStockOutService {
    private final ErpStockOutMapper outMapper;
    private final ErpStockOutItemService outItemService;
    private final ErpWarehouseService erpWarehouseService;
    private final OOrderStockingItemBatchService orderStockingItemBatchService;
    private final OOrderStockingItemService orderStockingItemService;
    private final OOrderStockingMapper orderStockingMapper;
    private final OGoodsInventoryService oGoodsInventoryService;
    private final OGoodsInventoryBatchService oGoodsInventoryBatchService;

    @Override
    public PageResult<ErpStockOut> queryPageList(ErpStockOut bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ErpStockOut> queryWrapper = new LambdaQueryWrapper<ErpStockOut>()
                .eq(bo.getMerchantId()!=null, ErpStockOut::getMerchantId, bo.getMerchantId())
                .eq( bo.getShopId()!=null, ErpStockOut::getShopId, bo.getShopId())
                .eq( bo.getShopGroupId()!=null, ErpStockOut::getShopGroupId, bo.getShopGroupId())
                .eq( bo.getStatus()!=null, ErpStockOut::getStatus, bo.getStatus())
                .eq( bo.getType()!=null, ErpStockOut::getType, bo.getType())
                .eq( bo.getWarehouseId()!=null, ErpStockOut::getWarehouseId, bo.getWarehouseId())
                .eq(StringUtils.isNotBlank(bo.getOutNum()), ErpStockOut::getOutNum, bo.getOutNum())
                .eq(StringUtils.isNotBlank(bo.getSourceNum()), ErpStockOut::getSourceNum, bo.getSourceNum())
                .eq(bo.getSourceId()!=null, ErpStockOut::getSourceId, bo.getSourceId())
                ;

        Page<ErpStockOut> pages = outMapper.selectPage(pageQuery.build(), queryWrapper);
        return PageResult.build(pages);
    }

    /**
     * 手动创建出库单
     * @param userId
     * @param userName
     * @param request
     * @return
     */
    @Transactional
    @Override
    public ResultVo<Long> createEntry(Long userId, String userName, StockOutCreateRequest request) {
        if(request.getType() == null ) return ResultVo.error(ResultVoEnum.ParamsError,"缺少参数type");
        if(request.getItemList().isEmpty()) return ResultVo.error(ResultVoEnum.ParamsError,"缺少参数itemList");
        ErpWarehouse erpWarehouse = erpWarehouseService.getById(request.getWarehouseId());
        if(erpWarehouse==null) return ResultVo.error("仓库信息不存在");
        if(StringUtils.isBlank(request.getOutNum())){
            request.setOutNum(DateUtils.parseDateToStr("yyyyMMddHHmmss",LocalDateTime.now()));
        }
        if(StringUtils.isBlank(request.getOperator())){
            request.setOperator(userName);
        }

        Map<Long, List<GoodsSkuInventoryVo>> goodsGroup = request.getItemList().stream()
                .filter(x -> x.getGoodsId() != null)
                .collect(Collectors.groupingBy(GoodsSkuInventoryVo::getGoodsId));
        Long total = request.getItemList().stream().mapToLong(x -> x.getQuantity()!=null ? x.getQuantity() : 0).sum();

        //添加主表信息
        ErpStockOut stockOut = new ErpStockOut();
        stockOut.setMerchantId(0L);
        stockOut.setShopId(0L);
        stockOut.setShopGroupId(0L);
        stockOut.setOutNum(request.getOutNum());
        stockOut.setType(request.getType());
        stockOut.setSourceNum(request.getSourceNo());
        stockOut.setSourceId(0L);
        stockOut.setWarehouseId(request.getWarehouseId());
        stockOut.setRemark(request.getRemark());
        stockOut.setCreateBy(userName);
        stockOut.setCreateTime(LocalDateTime.now());
        stockOut.setGoodsUnit(goodsGroup.size());
        stockOut.setSpecUnit(request.getItemList().size());
        stockOut.setSpecUnitTotal(total.intValue());
        stockOut.setOutTotal(0);
        stockOut.setOperatorId(userId.toString());
        stockOut.setOperatorName(StringUtils.isEmpty(request.getOperator())?userName:request.getOperator());
        stockOut.setPrintStatus(0);
        stockOut.setRemark(request.getRemark());
        stockOut.setStatus(0);//状态（0待入库1部分入库2全部入库）
        stockOut.setWarehouseId(request.getWarehouseId());
        stockOut.setWarehouseName(erpWarehouse.getWarehouseName());
        outMapper.insert(stockOut);

        //添加子表信息
        List<ErpStockOutItem> itemList = new ArrayList<>();
        for(GoodsSkuInventoryVo item: request.getItemList()){
            if(org.springframework.util.StringUtils.hasText(item.getBatchId())) {
                // 指定了库存批次
                OGoodsInventoryBatch batch = oGoodsInventoryBatchService.getById(item.getBatchId());
                if(batch!=null) {
                    ErpStockOutItem outItem = new ErpStockOutItem();
                    outItem.setMerchantId(stockOut.getMerchantId());
                    outItem.setShopId(stockOut.getShopId());
                    outItem.setShopGroupId(stockOut.getShopGroupId());
                    outItem.setEntryId(stockOut.getId());
                    outItem.setType(request.getType());
                    outItem.setBatchId(batch.getId());
                    outItem.setInventoryMode(batch.getInventoryMode());
                    outItem.setGoodsId(batch.getGoodsId());
                    outItem.setPurPrice(batch.getPurPrice() != null ? batch.getPurPrice().doubleValue() : null);
                    outItem.setSkuId(batch.getSkuId());
                    outItem.setSkuCode(batch.getSkuCode());
                    outItem.setGoodsName(item.getGoodsName());
                    outItem.setGoodsNum(item.getGoodsNum());
                    outItem.setSkuName(item.getSkuName());
                    outItem.setGoodsImage(item.getGoodsImg());
                    outItem.setOriginalQuantity(item.getQuantity());
                    outItem.setOutQuantity(0);
                    outItem.setStatus(0);
                    outItem.setWarehouseId(stockOut.getWarehouseId());
                    outItem.setPositionId(0L);
                    outItem.setPurPrice(0.0);
                    outItem.setCreateBy(userName);
                    outItem.setCreateTime(LocalDateTime.now());
                    outItem.setWarehouseId(batch.getWarehouseId());
                    outItem.setPositionId(batch.getPositionId());
                    outItem.setPositionNum(batch.getPositionNum());
                    itemList.add(outItem);
                }
            } else {
                // 未指定批次，仅记录SKU信息，出库时再分配批次
                ErpStockOutItem outItem = new ErpStockOutItem();
                outItem.setMerchantId(stockOut.getMerchantId());
                outItem.setShopId(stockOut.getShopId());
                outItem.setShopGroupId(stockOut.getShopGroupId());
                outItem.setEntryId(stockOut.getId());
                outItem.setType(request.getType());
                outItem.setBatchId(null);
                outItem.setInventoryMode(0);
                outItem.setGoodsId(item.getGoodsId());
                outItem.setSkuId(item.getSkuId());
                outItem.setSkuCode(item.getSkuCode());
                outItem.setGoodsName(item.getGoodsName());
                outItem.setSkuName(item.getSkuName());
                outItem.setGoodsImage(item.getGoodsImg());
                outItem.setGoodsNum(item.getGoodsNum());
                outItem.setPurPrice(item.getPurPrice() != null ? item.getPurPrice().doubleValue() : null);
                outItem.setOriginalQuantity(item.getQuantity());
                outItem.setOutQuantity(0);
                outItem.setStatus(0);
                outItem.setWarehouseId(stockOut.getWarehouseId());
                outItem.setPositionId(0L);
                outItem.setPurPrice(0.0);
                outItem.setCreateBy(userName);
                outItem.setCreateTime(LocalDateTime.now());
                outItem.setWarehouseId(request.getWarehouseId());
                itemList.add(outItem);
            }
        }
        outItemService.saveBatch(itemList);
        return ResultVo.success(stockOut.getId());
    }

    @Override
    public ErpStockOut getDetailAndItemById(Long id) {
        ErpStockOut erpStockOut = outMapper.selectById(id);
        if(erpStockOut !=null){
            List<ErpStockOutItem> itemList = outItemService.list(
                    new LambdaQueryWrapper<ErpStockOutItem>().eq(ErpStockOutItem::getEntryId,id)
                            .eq(ErpStockOutItem::getMerchantId,erpStockOut.getMerchantId()).eq(ErpStockOutItem::getShopId,erpStockOut.getShopId()));
            // 填充批次信息
            for(ErpStockOutItem item : itemList){
                if(item.getBatchId() != null){
                    OGoodsInventoryBatch batch = oGoodsInventoryBatchService.getById(item.getBatchId());
                    if(batch != null && java.util.Objects.equals(batch.getMerchantId(),item.getMerchantId())
                            && java.util.Objects.equals(batch.getSkuId(),item.getSkuId())
                            && java.util.Objects.equals(batch.getWarehouseId(),item.getWarehouseId())
                            && (java.util.Objects.equals(batch.getShopId(), 0L) || java.util.Objects.equals(batch.getShopId(),item.getShopId()))){
                        item.setBatchNum(batch.getBatchNum());
                        item.setBatchCurrentQty(batch.getCurrentQty());
                    }
                }
            }
            erpStockOut.setItemList(itemList);
        }
        return erpStockOut;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> stockOut(Long userId, String userName, StockOutItemRequest request) {
        if (request == null || request.getEntryItemId() == null) return ResultVo.error("请选择出库明细");
        if (request.getOutQty() == null || request.getOutQty() <= 0) return ResultVo.error("出库数量必须大于0");
        ErpStockOutItem snapshot = outItemService.getById(request.getEntryItemId());
        if (snapshot == null) return ResultVo.error("出库明细不存在");
        // 同一出库单先锁主单，保证多行同时出库时累计数量准确。
        ErpStockOut entry = outMapper.selectOne(new LambdaQueryWrapper<ErpStockOut>()
                .eq(ErpStockOut::getId, snapshot.getEntryId()).last("FOR UPDATE"));
        if (entry == null) return ResultVo.error("出库单不存在");
        ErpStockOutItem item = outItemService.getOne(new LambdaQueryWrapper<ErpStockOutItem>()
                .eq(ErpStockOutItem::getId, snapshot.getId()).last("FOR UPDATE"));
        if (request.getEntryId() != null && !request.getEntryId().equals(entry.getId()))
            return ResultVo.error("出库明细不属于该出库单");
        if (item == null || !java.util.Objects.equals(item.getEntryId(), entry.getId())
                || item.getMerchantId() == null || item.getShopId() == null
                || !java.util.Objects.equals(item.getMerchantId(), entry.getMerchantId())
                || !java.util.Objects.equals(item.getShopId(), entry.getShopId())
                || !java.util.Objects.equals(item.getWarehouseId(), entry.getWarehouseId()))
            return ResultVo.error("出库单和明细主体不一致");
        int completed = item.getOutQuantity() == null ? 0 : item.getOutQuantity();
        if (item.getOriginalQuantity() == null || Integer.valueOf(2).equals(item.getStatus())
                || request.getOutQty() > item.getOriginalQuantity() - completed)
            return ResultVo.error("出库数量不能超过剩余待出库数量");

        List<OGoodsInventory> inventories = oGoodsInventoryService.list(new LambdaQueryWrapper<OGoodsInventory>()
                .eq(OGoodsInventory::getSkuId, item.getSkuId())
                .eq(OGoodsInventory::getWarehouseId, item.getWarehouseId())
                .eq(OGoodsInventory::getMerchantId, item.getMerchantId())
                .eq(OGoodsInventory::getStockStatus, 1)
                .in(OGoodsInventory::getShopId, 0L, item.getShopId())
                .and(q -> q.eq(OGoodsInventory::getIsDelete, 0).or().isNull(OGoodsInventory::getIsDelete))
                .orderByAsc(OGoodsInventory::getId).last("FOR UPDATE"));
        if (inventories.isEmpty()) return ResultVo.error("没有找到库存数据");
        Long batchId = request.getBatchId() != null ? request.getBatchId() : item.getBatchId();
        OGoodsInventory inventory = inventories.get(0);
        List<OGoodsInventoryBatch> batches;
        if (batchId != null) {
            OGoodsInventoryBatch batch = oGoodsInventoryBatchService.getOne(new LambdaQueryWrapper<OGoodsInventoryBatch>()
                    .eq(OGoodsInventoryBatch::getId, batchId).last("FOR UPDATE"));
            if (batch == null || !Integer.valueOf(1).equals(batch.getStockStatus()) || !java.util.Objects.equals(batch.getSkuId(), item.getSkuId())
                    || !java.util.Objects.equals(batch.getWarehouseId(), item.getWarehouseId()))
                return ResultVo.error("所选批次与出库商品或仓库不一致");
            inventory = inventories.stream().filter(inv -> inv.getId().equals(batch.getInventoryId())).findFirst().orElse(null);
            if (inventory == null) return ResultVo.error("批次与总库存关联错误");
            batches = List.of(batch);
        } else {
            if (inventories.size() > 1) return ResultVo.error("存在多条可用库存，请选择所属批次或先核对库存归属");
            batches = oGoodsInventoryBatchService.list(new LambdaQueryWrapper<OGoodsInventoryBatch>()
                    .eq(OGoodsInventoryBatch::getInventoryId, inventory.getId())
                    .eq(OGoodsInventoryBatch::getSkuId, item.getSkuId())
                    .eq(OGoodsInventoryBatch::getWarehouseId, item.getWarehouseId())
                    .eq(OGoodsInventoryBatch::getStockStatus, 1)
                    .gt(OGoodsInventoryBatch::getCurrentQty, 0)
                    .orderByAsc(OGoodsInventoryBatch::getCreateTime, OGoodsInventoryBatch::getId).last("FOR UPDATE"));
        }
        long available = batches.stream().mapToLong(batch -> batch.getCurrentQty() == null ? 0 : batch.getCurrentQty()).sum();
        if (inventory.getQuantity() == null || inventory.getAvailableQuantity() == null
                || inventory.getQuantity() < request.getOutQty() || inventory.getAvailableQuantity() < request.getOutQty()
                || available < request.getOutQty()) return ResultVo.error("库存不足");
        OOrderStockingItem source = null;
        if (Integer.valueOf(1).equals(item.getType()) && item.getSourceOrderItemId() != null) {
            source = orderStockingItemService.getById(item.getSourceOrderItemId());
            if (source == null) return ResultVo.error("找不到关联的销售备货明细");
        }
        // 所有业务校验完成后再写入；任意一步失败均回滚。
        requireStockWrite(oGoodsInventoryService.deductStock(inventory.getId(), request.getOutQty()));
        int remaining = request.getOutQty();
        for (OGoodsInventoryBatch batch : batches) {
            if (remaining == 0) break;
            int quantity = Math.min(batch.getCurrentQty(), remaining);
            if (quantity <= 0) continue;
            requireStockWrite(oGoodsInventoryBatchService.deductBatchStock(batch.getId(), quantity));
            remaining -= quantity;
            if (source != null) {
                OOrderStockingItemBatch record = new OOrderStockingItemBatch();
                record.setOrderStockingId(source.getShipOrderId());
                record.setOrderStockingItemId(source.getId());
                record.setOrderId(source.getOOrderId().toString());
                record.setOrderItemId(source.getOOrderItemId().toString());
                record.setMerchantId(item.getMerchantId());
                record.setShopId(item.getShopId());
                record.setBatchId(batch.getId());
                record.setBatchNum(batch.getBatchNum());
                record.setInventoryId(batch.getInventoryId());
                record.setGoodsId(batch.getGoodsId());
                record.setGoodsNo(batch.getSkuCode());
                record.setQuantity(quantity);
                record.setUnitCost(batch.getPurPrice());
                record.setTotalCost(batch.getPurPrice() == null ? null : batch.getPurPrice().multiply(java.math.BigDecimal.valueOf(quantity)));
                record.setWarehouseId(batch.getWarehouseId());
                record.setInventoryMode(batch.getInventoryMode());
                record.setBarcode(batch.getBarcode());
                record.setActualGoldWeight(batch.getActualGoldWeight());
                record.setActualSilverWeight(batch.getActualSilverWeight());
                record.setLaborCost(batch.getLaborCost());
                record.setCertificateNo(batch.getCertificateNo());
                record.setPurPrice(batch.getPurPrice());
                record.setCreateTime(LocalDateTime.now());
                record.setCreateBy(userName);
                requireStockWrite(orderStockingItemBatchService.save(record));
            }
        }
        LocalDateTime now = LocalDateTime.now();
        ErpStockOutItem update = new ErpStockOutItem();
        update.setId(item.getId());
        update.setOutQuantity(completed + request.getOutQty());
        boolean complete = update.getOutQuantity().equals(item.getOriginalQuantity());
        update.setStatus(complete ? 2 : 1);
        if (complete) update.setCompleteTime(now);
        update.setUpdateBy(userName);
        update.setUpdateTime(now);
        requireStockWrite(outItemService.updateById(update));
        long unfinished = outItemService.count(new LambdaQueryWrapper<ErpStockOutItem>()
                .eq(ErpStockOutItem::getEntryId, entry.getId()).ne(ErpStockOutItem::getStatus, 2));
        ErpStockOut header = new ErpStockOut();
        header.setId(entry.getId());
        header.setStatus(unfinished == 0 ? 2 : 1);
        if (unfinished == 0) header.setCompleteTime(now);
        header.setOutTotal((entry.getOutTotal() == null ? 0 : entry.getOutTotal()) + request.getOutQty());
        header.setOperatorId(userId.toString());
        header.setOperatorName(userName);
        header.setOutTime(now);
        header.setUpdateBy(userName);
        header.setUpdateTime(now);
        requireStockWrite(outMapper.updateById(header) == 1);
        return ResultVo.success(entry.getId());
    }

    private static void requireStockWrite(boolean success) {
        if (!success) throw new IllegalStateException("保存出库信息失败，库存变更已回滚，请重试");
    }
}
