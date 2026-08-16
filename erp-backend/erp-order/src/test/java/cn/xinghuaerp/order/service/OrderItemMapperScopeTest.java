package cn.xinghuaerp.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import cn.xinghuaerp.order.mapper.OOrderItemMapper;
import cn.xinghuaerp.order.query.OrderItemQuery;
import cn.xinghuaerp.inventory.vo.OrderItemListVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OrderItemMapperScopeTest {
 @Test void merchantAndShopComeFromParentOrder() throws Exception {
  var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
  new ResourceDatabasePopulator(new ClassPathResource("sales-shipping-schema.sql")).execute(ds);
  var db=new JdbcTemplate(ds);
  db.update("INSERT INTO o_order(id,order_num,shop_type,shop_id,merchant_id,order_status,amount) VALUES(1,'A',999,10,7,1,10),(2,'B',999,11,7,1,10),(3,'C',999,12,8,1,10)");
  db.update("INSERT INTO o_order_item(id,order_id,order_num,sub_order_num,shop_type,shop_id,merchant_id,quantity,goods_price) VALUES(1,1,'A','A1',999,99,8,1,10),(2,2,'B','B1',999,11,7,1,10),(3,3,'C','C1',999,10,7,1,10)");
  var config=new MybatisConfiguration(); config.addMapper(OOrderItemMapper.class);
  var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config); factory.setMapperLocations(new ClassPathResource("mapper/oms/OOrderItemMapper.xml"));
  var mapper=new SqlSessionTemplate(factory.getObject()).getMapper(OOrderItemMapper.class);
  var query=new OrderItemQuery(); query.setMerchantId(7L); query.setShopId(10L);
  var rows=mapper.selectPageVo(new Page<OrderItemListVo>(1,50),query).getRecords();
  assertEquals(1,rows.size()); assertEquals("1",rows.get(0).getId());
  query.setShopId(null); assertEquals(2,mapper.selectPageVo(new Page<OrderItemListVo>(1,50),query).getRecords().size());
  query.setMerchantId(8L); assertEquals(1,mapper.selectPageVo(new Page<OrderItemListVo>(1,50),query).getRecords().size());
 }
}


