package cn.xinghuaerp.finance.service;

import cn.xinghuaerp.common.dto.ImportResult;
import cn.xinghuaerp.finance.entity.FmsExpense;
import cn.xinghuaerp.finance.entity.FmsExpenseItem;
import cn.xinghuaerp.finance.mapper.FmsExpenseItemMapper;
import cn.xinghuaerp.finance.mapper.FmsExpenseMapper;
import cn.xinghuaerp.finance.service.impl.FmsExpenseServiceImpl;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 平台账单批量导入的判重查询规模与语义。
 * 原实现按「每条明细查一次明细表 + 每条明细再查一次主单」访问数据库，最坏是
 * 订单号数 × 明细数 × 主单数 次查询；这里同时约束查询次数与判重结果，
 * 防止为了性能把「同文件内重复行也要跳过」的语义改掉。
 */
class ExpenseImportBatchQueryTest {

    FmsExpenseMapper expenseMapper;
    FmsExpenseItemMapper itemMapper;
    FmsExpenseServiceImpl service;

    @BeforeEach
    void setup() throws Exception {
        expenseMapper = mock(FmsExpenseMapper.class);
        itemMapper = mock(FmsExpenseItemMapper.class);
        service = new FmsExpenseServiceImpl();
        inject(service, "baseMapper", expenseMapper);
        inject(service, "expenseItemMapper", itemMapper);
        when(expenseMapper.insert(any(FmsExpense.class))).thenReturn(1);
        when(itemMapper.insert(any(FmsExpenseItem.class))).thenReturn(1);
    }

    @Test
    void duplicateLookupDoesNotScaleWithItemCount() {
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(expenseMapper.selectList(any())).thenReturn(List.of());

        ImportResult result = service.batchImportExpenses(batch(50, 10), "tester");

        assertEquals(50, result.getSuccessCount());
        assertEquals(0, result.getDuplicateCount());
        // 500 条明细只做一次 IN 查询；没有历史明细时连主单表都不用查
        verify(itemMapper, times(1)).selectList(any());
        verify(expenseMapper, times(0)).selectList(any());
    }

    @Test
    void historyWithSameTypeAndAmountIsSkipped() {
        FmsExpenseItem historyItem = new FmsExpenseItem();
        historyItem.setExpenseId(900L);
        historyItem.setOrderNo("O-0-0");
        when(itemMapper.selectList(any())).thenReturn(List.of(historyItem));

        FmsExpense historyExpense = new FmsExpense();
        historyExpense.setId(900L);
        historyExpense.setExpenseType(1);
        historyExpense.setAmount(new BigDecimal("100.00"));
        when(expenseMapper.selectList(any())).thenReturn(List.of(historyExpense));

        ImportResult result = service.batchImportExpenses(batch(50, 10), "tester");

        assertEquals(49, result.getSuccessCount());
        assertEquals(1, result.getDuplicateCount());
        // 主单只按去重后的 ID 批量查一次，不是每条明细一次
        verify(expenseMapper, times(1)).selectList(any());
    }

    @Test
    void historyWithDifferentAmountIsNotTreatedAsDuplicate() {
        FmsExpenseItem historyItem = new FmsExpenseItem();
        historyItem.setExpenseId(900L);
        historyItem.setOrderNo("O-0-0");
        when(itemMapper.selectList(any())).thenReturn(List.of(historyItem));

        FmsExpense historyExpense = new FmsExpense();
        historyExpense.setId(900L);
        historyExpense.setExpenseType(1);
        historyExpense.setAmount(new BigDecimal("999.00"));
        when(expenseMapper.selectList(any())).thenReturn(List.of(historyExpense));

        ImportResult result = service.batchImportExpenses(batch(2, 1), "tester");

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getDuplicateCount());
    }

    /**
     * 原实现每条都实时查库，因此同一个文件里先写入的费用会被后一条的判重看到。
     * 改成预加载索引后必须保留这个语义，否则同一份账单里的重复行会被放过。
     */
    @Test
    void duplicateInsideTheSameBatchIsStillSkipped() {
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(expenseMapper.selectList(any())).thenReturn(List.of());

        FmsExpense first = single("O-SAME", 1, new BigDecimal("100.00"));
        FmsExpense second = single("O-SAME", 1, new BigDecimal("100.00"));

        ImportResult result = service.batchImportExpenses(new ArrayList<>(List.of(first, second)), "tester");

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getDuplicateCount());
        verify(itemMapper, times(1)).selectList(any());
    }

    private static List<FmsExpense> batch(int count, int itemsPerExpense) {
        List<FmsExpense> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FmsExpense expense = new FmsExpense();
            expense.setExpenseType(1);
            expense.setAmount(new BigDecimal("100.00"));
            List<FmsExpenseItem> items = new ArrayList<>();
            for (int j = 0; j < itemsPerExpense; j++) {
                FmsExpenseItem item = new FmsExpenseItem();
                item.setOrderNo("O-" + i + "-" + j);
                items.add(item);
            }
            expense.setItems(items);
            list.add(expense);
        }
        return list;
    }

    private static FmsExpense single(String orderNo, int expenseType, BigDecimal amount) {
        FmsExpense expense = new FmsExpense();
        expense.setExpenseType(expenseType);
        expense.setAmount(amount);
        FmsExpenseItem item = new FmsExpenseItem();
        item.setOrderNo(orderNo);
        expense.setItems(new ArrayList<>(List.of(item)));
        return expense;
    }

    /** 沿父类链注入，ServiceImpl 的 baseMapper 定义在父类上。 */
    static void inject(Object target, String name, Object value) throws Exception {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
