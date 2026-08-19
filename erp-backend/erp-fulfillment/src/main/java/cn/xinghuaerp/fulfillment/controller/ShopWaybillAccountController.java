package cn.xinghuaerp.fulfillment.controller;

import cn.xinghuaerp.fulfillment.entity.DouLogisticsTemplate;
import cn.xinghuaerp.fulfillment.entity.PddLogisticsTemplate;
import cn.xinghuaerp.fulfillment.entity.PlatformLogisticsWaybillTemplate;
import cn.xinghuaerp.fulfillment.entity.ShopWaybillAccount;
import cn.xinghuaerp.fulfillment.entity.WeiLogisticsTemplate;
import cn.xinghuaerp.common.*;
import cn.xinghuaerp.enums.EnumShopType;
import cn.xinghuaerp.fulfillment.bo.WaybillAccountUpdateBo;
import cn.xinghuaerp.system.security.common.BaseController;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import cn.xinghuaerp.inventory.service.ErpWarehouseService;
import cn.xinghuaerp.system.service.OShopService;
import cn.xinghuaerp.fulfillment.service.DouLogisticsTemplateService;
import cn.xinghuaerp.system.service.ErpLogisticsCompanyService;
import cn.xinghuaerp.fulfillment.service.PddLogisticsTemplateService;
import cn.xinghuaerp.fulfillment.service.PlatformLogisticsWaybillTemplateService;
import cn.xinghuaerp.fulfillment.service.ShopWaybillAccountService;
import cn.xinghuaerp.fulfillment.service.ShopWaybillAccountShareService;
import cn.xinghuaerp.fulfillment.service.WeiLogisticsTemplateService;
import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.TableDataInfo;
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/oms-api/ewaybill")
public class ShopWaybillAccountController extends BaseController {

    private final ShopWaybillAccountService shopWaybillAccountService;
    private final WeiLogisticsTemplateService weiTemplateService;
    private final PlatformLogisticsWaybillTemplateService platformLogisticsWaybillTemplateService;
    private final PddLogisticsTemplateService pddTemplateService;
    private final DouLogisticsTemplateService douTemplateService;
    private final ShopWaybillAccountShareService shopWaybillAccountShareService;
    private final ErpWarehouseService erpWarehouseService;
    private final ErpLogisticsCompanyService logisticsCompanyService;
    private final OShopService shopService;
    /**
     * 获取电子面单账户list
     * @param
     * @return
     * @throws Exception
     */
    @GetMapping(value = "/get_waybill_account_list")
    public TableDataInfo getWaybillAccountList(ShopWaybillAccount bo, PageQuery pageQuery) throws Exception {
        PageResult<ShopWaybillAccount> pageResult = shopWaybillAccountService.queryPageList(bo, pageQuery);
        return getDataTable(pageResult);
    }


    /**
     * 修改电子面单账户
     * @param params
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/updateAccount", method = RequestMethod.POST)
    public AjaxResult updateAccount(@RequestBody WaybillAccountUpdateBo params) throws Exception {
        if (params.getId() == null || params.getId() <= 0) return AjaxResult.error("缺少参数:Id");
        if (!StringUtils.hasText(params.getDeliverName())) return AjaxResult.error("缺少参数:deliverName");
        if (!StringUtils.hasText(params.getDeliverMobile())&&!StringUtils.hasText(params.getDeliverPhone())) return AjaxResult.error("缺少参数:发货电话");

        if (!StringUtils.hasText(params.getBranchName())) return AjaxResult.error("缺少参数:branchName");
        if (!StringUtils.hasText(params.getBranchCode())) return AjaxResult.error("缺少参数:branchCode");
        if (params.getTemplateId()==null || params.getTemplateId()<=0) return AjaxResult.error("缺少参数:templateId");
        if (!StringUtils.hasText(params.getTemplateUrl())) return AjaxResult.error("缺少参数:templateUrl");

        ShopWaybillAccount branch = shopWaybillAccountService.getById(params.getId());
        if(branch==null){
            return AjaxResult.error("找不到电子面单账户信息");
        }
        ShopWaybillAccount account = new ShopWaybillAccount();
        account.setId(params.getId());
        account.setBranchName(params.getBranchName());
        account.setDeliverName(params.getDeliverName());
        account.setDeliverMobile(params.getDeliverMobile());
        account.setDeliverPhone(params.getDeliverPhone());
        account.setTemplateUrl(params.getTemplateUrl());
        account.setTemplateId(params.getTemplateId());
        shopWaybillAccountService.updateById(account);
        return AjaxResult.success();
    }

    /**
     * 获取快递模版
     * @param
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/get_waybill_template_list", method = RequestMethod.GET)
    public AjaxResult getWaybillAccountList(Integer shopType,String cpCode) throws Exception {
        log.info("======获取打印模板====={}:{}",shopType,cpCode);
        if(shopType==EnumShopType.PDD.getIndex()){
            List<PddLogisticsTemplate> pddLogisticsTemplates = pddTemplateService.queryListByWpCode(cpCode);
            return AjaxResult.success(pddLogisticsTemplates);
        }else if(shopType==EnumShopType.JD.getIndex()){
            List<PlatformLogisticsWaybillTemplate> list = platformLogisticsWaybillTemplateService.getLogisticsWaybillTemplate(shopType, cpCode);
            return AjaxResult.success(list);
        } else if (shopType==EnumShopType.DOU.getIndex()) {
            List<DouLogisticsTemplate> pddLogisticsTemplates = douTemplateService.getLogisticsTemplateByCode(cpCode);
            return AjaxResult.success(pddLogisticsTemplates);
        } else if (shopType==EnumShopType.XHS.getIndex()) {
            List<PlatformLogisticsWaybillTemplate> list = platformLogisticsWaybillTemplateService.getLogisticsWaybillTemplate(shopType, cpCode);
            return AjaxResult.success(list);
        }else if (shopType==EnumShopType.WEI.getIndex()) {
            List<WeiLogisticsTemplate> list = weiTemplateService.getByLogisticsCode(cpCode);
            if(!list.isEmpty()){
                for(WeiLogisticsTemplate weiLogisticsTemplate : list){
                    weiLogisticsTemplate.setTemplateId(weiLogisticsTemplate.getId());
                }
            }
            return AjaxResult.success(list);
        }
        return AjaxResult.error("还未支持");
    }



}
