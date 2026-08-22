package cn.xinghuaerp.report.service;

import cn.xinghuaerp.report.mapper.DashboardMapper;
import cn.xinghuaerp.report.vo.DashboardOverview;
import cn.xinghuaerp.report.vo.DashboardOverview.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class DashboardService {
    private final DashboardMapper mapper;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public DashboardService(DashboardMapper mapper) {this(mapper,Clock.systemDefaultZone());}
    public DashboardService(DashboardMapper mapper,Clock clock) {this.mapper=mapper;this.clock=clock;}

    /** 只读聚合，不拉平台、不请求模型。总部默认全部商户；其他账号的范围由 Controller 决定。 */
    @Transactional(readOnly=true)
    public DashboardOverview overview(int days,Long merchantId,Long shopId,String scopeLabel) {
        if(days!=7 && days!=30)throw new IllegalArgumentException("仅支持近7日或近30日");
        LocalDate endDate=LocalDate.now(clock),startDate=endDate.minusDays(days-1);
        LocalDateTime start=startDate.atStartOfDay(),end=endDate.plusDays(1).atStartOfDay();
        var sales=byDay(mapper.daily(start,end,merchantId,shopId));
        var refunds=byDay(mapper.refunds(start,end,merchantId,shopId));
        List<Daily> daily=new ArrayList<>();
        for(LocalDate date=startDate;!date.isAfter(endDate);date=date.plusDays(1)) {
            var row=sales.getOrDefault(date,Map.of());BigDecimal gross=money(row,"amount"),refund=money(refunds.getOrDefault(date,Map.of()),"amount");
            daily.add(new Daily(date,gross,refund,gross.subtract(refund),number(row,"orders")));
        }
        var statuses=mapper.statuses(start,end,merchantId,shopId).stream().map(r->new Slice((int)number(r,"code"),orderName((int)number(r,"code")),number(r,"value"))).toList();
        var afterSales=mapper.afterSales(start,end,merchantId,shopId).stream().map(r->new Slice((int)number(r,"code"),afterName((int)number(r,"code")),number(r,"value"))).toList();
        var warehouses=mapper.warehouses(merchantId,shopId).stream().map(r->new Warehouse(text(r,"id"),text(r,"name"),number(r,"quantity"),number(r,"available"),number(r,"locked"),number(r,"lowStockCount"))).toList();
        long orders=daily.stream().mapToLong(Daily::orderCount).sum(),afterCount=afterSales.stream().mapToLong(Slice::value).sum();
        BigDecimal gross=daily.stream().map(Daily::grossAmount).reduce(zero(),BigDecimal::add),refund=daily.stream().map(Daily::refundAmount).reduce(zero(),BigDecimal::add);
        var summary=new Summary(gross,refund,gross.subtract(refund),orders,afterCount,
                orders==0?zero():gross.divide(BigDecimal.valueOf(orders),2,RoundingMode.HALF_UP),
                warehouses.stream().mapToLong(Warehouse::quantity).sum(),warehouses.stream().mapToLong(Warehouse::available).sum(),warehouses.stream().mapToLong(Warehouse::lowStockCount).sum());
        var tasks=mapper.tasks(merchantId,shopId);
        return new DashboardOverview(startDate,endDate,LocalDateTime.now(clock),scopeLabel,merchantId,shopId,summary,daily,
                ranks(mapper.shops(start,end,merchantId,shopId)),ranks(mapper.products(start,end,merchantId,shopId)),statuses,afterSales,warehouses,
                new Tasks(number(tasks,"waitShip"),number(tasks,"waitInbound"),number(tasks,"waitOutbound"),number(tasks,"waitAfterSale")));
    }
    private static List<Rank> ranks(List<Map<String,Object>> rows) {return rows.stream().map(r->new Rank(text(r,"id"),text(r,"name"),text(r,"image"),money(r,"amount"),number(r,"quantity"))).toList();}
    private static Map<LocalDate,Map<String,Object>> byDay(List<Map<String,Object>> rows) {
        Map<LocalDate,Map<String,Object>> result=new HashMap<>();
        for(var row:rows)result.put(LocalDate.parse(text(row,"report_day").substring(0,10)),row);return result;
    }
    private static Object value(Map<String,Object> row,String name) {return row.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).filter(Objects::nonNull).findFirst().orElse(null);}
    private static long number(Map<String,Object> row,String name) {var value=value(row,name);return value==null?0:((Number)value).longValue();}
    private static String text(Map<String,Object> row,String name) {var value=value(row,name);return value==null?"":value.toString();}
    private static BigDecimal money(Map<String,Object> row,String name) {var value=value(row,name);return value==null?zero():new BigDecimal(value.toString()).setScale(2,RoundingMode.HALF_UP);}
    private static BigDecimal zero(){return new BigDecimal("0.00");}
    private static String orderName(int code) {return switch(code){case 1->"待发货";case 2->"已发货";case 3->"交易完成";case 11->"已取消/退款关闭";case 12->"退款处理中";case 13->"已关闭";case 22->"已锁定";case 31->"售后中";case 101->"部分发货";default->"其他订单";};}
    private static String afterName(int code) {return switch(code){case 1->"售前退款";case 10->"退货退款";case 11->"仅退款";case 20->"换货";case 80->"补发";default->"其他售后";};}
}
