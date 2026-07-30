package cn.xinghuaerp.inventory.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.inventory.service.WarehouseAccessService;
import cn.xinghuaerp.inventory.service.ErpStockOutItemService;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.inventory.entity.ErpStockOut;
import cn.xinghuaerp.inventory.request.StockOutCreateRequest;
import cn.xinghuaerp.inventory.request.StockOutItemRequest;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.inventory.service.ErpStockOutService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/stockOut")
public class StockOutController extends BaseController {
    private final ErpStockOutService stockOutService;
    private final ErpStockOutItemService stockOutItems;
    private final WarehouseAccessService access;

    @GetMapping("/list")
    public TableDataInfo list(ErpStockOut bo, PageQuery pageQuery)
    {
        WarehouseAccessService.apply(access.scope(getUserId(), "wms:stockout:query"), bo);
        var pageList = stockOutService.queryPageList(bo,pageQuery);
        return getDataTable(pageList);
    }



    @PostMapping("/create")
    public AjaxResult createEntry(@RequestBody StockOutCreateRequest request)
    {
        WarehouseAccessService.headquartersOnly(access.scope(getUserId(), "wms:stockout:operate"));
        ResultVo<Long> resultVo = stockOutService.createEntry(getUserId(), getUsername(), request);
        if(resultVo.getCode()==0)
            return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }

    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        var scope = access.scope(getUserId(), "wms:stockout:query");
        WarehouseAccessService.check(scope, stockOutService.getById(id));
        return success(stockOutService.getDetailAndItemById(id));
    }
    @GetMapping(value = "/print/{id}")
    public AjaxResult print(@PathVariable("id") Long id)
    {
        var scope = access.scope(getUserId(), "wms:stockout:operate");
        WarehouseAccessService.check(scope, stockOutService.getById(id));
        ErpStockOut out = new ErpStockOut();
        out.setId(id);
        out.setPrintStatus(1);
        out.setPrintTime(LocalDateTime.now());
        stockOutService.updateById(out);
        return AjaxResult.success();
    }
    @PostMapping("/out")
    public AjaxResult out(@RequestBody StockOutItemRequest request)
    {
        var scope = access.scope(getUserId(), "wms:stockout:operate");
        var item = request.getEntryItemId() == null ? null : stockOutItems.getById(request.getEntryItemId());
        WarehouseAccessService.check(scope, item == null || item.getEntryId() == null ? null : stockOutService.getById(item.getEntryId()));
        ResultVo<Long> resultVo = stockOutService.stockOut(getUserId(), getUsername(), request);
        if(resultVo.getCode()==0)
            return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }
}
