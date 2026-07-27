package cn.xinghuaerp.product.service;

import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.product.entity.ShopGoods;
import cn.xinghuaerp.product.entity.ShopGoodsSku;
import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.product.mapper.OGoodsSkuMapper;
import cn.xinghuaerp.product.mapper.ShopGoodsMapper;
import cn.xinghuaerp.product.mapper.ShopGoodsSkuMapper;
import cn.xinghuaerp.product.request.ShopGoodsAddRequest;
import cn.xinghuaerp.product.service.impl.ShopGoodsServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopGoodsServiceImplTest {
    private final ShopGoodsMapper goods = mock(ShopGoodsMapper.class);
    private final ShopGoodsSkuMapper skus = mock(ShopGoodsSkuMapper.class);
    private final OGoodsSkuMapper erpSkus = mock(OGoodsSkuMapper.class);
    private final ShopGoodsServiceImpl service = new ShopGoodsServiceImpl(erpSkus, null, null, skus, null);
    private OShop shop;
    private ShopGoodsAddRequest request;

    @BeforeEach
    void setup() throws Exception {
        for (Class<?> entity : List.of(ShopGoods.class, ShopGoodsSku.class, OGoodsSku.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), entity);
        }
        Class<?> type = service.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField("baseMapper");
                field.setAccessible(true);
                field.set(service, goods);
                break;
            } catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        shop = new OShop();
        shop.setId(10L);
        shop.setType(100);
        shop.setMerchantId(20L);
        request = new ShopGoodsAddRequest();
        request.setShopId(10L);
        request.setGoodsName("测试商品");
        request.setSkuList(new ArrayList<>(List.of(sku(101L, 12.34))));
        when(erpSkus.selectById(101L)).thenReturn(erpSku("101", "201"));
        when(erpSkus.selectById(102L)).thenReturn(erpSku("102", "201"));
        when(goods.insert(any(ShopGoods.class))).thenAnswer(call -> {
            ((ShopGoods) call.getArgument(0)).setId(301L);
            return 1;
        });
        when(skus.insert(any(ShopGoodsSku.class))).thenReturn(1);
    }

    @Test
    void savesSelectedShopAndErpLinksWithPricesInCents() {
        request.getSkuList().add(sku(102L, 8.55));
        assertEquals(301L, service.addGoods(request, shop).getData());
        var product = ArgumentCaptor.forClass(ShopGoods.class);
        verify(goods).insert(product.capture());
        assertEquals(10L, product.getValue().getShopId());
        assertEquals(855, product.getValue().getMinPrice());
        assertEquals(201L, product.getValue().getErpGoodsId());
        assertEquals(1, product.getValue().getBindShipSku());
        assertTrue(product.getValue().getProductId().startsWith("G-"));
        var rows = ArgumentCaptor.forClass(ShopGoodsSku.class);
        verify(skus, times(2)).insert(rows.capture());
        assertEquals(1234, rows.getAllValues().get(0).getPrice());
        assertEquals(0, rows.getValue().getStockNum());
        assertEquals(102L, rows.getValue().getErpGoodsSkuId());
        assertEquals(301L, rows.getValue().getShopGoodsId());
        assertNotEquals(rows.getAllValues().get(0).getSkuId(), rows.getValue().getSkuId());
    }

    @Test
    void duplicateChecksIncludeShop() {
        request.setProductId("same-product");
        request.getSkuList().get(0).setSkuId("same-sku");
        when(goods.selectCount(any())).thenAnswer(call -> {
            LambdaQueryWrapper<?> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("shop_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(10L));
            return 0L;
        });
        when(skus.selectCount(any())).thenAnswer(call -> {
            LambdaQueryWrapper<?> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("shop_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(10L));
            return 0L;
        });
        assertEquals(301L, service.addGoods(request, shop).getData());
    }

    @Test
    void invalidLaterSkuDoesNotPartiallyInsertProduct() {
        request.getSkuList().add(sku(102L, null));
        assertTrue(service.addGoods(request, shop).getMsg().contains("售价"));
        verify(goods, never()).insert(any(ShopGoods.class));
        verify(skus, never()).insert(any(ShopGoodsSku.class));
    }

    @Test
    void rejectsMissingAndMixedErpProducts() {
        request.getSkuList().get(0).setErpGoodsSkuId(999L);
        assertTrue(service.addGoods(request, shop).getMsg().contains("不存在"));
        request.getSkuList().get(0).setErpGoodsSkuId(101L);
        request.getSkuList().add(sku(102L, 2.0));
        when(erpSkus.selectById(102L)).thenReturn(erpSku("102", "202"));
        assertTrue(service.addGoods(request, shop).getMsg().contains("同一商品库"));
        verify(goods, never()).insert(any(ShopGoods.class));
    }

    @Test
    void linkedSkuDoesNotLeakIntoUnlinkedRow() {
        request.getSkuList().add(sku(null, 1.0));
        service.addGoods(request, shop);
        var rows = ArgumentCaptor.forClass(ShopGoodsSku.class);
        verify(skus, times(2)).insert(rows.capture());
        assertEquals(0L, rows.getValue().getErpGoodsId());
        assertEquals(0L, rows.getValue().getErpGoodsSkuId());
        assertEquals(0, rows.getValue().getBindShipSku());
    }

    @Test
    void rejectsInvalidInputsAndThrowsOnFailedWrite() {
        request.setShopId(null);
        assertTrue(service.addGoods(request, shop).getMsg().contains("店铺"));
        request.setShopId(10L);
        request.getSkuList().get(0).setStockNum(-1);
        assertTrue(service.addGoods(request, shop).getMsg().contains("库存"));
        request.getSkuList().get(0).setStockNum(0);
        when(skus.insert(any(ShopGoodsSku.class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.addGoods(request, shop));
    }

    private static ShopGoodsAddRequest.sku sku(Long id, Double price) {
        var sku = new ShopGoodsAddRequest.sku();
        sku.setErpGoodsSkuId(id);
        sku.setPrice(price);
        return sku;
    }

    private static OGoodsSku erpSku(String id, String goodsId) {
        var sku = new OGoodsSku();
        sku.setId(id);
        sku.setGoodsId(goodsId);
        sku.setSkuCode("SKU" + id);
        return sku;
    }
}
