package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.order.vo.SalesDailyVo;
import cn.xinghuaerp.ai.service.AiConfigService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.order.service.OOrderItemService;
import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.system.service.OShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI简报服务 - 为管理者生成智能工作台简报
 *
 * <p>模型生成在 Python 服务里完成；Java 负责汇总今日数据、组装提示词、解析结构，
 * 并在模型不可用时退回固定文案版简报。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBriefService {

    private final OOrderService orderService;
    private final OOrderItemService orderItemService;
    private final OShopService shopService;
    private final AiConfigService aiConfigService;
    private final PythonAgentClient python;

    private final cn.xinghuaerp.ai.service.SingleFlightCache<String,AiBriefResponse> cache = new cn.xinghuaerp.ai.service.SingleFlightCache<>();

    private static final String SYSTEM_PROMPT = "你是电商运营助手，只输出 JSON，不要输出 markdown 标记或解释。";

    /**
     * 生成AI工作台简报
     */
    public AiBriefResponse generateBrief() {
        AiConfig model=aiConfigService.getDefaultConfig();
        String signature=modelSignature(model);
        String key="HEADQUARTERS:brief:v1:"+java.time.LocalDate.now()+":"+signature;
        var response=cache.get(key,()->{
            var generated=generateUncached();generated.setGeneratedAt(java.time.LocalDateTime.now());return generated;
        },item->item.isAiAvailable()?60000:5000);
        // DTO 可变，防止调用方修改缓存的共享实例。
        return JsonUtils.readValue(JsonUtils.write(response),AiBriefResponse.class);
    }

    private String modelSignature(AiConfig model) {
        if(model==null)return "disabled";
        try {
            String version=JsonUtils.write(java.util.Arrays.asList(model.getId(),model.getUpdateTime(),model.getApiKey(),model.getApiEndpoint(),model.getModelName(),model.getIsEnabled()));
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(version.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException error) {throw new IllegalStateException(error);}
    }

    private AiBriefResponse generateUncached() {
        // 1. 获取数据
        Map<String, Object> todayData = getTodayData();
        List<SalesDailyVo> recentSales = getRecentSales();

        // 简报实际使用数据库中的默认模型，不能仅凭环境变量判断可用。
        AiConfig dbConfig = aiConfigService.getDefaultConfig();
        boolean hasDbConfig = dbConfig != null
                && StringUtils.hasText(dbConfig.getApiKey())
                && StringUtils.hasText(dbConfig.getApiEndpoint())
                && StringUtils.hasText(dbConfig.getModelName());

        if (!hasDbConfig) {
            // AI不可用，返回纯数据版
            return buildFallbackBrief(todayData, recentSales, false);
        }

        // 3. 调用AI生成简报
        try {
            return callAiForBrief(todayData, recentSales);
        } catch (Exception e) {
            log.warn("AI简报生成失败，使用本地降级", e);
            return buildFallbackBrief(todayData, recentSales, true);
        }
    }

    private AiBriefResponse callAiForBrief(Map<String, Object> todayData, List<SalesDailyVo> recentSales) {
        // 构建销售趋势文本
        StringBuilder trendText = new StringBuilder();
        if (recentSales != null && recentSales.size() >= 2) {
            trendText.append("近7日销售数据（按日期倒序）：\n");
            for (int i = 0; i < recentSales.size(); i++) {
                SalesDailyVo day = recentSales.get(i);
                trendText.append("  ").append(day.getDate())
                        .append("：销售额").append(day.getAmount())
                        .append("元，订单数").append(day.getCount()).append("\n");
            }
        }

        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String prompt = """
                你是一个电商运营助手，请根据以下数据生成今日工作台简报。

                当前时间：%s
                今日数据：
                - 今日销售额：%s 元
                - 今日订单数：%s 单
                - 待发货：%s 单
                - 店铺总数：%s 个

                近7日销售趋势：
                %s

                请按以下JSON格式返回，不要带markdown标记：
                {
                    "greeting": "根据当前时间段给出问候语（如当前20点应说'晚上好'）",
                    "summary": "一句话说清楚今日核心情况（不超过20字）",
                    "trend": "销售趋势一句话（环比变化，哪个表现突出，不超过50字）",
                    "priorities": [
                        {
                            "level": "high/medium/low",
                            "category": "发货/退款/库存/售后",
                            "title": "问题标题（一句话）",
                            "detail": "具体描述（不超过30字）",
                            "action": "按钮文字",
                            "link": "跳转路径",
                            "count": 8
                        }
                    ],
                    "quickStats": {
                        "salesVolume": "12,580",
                        "salesTrend": "+12.5%%",
                        "orderCount": 302,
                        "waitShip": 50,
                        "refundRate": "暂无数据"
                    }
                }

                注意：
                - priorities最多3条，按紧急程度排序
                - 没有数据支撑的不要编造
                - quickStats中salesTrend对比昨日
                """.formatted(
                now,
                todayData.get("salesVolume"),
                todayData.get("orderCount"),
                todayData.get("waitShip"),
                todayData.get("shopCount"),
                trendText.toString()
        );

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("system", SYSTEM_PROMPT);
            body.put("prompt", prompt);
            JsonNode reply = python.brief(body, "");
            String response = reply == null ? null : JsonUtils.text(reply, "text");
            if (response == null || response.isBlank()) {
                throw new IllegalStateException("AI 服务未返回简报内容");
            }
            return parseAiResponse(response, todayData);
        } catch (Exception e) {
            log.warn("AI简报生成失败，使用本地降级: {}", e.getMessage());
            return buildFallbackBrief(todayData, recentSales, true);
        }
    }

    /**
     * 解析AI返回的JSON
     */
    private AiBriefResponse parseAiResponse(String response, Map<String, Object> todayData) {
        String json = response;
        if (json.contains("{")) {
            json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
        }

        try {
            var obj = JsonUtils.readTree(json);
            AiBriefResponse brief = new AiBriefResponse();
            brief.setGreeting(JsonUtils.text(obj, "greeting"));
            brief.setSummary(JsonUtils.text(obj, "summary"));
            brief.setTrend(JsonUtils.text(obj, "trend"));

            var stats = JsonUtils.node(obj, "quickStats");
            if (stats != null) {
                var qs = new QuickStats();
                qs.setSalesVolume(JsonUtils.text(stats, "salesVolume"));
                qs.setSalesTrend(JsonUtils.text(stats, "salesTrend"));
                qs.setOrderCount(JsonUtils.integer(stats, "orderCount"));
                qs.setWaitShip(JsonUtils.integer(stats, "waitShip"));
                qs.setRefundRate(JsonUtils.text(stats, "refundRate"));
                brief.setQuickStats(qs);
            }

            var priArr = JsonUtils.node(obj, "priorities");
            if (priArr != null) {
                brief.setPriorities(JsonUtils.readList(priArr.toString(), Priority.class));
            }

            return brief;
        } catch (Exception e) {
            throw new RuntimeException("JSON解析失败", e);
        }
    }

    /**
     * 降级方案
     */
    private AiBriefResponse buildFallbackBrief(Map<String, Object> todayData, List<SalesDailyVo> recentSales,
                                               boolean aiConfigured) {
        AiBriefResponse brief = new AiBriefResponse();
        brief.setGreeting("欢迎使用");

        QuickStats qs = new QuickStats();
        qs.setSalesVolume(todayData.get("salesVolume")==null?"不可用":String.valueOf(todayData.get("salesVolume")));
        qs.setOrderCount(todayData.get("orderCount") instanceof Number value?value.intValue():null);
        qs.setWaitShip(todayData.get("waitShip") instanceof Number value?value.intValue():null);
        brief.setQuickStats(qs);

        if (!aiConfigured) {
            brief.setAiAvailable(false);
            brief.setSummary("AI分析未配置");
            brief.setTrend("请在 AI 智能 > 模型配置 中添加、启用并设置默认模型");

            Number waitShip = (Number) todayData.get("waitShip");
            if (waitShip != null && waitShip.intValue() > 0) {
                Priority p = new Priority();
                p.setLevel("medium");
                p.setCategory("发货");
                p.setTitle(waitShip.intValue() + "单待发货");
                p.setDetail("建议尽快处理");
                p.setAction("去发货");
                p.setLink("/shipping/manual_ship");
                p.setCount(waitShip.intValue());
                brief.setPriorities(List.of(p));
            }
            return brief;
        }

        brief.setAiAvailable(false);
        brief.setSummary("今日数据已就绪");
        brief.setTrend("AI分析暂不可用，系统自动生成概要");

        Number waitShip = (Number) todayData.get("waitShip");
        if (waitShip != null && waitShip.intValue() > 0) {
            Priority p = new Priority();
            p.setLevel("medium");
            p.setCategory("发货");
            p.setTitle(waitShip.intValue() + "单待发货");
            p.setDetail("请安排处理");
            p.setAction("去发货");
            p.setLink("/shipping/manual_ship");
            p.setCount(waitShip.intValue());
            brief.setPriorities(List.of(p));
        }

        return brief;
    }

    /**
     * 获取今日数据
     */
    private Map<String, Object> getTodayData() {
        Long shopCount = shopService.count();
        SalesDailyVo todaySales = orderService.getTodaySalesDaily(0L);
        Number waitShip = orderService.getWaitShipOrderAllCount(0L);

        var data=new LinkedHashMap<String,Object>();
        data.put("salesVolume",todaySales==null?null:todaySales.getAmount());
        data.put("orderCount",todaySales==null?null:todaySales.getCount());
        data.put("waitShip",waitShip);data.put("shopCount",shopCount);
        return data;
    }

    /**
     * 获取近期销售数据
     */
    private List<SalesDailyVo> getRecentSales() {
        return orderService.salesDaily();
    }

    // ====== 内部类 ======

    @lombok.Data
    public static class AiBriefResponse {
        private java.time.LocalDateTime generatedAt;
        private String greeting = "";
        private String summary = "";
        private String trend = "";
        private boolean aiAvailable = true;
        private QuickStats quickStats;
        private List<Priority> priorities = List.of();
    }

    @lombok.Data
    public static class QuickStats {
        private String salesVolume = "—";
        private String salesTrend = "";
        private Integer orderCount = 0;
        private Integer waitShip = 0;
        private String refundRate = "—";
    }

    @lombok.Data
    public static class Priority {
        private String level;    // high/medium/low
        private String category; // 发货/退款/库存/售后
        private String title;    // 问题标题
        private String detail;   // 具体描述
        private String action;   // 按钮文字
        private String link;     // 跳转路径
        private Integer count;   // 数量
    }
}
