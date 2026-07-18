package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.system.entity.SysTask;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.system.service.SysTaskLogsService;
import cn.xinghuaerp.system.service.SysTaskService;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 *
 * 
 * @author xinghua
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/sys-api/system/task")
public class SysTaskController extends BaseController
{
    private final SysTaskService taskService;
    private final SysTaskLogsService taskLogsService;
    @GetMapping("/list")
    public TableDataInfo list(PageQuery pageQuery)
    {
        PageResult<SysTask> pageResult = taskService.queryPageList(pageQuery);
        return getDataTable(pageResult);
    }

    @GetMapping(value = "/{taskId}")
    public AjaxResult getInfo(@PathVariable Long taskId)
    {
        return success(taskService.getById(taskId));
    }

    @GetMapping(value = "/logs/{taskId}")
    public AjaxResult getLogs(@PathVariable Integer taskId)
    {
        return success(taskLogsService.queryPageList(taskId,new PageQuery()));
    }

    @PutMapping
    public AjaxResult edit(@RequestBody SysTask task)
    {
        if(StringUtils.hasText(task.getCron())&&!task.getCron().equals("-")){
            task.setStatus(1);
        }else{
            task.setStatus(0);
        }
        return toAjax(taskService.updateById(task));
    }
}
