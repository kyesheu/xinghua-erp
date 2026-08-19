package cn.xinghuaerp.fulfillment.controller;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.inventory.entity.OOrderStocking;
import cn.xinghuaerp.inventory.request.ShipRecordQueryRequest;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.inventory.service.OOrderStockingService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一发货记录查询 Controller
 * 根据 type 参数区分发货人类型：
 *   0   - 本地仓
 *   300 - 供应商
 *   100/110/200 - 云仓
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/ship/record")
public class ShipRecordController extends BaseController {

    private final OOrderStockingService stockingService;

    /**
     * 统一发货记录分页查询
     * @param request 查询参数（含 type 区分发货人类型）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @GetMapping("/record_list")
    public TableDataInfo recordList(ShipRecordQueryRequest request, PageQuery pageQuery) {
        // 供应商任务暂只支持总部操作；未指定类型的查询也可能包含供应商任务。
        String type = getLoginUser().getUser().getUserType();
        boolean headquarters = "0".equals(type) || "00".equals(type);
        if (!headquarters && (Integer.valueOf(300).equals(request.getType())
                || (request.getType() == null && !Boolean.TRUE.equals(request.getAllCloud())))) {
            throw new org.springframework.security.access.AccessDeniedException("供应商发货记录暂仅支持总部查询");
        }
        PageResult<OOrderStocking> pageResult = stockingService.queryShipRecordPageList(request, pageQuery);
        return getDataTable(pageResult);
    }
}
