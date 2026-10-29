package cn.xinghuaerp.inventory.service;

import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 只读库存对账；异常仅提供定位依据，不以批次数量覆盖库存事实。 */
@Service
@RequiredArgsConstructor
public class InventoryReconciliationService {
    private final JdbcTemplate jdbc;
    public record Difference(String inventoryId, String skuId, Long quantity, Long locked, Long available,
                             Long batchQuantity, long batchCount, List<String> reasons) {}
    public record Page(LocalDateTime retrievedAt, int scanned, String nextAfterId, boolean hasMore,
                       List<Difference> differences, String limitation) {}

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=10)
    public Page inspect(Scope scope, long afterId, int size) {
        if(afterId<0 || size<1 || size>200)throw new IllegalArgumentException("对账分页无效，单页最多200条");
        if(scope==null || scope.type()==null)throw new AccessDeniedException("无库存对账范围");
        StringBuilder where=new StringBuilder(" WHERE COALESCE(is_delete,0)=0 AND id>?");
        var args=new ArrayList<Object>();args.add(afterId);
        switch(scope.type()) {
            case "HEADQUARTERS" -> { }
            case "MERCHANT" -> {
                if(scope.merchantId()==null || scope.merchantId()<=0)throw new AccessDeniedException("商户范围无效");
                where.append(" AND merchant_id=?");args.add(scope.merchantId());
            }
            case "SHOP" -> {
                if(scope.merchantId()==null || scope.merchantId()<0 || scope.shopId()==null || scope.shopId()<=0)
                    throw new AccessDeniedException("店铺范围无效");
                where.append(" AND merchant_id=? AND shop_id=?");args.add(scope.merchantId());args.add(scope.shopId());
            }
            case "WAREHOUSE" -> {
                if(scope.subjectId()==null || scope.subjectId()<=0)throw new AccessDeniedException("仓库范围无效");
                where.append(" AND warehouse_id=?");args.add(scope.subjectId());
            }
            default -> throw new AccessDeniedException("无库存对账范围");
        }
        args.add(size+1);
        var rows=jdbc.queryForList("SELECT id,sku_id,warehouse_id,merchant_id,shop_id,stock_status,quantity,locked_quantity,available_quantity FROM o_goods_inventory"+where+" ORDER BY id LIMIT ?",args.toArray());
        boolean more=rows.size()>size;if(more)rows.removeLast();
        var differences=new ArrayList<Difference>();
        if(!rows.isEmpty()) {
            var ids=rows.stream().map(row->row.get("id")).toArray();
            String placeholders=String.join(",",Collections.nCopies(ids.length,"?"));
            String matching="b.sku_id=i.sku_id AND b.warehouse_id=i.warehouse_id AND b.merchant_id=i.merchant_id AND b.shop_id=i.shop_id AND b.stock_status=i.stock_status";
            String sql="SELECT i.id,COUNT(b.id) AS batch_count,"
                    +"SUM(CASE WHEN "+matching+" THEN b.current_qty ELSE 0 END) AS batch_quantity,"
                    +"SUM(CASE WHEN b.id IS NULL OR ("+matching+") THEN 0 ELSE 1 END) AS foreign_batches,"
                    +"SUM(CASE WHEN b.id IS NOT NULL AND (b.current_qty IS NULL OR b.current_qty<0) THEN 1 ELSE 0 END) AS invalid_batches "
                    +"FROM o_goods_inventory i LEFT JOIN o_goods_inventory_batch b ON b.inventory_id=i.id WHERE i.id IN ("+placeholders+") GROUP BY i.id";
            var batches=new HashMap<Long,Map<String,Object>>();
            for(var batch:jdbc.queryForList(sql,ids))batches.put(number(batch,"id"),batch);
            for(var row:rows) {
                long id=number(row,"id");var batch=batches.get(id);
                Long quantity=number(row,"quantity"),locked=number(row,"locked_quantity"),available=number(row,"available_quantity");
                var reasons=new ArrayList<String>();
                if(quantity==null || locked==null || available==null || quantity<0 || locked<0 || available<0)reasons.add("INVALID_QUANTITY");
                if(quantity!=null && locked!=null && available!=null && quantity!=locked+available)reasons.add("BALANCE_MISMATCH");
                long invalid=number(batch,"invalid_batches"),foreign=number(batch,"foreign_batches");
                Long batchQuantity=invalid>0 || foreign>0 ? null : number(batch,"batch_quantity");
                if(invalid>0)reasons.add("INVALID_BATCH_QUANTITY");
                if(foreign>0)reasons.add("BATCH_SCOPE_MISMATCH");
                if(batchQuantity!=null && quantity!=null && !batchQuantity.equals(quantity))reasons.add("BATCH_TOTAL_MISMATCH");
                if(!reasons.isEmpty())differences.add(new Difference(Long.toString(id),String.valueOf(row.get("sku_id")),quantity,locked,available,batchQuantity,number(batch,"batch_count"),List.copyOf(reasons)));
            }
        }
        String next=rows.isEmpty()?Long.toString(afterId):String.valueOf(rows.getLast().get("id"));
        return new Page(LocalDateTime.now(),rows.size(),next,more,List.copyOf(differences),
                "仅核对本页有效库存与关联批次；无主库存的孤立批次、单据流水及财务对账不在本接口范围。跨页非同一快照，差异需结合单据复核，不自动修账。");
    }
    public record OrphanPage(int scanned,String nextAfterId,boolean hasMore,java.util.List<String> batchIds) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public OrphanPage orphanBatches(Scope scope,long afterId,int size) {
        if(afterId<0 || size<1 || size>200)throw new IllegalArgumentException("批次对账分页无效");
        if(scope==null || scope.type()==null)throw new AccessDeniedException("无批次对账范围");
        var args=new ArrayList<Object>();args.add(afterId);String where="b.id>?";
        switch(scope.type()) {
            case "HEADQUARTERS" -> {}
            case "MERCHANT","SHOP" -> {
                if(scope.merchantId()==null || scope.merchantId()<0 || (scope.type().equals("SHOP") && scope.shopId()==null))throw new AccessDeniedException("批次范围无效");
                where+=" AND b.merchant_id=?";args.add(scope.merchantId());
                if(scope.type().equals("SHOP")){where+=" AND b.shop_id=?";args.add(scope.shopId());}
            }
            case "WAREHOUSE" -> {
                if(scope.subjectId()==null || scope.subjectId()<=0)throw new AccessDeniedException("仓库范围无效");
                where+=" AND b.warehouse_id=?";args.add(scope.subjectId());
            }
            default -> throw new AccessDeniedException("无批次对账范围");
        }
        args.add(size+1);
        var rows=jdbc.queryForList("SELECT b.id,i.id AS linked_id,i.is_delete AS inventory_deleted FROM o_goods_inventory_batch b LEFT JOIN o_goods_inventory i ON i.id=b.inventory_id WHERE "+where+" ORDER BY b.id LIMIT ?",args.toArray());
        boolean more=rows.size()>size;if(more)rows.removeLast();var ids=new ArrayList<String>();
        for(var row:rows)if(row.get("linked_id")==null || (row.get("inventory_deleted") instanceof Number deleted && deleted.intValue()!=0))ids.add(row.get("id").toString());
        return new OrphanPage(rows.size(),rows.isEmpty()?Long.toString(afterId):rows.getLast().get("id").toString(),more,List.copyOf(ids));
    }
    private static Long number(Map<String,Object> row,String key) {
        Object value=row.get(key);return value==null?null:((Number)value).longValue();
    }
}
