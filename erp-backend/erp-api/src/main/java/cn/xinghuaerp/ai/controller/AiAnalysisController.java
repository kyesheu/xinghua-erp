package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.ai.service.impl.AiAnalysisService;
import cn.xinghuaerp.ai.entity.AiAnalysisRecord;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.system.security.common.SecurityUtils;
import cn.xinghuaerp.ai.service.IAiAnalysisRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import cn.xinghuaerp.system.service.AiAgentPermissionService;

/**
 * AI智能分析控制器
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/ai/analysis")
public class AiAnalysisController extends BaseController {

    private final AiAnalysisService aiAnalysisService;
    private final IAiAnalysisRecordService analysisRecordService;
    private final cn.xinghuaerp.system.service.AiAgentPermissionService permissions;

    private void requireAnalysisPermission() {
        if (!permissions.allows(getUserId(), "ai:analysis:list"))
            throw new AccessDeniedException("无 AI 分析权限");
    }

    /**
     * 触发智能分析（SSE流式返回）
     */
    @PostMapping(value = "/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeAnalysis(@RequestBody AnalysisRequest request) {
        requireAnalysisPermission();
        SseEmitter emitter = new SseEmitter(180000L);
        aiAnalysisService.executeAnalysis(
                request.getAnalysisType(),
                request.getContent(),
                emitter,
                getUserId()
        );
        return emitter;
    }

    /**
     * 分析记录分页列表（管理员看全部，其他人只看自己的记录）
     */
    @GetMapping("/list")
    public AjaxResult list(PageQuery query) {
        requireAnalysisPermission();
        Long userId = getUserId();
        PageResult<AiAnalysisRecord> page =
                analysisRecordService.queryPageList(query, SecurityUtils.isAdmin(userId) ? null : userId);
        return AjaxResult.success(page);
    }

    /**
     * 分析记录详情（仅本人或管理员）
     */
    @GetMapping("/{id}")
    public AjaxResult getById(@PathVariable Long id) {
        requireAnalysisPermission();
        AiAnalysisRecord record = analysisRecordService.getById(id);
        if (record == null) {
            return AjaxResult.error("记录不存在");
        }
        requireOwner(record);
        return AjaxResult.success(record);
    }

    /**
     * 删除分析记录（仅本人或管理员）
     */
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        requireAnalysisPermission();
        AiAnalysisRecord record = analysisRecordService.getById(id);
        if (record == null) {
            return AjaxResult.error("记录不存在");
        }
        requireOwner(record);
        analysisRecordService.removeById(id);
        return AjaxResult.success();
    }

    private void requireOwner(AiAnalysisRecord record) {
        Long userId = getUserId();
        if (!SecurityUtils.isAdmin(userId) && !userId.equals(record.getUserId())) {
            throw new AccessDeniedException("无权访问该分析记录");
        }
    }

    public static class AnalysisRequest {
        private String analysisType;
        private String content;

        public String getAnalysisType() { return analysisType; }
        public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
