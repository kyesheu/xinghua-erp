package cn.xinghuaerp.finance.service;

import cn.xinghuaerp.common.dto.ImportResult;
import cn.xinghuaerp.finance.entity.FmsExpense;

import java.util.List;

public interface PlatformBillImportService {

    List<String> queryPlatforms();

    ImportResult saveExpensesFromBillImport(List<FmsExpense> expenses, String operator);
}
