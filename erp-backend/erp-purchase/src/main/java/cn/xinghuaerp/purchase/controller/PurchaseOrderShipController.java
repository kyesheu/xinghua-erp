package cn.xinghuaerp.purchase.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderShip;
import cn.xinghuaerp.purchase.request.ConfirmReceiptRequest;
import cn.xinghuaerp.purchase.request.PurchaseOrderStockInBo;
import cn.xinghuaerp.purchase.request.SearchRequest;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.system.security.common.SecurityUtils;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderShipService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
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
public class PurchaseOrderShipController extends BaseController
{
    private final ErpPurchaseOrderShipService shipService;
    private final PurchaseAccessService access;
    /**
     *
     */
    @GetMapping("/shipList")
    public TableDataInfo shipList(SearchRequest bo, PageQuery pageQuery)
    {
        access.requireHeadquarters(getUserId(), "purchase:order:query");
        return getDataTable(shipService.queryPageList(bo, pageQuery));
    }
    @GetMapping(value = "/shipDetail/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        access.requireHeadquarters(getUserId(), "purchase:order:query");
        ErpPurchaseOrderShip detail = shipService.getById(id);
        if (detail == null) return AjaxResult.error("物流记录不存在");
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", detail.getId());
        map.put("orderId", detail.getOrderId());
        map.put("supplierId", detail.getSupplierId());
        map.put("shipCompany", detail.getShipCompany());
        map.put("shipNum", detail.getShipNum());
        map.put("freight", detail.getFreight());
        map.put("shipTime", detail.getShipTime() != null ? detail.getShipTime().toString() : null);
        map.put("receiptTime", detail.getReceiptTime() != null ? detail.getReceiptTime().toString() : null);
        map.put("createTime", detail.getCreateTime() != null ? detail.getCreateTime().toString() : null);
        map.put("status", detail.getStatus());
        map.put("remark", detail.getRemark());
        map.put("backCount", detail.getBackCount());
        map.put("stockInTime", detail.getStockInTime() != null ? detail.getStockInTime().toString() : null);
        map.put("stockInCount", detail.getStockInCount());
        map.put("warehouseId", detail.getWarehouseId());
        map.put("warehouseName", detail.getWarehouseName());
        map.put("warehouseType", detail.getWarehouseType());
        map.put("orderNum", detail.getOrderNum());
        map.put("orderDate", detail.getOrderDate());
        map.put("orderSpecUnit", detail.getOrderSpecUnit());
        map.put("orderGoodsUnit", detail.getOrderGoodsUnit());
        map.put("orderSpecUnitTotal", detail.getOrderSpecUnitTotal());
        return AjaxResult.success(map);
    }
    @PutMapping("/ship/confirmReceipt")
    public AjaxResult confirmReceipt(@RequestBody ConfirmReceiptRequest req, HttpServletRequest request)
    {
        access.requireHeadquarters(getUserId(), "purchase:order:operate");
        ErpPurchaseOrderShip ship = new ErpPurchaseOrderShip();
        ship.setId(req.getId());
        ship.setOrderId(req.getOrderId());
        ship.setRemark(req.getRemark());
        ship.setUpdateBy(getUsername());
        if (req.getReceiptTime() != null && !req.getReceiptTime().isEmpty()) {
            ship.setReceiptTime(java.time.LocalDateTime.of(
                java.time.LocalDate.parse(req.getReceiptTime(), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                java.time.LocalTime.MIN));
        } else {
            ship.setReceiptTime(java.time.LocalDateTime.now());
        }
        return toAjax(shipService.updateScmPurchaseOrderShip(ship));
    }

    @PostMapping("/ship/createStockInEntry")
    public AjaxResult createStockInEntry(@RequestBody PurchaseOrderStockInBo bo)
    {
        access.requireHeadquarters(getUserId(), "purchase:order:operate");
        if(bo.getId() == null) return AjaxResult.error("缺少参数id");
        if(bo.getWarehouseId()==null) return AjaxResult.error("请选择仓库");

        bo.setCreateBy(getUsername());
        ResultVo<Long> result = shipService.createStockInEntry(bo, SecurityUtils.getUserId(),SecurityUtils.getUsername());
        if(result.getCode()==0)
            return AjaxResult.success();
        else
            return AjaxResult.error(result.getMsg());
    }

}
