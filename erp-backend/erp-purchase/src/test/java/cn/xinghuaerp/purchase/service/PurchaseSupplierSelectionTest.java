package cn.xinghuaerp.purchase.service;

import cn.xinghuaerp.product.entity.ErpSupplierProductItem;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrder;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderItem;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.product.mapper.OGoodsSkuMapper;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderItemMapper;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.product.mapper.ErpSupplierProductItemMapper;
import cn.xinghuaerp.purchase.request.PurchaseOrderAddItemBo;
import cn.xinghuaerp.product.query.GoodsSkuQuery;
import cn.xinghuaerp.purchase.request.PurchaseOrderAddRequest;
import cn.xinghuaerp.product.service.impl.OGoodsServiceImpl;
import cn.xinghuaerp.purchase.service.impl.ErpPurchaseOrderServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Actual supplier filtering SQL against an isolated H2 database; order writes use test doubles. */
class PurchaseSupplierSelectionTest {
    private OGoodsServiceImpl goods;
    private ErpPurchaseOrderServiceImpl purchase;
    private final ErpPurchaseOrderMapper orders = mock(ErpPurchaseOrderMapper.class);
    private final ErpPurchaseOrderItemMapper items = mock(ErpPurchaseOrderItemMapper.class);
    private final ErpSupplierMapper suppliers = mock(ErpSupplierMapper.class);
    private JdbcTemplate db;

    @BeforeEach
    void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        // Keep a connection through setup/test via the session's data source.
        ds.setURL(ds.getURL() + ";DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("sales-shipping-schema.sql")).execute(ds);
        db = new JdbcTemplate(ds);
        var config = new MybatisConfiguration();
        config.addMapper(OGoodsSkuMapper.class);
        config.addMapper(ErpSupplierProductItemMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setConfiguration(config);
        factory.setDataSource(ds);
        var session = new SqlSessionTemplate(factory.getObject());
        // Build only the extra supplier table from entity metadata; no production data source is used.
        var info = TableInfoHelper.getTableInfo(ErpSupplierProductItem.class);
        var columns = info.getFieldList().stream().map(f -> f.getColumn() + " "
                + (f.getPropertyType() == BigDecimal.class ? "decimal(12,2)" : "varchar(255)"))
                .collect(Collectors.joining(","));
        db.execute("CREATE TABLE erp_supplier_product_item (id bigint primary key," + columns + ")");
        db.update("INSERT INTO o_goods_sku(id,goods_id,sku_code,goods_name,ship_type,merchant_id,pur_price) "
                + "VALUES(101,201,'RICE-WHITE','电饭煲',10,0,120),(102,201,'RICE-BLACK','电饭煲',10,0,130),(103,202,'OTHER','其他商品',10,0,140)");
        db.update("UPDATE o_goods_sku SET inventory_mode=0");
        db.update("INSERT INTO erp_supplier_product_item(id,supplier_id,erp_goods_sku_id,price) "
                + "VALUES(1,10,101,95),(2,20,102,88),(3,10,103,0),(4,20,101,99)");
        db.update("UPDATE erp_supplier_product_item SET status=1, supplier_product_id=supplier_id");
        db.execute("CREATE TABLE erp_supplier_product(id bigint primary key,supplier_id bigint,status int)");
        db.update("INSERT INTO erp_supplier_product VALUES(10,10,1),(20,20,1)");
        var skus = session.getMapper(OGoodsSkuMapper.class);
        var supplierSkus = session.getMapper(ErpSupplierProductItemMapper.class);
        goods = new OGoodsServiceImpl(null, skus, null, supplierSkus);
        var supplier = new ErpSupplier();
        supplier.setDisable(0); supplier.setIsDelete(0);
        when(suppliers.selectById(10L)).thenReturn(supplier);
        var productFacade = new cn.xinghuaerp.product.facade.ProductFacadeImpl(supplierSkus, null, skus, null, null);
        purchase = new ErpPurchaseOrderServiceImpl(orders, items, null, productFacade, productFacade, suppliers);
    }

    @Test
    void filtersBySupplierAndKeepsSupplierSpecificAndZeroQuotes() {
        var query = new GoodsSkuQuery();
        query.setSupplierId(10L);
        var result = goods.querySkuPageList(query, new PageQuery()).getRecords();
        assertEquals(List.of("101", "103"), result.stream().map(OGoodsSku::getId).sorted().toList());
        assertEquals(0, result.stream().filter(s -> s.getId().equals("101")).findFirst().orElseThrow()
                .getSupplierPrice().compareTo(new BigDecimal("95")));
        assertEquals(0, result.stream().filter(s -> s.getId().equals("103")).findFirst().orElseThrow()
                .getSupplierPrice().compareTo(BigDecimal.ZERO));
        query.setSupplierId(20L);
        query.setId(101L);
        assertEquals(0, goods.querySkuPageList(query, new PageQuery()).getRecords().get(0)
                .getSupplierPrice().compareTo(new BigDecimal("99")));
    }

    @Test
    void searchCannotEscapeSupplierAndMissingRelationsReturnEmpty() {
        var query = new GoodsSkuQuery();
        query.setSupplierId(10L);
        query.setSkuCode("BLACK");
        assertTrue(goods.querySkuPageList(query, new PageQuery()).getRecords().isEmpty());
        query.setSkuCode(null);
        query.setSupplierId(999L);
        assertTrue(goods.querySkuPageList(query, new PageQuery()).getRecords().isEmpty());
    }

    @Test
    void ordinarySkuSelectionStillListsAllGoodsWithoutSupplierFilter() {
        assertEquals(3, goods.querySkuPageList(new GoodsSkuQuery(), new PageQuery()).getRecords().size());
    }

    @Test
    void delistedSupplierSkuIsNeitherSelectableNorPurchasable() {
        db.update("UPDATE erp_supplier_product_item SET status=2 WHERE supplier_id=10 AND erp_goods_sku_id=101");
        var query = new GoodsSkuQuery(); query.setSupplierId(10L); query.setId(101L);
        assertTrue(goods.querySkuPageList(query, new PageQuery()).getRecords().isEmpty());
        assertTrue(purchase.createPurchaseOrder(request(10L,101L)).getMsg().contains("未关联"));
        verifyNoInteractions(orders, items);
    }

    @Test
    void invalidNewerRelationCannotOverrideValidSupplierQuote() {
        db.update("INSERT INTO erp_supplier_product VALUES(30,10,2)");
        db.update("INSERT INTO erp_supplier_product_item(id,supplier_id,supplier_product_id,erp_goods_sku_id,price,status) "
                + "VALUES(5,10,30,101,1,1)");
        var query = new GoodsSkuQuery(); query.setSupplierId(10L); query.setId(101L);
        assertEquals(0, goods.querySkuPageList(query, new PageQuery()).getRecords().get(0)
                .getSupplierPrice().compareTo(new BigDecimal("95")));
    }

    @Test
    void invalidParentProductIsNeitherSelectableNorPurchasable() {
        var query = new GoodsSkuQuery(); query.setSupplierId(10L); query.setId(101L);
        for (String change : List.of(
                "UPDATE erp_supplier_product SET status=2 WHERE id=10",
                "UPDATE erp_supplier_product SET status=1,supplier_id=20 WHERE id=10",
                "DELETE FROM erp_supplier_product WHERE id=10")) {
            db.update(change);
            assertTrue(goods.querySkuPageList(query, new PageQuery()).getRecords().isEmpty());
            assertTrue(purchase.createPurchaseOrder(request(10L,101L)).getMsg().contains("未关联"));
        }
        verifyNoInteractions(orders, items);
    }

    @Test
    void rejectsUnrelatedSkuBeforeAnyOrderWrite() {
        var request = request(10L, 102L);
        assertTrue(purchase.createPurchaseOrder(request).getMsg().contains("未关联"));
        verifyNoInteractions(orders, items);
        request.setContactId(null);
        assertTrue(purchase.createPurchaseOrder(request).getMsg().contains("供应商"));
        verifyNoInteractions(orders, items);
    }

    @Test
    void linkedSkuAllowsNegotiatedPurchasePrice() {
        when(orders.insert(any(ErpPurchaseOrder.class))).thenAnswer(call -> {
            ((ErpPurchaseOrder) call.getArgument(0)).setId(1L);
            return 1;
        });
        when(items.insert(any(ErpPurchaseOrderItem.class))).thenReturn(1);
        var request = request(10L, 101L);
        request.getGoodsList().get(0).setPurPrice(new BigDecimal("90"));
        request.getGoodsList().get(0).setGoodsName("伪造名称");
        request.getGoodsList().get(0).setAmount(new BigDecimal("1"));
        assertEquals(0, purchase.createPurchaseOrder(request).getCode());
        var row = org.mockito.ArgumentCaptor.forClass(ErpPurchaseOrderItem.class);
        verify(items).insert(row.capture());
        assertEquals(new BigDecimal("90"), row.getValue().getPrice());
        assertEquals(101L, row.getValue().getSpecId());
        assertEquals("电饭煲", row.getValue().getGoodsName());
        assertEquals(90.0, row.getValue().getAmount());
    }

    @Test
    void rejectsInvalidValuesBeforeWriting() {
        var request = request(10L, 101L);
        request.getGoodsList().get(0).setQuantity(0L);
        assertTrue(purchase.createPurchaseOrder(request).getMsg().contains("数量"));
        request.getGoodsList().get(0).setQuantity(1L);
        request.setOrderAmount(BigDecimal.ONE);
        assertTrue(purchase.createPurchaseOrder(request).getMsg().contains("不一致"));
        request.setOrderAmount(new BigDecimal("90"));
        request.getGoodsList().get(0).setPurPrice(new BigDecimal("-1"));
        assertTrue(purchase.createPurchaseOrder(request).getMsg().contains("单价"));
        request.getGoodsList().get(0).setPurPrice(new BigDecimal("90"));
        request.setGoodsList(List.of(request.getGoodsList().get(0), request.getGoodsList().get(0)));
        assertTrue(purchase.createPurchaseOrder(request).getMsg().contains("重复"));
        request.setGoodsList(List.of(request.getGoodsList().get(0)));
        request.setStatus(1);
        assertTrue(purchase.createPurchaseOrder(request).getMsg().contains("待审核"));
        verifyNoInteractions(orders, items);
    }

    @Test
    void rejectsDisabledSupplierEvenIfSkuRelationExists() {
        var supplier = new ErpSupplier();
        supplier.setDisable(1); supplier.setIsDelete(0);
        when(suppliers.selectById(10L)).thenReturn(supplier);
        assertTrue(purchase.createPurchaseOrder(request(10L, 101L)).getMsg().contains("停用"));
        verifyNoInteractions(orders, items);
    }

    private PurchaseOrderAddRequest request(Long supplier, Long sku) {
        var request = new PurchaseOrderAddRequest();
        request.setContactId(supplier);
        request.setOrderDate("2026-10-27");
        request.setOrderAmount(new BigDecimal("90"));
        var item = new PurchaseOrderAddItemBo();
        item.setId(sku);
        item.setQuantity(1L);
        item.setPurPrice(new BigDecimal("90"));
        request.setGoodsList(List.of(item));
        return request;
    }
}
