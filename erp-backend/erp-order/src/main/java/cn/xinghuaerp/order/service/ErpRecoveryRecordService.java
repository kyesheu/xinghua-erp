package cn.xinghuaerp.order.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.order.entity.ErpRecoveryRecord;
import cn.xinghuaerp.order.request.RecoveryAddRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 1
* @description 针对表【erp_recovery_record(回收记录表)】的数据库操作Service
* @createDate 2026-04-11 15:03:32
*/
public interface ErpRecoveryRecordService extends IService<ErpRecoveryRecord> {
    PageResult<ErpRecoveryRecord> queryPageList(ErpRecoveryRecord bo, PageQuery pageQuery);
    ResultVo<Long> add(String createBy, RecoveryAddRequest bo);
}
