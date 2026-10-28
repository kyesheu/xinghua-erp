package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.system.entity.ErpLogisticsCompany;
import cn.xinghuaerp.system.request.LogisticsCompanyRequest;
import java.util.List;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface ErpLogisticsCompanyFacade {
    PageResult<ErpLogisticsCompany> queryPageList(LogisticsCompanyRequest request, PageQuery pageQuery);
    List<ErpLogisticsCompany> queryListByStatus(String name, Integer status, Integer shopType, Integer shopId, Long supplierId);
    int updateStatus(String id,Integer status);
    ErpLogisticsCompany getById(java.io.Serializable id);
    java.util.List<ErpLogisticsCompany> list();
    java.util.List<ErpLogisticsCompany> list(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpLogisticsCompany> query);
    boolean removeBatchByIds(java.util.Collection<?> ids);
    boolean save(ErpLogisticsCompany entity);
    boolean updateById(ErpLogisticsCompany entity);
}
