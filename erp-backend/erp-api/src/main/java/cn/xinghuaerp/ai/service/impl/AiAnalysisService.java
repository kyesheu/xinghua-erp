package cn.xinghuaerp.ai.service.impl;

import tools.jackson.databind.node.ObjectNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.ai.entity.AiAnalysisRecord;
import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.ai.service.AiConfigService;
import cn.xinghuaerp.ai.service.AiTaskExecutor;
import cn.xinghuaerp.ai.service.IAiAnalysisRecordService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.ai.service.ToolToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 智能分析：模板与记录留在 Java，模型调用与工具循环交给 Python 服务。
 *
 * <p>分析失败分两种：模型明确报错（未配置、密钥无效）如实转达；
 * 服务连不上则退回本地数据摘要，保证页面仍有可读结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AiConfigService aiConfigService;
    private final IAiAnalysisRecordService analysisRecordService;
    private final PythonAgentClient python;
    private final ToolToken toolToken;
    private final AiTaskExecutor executor;
    private final AiAnalysisFallback fallback;

    private static final String SYSTEM_PROMPT = """
            你是兴华电商ERP系统的AI智能分析专家。你的职责是根据分析任务，调用工具获取真实业务数据，然后进行深度分析，输出结构化的分析报告。

            分析要求：
            1. 必须先调用工具获取真实数据，不可编造数据
            2. 对数据进行多维度分析：趋势、对比、异常识别、占比等
            3. 给出可执行的运营建议
            4. 使用Markdown格式输出，包含标题、表格、列表等
            5. 分析结论必须有数据支撑

            你可以多次调用工具来逐步获取数据，每次根据返回的结果决定下一步查什么。
            由你自行对数据进行分组、排序、统计、对比，得出结论。
            """;

    private static final Map<String, String> ANALYSIS_PROMPTS = Map.of(
            "sales", """
                    请对最近7天的销售数据进行深度分析，分析维度包括：
                    1. **销售趋势分析** - 整体销售走势，环比变化
                    2. **热销商品排名** - 按销量/金额排序Top10
                    3. **平台销售对比** - 各平台（淘宝/京东/拼多多/抖店等）销售占比
                    4. **订单状态分析** - 各状态订单分布，异常订单识别
                    5. **运营建议** - 基于数据给出3-5条可执行建议

                    请调用工具获取订单数据和店铺数据后进行分析。""",

            "inventory", """
                    请对当前库存数据进行深度分析，分析维度包括：
                    1. **库存总览** - SKU数量、总库存量、库存总值
                    2. **库存预警** - 低库存商品（可售数量<=5）清单
                    3. **库存周转分析** - 结合近期出入库数据分析周转率
                    4. **滞销商品识别** - 长期无动销的库存
                    5. **补货建议** - 基于销售速度和库存水平给出补货建议

                    请调用工具获取库存数据、出入库记录和商品数据后进行分析。""",

            "customer", """
                    请对客户数据进行深度分析，分析维度包括：
                    1. **客户概况** - 总客户数、新增趋势
                    2. **订单行为分析** - 客户下单频次、客单价分布
                    3. **售后分析** - 退款率、退款原因分布
                    4. **高价值客户识别** - 高频次/高金额客户特征
                    5. **客户运营建议** - 拉新、留存、复购策略

                    请调用工具获取客户数据、订单数据和退款数据后进行分析。""",

            "operation", """
                    请对运营效率进行深度分析，分析维度包括：
                    1. **订单处理效率** - 各环节（下单→发货→签收）耗时分析
                    2. **物流效率** - 发货及时率、物流签收时效
                    3. **售后处理效率** - 退款处理时长、成功率
                    4. **人效分析** - 订单/人、处理速度等
                    5. **效率提升建议** - 识别瓶颈，给出优化方案

                    请调用工具获取订单数据、物流数据和退款数据后进行分析。""",

            "purchase", """
                    请对采购数据进行深度分析，分析维度包括：
                    1. **采购概况** - 采购订单数量、金额、供应商分布
                    2. **采购周期分析** - 从下单到入库的平均时长
                    3. **供应商评估** - 各供应商的交付及时率、质量情况
                    4. **库存与采购匹配度** - 采购量与销售量的匹配分析
                    5. **采购优化建议** - 供应商选择、采购时机、批量优化

                    请调用工具获取采购数据、供应商数据和库存数据后进行分析。"""
    );

    /** 执行分析并流式返回结果。 */
    public void executeAnalysis(String analysisType, String userContent, SseEmitter emitter, Long userId) {
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            sendEventSafe(emitter, emitterDone, "error", "请先在 AI 智能 > 模型配置 中添加并启用默认模型");
            completeSafe(emitter, emitterDone);
            return;
        }

        AiAnalysisRecord record = new AiAnalysisRecord();
        record.setAnalysisType(analysisType);
        record.setAnalysisContent(userContent);
        record.setStatus(0);
        record.setUserId(userId);
        record.setCreatedTime(LocalDateTime.now());
        record.setUpdatedTime(LocalDateTime.now());

        String templatePrompt = ANALYSIS_PROMPTS.getOrDefault(analysisType == null ? "" : analysisType, ANALYSIS_PROMPTS.get("sales"));
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String fullPrompt = userContent != null && !userContent.isBlank() ? userContent : templatePrompt;
        fullPrompt = "当前时间：" + now + "\n\n" + fullPrompt;
        record.setPromptContent(fullPrompt);

        try {
            analysisRecordService.save(record);
        } catch (Exception e) {
            log.warn("保存分析记录失败", e);
        }

        sendEventSafe(emitter, emitterDone, "analysis_start", String.valueOf(record.getId()));

        String prompt = fullPrompt;
        executor.submit(emitter, () -> {
            StringBuilder fullResponse = new StringBuilder();
            AtomicBoolean modelRejected = new AtomicBoolean(false);
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("system", SYSTEM_PROMPT);
                body.put("prompt", prompt);
                python.stream("/internal/analysis", body, toolToken.issue(userId), (type, content) -> {
                    switch (type) {
                        case "message" -> {
                            if(fullResponse.length()+content.length()>32000)throw new IllegalStateException("AI输出超过限制");
                            fullResponse.append(content);
                            sendEventSafe(emitter, emitterDone, "message", content);
                        }
                        case "error" -> {
                            modelRejected.set(true);
                            sendEventSafe(emitter, emitterDone, "error", content);
                        }
                        default -> { }
                    }
                });
                if (modelRejected.get()) {
                    fail(record, "AI 分析未完成", emitter, emitterDone);
                } else if (fullResponse.isEmpty()) {
                    fail(record, "AI 分析未返回内容", emitter, emitterDone);
                } else {
                    complete(record, fullResponse.toString(), emitter, emitterDone);
                }
            } catch (Exception e) {
                if(Thread.currentThread().isInterrupted()){record.setStatus(2);record.setAnalysisResult("分析已取消");try {analysisRecordService.save(record);} catch(RuntimeException ignored){} return;}
                log.warn("AI 分析调用失败，返回本地数据摘要: {}", e.getMessage());
                fail(record, "AI 服务不可用：" + e.getMessage(), emitter, emitterDone);
            }
        });
    }

    /** 成功路径：记录模型产出。 */
    private void complete(AiAnalysisRecord record, String result, SseEmitter emitter, AtomicBoolean done) {
        record.setAnalysisResult(result);
        record.setStatus(1);
        record.setUpdatedTime(LocalDateTime.now());
        try { analysisRecordService.save(record); } catch (Exception ignored) { }
        sendEventSafe(emitter, done, "done", String.valueOf(record.getId()));
        completeSafe(emitter, done);
    }

    /** 失败路径：把本地数据摘要作为结果压入同一份记录，前端体验与成功一致。 */
    private void fail(AiAnalysisRecord record, String reason, SseEmitter emitter, AtomicBoolean done) {
        String local = fallback.build(record.getAnalysisType(),record.getUserId());
        record.setAnalysisResult(local);
        record.setStatus(1);
        record.setErrorMessage(reason);
        record.setUpdatedTime(LocalDateTime.now());
        try { analysisRecordService.save(record); } catch (Exception ignored) { }
        sendEventSafe(emitter, done, "message", local);
        sendEventSafe(emitter, done, "done", String.valueOf(record.getId()));
        completeSafe(emitter, done);
    }

    private void sendEventSafe(SseEmitter emitter, AtomicBoolean done, String type, String content) {
        if (done.get()) return;
        try {
            ObjectNode event = JsonUtils.object();
            event.put("type", type);
            if (content != null) {
                event.put("content", content);
            }
            emitter.send(SseEmitter.event().data(event.toString()));
        } catch (Exception e) {
            log.debug("发送SSE事件失败: {}", e.getMessage());
            done.set(true);
        }
    }

    private void completeSafe(SseEmitter emitter, AtomicBoolean done) {
        if (done.getAndSet(true)) return;
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}
