package cn.xinghuaerp.system.service.impl;

import cn.xinghuaerp.system.entity.SysTaskLogs;
import cn.xinghuaerp.system.mapper.SysTaskLogsMapper;
import cn.xinghuaerp.system.service.SysTaskLogsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;


import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
* @author TW
* @description 针对表【sys_task_logs】的数据库操作Service实现
* @createDate 2024-03-22 20:07:56
*/
@AllArgsConstructor
@Service
public class SysTaskLogsServiceImpl extends ServiceImpl<SysTaskLogsMapper, SysTaskLogs>
    implements SysTaskLogsService {
    private final SysTaskLogsMapper mapper;

    @Override
    public PageResult<SysTaskLogs> queryPageList(Integer taskId, PageQuery pageQuery) {
        LambdaQueryWrapper<SysTaskLogs> queryWrapper = new LambdaQueryWrapper<SysTaskLogs>()
                // 未指定任务时返回全部日志，避免拼出 task_id = null 的恒假条件
                .eq(taskId != null, SysTaskLogs::getTaskId, taskId)
                // 分页必须有确定排序，否则翻页时同一条日志可能重复出现或漏出
                .orderByDesc(SysTaskLogs::getId);
        Page<SysTaskLogs> pages = mapper.selectPage(pageQuery.build(), queryWrapper);
        return PageResult.build(pages);
    }
}




