package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.system.entity.OShopPullLogs;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.system.service.OShopPullLogsService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺拉取日志 Controller
 * 对应前端 /api/oms-api/shop/pull_logs_list
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/oms-api/shop")
public class ShopPullLogsController extends BaseController {
    private final OShopPullLogsService pullLogsService;

    @GetMapping("/pull_logs_list")
    public TableDataInfo list(OShopPullLogs logs, PageQuery pageQuery) {
        var pageList = pullLogsService.queryPageList(logs, pageQuery);
        return getDataTable(pageList);
    }
}