package cn.xinghuaerp.inventory.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.inventory.service.WarehouseAccessService;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.inventory.entity.ErpStockIn;
import cn.xinghuaerp.inventory.request.StockInCreateRequest;
import cn.xinghuaerp.inventory.request.StockInLocalRequest;
import cn.xinghuaerp.inventory.request.StockInRequest;

import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.inventory.service.ErpStockInService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/stockIn")
public class StockInController extends BaseController {
    private final ErpStockInService stockInService;
    private final WarehouseAccessService access;
    @GetMapping("/list")
    public TableDataInfo list(ErpStockIn bo, PageQuery pageQuery)
    {
        WarehouseAccessService.apply(access.scope(getUserId(), "wms:stockin:query"), bo);
        var pageList = stockInService.queryPageList(bo,pageQuery);
        return getDataTable(pageList);
    }

    @PostMapping("/create")
    public AjaxResult createEntry(@RequestBody StockInCreateRequest request)
    {
        WarehouseAccessService.headquartersOnly(access.scope(getUserId(), "wms:stockin:operate"));
        ResultVo<Long> resultVo = stockInService.createEntry(getUserId(), getUsername(), request);
        if(resultVo.getCode()==0)
            return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }

    @PostMapping("/in")
    public AjaxResult in(@RequestBody StockInRequest request)
    {
        var scope = access.scope(getUserId(), "wms:stockin:operate");
        var entry = request.getStockInId() == null ? null : stockInService.getById(request.getStockInId());
        WarehouseAccessService.check(scope, entry);
        if (!java.util.Objects.equals(request.getWarehouseId(), entry.getWarehouseId())) return AjaxResult.error("入库仓库与单据不一致");
        ResultVo<Long> resultVo = stockInService.stockIn(getUserId(), getUsername(), request);
        if(resultVo.getCode()==0)
            return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }

    @PostMapping("/in_local")
    public AjaxResult inLocal(@RequestBody StockInLocalRequest request)
    {
        var scope = access.scope(getUserId(), "wms:stockin:operate");
        WarehouseAccessService.check(scope, request.getId() == null ? null : stockInService.getById(request.getId()));
        ResultVo<Long> resultVo = stockInService.localStockIn(request, getUserId(), getUsername());
        if(resultVo.getCode()==0)
            return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }

    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        var scope = access.scope(getUserId(), "wms:stockin:query");
        WarehouseAccessService.check(scope, stockInService.getById(id));
        return success(stockInService.getDetailAndItemById(id));
    }

}
