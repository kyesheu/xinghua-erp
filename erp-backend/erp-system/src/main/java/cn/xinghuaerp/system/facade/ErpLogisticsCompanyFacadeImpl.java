package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.system.entity.ErpLogisticsCompany;
import cn.xinghuaerp.system.request.LogisticsCompanyRequest;
import java.util.List;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ErpLogisticsCompanyFacadeImpl implements ErpLogisticsCompanyFacade {
    private final cn.xinghuaerp.system.service.ErpLogisticsCompanyService delegate;
    @Override public PageResult<ErpLogisticsCompany> queryPageList(LogisticsCompanyRequest request, PageQuery pageQuery) { return delegate.queryPageList(request, pageQuery); }
    @Override public List<ErpLogisticsCompany> queryListByStatus(String name, Integer status, Integer shopType, Integer shopId, Long supplierId) { return delegate.queryListByStatus(name, status, shopType, shopId, supplierId); }
    @Override public int updateStatus(String id,Integer status) { return delegate.updateStatus(id, status); }
    @Override public ErpLogisticsCompany getById(java.io.Serializable id) { return delegate.getById(id); }
    @Override public java.util.List<ErpLogisticsCompany> list() { return delegate.list(); }
    @Override public java.util.List<ErpLogisticsCompany> list(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpLogisticsCompany> query) { return delegate.list(query); }
    @Override public boolean removeBatchByIds(java.util.Collection<?> ids) { return delegate.removeBatchByIds(ids); }
    @Override public boolean save(ErpLogisticsCompany entity) { return delegate.save(entity); }
    @Override public boolean updateById(ErpLogisticsCompany entity) { return delegate.updateById(entity); }
}
