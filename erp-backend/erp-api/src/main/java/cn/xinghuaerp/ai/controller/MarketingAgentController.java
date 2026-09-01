package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiMarketingReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 广告与营销智能体接口。
 *
 * <p>先校验权限与业务主体范围，再读取只读统计并以服务端范围组装事实，最后交给本机 AI 服务分析；
 * 前端无法指定统计范围，也不会因为分析失败而泄露未授权数据。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-agent/marketing")
public class MarketingAgentController extends BaseController {
    private final AiAgentPermissionService permissions;
    private final BusinessAccessService access;
    private final AiMarketingReadService marketing;
    private final PythonAgentClient python;

    /**
     * 交给 Python 的事实集合，字段数与 Python 侧 {@code extra="forbid"} 严格一致。
     *
     * <p>{@code limitation} 是资料口径的<b>唯一来源</b>（{@link AiMarketingReadService#LIMITATION}），
     * Python 基础节与前端页面都直接引用它，避免同一段话多处各写一份。它是资料口径而非提示词，
     * 因此无论是否启用模型都必须传递。</p>
     */
    record Facts(String scope, long total, long active, long pendingReview,
                 List<AiMarketingReadService.Finding> findings, String question, boolean useModel,
                 String limitation) {}

    public record Request(String question, boolean useModel) {}

    /**
     * 按当前账号授权范围分析营销规则资料。
     *
     * @param request 可选的分析问题与是否启用模型；问题最多 500 字
     * @return AI 服务返回的分析结果
     */
    @PostMapping("/analysis")
    public AjaxResult analyze(@RequestBody(required = false) Request request) {
        Long userId = getUserId();
        if (!permissions.allows(userId, "ai:marketing:query")) throw new AccessDeniedException("无营销资料查询权限");
        var scope = access.scope(userId);
        if (scope == null || !List.of("HEADQUARTERS", "MERCHANT", "SHOP").contains(scope.type()))
            throw new AccessDeniedException("当前账号不支持营销资料查询");
        boolean useModel = request != null && request.useModel();
        String question = request == null || request.question() == null ? "" : request.question().trim();
        if (question.length() > 500) throw new IllegalArgumentException("问题不能超过500字");
        // AI 服务约定：未启用模型时只传空问题，问题文本不进入规则汇总链路。
        if (!useModel) question = "";
        var data = marketing.overview(scope);
        return success(python.marketingAnalysis(new Facts(data.scope(), data.total(), data.active(), data.pendingReview(),
                data.findings(), question, useModel, AiMarketingReadService.LIMITATION)));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public AjaxResult invalid(RuntimeException error) {
        return error(error.getMessage());
    }
}
