package cn.xinghuaerp.report.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 数据库聚合的经营看板；金额均为元。 */
public record DashboardOverview(
        LocalDate startDate, LocalDate endDate, LocalDateTime updatedAt, String scopeLabel,
        Long merchantId, Long shopId, Summary summary, List<Daily> daily,
        List<Rank> shops, List<Rank> products, List<Slice> orderStatuses,
        List<Slice> afterSaleTypes, List<Warehouse> warehouses, Tasks tasks) {
    public record Summary(BigDecimal grossAmount, BigDecimal refundAmount, BigDecimal netAmount,
                          long orderCount, long afterSaleCount,
                          BigDecimal averageOrderAmount, long stockQuantity, long availableQuantity,
                          long lowStockCount) {}
    public record Daily(LocalDate date, BigDecimal grossAmount, BigDecimal refundAmount,
                        BigDecimal netAmount, long orderCount) {}
    public record Rank(String id, String name, String image, BigDecimal amount, long quantity) {}
    public record Slice(int code, String name, long value) {}
    public record Warehouse(String id, String name, long quantity, long available, long locked, long lowStockCount) {}
    public record Tasks(long waitShip, long waitInbound, long waitOutbound, long waitAfterSale) {}
}
