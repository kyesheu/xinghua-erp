package cn.xinghuaerp.system.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.system.bo.MerchantAddBo;
import cn.xinghuaerp.system.query.MerchantQuery;
import cn.xinghuaerp.system.entity.ErpMerchant;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author qilip
* @description 针对表【oms_tenant(租户用户表)】的数据库操作Service
* @createDate 2024-06-23 11:10:08
*/
public interface ErpMerchantService extends IService<ErpMerchant> {
    PageResult<ErpMerchant> queryPageList(MerchantQuery bo, PageQuery pageQuery);
    ErpMerchant selectUserByUserName(String userName);
    void updateByUserId(ErpMerchant tenant, Long userId);
    ResultVo<ErpMerchant> add(MerchantAddBo bo,String createBy);
    ResultVo<ErpMerchant> setLoginName(Long id,MerchantAddBo bo,String updateBy);


}
