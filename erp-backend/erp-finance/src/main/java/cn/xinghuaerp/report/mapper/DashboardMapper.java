package cn.xinghuaerp.report.mapper;

import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface DashboardMapper {
    List<Map<String,Object>> daily(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<Map<String,Object>> refunds(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<Map<String,Object>> shops(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<Map<String,Object>> products(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<Map<String,Object>> statuses(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<Map<String,Object>> afterSales(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<Map<String,Object>> warehouses(@Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    Map<String,Object> tasks(@Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
}
