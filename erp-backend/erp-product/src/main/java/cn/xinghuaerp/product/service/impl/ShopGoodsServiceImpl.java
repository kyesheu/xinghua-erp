package cn.xinghuaerp.product.service.impl;

import cn.xinghuaerp.product.entity.OGoods;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.product.entity.ShopGoods;
import cn.xinghuaerp.product.entity.ShopGoodsSku;
import cn.xinghuaerp.product.request.ShopGoodsAddRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuAddRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuInsertRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuStockUpdateRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuUpdateRequest;
import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.enums.EnumShopType;
import cn.xinghuaerp.product.mapper.OGoodsMapper;
import cn.xinghuaerp.product.mapper.OGoodsSkuMapper;
import cn.xinghuaerp.product.mapper.ShopGoodsMapper;
import cn.xinghuaerp.product.mapper.ShopGoodsSkuMapper;
import cn.xinghuaerp.product.service.OGoodsSkuService;
import cn.xinghuaerp.system.service.OShopService;
import cn.xinghuaerp.product.service.ShopGoodsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;

/**
* @author qilip
* @description 针对表【oms_shop_goods(其他渠道店铺商品)】的数据库操作Service实现
* @createDate 2025-07-15 08:29:21
*/
@Slf4j
@AllArgsConstructor
@Service
public class ShopGoodsServiceImpl extends ServiceImpl<ShopGoodsMapper, ShopGoods>
    implements ShopGoodsService {
    private final OGoodsSkuMapper goodsSkuMapper;
    private final OGoodsMapper goodsMapper;
    private final OGoodsSkuService goodsSkuService;
    private final ShopGoodsSkuMapper shopGoodsSkuMapper;
//    private final ShopGoodsSkuMappingService shopGoodsSkuMappingService;
    private final OShopService shopService;

    @Override
    public PageResult<ShopGoods> queryPageList(ShopGoods bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ShopGoods> queryWrapper = new LambdaQueryWrapper<ShopGoods>()
//                .eq(ShopGoods::getDeliverMethod,0)
                .eq(bo.getId()!=null,ShopGoods::getId,bo.getId())
                .eq(StringUtils.hasText(bo.getProductId()),ShopGoods::getProductId,bo.getProductId())
                .eq(StringUtils.hasText(bo.getSpuCode()),ShopGoods::getSpuCode,bo.getSpuCode())
                .eq(StringUtils.hasText(bo.getOuterProductId()),ShopGoods::getOuterProductId,bo.getOuterProductId())
                .like(StringUtils.hasText(bo.getTitle()),ShopGoods::getTitle,bo.getTitle())
                .eq(bo.getShopId()!=null,ShopGoods::getShopId,bo.getShopId())
                .eq(bo.getShopType()!=null,ShopGoods::getShopType,bo.getShopType())
                .eq(bo.getMerchantId()!=null,ShopGoods::getMerchantId,bo.getMerchantId())
                ;

        Page<ShopGoods> goodsPage = this.baseMapper.selectPage(pageQuery.build(), queryWrapper);
        if(goodsPage.getTotal()>0){
            for(ShopGoods goods : goodsPage.getRecords()){
                goods.setSkuList(shopGoodsSkuMapper.selectList(new LambdaQueryWrapper<ShopGoodsSku>().eq(ShopGoodsSku::getShopGoodsId,goods.getId())));
            }
        }
        return PageResult.build(goodsPage);
    }

    @Override
    public PageResult<ShopGoods> queryVirtualAndNoSkusPageList(ShopGoods bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ShopGoods> queryWrapper = new LambdaQueryWrapper<ShopGoods>()
                .eq(ShopGoods::getDeliverMethod,1)
                .like(StringUtils.hasText(bo.getTitle()),ShopGoods::getTitle,bo.getTitle())
                .eq(StringUtils.hasText(bo.getProductId()),ShopGoods::getProductId,bo.getProductId())
                .eq(bo.getShopId()!=null,ShopGoods::getShopId,bo.getShopId())
                .eq(bo.getShopType()!=null,ShopGoods::getShopType,bo.getShopType())
                .eq(bo.getMerchantId()!=null,ShopGoods::getMerchantId,bo.getMerchantId())
                .eq(bo.getBindShipSku()!=null,ShopGoods::getBindShipSku,bo.getBindShipSku())
                ;
        pageQuery.setIsAsc("desc");
        pageQuery.setOrderByColumn("bind_ship_sku,id");
        Page<ShopGoods> goodsPage = this.baseMapper.selectPage(pageQuery.build(), queryWrapper);
        return PageResult.build(goodsPage);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Integer> saveOrUpdateGoods(Long shopId,Long merchantId,Integer shopType, ShopGoods goods) {
//        OShop shop = shopService.getById(shopId);
//        if(shop==null) return ResultVo.error("店铺不存在");
//        goods.setMerchantId(shop.getMerchantId());
        Long shopGoodsId = 0L;
        goods.setMerchantId(merchantId);
        goods.setShopId(shopId);
        goods.setShopType(shopType);

        // 查询是否存在
        List<ShopGoods> shopGoods = this.baseMapper.selectList(new LambdaQueryWrapper<ShopGoods>()
                .eq(ShopGoods::getShopId, shopId)
                        .eq(ShopGoods::getShopType, shopType)
                .eq(ShopGoods::getMerchantId, merchantId)
                .eq(ShopGoods::getProductId, goods.getProductId())
        );
        if(shopGoods==null||shopGoods.size()==0){
            // 新增商品
            goods.setCreateOn(LocalDateTime.now());
            this.baseMapper.insert(goods);
            shopGoodsId = goods.getId();
            log.info("==========新增shopGoods==========");
        }else{
            // 修改商品
            goods.setId(shopGoods.get(0).getId());
            goods.setUpdateOn(LocalDateTime.now());
            this.baseMapper.updateById(goods);
            shopGoodsId =shopGoods.get(0).getId();
            log.info("==========修改shopGoods==========");
        }
        Long erpGoodsId = 0L;

        //处理skus
        if(goods.getSkuList()!=null&&goods.getSkuList().size()>0){
            for (ShopGoodsSku goodsSku : goods.getSkuList()) {
                goodsSku.setMerchantId(merchantId);
                goodsSku.setShopId(shopId);
                goodsSku.setShopType(shopType);
                Long erpGoodsSkuId = 0L;
                Long shopGoodsSkuId = 0L;
                //绑定skuId
                if(StringUtils.hasText(goodsSku.getOuterSkuId())){
                    OGoodsSku goodsSkuByCode = goodsSkuService.getGoodsSkuByCode(goodsSku.getOuterSkuId());
                    if(goodsSkuByCode!=null){
                        erpGoodsId = Long.parseLong(goodsSkuByCode.getGoodsId());
                        erpGoodsSkuId = Long.parseLong(goodsSkuByCode.getId());
//                        goodsSku.setErpGoodsId(Long.parseLong(goodsSkuByCode.getGoodsId()));
//                        goodsSku.setErpGoodsSkuId(Long.parseLong(goodsSkuByCode.getId()));
                    }
                }
                goodsSku.setErpGoodsId(erpGoodsId);
                goodsSku.setErpGoodsSkuId(erpGoodsSkuId);
                // 查询是否存在
                List<ShopGoodsSku> shopGoodsSkus = shopGoodsSkuMapper.selectList(new LambdaQueryWrapper<ShopGoodsSku>()
                        .eq(ShopGoodsSku::getShopGoodsId, goods.getId())
                        .eq(ShopGoodsSku::getSkuId, goodsSku.getSkuId())
                );
                if(shopGoodsSkus==null||shopGoodsSkus.size()==0){
                    //新增
                    goodsSku.setShopGoodsId(goods.getId());
                    goodsSku.setCreateOn(LocalDateTime.now());
                    shopGoodsSkuMapper.insert(goodsSku);
                    shopGoodsSkuId = goodsSku.getId();
                    log.info("==========新增shopGoodsSku==========");
                }else{
                    // update
                    if(shopGoodsSkus.get(0).getErpGoodsSkuId()!=null&&shopGoodsSkus.get(0).getErpGoodsSkuId()>0){
                        goodsSku.setErpGoodsId(null);
                        goodsSku.setErpGoodsSkuId(null);
                    }
                    goodsSku.setId(shopGoodsSkus.get(0).getId());
                    goodsSku.setUpdateOn(LocalDateTime.now());
                    shopGoodsSkuMapper.updateById(goodsSku);
                    shopGoodsSkuId = shopGoodsSkus.get(0).getId();
                    log.info("==========修改shopGoodsSku==========");
                }
//                if(erpGoodsSkuId.longValue()>0){
//                    // 添加绑定关联
//                    ShopGoodsSkuMapping shopGoodsSkuMapping = shopGoodsSkuMappingService.selectByPlatformSkuId(goodsSku.getSkuId(),goodsSku.getShopId());
//                    if (shopGoodsSkuMapping != null) {
//                        // 更新
////                        shopGoodsSkuMapping.setErpGoodsSkuId(erpGoodsSkuId);
////                        shopGoodsSkuMapping.setErpGoodsId(erpGoodsId);
////                        shopGoodsSkuMapping.setPlatformProductId(goodsSku.getProductId());
////                        shopGoodsSkuMapping.setPlatformSkuId(goodsSku.getSkuId());
////                        shopGoodsSkuMapping.setShopGoodsId(shopGoodsId);
////                        shopGoodsSkuMapping.setShopGoodsSkuId(shopGoodsSkuId);
////                        shopGoodsSkuMapping.setModifyOn(LocalDateTime.now());
////                        shopGoodsSkuMappingService.updateById(shopGoodsSkuMapping);
//                    } else {
//                        // 新增
//                        shopGoodsSkuMapping = new ShopGoodsSkuMapping();
//                        shopGoodsSkuMapping.setErpGoodsId(erpGoodsId);
//                        shopGoodsSkuMapping.setErpGoodsSkuId(erpGoodsSkuId);
//                        shopGoodsSkuMapping.setCreateOn(LocalDateTime.now());
//                        shopGoodsSkuMapping.setShopId(goodsSku.getShopId());
//                        shopGoodsSkuMapping.setShopType(goodsSku.getShopType());
//                        shopGoodsSkuMapping.setPlatformProductId(goodsSku.getProductId());
//                        shopGoodsSkuMapping.setPlatformSkuId(goodsSku.getSkuId());
//                        shopGoodsSkuMapping.setShopGoodsId(shopGoodsId);
//                        shopGoodsSkuMapping.setShopGoodsSkuId(shopGoodsSkuId);
//                        shopGoodsSkuMappingService.save(shopGoodsSkuMapping);
//                    }
//                }
            }
        }
//        else{
//            if(shopType== EnumShopType.TANG_LANG.getIndex()){
//                // 螳螂系统特殊处理
//                ShopGoodsSku sku = new ShopGoodsSku();
//            }
//        }
        if(erpGoodsId>0){
            ShopGoods updateGoods = new ShopGoods();
            updateGoods.setId(goods.getId());
            updateGoods.setErpGoodsId(erpGoodsId);
            this.baseMapper.updateById(updateGoods);
        }
//        log.info("=============添加店铺商品成功=============");
        return ResultVo.success();
    }

    /**
     * 手动添加店铺商品
     * @param request
     *
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> addGoods(ShopGoodsAddRequest request, OShop shop) {
        if (request == null || shop == null || shop.getId() == null || !shop.getId().equals(request.getShopId())) {
            return ResultVo.error("请选择有效的店铺");
        }
        if (!StringUtils.hasText(request.getGoodsName())) return ResultVo.error("请填写商品名称");
        if (request.getSkuList() == null || request.getSkuList().isEmpty()) return ResultVo.error("请至少添加一个商品规格");

        String productId = StringUtils.hasText(request.getProductId()) && !"0".equals(request.getProductId().trim())
                ? request.getProductId().trim() : "G-" + java.util.UUID.randomUUID().toString().replace("-", "");
        if (productId.length() > 50) return ResultVo.error("平台商品ID不能超过50个字符");
        if (this.baseMapper.selectCount(new LambdaQueryWrapper<ShopGoods>()
                .eq(ShopGoods::getShopId, shop.getId()).eq(ShopGoods::getProductId, productId)) > 0) {
            return ResultVo.error("该店铺中已存在相同的平台商品ID");
        }

        List<ShopGoodsSku> skuList = new ArrayList<>();
        java.util.Set<String> skuIds = new java.util.HashSet<>();
        Long erpGoodsId = null;
        long total = 0;
        int minPrice = Integer.MAX_VALUE;
        long now = System.currentTimeMillis() / 1000;
        for (ShopGoodsAddRequest.sku item : request.getSkuList()) {
            if (item == null || item.getPrice() == null || !Double.isFinite(item.getPrice()) || item.getPrice() < 0) {
                return ResultVo.error("请填写有效的规格售价");
            }
            BigDecimal cents = BigDecimal.valueOf(item.getPrice()).movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP);
            if (cents.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) return ResultVo.error("规格售价超出允许范围");
            int price = cents.intValueExact();
            int stock = item.getStockNum() == null ? 0 : item.getStockNum();
            if (stock < 0) return ResultVo.error("店铺库存不能小于0");
            total += stock;
            if (total > Integer.MAX_VALUE) return ResultVo.error("店铺商品总库存超出允许范围");
            minPrice = Math.min(minPrice, price);
            String skuId = StringUtils.hasText(item.getSkuId()) ? item.getSkuId().trim()
                    : "SKU-" + java.util.UUID.randomUUID().toString().replace("-", "");
            if (skuId.length() > 50) return ResultVo.error("平台SKU ID不能超过50个字符");
            if (!skuIds.add(skuId) || shopGoodsSkuMapper.selectCount(new LambdaQueryWrapper<ShopGoodsSku>()
                    .eq(ShopGoodsSku::getShopId, shop.getId()).eq(ShopGoodsSku::getSkuId, skuId)) > 0) {
                return ResultVo.error("该店铺中已存在相同的平台SKU ID");
            }

            OGoodsSku erpSku = null;
            if (item.getErpGoodsSkuId() != null) {
                erpSku = goodsSkuMapper.selectById(item.getErpGoodsSkuId());
                if (erpSku == null) return ResultVo.error("所选商品库SKU不存在，请重新选择");
            } else if (StringUtils.hasText(item.getSkuCode())) {
                List<OGoodsSku> candidates = goodsSkuMapper.selectList(new LambdaQueryWrapper<OGoodsSku>()
                        .eq(OGoodsSku::getSkuCode, item.getSkuCode()));
                if (candidates.size() > 1) return ResultVo.error("SKU编码对应多个商品，请明确选择商品库SKU");
                if (!candidates.isEmpty()) erpSku = candidates.get(0);
            }
            Long linkedGoodsId = erpSku == null ? 0L : Long.valueOf(erpSku.getGoodsId());
            if (erpSku != null) {
                if (erpGoodsId != null && !erpGoodsId.equals(linkedGoodsId)) {
                    return ResultVo.error("同一店铺商品只能关联同一商品库商品的规格");
                }
                erpGoodsId = linkedGoodsId;
            }

            ShopGoodsSku sku = new ShopGoodsSku();
            sku.setProductId(productId);
            sku.setProductTitle(request.getGoodsName().trim());
            sku.setSkuId(skuId);
            sku.setOuterProductId(request.getOuterProductId());
            sku.setOuterSkuId(item.getOuterSkuId());
            sku.setImg(StringUtils.hasText(item.getImg()) ? item.getImg() : request.getGoodsImg());
            sku.setPrice(price);
            sku.setStockNum(stock);
            sku.setStock(stock);
            sku.setSkuCode(erpSku == null ? item.getSkuCode() : erpSku.getSkuCode());
            sku.setSkuName(StringUtils.hasText(item.getSkuName()) ? item.getSkuName() : "默认规格");
            sku.setStatus(1);
            sku.setAddTime(now);
            sku.setCreateOn(LocalDateTime.now());
            sku.setBindShipSku(erpSku == null ? 0 : 1);
            sku.setErpGoodsId(linkedGoodsId);
            sku.setErpGoodsSkuId(erpSku == null ? 0L : Long.valueOf(erpSku.getId()));
            skuList.add(sku);
        }

        ShopGoods goods = new ShopGoods();
        goods.setShopId(shop.getId());
        goods.setShopType(shop.getType());
        goods.setMerchantId(shop.getMerchantId());
        goods.setProductId(productId);
        goods.setOuterProductId(request.getOuterProductId());
        goods.setTitle(request.getGoodsName().trim());
        goods.setImg(request.getGoodsImg());
        goods.setStatus(1);
        goods.setMinPrice(minPrice);
        goods.setSpuCode(request.getGoodsNum());
        goods.setQuantity((int) total);
        goods.setErpGoodsId(erpGoodsId == null ? 0L : erpGoodsId);
        goods.setAddTime(now);
        goods.setCreateOn(LocalDateTime.now());
        goods.setDeliverMethod(0);
        goods.setBindShipSku(skuList.stream().allMatch(sku -> sku.getErpGoodsSkuId() > 0) ? 1 : 0);
        if (this.baseMapper.insert(goods) != 1) throw new IllegalStateException("保存店铺商品失败");
        for (ShopGoodsSku sku : skuList) {
            sku.setShopGoodsId(goods.getId());
            sku.setShopId(goods.getShopId());
            sku.setShopType(goods.getShopType());
            sku.setMerchantId(goods.getMerchantId());
            if (shopGoodsSkuMapper.insert(sku) != 1) throw new IllegalStateException("保存店铺商品规格失败");
        }
        return ResultVo.success(goods.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> addGoodsSku(ShopGoodsSkuAddRequest request) {
        OGoodsSku oGoodsSku = goodsSkuMapper.selectById(request.getGoodsSkuId());
        if(oGoodsSku==null){
            return ResultVo.error("没有找到商品库sku");
        }
        OShop shop = shopService.getById(request.getShopId());
        if(shop==null) return ResultVo.error("店铺不存在");

        Long shopGoodsId = null;
        Long shopGoodsSkuId = null;
        if(shop.getType().intValue()==EnumShopType.TANG_LANG.getIndex()){
            // 螳螂系统不添加shopGoods表
            shopGoodsId = 0L;
        }else {
            // 查询商品是否存在
            List<ShopGoods> shopGoods = this.baseMapper.selectList(new LambdaQueryWrapper<ShopGoods>().eq(ShopGoods::getShopId, request.getShopId()).eq(ShopGoods::getErpGoodsId, oGoodsSku.getGoodsId()));
            if (shopGoods.isEmpty()) {
                // 不存在，增加一条商品信息
                ShopGoods goods = new ShopGoods();
                goods.setShopId(shop.getId());
                goods.setShopType(shop.getType());
                goods.setMerchantId(shop.getMerchantId());
                goods.setProductId("");
                goods.setOuterProductId(oGoodsSku.getGoodsNum());
                goods.setTitle(oGoodsSku.getGoodsName());
                goods.setImg(oGoodsSku.getColorImage());
                goods.setStatus(1);
                goods.setMinPrice(BigDecimal.valueOf(100).multiply(oGoodsSku.getRetailPrice()).intValue());
                goods.setSpuCode(oGoodsSku.getGoodsNum());
                goods.setQuantity(0);
                goods.setErpGoodsId(Long.parseLong(oGoodsSku.getGoodsId()));
                goods.setAddTime(System.currentTimeMillis() / 100);
                goods.setCreateOn(LocalDateTime.now());
                goods.setDeliverMethod(0);
                goods.setBindShipSku(0);
                this.baseMapper.insert(goods);
                shopGoodsId = goods.getId();
            } else {
                // 存在，不动
                shopGoodsId = shopGoods.get(0).getId();
            }
        }
        // 查询商品sku是否存在
        List<ShopGoodsSku> shopGoodsSkus = shopGoodsSkuMapper.selectList(new LambdaQueryWrapper<ShopGoodsSku>()
                .eq(ShopGoodsSku::getErpGoodsSkuId, request.getGoodsSkuId())
                .eq(StringUtils.hasText(request.getSkuId()), ShopGoodsSku::getSkuId, request.getSkuId())
                .eq(ShopGoodsSku::getShopId, request.getShopId())
        );
        if(shopGoodsSkus.isEmpty()){
            // 不存在 添加
            ShopGoodsSku s = new ShopGoodsSku();
            s.setShopGoodsId(shopGoodsId);
            s.setShopId(shop.getId());
            s.setShopType(shop.getType());
            s.setShopName(shop.getName());
            s.setMerchantId(shop.getMerchantId());
            s.setProductId("");
            s.setProductTitle(oGoodsSku.getGoodsName());
            s.setSkuId(request.getSkuId());
            s.setOuterProductId(oGoodsSku.getGoodsNum());
            s.setOuterSkuId(oGoodsSku.getSkuCode());
            s.setImg( oGoodsSku.getColorImage());
            s.setPrice(BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(request.getPrice())).intValue());
            s.setStockNum(0);
            s.setSkuCode(oGoodsSku.getSkuCode());
            s.setSkuName(oGoodsSku.getSkuName());
            s.setStatus(1);
            s.setAddTime(System.currentTimeMillis()/100);
            s.setStock(0);
            s.setCreateOn(LocalDateTime.now());
            s.setBindShipSku(0);
            s.setErpGoodsSkuId(Long.parseLong(oGoodsSku.getId()));
            s.setErpGoodsId(Long.parseLong(oGoodsSku.getGoodsId()));
            shopGoodsSkuMapper.insert(s);
            shopGoodsSkuId = s.getId();
        }else{
            // 存在就修改
            shopGoodsSkuId = shopGoodsSkus.get(0).getId();
            ShopGoodsSku s = new ShopGoodsSku();
            s.setId(shopGoodsSkuId);
            s.setSkuId(request.getSkuId());
            s.setPrice(BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(request.getPrice())).intValue());
            s.setUpdateOn(LocalDateTime.now());
            s.setModifyTime(System.currentTimeMillis()/100);
            shopGoodsSkuMapper.updateById(s);
        }

        return ResultVo.success(shopGoodsSkuId);
    }

    @Override
    public ResultVo<Long> insertGoodsSku(ShopGoodsSkuInsertRequest request) {
        ShopGoods shopGoods = this.baseMapper.selectById(request.getShopGoodsId());
        if(shopGoods == null) return ResultVo.error("店铺商品不存在");

        OGoodsSku oGoodsSku = goodsSkuMapper.selectById(request.getErpGoodsSkuId());
        if(oGoodsSku==null){
            return ResultVo.error("没有找到商品库sku");
        }
        List<ShopGoodsSku> shopGoodsSkuList = shopGoodsSkuMapper.selectList(new LambdaQueryWrapper<ShopGoodsSku>().eq(ShopGoodsSku::getSkuId, request.getSkuId()));
        if(!shopGoodsSkuList.isEmpty()){
            return ResultVo.error("平台SkuId已经存在");
        }
        // 不存在 添加
        ShopGoodsSku s = new ShopGoodsSku();
        s.setShopGoodsId(request.getShopGoodsId());
        s.setShopId(shopGoods.getShopId());
        s.setShopType(shopGoods.getShopType());
        s.setShopName("");
        s.setMerchantId(shopGoods.getMerchantId());
        s.setProductId(shopGoods.getProductId());
        s.setProductTitle(oGoodsSku.getGoodsName());
        s.setSkuId(request.getSkuId());
        s.setOuterProductId(oGoodsSku.getGoodsNum());
        s.setOuterSkuId(oGoodsSku.getSkuCode());
        s.setImg(oGoodsSku.getColorImage());
        s.setPrice(BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(request.getPrice())).intValue());
        s.setStockNum(0);
        s.setSkuCode(oGoodsSku.getSkuCode());
        s.setSkuName(oGoodsSku.getSkuName());
        s.setStatus(1);
        s.setAddTime(System.currentTimeMillis()/100);
        s.setStock(0);
        s.setCreateOn(LocalDateTime.now());
        s.setBindShipSku(0);
        s.setErpGoodsSkuId(Long.parseLong(oGoodsSku.getId()));
        s.setErpGoodsId(Long.parseLong(oGoodsSku.getGoodsId()));
        shopGoodsSkuMapper.insert(s);
        return ResultVo.success();
    }

    @Override
    public ResultVo<Long> updateGoodsSku(ShopGoodsSkuUpdateRequest request) {
        ShopGoodsSku shopGoodsSku = shopGoodsSkuMapper.selectById(request.getId());
        if(shopGoodsSku==null) return ResultVo.error("店铺商品Sku不存在");

        OGoodsSku oGoodsSku = goodsSkuMapper.selectById(request.getErpGoodsSkuId());
        if(oGoodsSku==null){
            return ResultVo.error("没有找到商品库sku");
        }

        if(!request.getSkuId().equals(shopGoodsSku.getSkuId())){
            List<ShopGoodsSku> shopGoodsSkuList = shopGoodsSkuMapper.selectList(new LambdaQueryWrapper<ShopGoodsSku>().eq(ShopGoodsSku::getSkuId, request.getSkuId()));
            if(!shopGoodsSkuList.isEmpty()){
                return ResultVo.error("平台SkuId已经存在");
            }
        }

        ShopGoodsSku s = new ShopGoodsSku();
        s.setId(request.getId());
        s.setProductTitle(request.getProductTitle());
        s.setSkuId(request.getSkuId());
        s.setOuterProductId(oGoodsSku.getGoodsNum());
        s.setOuterSkuId(oGoodsSku.getSkuCode());
        s.setErpGoodsSkuId(Long.parseLong(oGoodsSku.getId()));
        s.setErpGoodsId(Long.parseLong(oGoodsSku.getGoodsId()));
        s.setImg(request.getImg());
        s.setPrice(BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(request.getPrice())).intValue());
        s.setSkuCode(oGoodsSku.getSkuCode());
        s.setSkuName(request.getSkuName());
        s.setUpdateOn(LocalDateTime.now());
        s.setModifyTime(System.currentTimeMillis()/100);
        shopGoodsSkuMapper.updateById(s);

        return ResultVo.success();
    }

    @Override
    public ResultVo<Long> updateGoodsStock(ShopGoodsSkuStockUpdateRequest request) {
        ShopGoodsSku sku=new ShopGoodsSku();
        sku.setId(request.getId());
        sku.setStock(request.getStockNum());
        sku.setStockNum(request.getStockNum());
        sku.setUpdateOn(LocalDateTime.now());
        shopGoodsSkuMapper.updateById(sku);
        return ResultVo.success();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVo<Long> savePddGoods(ShopGoods shopGoods, Long shopId) {
        if(shopGoods==null) return ResultVo.error("商品数据不能为空");
        if(shopGoods.getId()==null&&!StringUtils.hasLength(shopGoods.getProductId())) return ResultVo.error("商品ID或者平台商品ID不能为空");
//        if(shopGoods.getSkuList()==null||shopGoods.getSkuList().isEmpty()) return ResultVo.error("没有商品规格信息");

        OShop shop = shopService.getById(shopId);
        if(shop==null) return ResultVo.error("店铺不存在");

        ShopGoods goods = null;
        if(shopGoods.getId()!=null){
            goods = this.baseMapper.selectById(shopGoods.getId());
        }else {
            List<ShopGoods> shopGoodsTmp = this.baseMapper.selectList(
                    new LambdaQueryWrapper<ShopGoods>()
                            .eq(ShopGoods::getShopId, shopId)
                            .eq(ShopGoods::getProductId, shopGoods.getProductId()));
            if (!shopGoodsTmp.isEmpty()) {
                goods = shopGoodsTmp.get(0);
            }
        }
        Long erpGoodsId = 0L;
        // 查询商品外部编码
        if(StringUtils.hasText(shopGoods.getOuterProductId())) {
            List<OGoods> oGoods = goodsMapper.selectList(new LambdaQueryWrapper<OGoods>().eq(OGoods::getGoodsNum, shopGoods.getOuterProductId()));
            if (!oGoods.isEmpty()) {
                erpGoodsId = Long.parseLong(oGoods.get(0).getId());
            }
        }
        Long shopGoodsId = null;
        if(goods==null){
            // 新增
            // 不存在，增加一条商品信息
            ShopGoods goodsNew = new ShopGoods();
            goodsNew.setShopId(shop.getId());
            goodsNew.setShopType(shop.getType());
            goodsNew.setMerchantId(shop.getMerchantId());
            goodsNew.setProductId(shopGoods.getProductId());
            goodsNew.setOuterProductId(shopGoods.getOuterProductId());
            goodsNew.setTitle(shopGoods.getTitle());
            goodsNew.setImg(shopGoods.getImg());
            goodsNew.setStatus(shopGoods.getStatus());
            goodsNew.setMinPrice(shopGoods.getMinPrice());
            goodsNew.setMarketPrice(shopGoods.getMarketPrice());
            goodsNew.setSpuCode(shopGoods.getOuterProductId());
            goodsNew.setQuantity(shopGoods.getQuantity());
            goodsNew.setErpGoodsId(erpGoodsId);
            goodsNew.setAddTime(shopGoods.getAddTime());
            goodsNew.setCreateOn(LocalDateTime.now());
            goodsNew.setDeliverMethod(0);
            goodsNew.setBindShipSku(0);
            this.baseMapper.insert(goodsNew);
            shopGoodsId = goodsNew.getId();
        }else{
            // 修改
            shopGoodsId = goods.getId();
            shopGoods.setId(goods.getId());
            shopGoods.setUpdateOn(LocalDateTime.now());
            this.baseMapper.updateById(shopGoods);
        }

        // 添加sku
        for (ShopGoodsSku goodsSku : shopGoods.getSkuList()) {
            goodsSku.setMerchantId(shop.getMerchantId());
            goodsSku.setShopId(shopId);
            goodsSku.setShopType(shop.getType());
            Long erpGoodsSkuId = 0L;
            Long shopGoodsSkuId = 0L;
            //绑定skuId
            if(StringUtils.hasText(goodsSku.getOuterSkuId())){
                OGoodsSku goodsSkuByCode = goodsSkuService.getGoodsSkuByCode(goodsSku.getOuterSkuId());
                if(goodsSkuByCode!=null){
                    erpGoodsId = Long.parseLong(goodsSkuByCode.getGoodsId());
                    erpGoodsSkuId = Long.parseLong(goodsSkuByCode.getId());
                }
            }
            goodsSku.setErpGoodsId(erpGoodsId);
            goodsSku.setErpGoodsSkuId(erpGoodsSkuId);
            // 查询是否存在
            List<ShopGoodsSku> shopGoodsSkus = shopGoodsSkuMapper.selectList(new LambdaQueryWrapper<ShopGoodsSku>()
                    .eq(ShopGoodsSku::getShopGoodsId, shopGoodsId)
                    .eq(ShopGoodsSku::getSkuId, goodsSku.getSkuId())
            );
            if(shopGoodsSkus==null||shopGoodsSkus.size()==0){
                //新增
                goodsSku.setShopGoodsId(shopGoodsId);
                goodsSku.setCreateOn(LocalDateTime.now());
                shopGoodsSkuMapper.insert(goodsSku);
                shopGoodsSkuId = goodsSku.getId();
                log.info("==========新增shopGoodsSku==========");
            }else{
                // update
                if(shopGoodsSkus.get(0).getErpGoodsSkuId()!=null&&shopGoodsSkus.get(0).getErpGoodsSkuId()>0){
                    goodsSku.setErpGoodsId(null);
                    goodsSku.setErpGoodsSkuId(null);
                }
                goodsSku.setId(shopGoodsSkus.get(0).getId());
                goodsSku.setUpdateOn(LocalDateTime.now());
                shopGoodsSkuMapper.updateById(goodsSku);
                shopGoodsSkuId = shopGoodsSkus.get(0).getId();
                log.info("==========修改shopGoodsSku==========");
            }

        }
        return ResultVo.success();
    }

    @Override
    public boolean save(ShopGoods entity) {
        return super.save(entity);
    }
}




