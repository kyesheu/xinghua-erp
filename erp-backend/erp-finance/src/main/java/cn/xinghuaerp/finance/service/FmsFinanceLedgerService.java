package cn.xinghuaerp.finance.service;

import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.finance.entity.FmsFinanceLedger;
import cn.xinghuaerp.finance.query.FinanceLedgerQuery;
import com.baomidou.mybatisplus.extension.service.IService;

public interface FmsFinanceLedgerService extends IService<FmsFinanceLedger> {
    PageResult<FmsFinanceLedger> queryPageList(FinanceLedgerQuery query, PageQuery pageQuery);

    boolean add(FmsFinanceLedger ledger);

    boolean updateLedger(FmsFinanceLedger ledger);

    boolean deleteLedger(Long id);

    FmsFinanceLedger getById(Long id);

    FmsFinanceLedger getByVoucherNo(String voucherNo);
}