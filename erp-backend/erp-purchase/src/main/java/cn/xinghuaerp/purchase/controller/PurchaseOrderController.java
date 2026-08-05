package cn.xinghuaerp.purchase.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrder;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderItem;
import cn.xinghuaerp.purchase.request.PurchaseOrderAddRequest;
import cn.xinghuaerp.purchase.request.PurchaseOrderOptionRequest;
import cn.xinghuaerp.purchase.request.SearchRequest;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderItemService;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 商品管理Controller
 * 
 * @author xinghua
 * @date 2023-12-29
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/erp/purchase")
public class PurchaseOrderController extends BaseController
{
    private final ErpPurchaseOrderService erpPurchaseOrderService;
    private final ErpPurchaseOrderItemService erpPurchaseOrderItemService;
    private final PurchaseAccessService access;

    /**
     * 采购商品明细分页查询
     */
    @GetMapping("/item_list")
    public TableDataInfo itemList(ErpPurchaseOrderItem query, PageQuery pageQuery)
    {
        access.requireHeadquarters(getUserId(), "purchase:order:query");
        LambdaQueryWrapper<ErpPurchaseOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(query.getOrderNum()), ErpPurchaseOrderItem::getOrderNum, query.getOrderNum())
               .like(StringUtils.hasText(query.getGoodsName()), ErpPurchaseOrderItem::getGoodsName, query.getGoodsName())
               .like(StringUtils.hasText(query.getSpecNum()), ErpPurchaseOrderItem::getSpecNum, query.getSpecNum())
               .eq(query.getSpecId() != null, ErpPurchaseOrderItem::getSpecId, query.getSpecId())
               .orderByDesc(ErpPurchaseOrderItem::getId);
        Page<ErpPurchaseOrderItem> page = erpPurchaseOrderItemService.page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()), wrapper);
        return getDataTable(PageResult.build(page));
    }
    /**
     *
     */
    @GetMapping("/list")
    public TableDataInfo list(SearchRequest bo, PageQuery pageQuery)
    {
        access.requireHeadquarters(getUserId(), "purchase:order:query");
        PageResult<ErpPurchaseOrder> pageResult = erpPurchaseOrderService.queryPageList(bo, pageQuery);
        return getDataTable(pageResult);
    }

    @GetMapping(value = "/detail/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        access.requireHeadquarters(getUserId(), "purchase:order:query");
        ErpPurchaseOrder detail = erpPurchaseOrderService.getDetailById(id);
        return AjaxResult.success(detail);
    }

    @PostMapping("/create")
    public AjaxResult add(@RequestBody PurchaseOrderAddRequest addBo, HttpServletRequest request)
    {
        access.requireHeadquarters(getUserId(), "purchase:order:create");
        addBo.setMerchantId(0L);
        addBo.setShopId(0L);
        addBo.setCreateBy(getUsername());
        var result = erpPurchaseOrderService.createPurchaseOrder(addBo);
        if (result.getCode() == 0) return AjaxResult.success();
        else return AjaxResult.error(result.getMsg());
    }
    @PutMapping("/updateStatus")
    public AjaxResult updateStatus(@RequestBody PurchaseOrderOptionRequest req, HttpServletRequest request)
    {
        access.requireHeadquarters(getUserId(), "audit".equals(req.getOptionType()) ? "purchase:order:audit" : "purchase:order:operate");
        req.setUpdateBy(getUsername());
        int result = erpPurchaseOrderService.updateScmPurchaseOrder(req);
        if(result == -1){
            return AjaxResult.error("操作无效或状态不正确");
        }else{
            return toAjax(result);
        }

    }
}
