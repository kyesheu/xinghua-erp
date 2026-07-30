package cn.xinghuaerp.inventory.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;
import org.springframework.security.access.AccessDeniedException;
import java.util.Objects;
import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.entity.OGoodsInventoryBatch;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.inventory.service.OGoodsInventoryBatchService;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 主系统商品库存 Controller
 * 使用 o_goods_inventory / o_goods_inventory_batch 两张表
 * 区别于多仓库子系统的 ErpWarehouseGoodsStockController
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/goodsInventory")
public class GoodsInventoryController extends BaseController {

    private final OGoodsInventoryService goodsInventoryService;
    private final OGoodsInventoryBatchService inventoryBatchService;
    private final BusinessAccessService access;
    private final AiAgentPermissionService permissions;

    /**
     * 分页查询主系统库存列表
     */
    @GetMapping("/list")
    public TableDataInfo list(OGoodsInventory bo, PageQuery pageQuery) {
        var scope = inventoryScope();
        applyInventoryScope(bo, scope);
        PageResult<OGoodsInventory> pageResult = goodsInventoryService.queryPageList(bo, pageQuery);
        return getDataTable(pageResult);
    }

    /**
     * 查询库存批次明细
     *
     * @param id 库存记录ID (o_goods_inventory.id)
     */
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        var scope = inventoryScope();
        OGoodsInventory goodsInventory = goodsInventoryService.getById(id);
        if (goodsInventory != null) {
            checkInventory(scope, goodsInventory);
            var wrapper = new LambdaQueryWrapper<OGoodsInventoryBatch>().eq(OGoodsInventoryBatch::getInventoryId, id);
            applyBatchScope(wrapper, scope);
            List<OGoodsInventoryBatch> list = inventoryBatchService.list(wrapper);
            return AjaxResult.success(list);
        }
        return success();
    }

    /**
     * 根据SKU+仓库查询可用库存批次（出库选批次用）
     *
     * @param skuId       SKU ID
     * @param warehouseId 仓库ID
     */
    @GetMapping("/batches")
    public AjaxResult getBatches(@RequestParam Long skuId, @RequestParam Long warehouseId) {
        var wrapper = new LambdaQueryWrapper<OGoodsInventoryBatch>()
                .eq(OGoodsInventoryBatch::getSkuId, skuId)
                .eq(OGoodsInventoryBatch::getWarehouseId, warehouseId)
                .gt(OGoodsInventoryBatch::getCurrentQty, 0)
                .orderByAsc(OGoodsInventoryBatch::getCreateTime);
        applyBatchScope(wrapper, inventoryScope());
        List<OGoodsInventoryBatch> list = inventoryBatchService.list(wrapper);
        return AjaxResult.success(list);
    }

    /**
     * 扫码出库：根据条码/SKU编码查询可用批次
     *
     * @param keyword     条码或SKU编码
     * @param warehouseId 仓库ID（可选）
     */
    @GetMapping("/batches/search")
    public AjaxResult searchBatches(@RequestParam String keyword, @RequestParam(required = false) Long warehouseId) {
        var wrapper = new LambdaQueryWrapper<OGoodsInventoryBatch>()
                .and(w -> w.like(OGoodsInventoryBatch::getBarcode, keyword).or().like(OGoodsInventoryBatch::getSkuCode, keyword).or().like(OGoodsInventoryBatch::getBatchNum, keyword))
                .gt(OGoodsInventoryBatch::getCurrentQty, 0)
                .eq(warehouseId != null, OGoodsInventoryBatch::getWarehouseId, warehouseId)
                .orderByAsc(OGoodsInventoryBatch::getCreateTime);
        applyBatchScope(wrapper, inventoryScope());
        return AjaxResult.success(inventoryBatchService.list(wrapper));
    }
    private BusinessSubjectScopeService.Scope inventoryScope() {
        Long userId = getUserId();
        if (!permissions.allows(userId, "wms:inventory:query")) throw new AccessDeniedException("无库存查询权限");
        return access.scope(userId);
    }
    static void applyInventoryScope(OGoodsInventory query, BusinessSubjectScopeService.Scope scope) {
        validateScope(scope);
        switch (scope.type()) {
            case "HEADQUARTERS" -> { }
            case "MERCHANT" -> query.setMerchantId(scope.merchantId());
            case "SHOP" -> { query.setMerchantId(scope.merchantId()); query.setShopId(scope.shopId()); }
            case "WAREHOUSE" -> query.setWarehouseId(scope.subjectId());
            default -> throw new AccessDeniedException("无库存查询范围");
        }
    }
    static void checkInventory(BusinessSubjectScopeService.Scope scope, OGoodsInventory item) {
        validateScope(scope);
        boolean allowed = switch (scope.type()) {
            case "HEADQUARTERS" -> true;
            case "MERCHANT" -> Objects.equals(scope.merchantId(), item.getMerchantId());
            case "SHOP" -> Objects.equals(scope.merchantId(), item.getMerchantId()) && Objects.equals(scope.shopId(), item.getShopId());
            case "WAREHOUSE" -> Objects.equals(scope.subjectId(), item.getWarehouseId());
            default -> false;
        };
        if (!allowed) throw new AccessDeniedException("无库存查询范围");
    }
    static void applyBatchScope(LambdaQueryWrapper<OGoodsInventoryBatch> query, BusinessSubjectScopeService.Scope scope) {
        validateScope(scope);
        switch (scope.type()) {
            case "HEADQUARTERS" -> { }
            case "MERCHANT" -> query.eq(OGoodsInventoryBatch::getMerchantId, scope.merchantId());
            case "SHOP" -> query.eq(OGoodsInventoryBatch::getMerchantId, scope.merchantId()).eq(OGoodsInventoryBatch::getShopId, scope.shopId());
            case "WAREHOUSE" -> query.eq(OGoodsInventoryBatch::getWarehouseId, scope.subjectId());
            default -> throw new AccessDeniedException("无库存查询范围");
        }
    }
    private static void validateScope(BusinessSubjectScopeService.Scope scope) {
        if (scope == null || scope.type() == null) throw new AccessDeniedException("无库存查询范围");
        boolean valid = switch (scope.type()) {
            case "HEADQUARTERS" -> true;
            case "MERCHANT" -> scope.merchantId() != null && scope.merchantId() > 0;
            case "SHOP" -> scope.merchantId() != null && scope.merchantId() >= 0 && scope.shopId() != null && scope.shopId() > 0;
            case "WAREHOUSE" -> scope.subjectId() != null && scope.subjectId() > 0;
            default -> false;
        };
        if (!valid) throw new AccessDeniedException("无库存查询范围");
    }

}