package cn.xinghuaerp.inventory.mapper;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Update;
import java.sql.DriverManager;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryAtomicUpdateTest {
    @Test
    void concurrentDeductionsNeverOversellOrLoseUpdates() throws Exception {
        String url = "jdbc:h2:mem:stock_atomic;DB_CLOSE_DELAY=-1;MODE=MySQL";
        try (var conn = DriverManager.getConnection(url); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE o_goods_inventory(id bigint primary key, quantity int, available_quantity int, locked_quantity int, is_delete int)");
            stmt.execute("INSERT INTO o_goods_inventory VALUES(1,10,10,0,0)");
        }
        String sql = OGoodsInventoryMapper.class.getMethod("adjustStock", Long.class, long.class)
                .getAnnotation(Update.class).value()[0].replace("#{delta}", "-1").replace("#{id}", "1");
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = new java.util.ArrayList<Future<Integer>>();
            for (int i=0;i<40;i++) jobs.add(pool.submit(() -> {
                try (var conn = DriverManager.getConnection(url); var stmt = conn.createStatement()) {
                    return stmt.executeUpdate(sql);
                }
            }));
            int successful = 0;
            for (var job : jobs) successful += job.get();
            assertEquals(10, successful);
        }
        try (var conn = DriverManager.getConnection(url); var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT quantity,available_quantity FROM o_goods_inventory WHERE id=1");
            assertTrue(rs.next()); assertEquals(0,rs.getInt(1)); assertEquals(0,rs.getInt(2));
            stmt.executeUpdate("UPDATE o_goods_inventory SET quantity=10,available_quantity=10");
            String lock = OGoodsInventoryMapper.class.getMethod("adjustLock", Long.class, long.class)
                    .getAnnotation(Update.class).value()[0].replace("#{id}", "1");
            assertEquals(1, stmt.executeUpdate(lock.replace("#{delta}", "10")));
            assertEquals(0, stmt.executeUpdate(lock.replace("#{delta}", "1")));
            assertEquals(0, stmt.executeUpdate(lock.replace("#{delta}", "(-11)")));
            assertEquals(1, stmt.executeUpdate(lock.replace("#{delta}", "(-10)")));
        }
    }
}
