package cn.xinghuaerp.purchase.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.purchase.entity.ErpSupplierCustomer;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ErpSupplierCustomerService extends IService<ErpSupplierCustomer> {
    PageResult<ErpSupplierCustomer> queryPageList(ErpSupplierCustomer bo, PageQuery pageQuery);
    
    ResultVo updateStatus(Long id, Integer status);
}
