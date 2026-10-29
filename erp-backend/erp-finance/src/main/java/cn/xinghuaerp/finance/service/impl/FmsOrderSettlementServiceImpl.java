package cn.xinghuaerp.finance.service.impl;

import cn.xinghuaerp.finance.entity.FmsExpense;
import cn.xinghuaerp.finance.entity.FmsExpenseItem;
import cn.xinghuaerp.finance.entity.FmsOrderSettlement;
import cn.xinghuaerp.finance.entity.FmsOrderSettlementItem;
import cn.xinghuaerp.inventory.entity.OOrderStocking;
import cn.xinghuaerp.inventory.entity.OOrderStockingItemBatch;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.finance.mapper.FmsOrderSettlementItemMapper;
import cn.xinghuaerp.finance.mapper.FmsOrderSettlementMapper;
import cn.xinghuaerp.finance.query.OrderSettlementQuery;
import cn.xinghuaerp.finance.service.FmsOrderSettlementService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.time.LocalDateTime;
import cn.xinghuaerp.finance.mapper.FmsExpenseItemMapper;
import cn.xinghuaerp.finance.mapper.FmsExpenseMapper;




@Service
public class FmsOrderSettlementServiceImpl extends ServiceImpl<FmsOrderSettlementMapper, FmsOrderSettlement> implements FmsOrderSettlementService {

    @Autowired
    private FmsOrderSettlementItemMapper settlementItemMapper;

    @Autowired
    private cn.xinghuaerp.order.facade.OrderFacade orderMapper;

    @Autowired
    private cn.xinghuaerp.inventory.facade.InventoryFacade orderStockingMapper;

    @Autowired
    private cn.xinghuaerp.inventory.facade.InventoryFacade itemBatchMapper;

    @Autowired
    private cn.xinghuaerp.finance.mapper.FmsExpenseItemMapper expenseItemMapper;

    @Autowired
    private cn.xinghuaerp.finance.mapper.FmsExpenseMapper expenseMapper;

    @Override
    public PageResult<FmsOrderSettlement> queryPageList(OrderSettlementQuery query, PageQuery pageQuery) {
        Page<FmsOrderSettlement> page = pageQuery.build();
        QueryWrapper<FmsOrderSettlement> wrapper = new QueryWrapper<>();
        if (query != null) {
            if (query.getMerchantId() != null) {
                wrapper.eq("merchant_id", query.getMerchantId());
            }
            if (query.getShopId() != null) {
                wrapper.eq("shop_id", query.getShopId());
            }
            if (query.getOrderNo() != null) {
                wrapper.like("order_no", query.getOrderNo());
            }
            if (query.getStatus() != null) {
                wrapper.eq("status", query.getStatus());
            }
        }
        wrapper.orderByDesc("create_time");
        page = baseMapper.selectPage(page, wrapper);
        return PageResult.build(page);
    }

    @Override
    public List<OOrder> getUnsettledOrders(OrderSettlementQuery query, Page<OOrder> page) {
        QueryWrapper<OOrder> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("settlement_status", 0).or().isNull("settlement_status"));
        if (query != null) {
            if (query.getMerchantId() != null) {
                wrapper.eq("merchant_id", query.getMerchantId());
            }
            if (query.getShopId() != null) {
                wrapper.eq("shop_id", query.getShopId());
            }
            if (query.getOrderNo() != null) {
                wrapper.like("order_num", query.getOrderNo());
            }
        }
        wrapper.orderByDesc("create_time");
        orderMapper.pageOOrder(page, wrapper);
        List<OOrder> orders = page.getRecords();
        for (OOrder order : orders) {
            order.setRemark(getPlatformName(order.getShopType()));
        }
        return orders;
    }

    @Override
    public List<FmsOrderSettlement> getSettledOrders(OrderSettlementQuery query, Page<FmsOrderSettlement> page) {
        QueryWrapper<FmsOrderSettlement> wrapper = new QueryWrapper<>();
        if (query != null) {
            if (query.getMerchantId() != null) {
                wrapper.eq("merchant_id", query.getMerchantId());
            }
            if (query.getShopId() != null) {
                wrapper.eq("shop_id", query.getShopId());
            }
            if (query.getOrderNo() != null) {
                wrapper.like("order_no", query.getOrderNo());
            }
        }
        wrapper.orderByDesc("create_time");
        baseMapper.selectPage(page, wrapper);
        return page.getRecords();
    }

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Override
    public Map<String,Object> autoSettlement(List<String> orderIds,String operator) {
        return autoSettlement(orderIds,operator,new cn.xinghuaerp.system.service.SalesScope(null,null));
    }
    @Override
    public Map<String,Object> autoSettlement(List<String> orderIds,String operator,cn.xinghuaerp.system.service.SalesScope scope) {
        Objects.requireNonNull(scope,"结算范围缺失");
        if(orderIds==null || orderIds.isEmpty() || orderIds.size()>200)throw new IllegalArgumentException("单批结算订单数量须为1至200");
        int success=0;var failures=new ArrayList<String>();
        for(String id:orderIds) {
            try { settleOne(id,operator,scope);success++; }
            catch(RuntimeException failure){failures.add("订单 "+id+" 结算失败，请核查订单状态及费用资料");}
        }
        var result=new HashMap<String,Object>();result.put("successCount",success);result.put("failCount",failures.size());result.put("failReasons",failures);return result;
    }

    @Override
    public Map<String,Object> manualSettlement(String orderId,String settlementId,String operator) {
        var result=new HashMap<String,Object>();
        try {var settlement=settleOne(orderId,operator,new cn.xinghuaerp.system.service.SalesScope(null,null));result.put("success",true);result.put("settlementId",settlement.getId());}
        catch(RuntimeException failure){result.put("success",false);result.put("message","结算未完成，请核查订单状态及费用资料");}
        return result;
    }

    private FmsOrderSettlement settleOne(String orderId,String operator,cn.xinghuaerp.system.service.SalesScope scope) {
        if(orderId==null || !orderId.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("订单标识无效");
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status->{
            OOrder order=orderMapper.getOOrderOne(new QueryWrapper<OOrder>().eq("id",orderId).last("FOR UPDATE"));
            if(order==null || (order.getSettlementStatus()!=null && order.getSettlementStatus()!=0))
                throw new IllegalStateException("订单不存在或已经结算");
            scope.check(order.getMerchantId(),order.getShopId());
            var settlement=calculateOrderSettlement(orderId);
            if(settlement==null)throw new IllegalStateException("订单资料不可用");
            settlement.setCreateBy(operator);settlement.setCreateTime(LocalDateTime.now());settlement.setVersion(1);settlement.setStatus(1);
            requireWrite(baseMapper.insert(settlement));
            saveSettlementItems(settlement);
            updateOrderSettlementStatus(orderId,settlement.getId());
            updateExpenseItemSettlementStatus(Long.valueOf(orderId),settlement.getId());
            return settlement;
        });
    }
    private static void requireWrite(int affected) {
        if(affected!=1)throw new IllegalStateException("结算写入失败");
    }

    @Override
    @Transactional
    public boolean cancelSettlement(Long id, String operator) {
        FmsOrderSettlement snapshot=baseMapper.selectById(id);
        if(snapshot==null)return false;
        // 与创建结算一致：先锁订单，再锁结算单，避免取消与重结算交错。
        OOrder order=orderMapper.getOOrderOne(new QueryWrapper<OOrder>().eq("id",snapshot.getOrderId()).last("FOR UPDATE"));
        FmsOrderSettlement settlement=baseMapper.selectOne(new QueryWrapper<FmsOrderSettlement>().eq("id",id).last("FOR UPDATE"));
        if(settlement==null)return false;
        if(order==null || !Objects.equals(order.getSettlementId(),id))throw new IllegalStateException("订单与结算关联不一致，请先核对");
        settlementItemMapper.delete(new QueryWrapper<FmsOrderSettlementItem>().eq("settlement_id",id));
        var affectedExpenses=expenseItemMapper.selectList(new QueryWrapper<FmsExpenseItem>().eq("settlement_id",id)).stream()
                .map(FmsExpenseItem::getExpenseId).filter(Objects::nonNull).distinct().sorted().toList();
        // 显式 SET NULL，不能依赖 updateById 的非空字段更新策略。
        expenseItemMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FmsExpenseItem>()
                .eq("settlement_id",id).set("settlement_status",0).set("settlement_id",null).set("settlement_time",null));
        for(Long expenseId:affectedExpenses) {
            requireWrite(expenseMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FmsExpense>()
                    .eq("id",expenseId).set("settlement_status",0).set("settlement_id",null).set("settlement_time",null)));
        }
        requireWrite(orderMapper.clearOrderSettlement(settlement.getOrderId(),id));
        requireWrite(baseMapper.deleteById(id));
        return true;
    }

    @Override
    public FmsOrderSettlement getSettlementDetail(Long id) {
        FmsOrderSettlement settlement = baseMapper.selectById(id);
        if (settlement != null) {
            QueryWrapper<FmsOrderSettlementItem> wrapper = new QueryWrapper<>();
            wrapper.eq("settlement_id", id);
            List<FmsOrderSettlementItem> items = settlementItemMapper.selectList(wrapper);
            settlement.setItems(items);
        }
        return settlement;
    }

    @Override
    public FmsOrderSettlement calculateOrderSettlement(String orderId) {
        OOrder order = orderMapper.getOOrderById(orderId);
        if (order == null) {
            return null;
        }

        FmsOrderSettlement settlement = new FmsOrderSettlement();
        settlement.setOrderId(Long.valueOf(orderId));
        settlement.setOrderNo(order.getOrderNum());
        settlement.setMerchantId(order.getMerchantId());
        settlement.setShopId(order.getShopId());

        if(order.getAmount()==null)throw new IllegalStateException("订单销售金额缺失，不能默认零结算");
        settlement.setSalesAmount(BigDecimal.valueOf(order.getAmount()));

        BigDecimal purchaseCost = calculatePurchaseCost(orderId);
        settlement.setPurchaseCost(purchaseCost);

        BigDecimal shippingFee = calculateShippingFee(orderId);
        settlement.setShippingFee(shippingFee);

        Map<Integer, BigDecimal> expenseMap = calculateExpenses(orderId);
        BigDecimal platformFee = expenseMap.getOrDefault(10, BigDecimal.ZERO);
        BigDecimal marketingFee = expenseMap.getOrDefault(11, BigDecimal.ZERO);
        BigDecimal otherFee = expenseMap.getOrDefault(12, BigDecimal.ZERO)
                .add(expenseMap.getOrDefault(13, BigDecimal.ZERO))
                .add(expenseMap.getOrDefault(14, BigDecimal.ZERO))
                .add(expenseMap.getOrDefault(15, BigDecimal.ZERO))
                .add(expenseMap.getOrDefault(16, BigDecimal.ZERO))
                .add(expenseMap.getOrDefault(99, BigDecimal.ZERO));

        settlement.setPlatformFee(platformFee);
        settlement.setMarketingFee(marketingFee);
        settlement.setOtherFee(otherFee);

        BigDecimal totalCost = purchaseCost.add(shippingFee).add(platformFee).add(marketingFee).add(otherFee);
        settlement.setTotalCost(totalCost);

        BigDecimal profit = settlement.getSalesAmount().subtract(totalCost);
        settlement.setProfit(profit);

        if (settlement.getSalesAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitRate = profit.divide(settlement.getSalesAmount(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            settlement.setProfitRate(profitRate);
        } else {
            settlement.setProfitRate(BigDecimal.ZERO);
        }

        settlement.setSettlementNo("STL" + System.currentTimeMillis());

        return settlement;
    }

    private BigDecimal calculatePurchaseCost(String orderId) {
        QueryWrapper<OOrderStockingItemBatch> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId);
        List<OOrderStockingItemBatch> batches = itemBatchMapper.listStockingBatches(wrapper);

        BigDecimal totalCost = BigDecimal.ZERO;
        for (OOrderStockingItemBatch batch : batches) {
            if (batch.getTotalCost() != null) {
                totalCost = totalCost.add(batch.getTotalCost());
            } else if (batch.getUnitCost() != null && batch.getQuantity() != null) {
                totalCost = totalCost.add(batch.getUnitCost().multiply(BigDecimal.valueOf(batch.getQuantity())));
            } else if (batch.getPurPrice() != null && batch.getQuantity() != null) {
                totalCost = totalCost.add(batch.getPurPrice().multiply(BigDecimal.valueOf(batch.getQuantity())));
            } else throw new IllegalStateException("出库批次成本资料缺失，请核对后结算");
        }
        return totalCost;
    }

    private BigDecimal calculateShippingFee(String orderId) {
        QueryWrapper<OOrderStocking> wrapper = new QueryWrapper<>();
        wrapper.eq("o_order_id", Long.valueOf(orderId));
        List<OOrderStocking> stockings = orderStockingMapper.listOOrderStocking(wrapper);

        BigDecimal totalShippingFee = BigDecimal.ZERO;
        for (OOrderStocking stocking : stockings) {
            if(!Integer.valueOf(0).equals(stocking.getType()))throw new IllegalStateException("非本地履约采购成本口径未完整提供，不能自动结算");
            if(stocking.getShippingCost()==null)throw new IllegalStateException("发货运费资料缺失，请明确金额后结算");
            totalShippingFee = totalShippingFee.add(stocking.getShippingCost());
        }
        return totalShippingFee;
    }

    private Map<Integer, BigDecimal> calculateExpenses(String orderId) {
        QueryWrapper<FmsExpenseItem> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", Long.valueOf(orderId));
        wrapper.eq("settlement_status", 0);
        wrapper.last(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive() ? "LIMIT 10001 FOR UPDATE" : "LIMIT 10001");
        List<FmsExpenseItem> items = expenseItemMapper.selectList(wrapper);

        if(items.size()>10000)throw new IllegalStateException("单订单费用明细超过结算处理上限，请先核对");
        Map<Integer, BigDecimal> expenseMap = new HashMap<>();
        if(items.isEmpty())return expenseMap;
        var expenseIds=items.stream().map(FmsExpenseItem::getExpenseId).filter(Objects::nonNull).distinct().toList();
        var headers=new HashMap<Long,FmsExpense>();
        for(int start=0;start<expenseIds.size();start+=500) {
            var query=new QueryWrapper<FmsExpense>().in("id",expenseIds.subList(start,Math.min(start+500,expenseIds.size()))).orderByAsc("id");
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())query.last("FOR UPDATE");
            for(var expense:expenseMapper.selectList(query))headers.put(expense.getId(),expense);
        }
        for(FmsExpenseItem item:items) {
            var expense=headers.get(item.getExpenseId());
            if(expense!=null && Integer.valueOf(5).equals(expense.getStatus())) {
                if(expense.getExpenseType()==null || item.getAmount()==null)throw new IllegalStateException("费用类型或金额缺失，请先核对");
                expenseMap.merge(expense.getExpenseType(),item.getAmount(),BigDecimal::add);
            }
        }
        return expenseMap;
    }

    private void saveSettlementItems(FmsOrderSettlement settlement) {
        List<FmsOrderSettlementItem> items = new ArrayList<>();

        if (settlement.getPurchaseCost().compareTo(BigDecimal.ZERO) != 0) {
            FmsOrderSettlementItem item = new FmsOrderSettlementItem();
            item.setSettlementId(settlement.getId());
            item.setItemType(1);
            item.setItemName("商品采购成本");
            item.setAmount(settlement.getPurchaseCost());
            item.setCreateTime(LocalDateTime.now());
            items.add(item);
        }

        if (settlement.getShippingFee().compareTo(BigDecimal.ZERO) != 0) {
            FmsOrderSettlementItem item = new FmsOrderSettlementItem();
            item.setSettlementId(settlement.getId());
            item.setItemType(2);
            item.setItemName("发货费用");
            item.setAmount(settlement.getShippingFee());
            item.setCreateTime(LocalDateTime.now());
            items.add(item);
        }

        if (settlement.getPlatformFee().compareTo(BigDecimal.ZERO) != 0) {
            FmsOrderSettlementItem item = new FmsOrderSettlementItem();
            item.setSettlementId(settlement.getId());
            item.setItemType(4);
            item.setItemName("平台扣点");
            item.setAmount(settlement.getPlatformFee());
            item.setCreateTime(LocalDateTime.now());
            items.add(item);
        }

        if (settlement.getMarketingFee().compareTo(BigDecimal.ZERO) != 0) {
            FmsOrderSettlementItem item = new FmsOrderSettlementItem();
            item.setSettlementId(settlement.getId());
            item.setItemType(5);
            item.setItemName("营销费用");
            item.setAmount(settlement.getMarketingFee());
            item.setCreateTime(LocalDateTime.now());
            items.add(item);
        }

        if (settlement.getOtherFee().compareTo(BigDecimal.ZERO) != 0) {
            FmsOrderSettlementItem item = new FmsOrderSettlementItem();
            item.setSettlementId(settlement.getId());
            item.setItemType(99);
            item.setItemName("其他费用");
            item.setAmount(settlement.getOtherFee());
            item.setCreateTime(LocalDateTime.now());
            items.add(item);
        }

        for (FmsOrderSettlementItem item : items) {
            requireWrite(settlementItemMapper.insert(item));
        }
    }

    private void updateOrderSettlementStatus(String orderId, Long settlementId) {
        OOrder order = orderMapper.getOOrderById(orderId);
        if (order != null) {
            order.setSettlementStatus(1);
            order.setSettlementId(settlementId);
            order.setSettlementTime(LocalDateTime.now());
            requireWrite(orderMapper.updateOOrder(order));
        }
    }

    private void updateExpenseItemSettlementStatus(Long orderId, Long settlementId) {
        QueryWrapper<FmsExpenseItem> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId);
        wrapper.eq("settlement_status", 0);
        wrapper.last("LIMIT 10001");
        List<FmsExpenseItem> candidates = expenseItemMapper.selectList(wrapper);
        if(candidates.size()>10000)throw new IllegalStateException("费用明细超过结算处理上限");
        var approved=new HashMap<Long,FmsExpense>();
        var ids=candidates.stream().map(FmsExpenseItem::getExpenseId).filter(Objects::nonNull).distinct().sorted().toList();
        for(int start=0;start<ids.size();start+=500)
            for(var expense:expenseMapper.selectList(new QueryWrapper<FmsExpense>().in("id",ids.subList(start,Math.min(start+500,ids.size()))).orderByAsc("id").last("FOR UPDATE")))
                if(Integer.valueOf(5).equals(expense.getStatus()))approved.put(expense.getId(),expense);
        List<FmsExpenseItem> items=candidates.stream().filter(item->approved.containsKey(item.getExpenseId())).toList();

        for (FmsExpenseItem item : items) {
            item.setSettlementStatus(1);
            item.setSettlementId(settlementId);
            item.setSettlementTime(LocalDateTime.now());
            requireWrite(expenseItemMapper.updateById(item));
        }

        if (!items.isEmpty()) {
            Set<Long> expenseIds = new HashSet<>();
            for (FmsExpenseItem item : items) {
                expenseIds.add(item.getExpenseId());
            }
            for (Long expenseId : expenseIds) {
                FmsExpense expense = expenseMapper.selectById(expenseId);
                if (expense != null) {
                    QueryWrapper<FmsExpenseItem> checkWrapper = new QueryWrapper<>();
                    checkWrapper.eq("expense_id", expenseId);
                    checkWrapper.eq("settlement_status", 0);
                    long unsettledCount = expenseItemMapper.selectCount(checkWrapper);
                    if (unsettledCount == 0) {
                        expense.setSettlementStatus(1);
                        expense.setSettlementId(settlementId);
                        expense.setSettlementTime(LocalDateTime.now());
                        requireWrite(expenseMapper.updateById(expense));
                    }
                }
            }
        }
    }

    private String getPlatformName(Integer shopType) {
        if (shopType == null) return "未知";
        switch (shopType) {
            case 1: return "淘宝";
            case 2: return "拼多多";
            case 3: return "抖音";
            case 4: return "京东";
            default: return "未知";
        }
    }
}
