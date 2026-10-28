package cn.xinghuaerp.inventory.service.impl;

import cn.xinghuaerp.product.entity.OGoods;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.inventory.entity.ErpStockIn;
import cn.xinghuaerp.inventory.entity.ErpStockInItem;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.entity.OGoodsInventoryBatch;
import cn.xinghuaerp.inventory.request.StockInCreateItem;
import cn.xinghuaerp.inventory.request.StockInCreateRequest;
import cn.xinghuaerp.inventory.request.StockInItem;
import cn.xinghuaerp.inventory.request.StockInItemInRequest;
import cn.xinghuaerp.inventory.request.StockInLocalRequest;
import cn.xinghuaerp.inventory.request.StockInRequest;
import cn.xinghuaerp.common.*;
import cn.xinghuaerp.utils.DateUtils;
import cn.xinghuaerp.inventory.mapper.ErpStockInMapper;
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
import cn.xinghuaerp.product.facade.OGoodsFacade;
import cn.xinghuaerp.product.facade.OGoodsSkuFacade;
import cn.xinghuaerp.inventory.service.ErpStockInItemService;
import cn.xinghuaerp.inventory.service.ErpStockInService;
import cn.xinghuaerp.inventory.service.ErpWarehouseService;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.ResultVoEnum;
/**
* @author qilip
* @description 针对表【wms_stock_in(入库单)】的数据库操作Service实现
* @createDate 2024-09-22 16:10:08
*/
@AllArgsConstructor
@Service
public class ErpStockInServiceImpl extends ServiceImpl<ErpStockInMapper, ErpStockIn>
    implements ErpStockInService {
    private final ErpStockInMapper mapper;
    private final ErpStockInItemService inItemService;
    private final OGoodsSkuFacade skuService;
    private final OGoodsFacade goodsService;
    private final ErpWarehouseService warehouseService;
    private final OGoodsInventoryService oGoodsInventoryService;
    private final OGoodsInventoryBatchService oGoodsInventoryBatchService;


    @Override
    public PageResult<ErpStockIn> queryPageList(ErpStockIn bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ErpStockIn> queryWrapper = new LambdaQueryWrapper<ErpStockIn>()
                .eq(bo.getMerchantId()!=null,ErpStockIn::getMerchantId,bo.getMerchantId())
                .eq(bo.getShopId()!=null,ErpStockIn::getShopId,bo.getShopId())
                .eq(bo.getWarehouseId()!=null,ErpStockIn::getWarehouseId,bo.getWarehouseId())
                .eq( bo.getStatus()!=null, ErpStockIn::getStatus, bo.getStatus())
                .eq( bo.getStockInType()!=null, ErpStockIn::getStockInType, bo.getStockInType())
                .eq(StringUtils.isNotBlank(bo.getStockInNum()), ErpStockIn::getStockInNum, bo.getStockInNum())
                .eq(StringUtils.isNotBlank(bo.getSourceNo()), ErpStockIn::getSourceNo, bo.getSourceNo())
                .eq(bo.getSourceId()!=null, ErpStockIn::getSourceId, bo.getSourceId())
            ;

        Page<ErpStockIn> pages = mapper.selectPage(pageQuery.build(), queryWrapper);
        if(!pages.getRecords().isEmpty()){
            for (ErpStockIn record : pages.getRecords()){
                record.setItemList(inItemService.list(new LambdaQueryWrapper<ErpStockInItem>().eq(ErpStockInItem::getStockInId,record.getId()).eq(ErpStockInItem::getMerchantId,record.getMerchantId()).eq(ErpStockInItem::getShopId,record.getShopId())));
            }
        }
        return PageResult.build(pages);
    }

    @Override
    public PageResult<ErpStockIn> queryCloudWarehouseStockInPageList(ErpStockIn bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ErpStockIn> queryWrapper = new LambdaQueryWrapper<ErpStockIn>()
                .eq(bo.getMerchantId()!=null,ErpStockIn::getMerchantId,bo.getMerchantId())
                .eq(bo.getWarehouseId()!=null,ErpStockIn::getWarehouseId,bo.getWarehouseId())
                .eq( bo.getStatus()!=null, ErpStockIn::getStatus, bo.getStatus())
                .eq( bo.getStockInType()!=null, ErpStockIn::getStockInType, bo.getStockInType())
                .eq(StringUtils.isNotBlank(bo.getStockInNum()), ErpStockIn::getStockInNum, bo.getStockInNum())
                .eq(StringUtils.isNotBlank(bo.getSourceNo()), ErpStockIn::getSourceNo, bo.getSourceNo())
                .eq(bo.getSourceId()!=null, ErpStockIn::getSourceId, bo.getSourceId())
                ;

        Page<ErpStockIn> pages = mapper.selectPage(pageQuery.build(), queryWrapper);
        if(!pages.getRecords().isEmpty()){
            for (ErpStockIn record : pages.getRecords()){
                record.setItemList(inItemService.list(new LambdaQueryWrapper<ErpStockInItem>().eq(ErpStockInItem::getStockInId,record.getId()).eq(ErpStockInItem::getMerchantId,record.getMerchantId()).eq(ErpStockInItem::getShopId,record.getShopId())));
            }
        }
        return PageResult.build(pages);
    }

    @Transactional
    @Override
    public ResultVo<Long> createEntry(Long userId, String userName, StockInCreateRequest request) {
        if(request.getStockInType() == null ) return ResultVo.error(ResultVoEnum.ParamsError,"缺少参数stockInType");
        if(request.getItemList().isEmpty()) return ResultVo.error(ResultVoEnum.ParamsError,"缺少参数itemList");
        if(request.getWarehouseId()==null) return ResultVo.error("缺少参数：warehouseId");

        ErpWarehouse erpWarehouse = warehouseService.getById(request.getWarehouseId());
        if(erpWarehouse==null) {
            return ResultVo.error("仓库不存在");
        }
        if(StringUtils.isBlank(request.getStockInNum())){
            request.setStockInNum(DateUtils.parseDateToStr("yyyyMMddHHmmss",LocalDateTime.now()));
        }
        if(StringUtils.isBlank(request.getStockInOperator())){
            request.setStockInOperator(userName);
        }

        Map<String, List<StockInCreateItem>> goodsGroup = request.getItemList().stream()
                .filter(x -> x.getGoodsId() != null)
                .collect(Collectors.groupingBy(StockInCreateItem::getGoodsId));
        Long total = request.getItemList().stream().mapToLong(StockInCreateItem::getQuantity).sum();
        //添加主表信息
        ErpStockIn insert = new ErpStockIn();
        insert.setMerchantId(request.getMerchantId());
        insert.setShopId(request.getShopId());
        insert.setStockInNum(request.getStockInNum());
        insert.setStockInType(request.getStockInType());
        insert.setStockInOperator(request.getStockInOperator());
        insert.setStockInOperatorId(userId+"");
//        insert.setStockInTime(LocalDateTime.now());
        insert.setSourceNo(request.getSourceNo());
        insert.setRemark(request.getRemark());
        insert.setCreateBy(userName);
        insert.setCreateTime(LocalDateTime.now());
        insert.setSourceGoodsUnit(goodsGroup.size());
        insert.setSourceSpecUnit(request.getItemList().size());
        insert.setSourceSpecUnitTotal(total.intValue());
        insert.setStatus(0);//状态（0待入库1部分入库2全部入库）
        insert.setWarehouseId(erpWarehouse.getId());
        insert.setWarehouseNo(erpWarehouse.getWarehouseNo());
        insert.setWarehouseName(erpWarehouse.getWarehouseName());
        insert.setWarehouseType(erpWarehouse.getWarehouseType());
        insert.setMerchantId(0L);
        insert.setShopId(0L);
        mapper.insert(insert);

        //添加子表信息
        List<ErpStockInItem> itemList = new ArrayList<>();
        for(StockInCreateItem item: request.getItemList()){
            if(item.getSkuId()!=null) {
                OGoodsSku goodsSku = skuService.getById(item.getSkuId());
                if(goodsSku!=null) {
                    OGoods goods = goodsService.getById(goodsSku.getGoodsId());
                    ErpStockInItem inItem = new ErpStockInItem();
                    inItem.setMerchantId(request.getMerchantId());
                    inItem.setShopId(request.getShopId());
                    inItem.setStockInId(insert.getId());
                    inItem.setWarehouseId(insert.getWarehouseId());
                    inItem.setStockInType(insert.getStockInType());
                    inItem.setSourceNo(insert.getSourceNo());
                    inItem.setSourceId(0L);
                    inItem.setSourceItemId(0L);
                    inItem.setGoodsId(goodsSku.getGoodsId());
                    inItem.setGoodsName(item.getGoodsName());
                    inItem.setGoodsImage(item.getGoodsImg());
                    inItem.setGoodsNum(goods!=null?goods.getGoodsNum():"");
                    inItem.setSkuName(item.getSkuName());
                    inItem.setSkuId(item.getSkuId());
                    inItem.setSkuCode(item.getSkuCode());
                    inItem.setInventoryMode(0);
                    inItem.setQuantity(item.getQuantity());
                    inItem.setInQuantity(0);
                    inItem.setPurPrice(item.getPurPrice());
                    inItem.setStatus(0);
                    inItem.setCreateBy(userName);
                    inItem.setCreateTime(LocalDateTime.now());
                    inItem.setMerchantId(0L);
                    inItem.setShopId(0L);
                    itemList.add(inItem);
                }
            }
        }
        inItemService.saveBatch(itemList);
        return ResultVo.success();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> stockIn(Long userId, String userName, StockInRequest request) {
        if (request.getStockInId() == null) return ResultVo.error(ResultVoEnum.ParamsError, "缺少参数stockInId");
        if (request.getWarehouseId() == null) return ResultVo.error(ResultVoEnum.ParamsError, "缺少参数warehouseId");
        if (request.getItemList() == null || request.getItemList().isEmpty()) return ResultVo.error(ResultVoEnum.ParamsError, "缺少入库数据");

        ErpStockIn erpStockIn = mapper.selectOne(new LambdaQueryWrapper<ErpStockIn>().eq(ErpStockIn::getId, request.getStockInId()).last("FOR UPDATE"));
        if (erpStockIn != null && erpStockIn.getSourceNo() != null && erpStockIn.getSourceNo().startsWith("LOCAL-AF-")) return ResultVo.error("本地售后入库请使用本地仓实际入库入口，不能通过旧入口绕过校验");
        if (erpStockIn == null) return ResultVo.error(ResultVoEnum.NotFound, "没有找到入库单");
        else if (erpStockIn.getStatus() == 2) {
            return ResultVo.error(ResultVoEnum.SystemException, "入库单状态不能入库");
        }
        if (!java.util.Objects.equals(erpStockIn.getWarehouseId(), request.getWarehouseId())) return ResultVo.error("入库仓库与单据不一致");

        List<StockInItem> waitList = new ArrayList<>();
        for (StockInItem item : request.getItemList()) {
            if (item == null || item.getId() == null || item.getIntoQuantity() == null || item.getIntoQuantity() <= 0)
                return ResultVo.error("入库明细及数量无效");
//            if (item.getIntoQuantity() != null && item.getIntoQuantity() > 0 && item.getPositionId() != null && item.getPositionId() > 0) {
            if (item.getIntoQuantity() != null && item.getIntoQuantity() > 0 ) {
                waitList.add(item);
            }
        }
        if (waitList.size() == 0) return ResultVo.error(ResultVoEnum.ParamsError, "缺少入库明细数据");
        var ids = waitList.stream().map(StockInItem::getId).distinct().sorted().toList();
        if (ids.size() != waitList.size()) return ResultVo.error("入库明细不能重复");
        var lockedItems = inItemService.list(new LambdaQueryWrapper<ErpStockInItem>().in(ErpStockInItem::getId, ids)
                .orderByAsc(ErpStockInItem::getId).last("FOR UPDATE"));
        if (lockedItems.size() != ids.size()) return ResultVo.error("入库明细不存在");
        for (var requested : waitList) {
            var detail = lockedItems.stream().filter(i -> i.getId().equals(requested.getId())).findFirst().orElseThrow();
            if (!java.util.Objects.equals(detail.getStockInId(), erpStockIn.getId())
                    || !java.util.Objects.equals(detail.getMerchantId(), erpStockIn.getMerchantId())
                    || !java.util.Objects.equals(detail.getShopId(), erpStockIn.getShopId())
                    || (detail.getWarehouseId() != null && !java.util.Objects.equals(detail.getWarehouseId(), erpStockIn.getWarehouseId())))
                return ResultVo.error("入库单和明细归属不一致");
            if (detail.getSkuId() == null || detail.getSkuId().isBlank()) return ResultVo.error("入库商品SKU不存在");
            if (!Integer.valueOf(0).equals(detail.getInventoryMode())) return ResultVo.error("该入口仅支持传统SKU");
            int completed = detail.getInQuantity() == null ? 0 : detail.getInQuantity();
            if (completed < 0 || detail.getQuantity() == null || Integer.valueOf(2).equals(detail.getStatus()) || requested.getIntoQuantity() > detail.getQuantity() - completed)
                return ResultVo.error("入库数量超过剩余待入库数量");
        }
        for (String skuId : lockedItems.stream().map(ErpStockInItem::getSkuId).distinct().sorted().toList()) {
            if (skuService.getOne(new LambdaQueryWrapper<OGoodsSku>().eq(OGoodsSku::getId, skuId).last("FOR UPDATE")) == null)
                return ResultVo.error("入库商品SKU不存在");
        }

        // 开始入库
        for (StockInItem item : waitList) {
            // 查询明细
            ErpStockInItem stockInItem = lockedItems.stream().filter(i -> i.getId().equals(item.getId())).findFirst().orElseThrow();
            if (stockInItem == null) {
                return ResultVo.error(ResultVoEnum.DataError, "数据错误！没有找到入库单明细");
            }
            requireMatchingEntry(erpStockIn, stockInItem);
            OGoodsSku oGoodsSku = skuService.getById(stockInItem.getSkuId());

            // 使用主系统库存表 OGoodsInventory
            Long goodsInventoryId = null;
            List<OGoodsInventory> invList = oGoodsInventoryService.list(
                    inventoryScope(request.getWarehouseId(), stockInItem));
            if (invList.size() > 1) throw new IllegalStateException("存在多条可用库存，请先核对库存归属");
            if (invList.isEmpty()) {
                OGoodsInventory inv = new OGoodsInventory();
                inv.setWarehouseId(request.getWarehouseId());
                inv.setMerchantId(stockInItem.getMerchantId());
                inv.setShopId(stockInItem.getShopId());
                inv.setGoodsId(Long.parseLong(stockInItem.getGoodsId()));
                inv.setSkuId(Long.parseLong(stockInItem.getSkuId()));
                inv.setSkuCode(stockInItem.getSkuCode());
                inv.setGoodsNum(oGoodsSku.getGoodsNum());
                inv.setGoodsName(oGoodsSku.getGoodsName());
                inv.setSkuName(oGoodsSku.getSkuName());
                inv.setGoodsImg(oGoodsSku.getColorImage());
                inv.setQuantity(item.getIntoQuantity());
                inv.setLockedQuantity(0);
                inv.setAvailableQuantity(item.getIntoQuantity());
                inv.setStockStatus(1);
                inv.setIsDelete(0);
                inv.setCreateBy(userName);
                inv.setCreateTime(LocalDateTime.now());
                if (!oGoodsInventoryService.save(inv)) throw new IllegalStateException("保存入库库存失败");
                goodsInventoryId = inv.getId();
            } else {
                if (!oGoodsInventoryService.addStock(invList.get(0).getId(), item.getIntoQuantity())) throw new IllegalStateException("更新入库库存失败");
                goodsInventoryId = invList.get(0).getId();
            }

            // 增加商品库存批次表
            OGoodsInventoryBatch batch = new OGoodsInventoryBatch();
            batch.setInventoryId(goodsInventoryId);
            batch.setBatchNum(DateUtils.parseDateToStr("yyyyMMddHHmmss", LocalDateTime.now()));
            batch.setOriginQty(item.getIntoQuantity());
            batch.setCurrentQty(item.getIntoQuantity());
            batch.setPurPrice(stockInItem.getPurPrice() != null ? java.math.BigDecimal.valueOf(stockInItem.getPurPrice()) : null);
            batch.setMerchantId(stockInItem.getMerchantId());
            batch.setShopId(stockInItem.getShopId());
            batch.setPurId(stockInItem.getSourceId());
            batch.setPurItemId(stockInItem.getSourceItemId());
            batch.setInventoryMode(stockInItem.getInventoryMode());
            batch.setSkuId(Long.parseLong(stockInItem.getSkuId()));
            batch.setSkuCode(stockInItem.getSkuCode());
            batch.setGoodsId(Long.parseLong(stockInItem.getGoodsId()));
            batch.setWarehouseId(request.getWarehouseId());
            batch.setPositionId(item.getPositionId() == null ? 0L : item.getPositionId());
            batch.setPositionNum(item.getPositionNum() != null ? item.getPositionNum() : "");
            batch.setCreateTime(LocalDateTime.now());
            batch.setCreateBy(userName);
            if (!oGoodsInventoryBatchService.save(batch)) throw new IllegalStateException("保存入库批次失败");

            // 回写状态
            ErpStockInItem update = new ErpStockInItem();
            update.setId(stockInItem.getId());
            int inQuantity = (stockInItem.getInQuantity() == null ? 0 : stockInItem.getInQuantity()) + item.getIntoQuantity();
            update.setInQuantity(inQuantity);
            update.setStatus(inQuantity >= stockInItem.getQuantity() ? 2 : 1);
            update.setWarehouseId(request.getWarehouseId());
            update.setPositionId(item.getPositionId());
            update.setPositionNum(item.getPositionNum());
            update.setUpdateBy(userName);
            update.setUpdateTime(LocalDateTime.now());
            if (!inItemService.updateById(update)) throw new IllegalStateException("更新入库明细失败");
        }

        // 查询入库表单是否入库完成
        List<ErpStockInItem> itemList = inItemService.list(new LambdaQueryWrapper<ErpStockInItem>().eq(ErpStockInItem::getStockInId, erpStockIn.getId()).ne(ErpStockInItem::getStatus, 2));
        ErpStockIn sUpdate = new ErpStockIn();
        if (itemList.isEmpty()) {
            // 全部入库完成了
            sUpdate.setStatus(2);
        } else {
            // 部分入库
            sUpdate.setStatus(1);
        }
        sUpdate.setId(erpStockIn.getId());
        sUpdate.setStockInOperatorId(userId.toString());
        sUpdate.setStockInOperator(request.getStockInOperator());
        sUpdate.setStockInTime(LocalDateTime.now());
        sUpdate.setUpdateBy(userName);
        sUpdate.setUpdateTime(LocalDateTime.now());
        if (mapper.updateById(sUpdate) != 1) throw new IllegalStateException("更新入库单失败");

        return ResultVo.success();
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> StockInItemIn(Long userId, String userName, StockInItemInRequest request) {
        if (request == null) return ResultVo.error("入库请求不能为空");
        StockInItem item = new StockInItem(); item.setId(request.getStockInItemId());
        item.setIntoQuantity(request.getIntoQuantity()); item.setPositionId(request.getPositionId());
        var converted = new StockInRequest(); converted.setStockInId(request.getStockInId());
        converted.setWarehouseId(request.getWarehouseId()); converted.setItemList(List.of(item));
        return stockIn(userId, userName, converted);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> localStockIn(StockInLocalRequest request, Long userId, String userName) {
        if (request == null) return ResultVo.error("请选择入库单");
        if(request.getId()==null) return ResultVo.error("入库单id不能为空");
        if(request.getItems()==null||request.getItems().size()==0) return ResultVo.error("入库商品不能为空");

        ErpStockIn erpStockIn = mapper.selectOne(new LambdaQueryWrapper<ErpStockIn>()
                .eq(ErpStockIn::getId, request.getId()).last("FOR UPDATE"));
        if (erpStockIn == null) return ResultVo.error(ResultVoEnum.NotFound, "没有找到入库单");
        else if (erpStockIn.getStatus() == 2) {
            return ResultVo.error(ResultVoEnum.SystemException, "入库单状态不能操作");
        }

        // 先锁定并验证全部明细，不能在前一行已加库存后才发现下一行参数错误。
        if (request.getItems().stream().anyMatch(i -> i == null || i.getItemId() == null)) return ResultVo.error("请选择入库明细");
        var ids = request.getItems().stream().map(StockInLocalRequest.Item::getItemId).distinct().sorted().toList();
        if (ids.size() != request.getItems().size()) return ResultVo.error("入库明细不能重复");
        var lockedItems = inItemService.list(new LambdaQueryWrapper<ErpStockInItem>().in(ErpStockInItem::getId, ids)
                .orderByAsc(ErpStockInItem::getId).last("FOR UPDATE"));
        if (lockedItems.size() != ids.size()) return ResultVo.error("入库明细不存在");
        for (var incoming : request.getItems()) {
            var detail = lockedItems.stream().filter(i -> i.getId().equals(incoming.getItemId())).findFirst().orElseThrow();
            if (!request.getId().equals(detail.getStockInId())) return ResultVo.error("明细不属于该入库单");
            if (!java.util.Objects.equals(erpStockIn.getMerchantId(), detail.getMerchantId()) || !java.util.Objects.equals(erpStockIn.getShopId(), detail.getShopId()) || (detail.getWarehouseId() != null && !java.util.Objects.equals(erpStockIn.getWarehouseId(), detail.getWarehouseId()))) return ResultVo.error("入库单和明细归属不一致");
            if (Integer.valueOf(2).equals(detail.getStatus()) || detail.getQuantity() == null) return ResultVo.error("入库明细已完成或数量异常");
            int count;
            if (Integer.valueOf(0).equals(detail.getInventoryMode())) count = incoming.getQuantity() == null ? 0 : incoming.getQuantity();
            else if (Integer.valueOf(1).equals(detail.getInventoryMode())) {
                if (incoming.getBatchList() == null) return ResultVo.error("请录入单件条码");
                var codes = new java.util.HashSet<String>();
                for (var batch : incoming.getBatchList()) if (batch == null || !org.springframework.util.StringUtils.hasText(batch.getBarcode()) || !codes.add(batch.getBarcode())) return ResultVo.error("单件条码不能为空或重复");
                count = incoming.getBatchList().size();
            } else return ResultVo.error("库存模式无效");
            if (count <= 0 || count > detail.getQuantity() - (detail.getInQuantity() == null ? 0 : detail.getInQuantity())) return ResultVo.error("入库数量不能超过剩余待入库数量，且必须大于0");
        }
        for (String skuId : lockedItems.stream().map(ErpStockInItem::getSkuId).distinct().sorted().toList()) {
            if (skuService.getOne(new LambdaQueryWrapper<OGoodsSku>().eq(OGoodsSku::getId, skuId).last("FOR UPDATE")) == null) return ResultVo.error("入库商品SKU不存在");
        }

        for (StockInLocalRequest.Item item : request.getItems()) {
            // 查询明细
            ErpStockInItem stockInItem = inItemService.getById(item.getItemId());
            if (stockInItem == null) {
                return ResultVo.error(ResultVoEnum.DataError, "数据错误！没有找到入库单明细");
            }else if (stockInItem.getStatus() == 2) {
                return ResultVo.error("该商品已全部入库");
            }
            if(stockInItem.getInventoryMode()==0 && item.getQuantity()<=0) return ResultVo.error("没有入库数量");
            else if(stockInItem.getInventoryMode()==1){
                // 一物一码
                if(item.getBatchList()==null||item.getBatchList().isEmpty()) return ResultVo.error("没有录入批次信息");
            }

            // 开始入库
            OGoodsSku oGoodsSku = skuService.getById(stockInItem.getSkuId());
            if (oGoodsSku == null) return ResultVo.error("数据错误！没有找到入库商品SKU信息");
            
            // 计算入库数量
            Integer intoQty = null;
            if(stockInItem.getInventoryMode()==0) intoQty = item.getQuantity();
            else if(stockInItem.getInventoryMode()==1) intoQty = item.getBatchList().size();
            
            // 使用主系统库存表 OGoodsInventory
            Long goodsInventoryId = null;
            List<OGoodsInventory> invList = oGoodsInventoryService.list(
                    inventoryScope(erpStockIn.getWarehouseId(), stockInItem));
            if (invList.size() > 1) throw new IllegalStateException("存在多条可用库存，请先核对库存归属");
            if (invList.isEmpty()) {
                OGoodsInventory inv = new OGoodsInventory();
                inv.setWarehouseId(erpStockIn.getWarehouseId());
                inv.setMerchantId(stockInItem.getMerchantId());
                inv.setShopId(stockInItem.getShopId());
                inv.setGoodsId(Long.parseLong(stockInItem.getGoodsId()));
                inv.setSkuId(Long.parseLong(stockInItem.getSkuId()));
                inv.setSkuCode(stockInItem.getSkuCode());
                inv.setGoodsNum(oGoodsSku.getGoodsNum());
                inv.setGoodsName(oGoodsSku.getGoodsName());
                inv.setSkuName(oGoodsSku.getSkuName());
                inv.setGoodsImg(oGoodsSku.getColorImage());
                inv.setQuantity(intoQty);
                inv.setLockedQuantity(0);
                inv.setAvailableQuantity(intoQty);
                inv.setStockStatus(1);
                inv.setIsDelete(0);
                inv.setCreateBy(userName);
                inv.setCreateTime(LocalDateTime.now());
                if (!oGoodsInventoryService.save(inv)) throw new IllegalStateException("保存入库库存失败");
                goodsInventoryId = inv.getId();
            } else {
                if (!oGoodsInventoryService.addStock(invList.get(0).getId(), intoQty)) throw new IllegalStateException("更新入库库存失败");
                goodsInventoryId = invList.get(0).getId();
            }

            // 增加商品库存批次表 OGoodsInventoryBatch
            if(stockInItem.getInventoryMode()==0) {
                // 普通模式
                OGoodsInventoryBatch inventoryBatch = new OGoodsInventoryBatch();
                inventoryBatch.setInventoryId(goodsInventoryId);
                inventoryBatch.setBatchNum(DateUtils.parseDateToStr("yyyyMMddHHmmss", LocalDateTime.now()));
                inventoryBatch.setOriginQty(intoQty);
                inventoryBatch.setCurrentQty(intoQty);
                inventoryBatch.setPurPrice(stockInItem.getPurPrice() != null ? java.math.BigDecimal.valueOf(stockInItem.getPurPrice()) : null);
                inventoryBatch.setPurId(stockInItem.getSourceId());
                inventoryBatch.setPurItemId(stockInItem.getSourceItemId());
                inventoryBatch.setMerchantId(stockInItem.getMerchantId());
                inventoryBatch.setShopId(stockInItem.getShopId());
                inventoryBatch.setInventoryMode(stockInItem.getInventoryMode());
                inventoryBatch.setSkuId(Long.parseLong(stockInItem.getSkuId()));
                inventoryBatch.setSkuCode(stockInItem.getSkuCode());
                inventoryBatch.setGoodsId(Long.parseLong(stockInItem.getGoodsId()));
                inventoryBatch.setWarehouseId(erpStockIn.getWarehouseId());
                inventoryBatch.setPositionId(0L);
                inventoryBatch.setPositionNum("");
                inventoryBatch.setCreateTime(LocalDateTime.now());
                inventoryBatch.setCreateBy(userName);
                if (!oGoodsInventoryBatchService.save(inventoryBatch)) throw new IllegalStateException("保存入库批次失败");
            } else if(stockInItem.getInventoryMode()==1) {
                // 一物一码
                for (var batch : item.getBatchList()) {
                    OGoodsInventoryBatch inventoryBatch = new OGoodsInventoryBatch();
                    inventoryBatch.setInventoryId(goodsInventoryId);
                    inventoryBatch.setBatchNum(batch.getBarcode());
                    inventoryBatch.setBarcode(batch.getBarcode());
                    inventoryBatch.setOriginQty(1);
                    inventoryBatch.setCurrentQty(1);
                    inventoryBatch.setPurPrice(stockInItem.getPurPrice() != null ? java.math.BigDecimal.valueOf(stockInItem.getPurPrice()) : null);
                    inventoryBatch.setPurId(stockInItem.getSourceId());
                    inventoryBatch.setPurItemId(stockInItem.getSourceItemId());
                    inventoryBatch.setMerchantId(stockInItem.getMerchantId());
                    inventoryBatch.setShopId(stockInItem.getShopId());
                    inventoryBatch.setInventoryMode(stockInItem.getInventoryMode());
                    inventoryBatch.setActualGoldWeight(batch.getGoldWeight() != null ? java.math.BigDecimal.valueOf(batch.getGoldWeight()) : null);
                    inventoryBatch.setActualSilverWeight(batch.getSilverWeight() != null ? java.math.BigDecimal.valueOf(batch.getSilverWeight()) : null);
                    inventoryBatch.setLaborCost(batch.getLaborCost() != null ? java.math.BigDecimal.valueOf(batch.getLaborCost()) : null);
                    inventoryBatch.setCertificateNo(batch.getCertificateNo());
                    inventoryBatch.setSkuId(Long.parseLong(stockInItem.getSkuId()));
                    inventoryBatch.setSkuCode(stockInItem.getSkuCode());
                    inventoryBatch.setGoodsId(Long.parseLong(stockInItem.getGoodsId()));
                    inventoryBatch.setWarehouseId(erpStockIn.getWarehouseId());
                    inventoryBatch.setPositionId(0L);
                    inventoryBatch.setPositionNum("");
                    inventoryBatch.setCreateTime(LocalDateTime.now());
                    inventoryBatch.setCreateBy(userName);
                    if (!oGoodsInventoryBatchService.save(inventoryBatch)) throw new IllegalStateException("保存入库批次失败");
                }
            }

            // 回写状态
            ErpStockInItem update = new ErpStockInItem();
            update.setId(stockInItem.getId());
            Integer inQuantity = (stockInItem.getInQuantity() == null ? 0 : stockInItem.getInQuantity()) + intoQty;
            update.setInQuantity(inQuantity);
            update.setStatus(inQuantity.intValue() >= stockInItem.getQuantity().intValue() ? 2 : 1); // 状态（0待入库1部分入库2已入库）
            update.setWarehouseId(erpStockIn.getWarehouseId());
            update.setUpdateBy(userName);
            update.setUpdateTime(LocalDateTime.now());
            if (!inItemService.updateById(update)) throw new IllegalStateException("更新入库明细失败");
        }
        // 查询入库表单是否入库完成
        List<ErpStockInItem> itemList = inItemService.list(new LambdaQueryWrapper<ErpStockInItem>().eq(ErpStockInItem::getStockInId, erpStockIn.getId()).ne(ErpStockInItem::getStatus, 2));
        ErpStockIn sUpdate = new ErpStockIn();
        if (itemList.isEmpty()) {
            // 全部入库完成了
            sUpdate.setStatus(2);
        } else {
            // 部分入库
            sUpdate.setStatus(1);
        }
        sUpdate.setId(erpStockIn.getId());
        sUpdate.setStockInOperatorId(userId.toString());
        sUpdate.setStockInTime(LocalDateTime.now());
        sUpdate.setUpdateBy(userName);
        sUpdate.setUpdateTime(LocalDateTime.now());
        if (mapper.updateById(sUpdate) != 1) throw new IllegalStateException("更新入库单失败");

        return ResultVo.success();
    }

    private static void requireMatchingEntry(ErpStockIn entry, ErpStockInItem item) {
        if (!java.util.Objects.equals(entry.getId(), item.getStockInId())
                || !java.util.Objects.equals(entry.getMerchantId(), item.getMerchantId())
                || !java.util.Objects.equals(entry.getShopId(), item.getShopId())
                || (item.getWarehouseId() != null && !java.util.Objects.equals(entry.getWarehouseId(), item.getWarehouseId())))
            throw new IllegalArgumentException("入库单和明细归属不一致");
    }

    private static LambdaQueryWrapper<OGoodsInventory> inventoryScope(Long warehouseId, ErpStockInItem item) {
        if (warehouseId == null || item.getMerchantId() == null || item.getShopId() == null)
            throw new IllegalArgumentException("库存主体或仓库缺失");
        return new LambdaQueryWrapper<OGoodsInventory>()
                .eq(OGoodsInventory::getWarehouseId, warehouseId)
                .eq(OGoodsInventory::getSkuId, Long.parseLong(item.getSkuId()))
                .eq(OGoodsInventory::getMerchantId, item.getMerchantId())
                .eq(OGoodsInventory::getStockStatus, 1)
                .in(OGoodsInventory::getShopId, 0L, item.getShopId())
                .and(q -> q.eq(OGoodsInventory::getIsDelete, 0).or().isNull(OGoodsInventory::getIsDelete));
    }

    @Override
    public ErpStockIn getDetailAndItemById(Long id) {
        ErpStockIn erpStockIn = mapper.selectById(id);
        if(erpStockIn !=null){
            erpStockIn.setItemList(inItemService.list(new LambdaQueryWrapper<ErpStockInItem>().eq(ErpStockInItem::getStockInId,id).eq(ErpStockInItem::getMerchantId,erpStockIn.getMerchantId()).eq(ErpStockInItem::getShopId,erpStockIn.getShopId())));
            return erpStockIn;
        }else
            return null;
    }

}



