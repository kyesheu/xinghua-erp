package cn.xinghuaerp.system.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.system.entity.OShopPullLogs;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author TW
* @description 针对表【sys_shop_pull_logs(更新日志表)】的数据库操作Service
* @createDate 2024-03-22 17:43:38
*/
public interface OShopPullLogsService extends IService<OShopPullLogs> {

    PageResult<OShopPullLogs> queryPageList(OShopPullLogs bo, PageQuery pageQuery);
}
