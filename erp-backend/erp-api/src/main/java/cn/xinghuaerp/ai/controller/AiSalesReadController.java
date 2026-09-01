package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiCsvExportService;
import cn.xinghuaerp.ai.service.AiSalesReadService;
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
@RequestMapping("/api/erp-api/ai-data/sales")
public class AiSalesReadController extends BaseController {
    private final AiSalesReadService sales;
    private final BusinessAccessService access;
    private final AiAgentPermissionService permissions;
    private final AiCsvExportService csv;
    private static final String PERMISSION = "ai:sales:query";

    @GetMapping("/overview")
    public AjaxResult overview(@RequestParam(defaultValue="30") int days) {
        Long userId=getUserId();
        if (!permissions.allows(userId,PERMISSION)) throw new AccessDeniedException("无 AI 销售查询权限");
        return success(sales.overview(days,access.scope(userId)));
    }
    /** SKU 表现排行完整版（JSON）：聚合、退款归集、集中度与四象限，默认展示前 100 行。 */
    @GetMapping("/sku-performance")
    public AjaxResult skuPerformance(@RequestParam(defaultValue="30") int days) {
        Long userId=getUserId();
        if (!permissions.allows(userId,PERMISSION)) throw new AccessDeniedException("无 AI 销售查询权限");
        return success(sales.skuPerformance(days,access.scope(userId),100));
    }
    /** SKU 表现排行 CSV 导出：最多 5000 行，文件名含统计天数与日期。 */
    @GetMapping("/sku-performance/export")
    public ResponseEntity<byte[]> exportSkuPerformance(@RequestParam(defaultValue="30") int days) {
        Long userId=getUserId();
        if (!permissions.allows(userId,PERMISSION)) throw new AccessDeniedException("无 AI 销售查询权限");
        var data=sales.skuPerformance(days,access.scope(userId),AiCsvExportService.MAX_ROWS);
        List<String> headers=List.of("内部SKU ID","商品标题","付款订单数","销量","销售额","退款订单数","退款金额","退款率","四象限");
        List<List<String>> rows=data.rows().stream().map(row->List.of(
                row.skuId(),row.title()==null?"":row.title(),String.valueOf(row.paidOrders()),String.valueOf(row.quantity()),
                row.salesAmount().toPlainString(),String.valueOf(row.refundOrders()),row.refundAmount().toPlainString(),
                row.refundRate()==null?"":row.refundRate().toPlainString(),quadrantName(row.quadrant()))).toList();
        return csvResponse(csv.write(headers,rows,data.limitation()),"sku-performance-"+days+"d-"+LocalDate.now()+".csv");
    }
    /** 四象限英文标识转中文文案；null（未分类/数据不足）原样标注。 */
    static String quadrantName(String quadrant) {
        return switch (quadrant==null?"unclassified":quadrant) {
            case "highSalesLowRefund" -> "高销量低退款";
            case "highSalesHighRefund" -> "高销量高退款";
            case "lowSalesLowRefund" -> "低销量低退款";
            case "lowSalesHighRefund" -> "低销量高退款";
            default -> "未分类";
        };
    }
    private ResponseEntity<byte[]> csvResponse(byte[] body,String filename) {
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException error) { return error(error.getMessage()); }
}
