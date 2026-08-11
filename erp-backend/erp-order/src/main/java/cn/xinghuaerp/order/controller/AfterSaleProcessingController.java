package cn.xinghuaerp.order.controller;


import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.TableDataInfo;

import cn.xinghuaerp.order.entity.ORefundAfterSale;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.order.service.ORefundAfterSaleService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import cn.xinghuaerp.system.service.BusinessAccessService;

@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/afterSale")
public class AfterSaleProcessingController extends BaseController {
    private final ORefundAfterSaleService afterSaleService;
    private final cn.xinghuaerp.system.service.BusinessAccessService access;
    /**
     * 查询列表
     */
    @GetMapping("/list")
    public TableDataInfo ship_again_list(ORefundAfterSale bo, PageQuery pageQuery)
    {
        var scope = access.salesScope(getUserId());
        if (scope.merchantId() != null) bo.setMerchantId(scope.merchantId());
        if (scope.shopId() != null) bo.setShopId(scope.shopId());
        //private final OAfterSaleMapper mapper;
        //    @Override
        //    public PageResult<OAfterSale> queryPageList(OAfterSale bo, PageQuery pageQuery) {
        //        LambdaQueryWrapper<OAfterSale> queryWrapper = new LambdaQueryWrapper<OAfterSale>().
        //                eq( OAfterSale::getType, bo.getType())
        //                .eq(bo.getShopId() != null, OAfterSale::getShopId, bo.getShopId());
        //
        //        Page<OAfterSale> pages = mapper.selectPage(pageQuery.build(), queryWrapper);
        //        return PageResult.build(pages);
        //    }
//        bo.setType(80);
        PageResult<ORefundAfterSale> result = afterSaleService.queryPageList(bo, pageQuery);
        return getDataTable(result);
    }

//    @PostMapping("/shipAgain")
//    public AjaxResult shipAgainAdd(@RequestBody ErpAfterSale addBo)
//    {
//        addBo.setType(80);
//        addBo.setCreateTime(LocalDateTime.now());
//        addBo.setCreateBy("手动添加");
//        addBo.setStatus(1);
//        var result = afterSaleService.save(addBo);
//        return toAjax(result);
//    }
//
//    @PutMapping("/shipAgain/complete/{id}")
//    public AjaxResult completeShipAgain(@PathVariable Long id)
//    {
//        ErpAfterSale complete = new ErpAfterSale();
//        complete.setId(id.toString());
//        complete.setStatus(2);
//        complete.setUpdateTime(LocalDateTime.now());
//        complete.setUpdateBy("手动完成");
//        afterSaleService.updateById(complete);
//        return toAjax(1);
//    }
//
//
//    @GetMapping("/returned_list")
//    public TableDataInfo returned_list(ErpAfterSale bo, PageQuery pageQuery)
//    {
//        bo.setType(10);
//        PageResult<ErpAfterSale> result = afterSaleService.queryPageList(bo, pageQuery);
//        return getDataTable(result);
//    }
//
//    @GetMapping("/exchange_list")
//    public TableDataInfo exchange_list(ErpAfterSale bo, PageQuery pageQuery)
//    {
//        bo.setType(20);
//        PageResult<ErpAfterSale> result = afterSaleService.queryPageList(bo, pageQuery);
//        return getDataTable(result);
//    }
//
//    @PostMapping("/exchange")
//    public AjaxResult exchangeAdd(@RequestBody ErpAfterSale addBo)
//    {
//        addBo.setType(20);
//        addBo.setCreateTime(LocalDateTime.now());
//        addBo.setCreateBy("手动添加");
//        addBo.setStatus(1);
//        var result = afterSaleService.save(addBo);
//        return toAjax(result);
//    }
//
//    @GetMapping("/intercept_list")
//    public TableDataInfo intercept_list(ErpAfterSale bo, PageQuery pageQuery)
//    {
//        bo.setType(99);
//        PageResult<ErpAfterSale> result = afterSaleService.queryPageList(bo, pageQuery);
//        return getDataTable(result);
//    }


}
