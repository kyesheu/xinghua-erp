package cn.xinghuaerp.ai.service;

import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI 资料明细 CSV 导出：UTF-8 带 BOM、字段全部引号包裹并转义双引号、末行输出说明。
 *
 * <p>仅用于资料明细（排行、亏损订单等），不包含买家、地址、电话等敏感列；
 * 行数上限 {@link #MAX_ROWS} 由调用方通过查询参数控制截断，导出说明中声明口径限制。</p>
 */
@Service
public class AiCsvExportService {
    /** 单次导出最大明细行数，与控制器传入的查询 rowLimit 对齐。 */
    public static final int MAX_ROWS = 5000;

    /**
     * 生成 CSV 字节流（UTF-8 with BOM，CRLF 行结束）。
     *
     * @param headers 表头列名
     * @param rows    数据行，单元格为字符串（数值由调用方格式化）；超过 {@link #MAX_ROWS} 行时截断
     * @param note    末行“说明”单元格，通常为服务端生成的 LIMITATION 文案
     * @return CSV 文件字节内容
     */
    public byte[] write(List<String> headers, List<List<String>> rows, String note) {
        var sb=new StringBuilder();
        appendLine(sb,headers);
        int limit=Math.min(rows==null?0:rows.size(),MAX_ROWS);
        for (int i=0;i<limit;i++) appendLine(sb,rows.get(i));
        appendLine(sb,List.of("说明",note==null?"":note));
        var out=new ByteArrayOutputStream();
        try {
            out.write("\uFEFF".getBytes(StandardCharsets.UTF_8));
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("CSV 导出失败",e);
        }
        return out.toByteArray();
    }

    /** 单行写入：逗号分隔，每个字段引号包裹，内部双引号转义为两个双引号。 */
    private void appendLine(StringBuilder sb,List<String> cells) {
        for (int i=0;i<cells.size();i++) {
            if (i>0) sb.append(',');
            String value=cells.get(i)==null?"":cells.get(i);
            sb.append('"').append(value.replace("\"","\"\"")).append('"');
        }
        sb.append('\r').append('\n');
    }
}
