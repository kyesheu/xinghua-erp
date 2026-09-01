package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiCsvExportService;
import cn.xinghuaerp.ai.service.AiFinanceReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-data/finance")
public class AiFinanceReadController extends BaseController {
    private final AiFinanceReadService finance;
    private final BusinessAccessService access;
    private final AiAgentPermissionService permissions;
    private final AiCsvExportService csv;
    private static final String PERMISSION = "ai:finance:query";

    @GetMapping("/overview")
    public AjaxResult overview(@RequestParam(defaultValue="30") int days) {
        Long userId=getUserId();
        if(!permissions.allows(userId,PERMISSION)) throw new AccessDeniedException("无 AI 财务查询权限");
        return success(finance.overview(days,access.scope(userId)));
    }
    /** 订单级亏损穿透（JSON）：负利润订单明细与确定性诱因分布。 */
    @GetMapping("/loss-orders")
    public AjaxResult lossOrders(@RequestParam(defaultValue="30") int days) {
        Long userId=getUserId();
        if(!permissions.allows(userId,PERMISSION)) throw new AccessDeniedException("无 AI 财务查询权限");
        return success(finance.lossOrders(days,access.scope(userId)));
    }
    /** 负利润订单 CSV 导出：最多 5000 行，文件名含统计天数与日期。 */
    @GetMapping("/loss-orders/export")
    public ResponseEntity<byte[]> exportLossOrders(@RequestParam(defaultValue="30") int days) {
        Long userId=getUserId();
        if(!permissions.allows(userId,PERMISSION)) throw new AccessDeniedException("无 AI 财务查询权限");
        var data=finance.lossOrders(days,access.scope(userId));
        List<String> headers=List.of("订单号","销售额","采购成本","运费","平台扣点","营销费用","其他费用","总成本","利润","诱因");
        List<List<String>> rows=data.orders().stream().map(order->List.of(
                order.orderNo()==null?"":order.orderNo(),order.salesAmount().toPlainString(),order.purchaseCost().toPlainString(),
                order.shippingFee().toPlainString(),order.platformFee().toPlainString(),order.marketingFee().toPlainString(),
                order.otherFee().toPlainString(),order.totalCost().toPlainString(),order.profit().toPlainString(),
                String.join("；",order.causes()))).toList();
        return csvResponse(csv.write(headers,rows,data.limitation()),"loss-orders-"+days+"d-"+LocalDate.now()+".csv");
    }
    /** 多店对比（JSON，仅提供资料，不导出）。 */
    @GetMapping("/shop-comparison")
    public AjaxResult shopComparison(@RequestParam(defaultValue="30") int days) {
        Long userId=getUserId();
        if(!permissions.allows(userId,PERMISSION)) throw new AccessDeniedException("无 AI 财务查询权限");
        return success(finance.shopComparison(days,access.scope(userId)));
    }
    private ResponseEntity<byte[]> csvResponse(byte[] body,String filename) {
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException error){return error(error.getMessage());}
}
