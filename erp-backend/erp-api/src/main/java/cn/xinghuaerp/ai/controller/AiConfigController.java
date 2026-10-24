package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiConfigService;
import cn.xinghuaerp.ai.service.impl.AiOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 模型配置含 apiKey，属于敏感凭据：所有端点都要求 ai:config:list 权限，
 *  读取接口一律脱敏返回；编辑时客户端回传脱敏值则保留原密钥。 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/ai/config")
public class AiConfigController extends BaseController {

    private final AiConfigService aiConfigService;
    private final AiAgentPermissionService permissions;
    private final AiOrchestrationService aiOrchestrationService;

    private void requireConfigPermission() {
        if (!permissions.allows(getUserId(), "ai:config:list"))
            throw new AccessDeniedException("无模型配置管理权限");
    }

    /** apiKey 脱敏：露出前 4 位与后 4 位，中间以 **** 代替；长度不足 8 或空白整体打码。 */
    static String maskApiKey(String key) {
        if (key == null) return null;
        String trimmed = key.trim();
        if (trimmed.isEmpty() || trimmed.length() <= 8) return "****";
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private AiConfig masked(AiConfig config) {
        if (config != null) config.setApiKey(maskApiKey(config.getApiKey()));
        return config;
    }

    @GetMapping("/list")
    public AjaxResult list() {
        requireConfigPermission();
        List<AiConfig> list = aiConfigService.list();
        list.forEach(this::masked);
        return AjaxResult.success(list);
    }

    @GetMapping("/{id}")
    public AjaxResult getById(@PathVariable Long id) {
        requireConfigPermission();
        return AjaxResult.success(masked(aiConfigService.getById(id)));
    }

    @PostMapping
    public AjaxResult add(@RequestBody AiConfig config) {
        requireConfigPermission();
        config.setCreateTime(LocalDateTime.now());
        aiConfigService.save(config);
        return AjaxResult.success();
    }

    @PutMapping
    public AjaxResult edit(@RequestBody AiConfig config) {
        requireConfigPermission();
        // 客户端拿到的是脱敏值；回传含 **** 视为「未修改密钥」，保留库中原文，避免把掩码存成真 key。
        if (config.getApiKey() != null && config.getApiKey().contains("****")) {
            AiConfig current = config.getId() != null ? aiConfigService.getById(config.getId()) : null;
            config.setApiKey(current != null ? current.getApiKey() : null);
        }
        config.setUpdateTime(LocalDateTime.now());
        aiConfigService.updateById(config);
        return AjaxResult.success();
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        requireConfigPermission();
        aiConfigService.removeById(id);
        return AjaxResult.success();
    }

    @PutMapping("/{id}/default")
    public AjaxResult setDefault(@PathVariable Long id) {
        requireConfigPermission();
        aiConfigService.setDefault(id);
        return AjaxResult.success();
    }

    /**
     * 用保存后的配置发一次最小请求，验证地址、密钥与模型名是否可用。
     * 成功只回一小段回复摘要，失败回可读原因，均不包含密钥本身。
     */
    @PostMapping("/{id}/test")
    public AjaxResult test(@PathVariable Long id) {
        requireConfigPermission();
        AiConfig config = aiConfigService.getById(id);
        if (config == null) return AjaxResult.error("模型配置不存在");
        try {
            String reply = aiOrchestrationService.testConnection(config);
            return AjaxResult.success("连接正常", Map.of("reply", abbreviate(reply)));
        } catch (Exception e) {
            return AjaxResult.error("连接失败：" + reason(e));
        }
    }

    private static String reason(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        message = message.trim();
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }

    private static String abbreviate(String reply) {
        if (reply == null) return "";
        String trimmed = reply.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) + "…" : trimmed;
    }
}
