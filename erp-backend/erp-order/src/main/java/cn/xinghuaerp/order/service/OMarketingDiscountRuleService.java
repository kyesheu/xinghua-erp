package cn.xinghuaerp.order.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.order.entity.OMarketingDiscountRule;
import cn.xinghuaerp.order.request.OMarketingDiscountRuleAdd;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 1
* @description 针对表【o_marketing_discount_rule(订单折扣规则表（营销模块-手动订单折扣）)】的数据库操作Service
* @createDate 2026-04-09 17:27:35
*/
public interface OMarketingDiscountRuleService extends IService<OMarketingDiscountRule> {
    PageResult<OMarketingDiscountRule> queryPageList(OMarketingDiscountRule bo, PageQuery pageQuery);
    PageResult<OMarketingDiscountRule> queryMerchantPageList(Long merchantId,OMarketingDiscountRule bo, PageQuery pageQuery);
    PageResult<OMarketingDiscountRule> queryShopPageList(Long merchantId,Long shopId,OMarketingDiscountRule bo, PageQuery pageQuery);
    ResultVo<Long> add(OMarketingDiscountRuleAdd bo);
}
