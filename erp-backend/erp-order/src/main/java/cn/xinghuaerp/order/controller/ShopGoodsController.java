package cn.xinghuaerp.order.controller;

import cn.xinghuaerp.product.request.GoodsPullRequest;
import cn.xinghuaerp.product.request.ShopGoodsAddRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuAddRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuInsertRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuUpdateRequest;
import cn.xinghuaerp.common.*;
import cn.xinghuaerp.order.service.ShopPullApiService;
import cn.xinghuaerp.enums.EnumShopType;
import cn.xinghuaerp.product.bo.LinkErpGoodsSkuBo;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.product.entity.ShopGoods;
import cn.xinghuaerp.product.entity.ShopGoodsSku;
import cn.xinghuaerp.product.query.ShopGoodsSkuBo;
import cn.xinghuaerp.system.security.common.BaseController;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import cn.xinghuaerp.product.facade.OGoodsSkuFacade;
import cn.xinghuaerp.product.facade.ShopGoodsFacade;
import cn.xinghuaerp.product.facade.ShopGoodsSkuFacade;
import cn.xinghuaerp.system.facade.OShopFacade;
import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.TableDataInfo;
@Slf4j
@RequestMapping("/api/oms-api/shop/goods")
@RestController
@AllArgsConstructor
public class ShopGoodsController extends BaseController {
    private final OShopFacade oShopService;
    private final ShopGoodsFacade goodsService;
    private final ShopGoodsSkuFacade skuService;
    private final OGoodsSkuFacade oGoodsSkuService;
    private final OShopFacade shopService;
    private final ShopPullApiService shopPullApiService;

    /**
     * 店铺商品list
     * @param bo
     * @param pageQuery
     * @return
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public TableDataInfo goodsList(ShopGoods bo, PageQuery pageQuery) {
        PageResult<ShopGoods> result = goodsService.queryPageList(bo, pageQuery);
        return getDataTable(result);
    }

    /**
     * 添加店铺商品
     * @param request
     * @return
     */
    @PutMapping("/update")
    public AjaxResult updateGoods(@RequestBody ShopGoods request) {
        if(request.getId()==null) return AjaxResult.error("缺少参数：Id");
        if(StringUtils.isEmpty(request.getTitle())) return AjaxResult.error("缺少参数：title");
        if(StringUtils.isEmpty(request.getProductId())) return AjaxResult.error("缺少参数：平台商品ID");
        request.setUpdateOn(LocalDateTime.now());
        Boolean result = goodsService.updateById(request);
        if(!result) return AjaxResult.error("系统异常");
        else return AjaxResult.success();

    }

    @PostMapping("/add")
    public AjaxResult addGoods(@RequestBody ShopGoodsAddRequest request) {
        if(request.getShopId()==null) return AjaxResult.error("缺少参数：shopId");
        if(StringUtils.isEmpty(request.getGoodsName())) return AjaxResult.error("缺少参数：goodsName");
//        if(StringUtils.isEmpty(request.getGoodsNum())) return AjaxResult.error("缺少参数：goodsNum");
        if(request.getSkuList()==null||request.getSkuList().isEmpty()) return AjaxResult.error("缺少参数：skuList");

        OShop shop = oShopService.getById(request.getShopId());
        if(shop==null) return AjaxResult.error("店铺ID错误：找不到店铺数据"+request.getShopId());

        ResultVo<Long> result = goodsService.addGoods(request,shop);
        if(result.getCode() != 0) return AjaxResult.error(result.getMsg());
        else return AjaxResult.success();

    }

    /**
     * 店铺商品sku list
     * @param bo
     * @param pageQuery
     * @return
     */
    @RequestMapping(value = "/skuList", method = RequestMethod.GET)
    public TableDataInfo skuList(ShopGoodsSkuBo bo, PageQuery pageQuery) {
        PageResult<ShopGoodsSku> result = skuService.queryPageList(bo, pageQuery);
        if(result.getTotal()==0){
            if(bo.getShopId()!=null){
                OShop oShop = shopService.selectShopById(bo.getShopId());
                if(oShop.getType().intValue() == EnumShopType.OFFLINE.getIndex()) {
                    result = skuService.queryBenShuPageList(bo, pageQuery);
                }
            }

        }

        return getDataTable(result);
    }

    /**
     * 获取店铺订单详细信息
     */
    @GetMapping(value = "/sku/{id}")
    public AjaxResult getSkuInfo(@PathVariable("id") Long id)
    {
        return AjaxResult.success(skuService.getById(id));
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/del/{id}")
    public AjaxResult removeGoods(@PathVariable Long id) {
        ShopGoods shopGoods = goodsService.getById(id);
        if(shopGoods==null) return AjaxResult.error("没有找到店铺商品数据");

        List<ShopGoodsSku> shopGoodsSkuList = skuService.querySkuList(shopGoods.getId());
        for(ShopGoodsSku shopGoodsSku : shopGoodsSkuList){
            skuService.removeById(shopGoodsSku.getId());
        }
//        if(shopGoodsSkuList!=null&&shopGoodsSkuList.size()>0) return AjaxResult.error("店铺商品存在SKU，不允许删除！");
        boolean result = goodsService.removeById(id);
        if(result) return AjaxResult.success();
        else return AjaxResult.error("系统异常");
    }

    /**
     * 添加店铺商品sku
     * @param request
     * @return
     */
    @PostMapping("/sku/insert")
    public AjaxResult skuInsert(@RequestBody ShopGoodsSkuInsertRequest request) {
        if(request.getShopGoodsId()==null) return AjaxResult.error("缺少参数：shopGoodsId");
        if(request.getErpGoodsSkuId()==null||request.getErpGoodsSkuId()<=0) return AjaxResult.error("请选择商品库商品");
        if(StringUtils.isEmpty(request.getSkuId())) return AjaxResult.error("缺少参数：skuId");

        ResultVo<Long> result = goodsService.insertGoodsSku(request);
        if(result.getCode() != 0) return AjaxResult.error(result.getMsg());
        else return AjaxResult.success();

    }
    @PostMapping("/sku/add")
    public AjaxResult skuAdd(@RequestBody ShopGoodsSkuAddRequest request) {
        if(request.getShopId()==null) return AjaxResult.error("缺少参数：shopId");
        if(request.getGoodsSkuId()==null||request.getGoodsSkuId()<=0) return AjaxResult.error("请选择商品库商品");

        OShop shop = oShopService.getById(request.getShopId());
        if(shop==null) return AjaxResult.error("店铺ID错误：找不到店铺数据"+request.getShopId());

        ResultVo<Long> result = goodsService.addGoodsSku(request);
        if(result.getCode() != 0) return AjaxResult.error(result.getMsg());
        else return AjaxResult.success();

    }

    /**
     * 店铺商品SKU修改
     * @param request
     * @return
     */
    @PostMapping("/sku/update")
    public AjaxResult skuUpdate(@RequestBody ShopGoodsSkuUpdateRequest request) {
        if(request.getId()==null||request.getId()<=0) return AjaxResult.error("缺少参数：shopId");
        if(request.getErpGoodsSkuId()==null||request.getErpGoodsSkuId()<=0) return AjaxResult.error("请选择商品库商品");
        if(StringUtils.isEmpty(request.getSkuId())) return AjaxResult.error("缺少参数：skuId");

        ResultVo<Long> result = goodsService.updateGoodsSku(request);
        if(result.getCode() != 0) return AjaxResult.error(result.getMsg());
        else return AjaxResult.success();
    }

    @PostMapping(value = "/sku/linkErp")
    public AjaxResult linkErp(@RequestBody LinkErpGoodsSkuBo bo) {
        if(StringUtils.isBlank(bo.getId())){
            return AjaxResult.error(500,"缺少参数Id");
        }
        if(bo.getErpGoodsSkuId()==null){
            return AjaxResult.error(500,"缺少参数erpGoodsSkuId");
        }

        ResultVo resultVo = skuService.linkErpGoodsSku(bo);
        if(resultVo.getCode()==0)
            return success();
        else return AjaxResult.error(resultVo.getMsg());

    }
    /**
     * 修改商品sku
     * @param sku
     * @return
     */
    @PutMapping("/sku/edit")
    public AjaxResult editSku(@RequestBody OGoodsSku sku) {

//        ResultVo resultVo = skuService.updateSku(sku);
//        if(resultVo.getCode()==0) return AjaxResult.success();
//        else return AjaxResult.error(resultVo.getMsg());
        return AjaxResult.success();
    }

    /**
     * 删除商品sku
     */
    @DeleteMapping("/sku/del/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        boolean result = skuService.removeById(id);
        if(result) return AjaxResult.success();
        else return AjaxResult.error("系统异常");
    }

    /**
     * 拉取店铺商品列表（含SKU，根据 shopType 路由到对应平台 SDK）。
     * 前端：{@code /api/oms-api/shop/goods/pull_list}，参数 {shopId, pullType}
     */
    @PostMapping("/pull_list")
    public AjaxResult pullList(@RequestBody GoodsPullRequest params) {
        ResultVo<String> result = shopPullApiService.pullGoods(params.getShopId(), params.getPullType());
        if (result.getCode() == 0) {
            return AjaxResult.success(result.getMsg());
        }
        return AjaxResult.error(result.getCode(), result.getMsg());
    }
}
