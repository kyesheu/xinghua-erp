package cn.xinghuaerp.order.service.impl;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.product.facade.ProductFacade;
import cn.xinghuaerp.system.facade.SystemFacade;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.order.bo.MerchantGoodsPriceAddRequest;
import cn.xinghuaerp.system.entity.ErpMerchant;
import cn.xinghuaerp.product.entity.OGoodsSku;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.xinghuaerp.order.entity.ErpMerchantGoodsPrice;
import cn.xinghuaerp.order.service.ErpMerchantGoodsPriceService;
import cn.xinghuaerp.order.mapper.ErpMerchantGoodsPriceMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
* @author 1
* @description 针对表【erp_merchant_goods_price(渠道商户-商品价格历史表（数据来源：总部定价）)】的数据库操作Service实现
* @createDate 2026-03-02 15:04:29
*/
@AllArgsConstructor
@Service
public class ErpMerchantGoodsPriceServiceImpl extends ServiceImpl<ErpMerchantGoodsPriceMapper, ErpMerchantGoodsPrice>
    implements ErpMerchantGoodsPriceService{
    private final ProductFacade goodsSkuMapper;
    private final SystemFacade merchantMapper;

    @Override
    public PageResult<ErpMerchantGoodsPrice> queryPageList(ErpMerchantGoodsPrice bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ErpMerchantGoodsPrice> queryWrapper = new LambdaQueryWrapper<ErpMerchantGoodsPrice>();
        queryWrapper.eq(bo.getMerchantId()!=null,ErpMerchantGoodsPrice::getMerchantId,bo.getMerchantId());
        queryWrapper.eq(bo.getStatus()!=null,ErpMerchantGoodsPrice::getStatus,bo.getStatus());
        queryWrapper.eq(bo.getGoodsId()!=null,ErpMerchantGoodsPrice::getGoodsId,bo.getGoodsId());
        queryWrapper.eq(bo.getGoodsSkuId()!=null,ErpMerchantGoodsPrice::getGoodsSkuId,bo.getGoodsSkuId());

        Page<ErpMerchantGoodsPrice> pages = this.baseMapper.selectPage(pageQuery.build(), queryWrapper);
        return PageResult.build(pages);
    }

    /**
     * 添加商户价格
     * @param request
     * @param username
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> addPrice(MerchantGoodsPriceAddRequest request, String username) {
        OGoodsSku oGoodsSku = goodsSkuMapper.getOGoodsSkuById(request.getGoodsSkuId());
        if (oGoodsSku == null) return ResultVo.error("商品不存在");
        ErpMerchant erpMerchant = merchantMapper.getErpMerchantById(request.getMerchantId());
        if (erpMerchant == null) return ResultVo.error("商户不存在");

        // 添加最新价格信息
        ErpMerchantGoodsPrice price = new ErpMerchantGoodsPrice();
        price.setGoodsId(Long.parseLong(oGoodsSku.getGoodsId()));
        price.setGoodsSkuId(Long.parseLong(oGoodsSku.getId()));
        price.setGoodsName(oGoodsSku.getGoodsName());
        price.setSkuName(oGoodsSku.getSkuName());
        price.setSkuCode(oGoodsSku.getSkuCode());
        price.setShopPlatformId(0);
        price.setMerchantId(erpMerchant.getId());
        price.setPurPrice(request.getPrice());
        price.setRetailPrice(request.getPrice());
        price.setStatus(1);
        price.setCreateTime(LocalDateTime.now());
        price.setCreateBy(username);
        this.baseMapper.insert(price);

        return ResultVo.success();
    }
}




