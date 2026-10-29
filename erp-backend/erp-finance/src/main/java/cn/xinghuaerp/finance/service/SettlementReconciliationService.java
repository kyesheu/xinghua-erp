package cn.xinghuaerp.finance.service;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
@Service
@RequiredArgsConstructor
public class SettlementReconciliationService {
 private final JdbcTemplate jdbc;
 public record Difference(String settlementId,List<String> reasons) {}
 public record Page(int scanned,String nextAfterId,boolean hasMore,List<Difference> differences,String limitation) {}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
 public Page inspect(Scope scope,long afterId,int size) {
  if(afterId<0 || size<1 || size>200)throw new IllegalArgumentException("对账分页无效");
  if(scope==null || scope.type()==null)throw new org.springframework.security.access.AccessDeniedException("无结算对账范围");
  var args=new ArrayList<Object>();args.add(afterId);String where="id>?";
  switch(scope.type()) {
   case "HEADQUARTERS" -> {}
   case "MERCHANT","SHOP" -> {
    if(scope.merchantId()==null || scope.merchantId()<0 || (scope.type().equals("SHOP") && scope.shopId()==null))throw new IllegalArgumentException("主体范围无效");
    where+=" AND merchant_id=?";args.add(scope.merchantId());
    if(scope.type().equals("SHOP")){where+=" AND shop_id=?";args.add(scope.shopId());}
   }
   default -> throw new org.springframework.security.access.AccessDeniedException("无结算对账范围");
  }
  args.add(size+1);var rows=jdbc.queryForList("SELECT * FROM fms_order_settlement WHERE "+where+" ORDER BY id LIMIT ?",args.toArray());
  boolean more=rows.size()>size;if(more)rows.removeLast();var differences=new ArrayList<Difference>();
  if(!rows.isEmpty()) {
   var ids=rows.stream().map(row->row.get("id")).toArray();var sums=new HashMap<String,Map<String,Object>>();
   for(var row:jdbc.queryForList("SELECT settlement_id,SUM(amount) AS amount,SUM(CASE WHEN amount IS NULL THEN 1 ELSE 0 END) AS invalid FROM fms_order_settlement_item WHERE settlement_id IN ("+String.join(",",Collections.nCopies(ids.length,"?"))+") GROUP BY settlement_id",ids))sums.put(row.get("settlement_id").toString(),row);
   for(var row:rows) {
    var reasons=new ArrayList<String>();String id=row.get("id").toString();BigDecimal cost=money(row,"total_cost"),sales=money(row,"sales_amount"),profit=money(row,"profit");BigDecimal parts=BigDecimal.ZERO;
    for(String key:List.of("purchase_cost","shipping_fee","platform_fee","marketing_fee","other_fee")){var value=money(row,key);if(value==null){parts=null;break;}parts=parts.add(value);}
    if(cost==null || sales==null || profit==null || parts==null)reasons.add("UNKNOWN_AMOUNT");
    if(cost!=null && parts!=null && cost.compareTo(parts)!=0)reasons.add("COST_COMPONENT_MISMATCH");
    if(cost!=null && sales!=null && profit!=null && profit.compareTo(sales.subtract(cost))!=0)reasons.add("PROFIT_MISMATCH");
    var detail=sums.get(id);BigDecimal total=detail==null?BigDecimal.ZERO:money(detail,"amount");
    if(detail!=null && ((Number)detail.get("invalid")).longValue()>0)reasons.add("UNKNOWN_DETAIL_AMOUNT");
    else if(cost!=null && total!=null && cost.compareTo(total)!=0)reasons.add("DETAIL_TOTAL_MISMATCH");
    if(!reasons.isEmpty())differences.add(new Difference(id,List.copyOf(reasons)));
   }
  }
  return new Page(rows.size(),rows.isEmpty()?Long.toString(afterId):rows.getLast().get("id").toString(),more,List.copyOf(differences),"仅核对本页结算金额恒等式与成本明细；不自动修账，不包含订单关联、支付平台账单及孤立明细；跨页不是同一快照。");
 }
 private static BigDecimal money(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:new BigDecimal(value.toString());}
}
