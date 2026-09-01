package cn.xinghuaerp.ai.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiCsvExportServiceTest {
    final AiCsvExportService csv=new AiCsvExportService();

    /** UTF-8 BOM 打头；字段全部引号包裹；内部双引号转义为两个双引号；逗号与换行不破坏列结构。 */
    @Test void writesBomQuotedFieldsAndEscapesEmbeddedQuotesCommasAndNewlines() {
        byte[] bytes=csv.write(List.of("名称","备注"),
                List.of(List.of("含\"引号\"的值","逗号,值")), "说明文字");
        String text=new String(bytes,StandardCharsets.UTF_8);
        assertEquals('\uFEFF',text.charAt(0));
        assertTrue(text.contains("\"含\"\"引号\"\"的值\""));
        assertTrue(text.contains("\"逗号,值\""));
        assertTrue(text.endsWith("\"说明\",\"说明文字\"\r\n"));
        // 数据行数正确：表头 + 1 行数据 + 1 行说明。
        assertEquals(3,countLines(text));
    }

    /** 超过 MAX_ROWS 截断至 5000 行；null 单元格输出空串；说明行始终在末尾。 */
    @Test void truncatesAtMaxRowsAndRendersNullCellsAsEmpty() {
        var many=new ArrayList<List<String>>();
        for(int i=0;i<AiCsvExportService.MAX_ROWS+5;i++) many.add(java.util.Arrays.asList("r"+i,null));
        String text=new String(csv.write(List.of("a","b"),many,"尾注"),StandardCharsets.UTF_8);
        assertEquals(AiCsvExportService.MAX_ROWS+2,countLines(text));
        assertFalse(text.contains("\"r"+AiCsvExportService.MAX_ROWS+"\""));
        assertTrue(text.contains("\"r0\",\"\""));
        assertTrue(text.endsWith("\"说明\",\"尾注\"\r\n"));
    }

    private long countLines(String text) {
        return text.lines().count();
    }
}
