package cn.xinghuaerp.finance.service.impl;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.finance.mapper.FmsExpenseItemMapper;
import cn.xinghuaerp.common.dto.ImportResult;
import cn.xinghuaerp.finance.entity.FmsExpense;
import cn.xinghuaerp.finance.entity.FmsExpenseItem;
import cn.xinghuaerp.finance.query.ExpenseApplicationQuery;
import cn.xinghuaerp.finance.mapper.FmsExpenseMapper;
import cn.xinghuaerp.finance.service.FmsExpenseService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 费用管理服务实现类
 * @author xinghua
 * @date 2026-04-20
 */
@Service
public class FmsExpenseServiceImpl extends ServiceImpl<FmsExpenseMapper, FmsExpense> implements FmsExpenseService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FmsExpenseServiceImpl.class);

    /** 导入判重时 IN 查询的单批上限，避免 SQL 过长。 */
    private static final int IMPORT_BATCH_SIZE = 500;

    @Autowired
    private FmsExpenseItemMapper expenseItemMapper;

    @Override
    public PageResult<FmsExpense> queryPageList(ExpenseApplicationQuery query, PageQuery pageQuery) {
        Page<FmsExpense> page = pageQuery.build();
        QueryWrapper<FmsExpense> wrapper = new QueryWrapper<>();
        if (query != null) {
            if (query.getMerchantId() != null) {
                wrapper.eq("merchant_id", query.getMerchantId());
            }
            if (query.getShopId() != null) {
                wrapper.eq("shop_id", query.getShopId());
            }
            if (query.getExpenseNo() != null) {
                wrapper.like("expense_no", query.getExpenseNo());
            }
            if (query.getExpenseType() != null) {
                wrapper.eq("expense_type", query.getExpenseType());
            }
            if (query.getStatus() != null) {
                wrapper.eq("status", query.getStatus());
            }
            if (query.getApplicant() != null) {
                wrapper.like("applicant", query.getApplicant());
            }
            if (query.getExpenseDateStart() != null) {
                wrapper.ge("expense_date", query.getExpenseDateStart());
            }
            if (query.getExpenseDateEnd() != null) {
                wrapper.le("expense_date", query.getExpenseDateEnd());
            }
        }
        wrapper.orderByDesc("created_time");
        page = baseMapper.selectPage(page, wrapper);
        return PageResult.build(page);
    }

    @Override
    @Transactional
    public boolean addExpense(FmsExpense expense, List<FmsExpenseItem> items) {
        // 生成申请单号
        expense.setExpenseNo("EXP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8));
        expense.setCreatedTime(LocalDateTime.now());
        expense.setSettlementStatus(0);
        expense.setOrderCount(items != null ? items.size() : 0);

        // 进销存费用默认已审核，支出报销默认待审批
        if (expense.getExpenseType() != null && expense.getExpenseType() >= 10) {
            expense.setStatus(3); // 已审批
        } else {
            expense.setStatus(2); // 待审批
        }

        boolean result = save(expense);

        if (result && items != null && !items.isEmpty()) {
            for (FmsExpenseItem item : items) {
                item.setExpenseId(expense.getId());
                item.setSettlementStatus(0);
                item.setCreateTime(LocalDateTime.now());
                expenseItemMapper.insert(item);
            }
        }

        return result;
    }

    @Override
    @Transactional
    public boolean updateExpense(FmsExpense expense, List<FmsExpenseItem> items) {
        expense.setUpdatedTime(LocalDateTime.now());
        expense.setOrderCount(items != null ? items.size() : 0);
        boolean result = updateById(expense);

        if (result && items != null && !items.isEmpty()) {
            // 删除旧的明细
            QueryWrapper<FmsExpenseItem> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("expense_id", expense.getId());
            expenseItemMapper.delete(deleteWrapper);

            // 插入新的明细
            for (FmsExpenseItem item : items) {
                item.setExpenseId(expense.getId());
                item.setSettlementStatus(0);
                item.setCreateTime(LocalDateTime.now());
                expenseItemMapper.insert(item);
            }
        }

        return result;
    }

    @Override
    @Transactional
    public boolean deleteExpense(Long id) {
        QueryWrapper<FmsExpenseItem> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("expense_id", id);
        expenseItemMapper.delete(deleteWrapper);
        return removeById(id);
    }

    @Override
    public boolean approveExpense(Long id, String approver) {
        FmsExpense expense = getById(id);
        if (expense != null && expense.getStatus() == 2) {
            expense.setStatus(3); // 已审批
            return updateById(expense);
        }
        return false;
    }

    @Override
    public boolean payExpense(Long id, String payer) {
        FmsExpense expense = getById(id);
        if (expense != null && expense.getStatus() == 3) {
            expense.setStatus(5); // 已支付
            expense.setPaidTime(LocalDateTime.now());
            return updateById(expense);
        }
        return false;
    }

    @Override
    public boolean closeExpense(Long id) {
        FmsExpense expense = getById(id);
        if (expense != null && (expense.getStatus() == 1 || expense.getStatus() == 2)) {
            expense.setStatus(4); // 已驳回
            return updateById(expense);
        }
        return false;
    }

    @Override
    public List<FmsExpenseItem> queryExpenseItems(Long expenseId) {
        QueryWrapper<FmsExpenseItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("expense_id", expenseId);
        queryWrapper.orderByDesc("create_time");
        return expenseItemMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional
    public ImportResult batchImportExpenses(List<FmsExpense> expenses, String operator) {
        int successCount = 0;
        int duplicateCount = 0;
        int failCount = 0;

        // 判重索引一次预加载，替代原来「每条明细查一次 + 每个主单再查一次」的逐条访问。
        // 导入过程中新写入的费用会回填到同一索引，保证批内去重语义与逐条查库时一致。
        Map<String, List<ExpenseRef>> existingByOrderNo = preloadExistingExpenseRefs(expenses);

        for (FmsExpense expense : expenses) {
            try {
                boolean isDuplicate = false;
                
                if (expense.getItems() != null && !expense.getItems().isEmpty()) {
                    for (FmsExpenseItem item : expense.getItems()) {
                        if (isDuplicateExpense(existingByOrderNo, expense.getExpenseType(), item.getOrderNo(), expense.getAmount())) {
                            isDuplicate = true;
                            break;
                        }
                    }
                }
                
                if (isDuplicate) {
                    duplicateCount++;
                    log.info("跳过重复费用: expenseType={}, orderNo={}, amount={}", 
                        expense.getExpenseType(), 
                        expense.getItems() != null && !expense.getItems().isEmpty() ? expense.getItems().get(0).getOrderNo() : "N/A",
                        expense.getAmount());
                    continue;
                }
                
                expense.setExpenseNo("EXP" + System.currentTimeMillis() + String.format("%04d", successCount));
                expense.setCreatedTime(LocalDateTime.now());
                expense.setCreatedBy(operator);
                expense.setSettlementStatus(0);
                expense.setOrderCount(expense.getItems() != null ? expense.getItems().size() : 0);
                expense.setStatus(3);

                if (save(expense)) {
                    if (expense.getItems() != null && !expense.getItems().isEmpty()) {
                        for (FmsExpenseItem item : expense.getItems()) {
                            item.setExpenseId(expense.getId());
                            item.setSettlementStatus(0);
                            item.setCreateTime(LocalDateTime.now());
                            expenseItemMapper.insert(item);
                        }
                    }
                    // 本批已写入的费用同样参与后续判重，否则同文件内重复行会被放过
                    indexExpense(existingByOrderNo, expense);
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("批量导入费用失败, expenseType={}", expense.getExpenseType(), e);
            }
        }
        
        log.info("批量导入完成: 成功={}, 重复跳过={}, 失败={}", successCount, duplicateCount, failCount);
        
        if (failCount > 0) {
            return ImportResult.fail(successCount, duplicateCount, failCount);
        }
        return ImportResult.success(successCount, duplicateCount);
    }
    
    /**
     * 预加载本批涉及订单号的历史费用索引。
     * 原实现对每个订单号先查全部明细、再对每条明细单独取主单，最坏是 订单号数 × 明细数 × 主单数 次查询；
     * 这里改为按订单号、主单ID各做一批 IN 查询，查询次数与数据量无关。
     */
    private Map<String, List<ExpenseRef>> preloadExistingExpenseRefs(List<FmsExpense> expenses) {
        Map<String, List<ExpenseRef>> index = new HashMap<>();
        if (expenses == null || expenses.isEmpty()) {
            return index;
        }

        Set<String> orderNos = new LinkedHashSet<>();
        for (FmsExpense expense : expenses) {
            if (expense == null || expense.getItems() == null) {
                continue;
            }
            for (FmsExpenseItem item : expense.getItems()) {
                if (item != null && item.getOrderNo() != null && !item.getOrderNo().isEmpty()) {
                    orderNos.add(item.getOrderNo());
                }
            }
        }
        if (orderNos.isEmpty()) {
            return index;
        }

        List<String> orderNoList = new ArrayList<>(orderNos);
        for (int start = 0; start < orderNoList.size(); start += IMPORT_BATCH_SIZE) {
            List<String> chunk = orderNoList.subList(start, Math.min(start + IMPORT_BATCH_SIZE, orderNoList.size()));
            List<FmsExpenseItem> items = expenseItemMapper.selectList(
                    new QueryWrapper<FmsExpenseItem>().in("order_no", chunk));
            if (items == null || items.isEmpty()) {
                continue;
            }

            Set<Long> expenseIds = new LinkedHashSet<>();
            for (FmsExpenseItem item : items) {
                if (item != null && item.getExpenseId() != null) {
                    expenseIds.add(item.getExpenseId());
                }
            }
            Map<Long, FmsExpense> expenseById = loadExpensesByIds(expenseIds);

            for (FmsExpenseItem item : items) {
                if (item == null) {
                    continue;
                }
                FmsExpense owner = expenseById.get(item.getExpenseId());
                if (owner == null) {
                    continue;
                }
                index.computeIfAbsent(item.getOrderNo(), key -> new ArrayList<>())
                        .add(new ExpenseRef(owner.getExpenseType(), owner.getAmount()));
            }
        }
        return index;
    }

    private Map<Long, FmsExpense> loadExpensesByIds(Set<Long> expenseIds) {
        Map<Long, FmsExpense> result = new HashMap<>();
        if (expenseIds == null || expenseIds.isEmpty()) {
            return result;
        }
        List<Long> idList = new ArrayList<>(expenseIds);
        for (int start = 0; start < idList.size(); start += IMPORT_BATCH_SIZE) {
            List<Long> chunk = idList.subList(start, Math.min(start + IMPORT_BATCH_SIZE, idList.size()));
            List<FmsExpense> found = baseMapper.selectList(new QueryWrapper<FmsExpense>().in("id", chunk));
            if (found == null) {
                continue;
            }
            for (FmsExpense expense : found) {
                result.put(expense.getId(), expense);
            }
        }
        return result;
    }

    private void indexExpense(Map<String, List<ExpenseRef>> index, FmsExpense expense) {
        if (expense.getItems() == null || expense.getItems().isEmpty()) {
            return;
        }
        for (FmsExpenseItem item : expense.getItems()) {
            if (item == null || item.getOrderNo() == null || item.getOrderNo().isEmpty()) {
                continue;
            }
            index.computeIfAbsent(item.getOrderNo(), key -> new ArrayList<>())
                    .add(new ExpenseRef(expense.getExpenseType(), expense.getAmount()));
        }
    }

    private boolean isDuplicateExpense(Map<String, List<ExpenseRef>> index, Integer expenseType,
                                       String orderNo, BigDecimal amount) {
        if (orderNo == null || orderNo.isEmpty()) {
            return false;
        }
        List<ExpenseRef> refs = index.get(orderNo);
        if (refs == null || refs.isEmpty()) {
            return false;
        }
        for (ExpenseRef ref : refs) {
            // 类型或金额缺失时原实现会抛 NPE 被记成导入失败，这里按「无法比较即不判重」处理
            if (ref.expenseType() != null && ref.amount() != null && amount != null
                    && ref.expenseType().equals(expenseType)
                    && ref.amount().compareTo(amount) == 0) {
                return true;
            }
        }
        return false;
    }

    /** 判重只需要费用类型与金额，不持有整个主单实体。 */
    private record ExpenseRef(Integer expenseType, BigDecimal amount) {
    }
}
