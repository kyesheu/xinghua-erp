package cn.xinghuaerp.order.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.order.bo.RefundProcessingBo;
import cn.xinghuaerp.order.bo.RefundSearchBo;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.order.service.ORefundService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import cn.xinghuaerp.system.service.BusinessAccessService;

/**
 * 退换货Controller
 * 
 * @author xinghua
 * @date 2024-01-13
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/refund")
public class RefundController extends BaseController
{

    private final ORefundService refundService;
    private final cn.xinghuaerp.system.service.BusinessAccessService access;


    /**
     * 查询退换货列表
     */
    @PreAuthorize("@ss.hasPermi('api:returned:list')")
    @GetMapping("/list")
    public TableDataInfo list(RefundSearchBo bo, PageQuery pageQuery)
    {
        var scope = access.salesScope(getUserId());
        if (scope.merchantId() != null) bo.setMerchantId(scope.merchantId());
        if (scope.shopId() != null) bo.setShopId(scope.shopId());
        var pageList = refundService.queryPageList(bo,pageQuery);
        return getDataTable(pageList);
    }


    /**
     * 获取退换货详细信息
     */
    @PreAuthorize("@ss.hasPermi('api:returned:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(refundService.selectById(id));
    }


    /**
     * 推送退款到ERP
     * @param ids
     * @return
     */
    @PostMapping("/pushErp/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
//        for (Long id:ids) {
//            ORefund ORefund = refundService.selectById(id);
//            if(ORefund!=null) {
//
//                ResultVo resultVo = erpPushHelper.pushRefundSingle(ORefund);
//                ORefund pushUpdate = new ORefund();
//                pushUpdate.setErpPushStatus(resultVo.getCode()== 0 ? 200:resultVo.getCode());
//                pushUpdate.setErpPushResult(resultVo.getMsg());
//                pushUpdate.setErpPushTime(LocalDateTime.now());
//                pushUpdate.setUpdateBy("手动推送到ERP");
//                pushUpdate.setUpdateTime(LocalDateTime.now());
//                pushUpdate.setId(id.toString());
//                refundService.updateById(pushUpdate);
//            }
//        }

        return success();
    }


    @PostMapping("/processing")
    public AjaxResult refundProcessing(@RequestBody RefundProcessingBo bo ) {
        if (bo.getRefundId() == null) return AjaxResult.error(500, "缺少参数refundId");
        ResultVo<Long> resultVo = refundService.refundProcessing(bo,getUsername());
        if(resultVo.getCode() == 0) return success();
        else return AjaxResult.error(resultVo.getMsg());
    }
}
