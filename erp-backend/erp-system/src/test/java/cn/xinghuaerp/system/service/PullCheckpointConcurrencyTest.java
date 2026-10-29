package cn.xinghuaerp.system.service;
import cn.xinghuaerp.system.entity.*;
import cn.xinghuaerp.system.mapper.*;
import cn.xinghuaerp.system.service.impl.OShopPullLasttimeServiceImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PullCheckpointConcurrencyTest {
 @Test void firstCreationAndOlderWritersSerializeOnShopRow() throws Exception {
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:checkpoint_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE shop_probe(id BIGINT PRIMARY KEY)");db.update("INSERT INTO shop_probe VALUES(1)");
  db.execute("CREATE TABLE checkpoint_probe(id BIGINT AUTO_INCREMENT PRIMARY KEY,lasttime TIMESTAMP)");
  var mapper=mock(OShopPullLasttimeMapper.class);var shops=mock(OShopMapper.class);
  when(shops.selectOne(any())).thenAnswer(call->{db.queryForObject("SELECT id FROM shop_probe WHERE id=1 FOR UPDATE",Long.class);return new OShop();});
  when(mapper.selectList(any())).thenAnswer(call->db.query("SELECT id,lasttime FROM checkpoint_probe ORDER BY lasttime DESC,id DESC LIMIT 1",(rs,n)->{var row=new OShopPullLasttime();row.setId(rs.getString(1));row.setLasttime(rs.getTimestamp(2).toLocalDateTime());return row;}));
  when(mapper.insert(any(OShopPullLasttime.class))).thenAnswer(call->{OShopPullLasttime row=call.getArgument(0);return db.update("INSERT INTO checkpoint_probe(lasttime) VALUES(?)",row.getLasttime());});
  when(mapper.updateById(any(OShopPullLasttime.class))).thenAnswer(call->{OShopPullLasttime row=call.getArgument(0);return db.update("UPDATE checkpoint_probe SET lasttime=? WHERE id=?",row.getLasttime(),row.getId());});
  var service=new OShopPullLasttimeServiceImpl(mapper,shops);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  var start=new java.util.concurrent.CountDownLatch(1);var base=java.time.LocalDateTime.of(2026,1,1,0,0);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(4)) {
   var jobs=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
   for(int i=0;i<4;i++){int step=i;jobs.add(pool.submit(()->{start.await();return tx.execute(status->service.advance(1L,"ORDER",base.plusHours(step)));}));}
   start.countDown();for(var job:jobs)assertTrue(job.get(10,java.util.concurrent.TimeUnit.SECONDS));
  }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM checkpoint_probe",Integer.class));
  assertEquals(base.plusHours(3),db.queryForObject("SELECT lasttime FROM checkpoint_probe",java.time.LocalDateTime.class));db.execute("SHUTDOWN");
 }
}
