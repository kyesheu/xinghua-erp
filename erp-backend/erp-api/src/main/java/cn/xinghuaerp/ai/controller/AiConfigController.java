package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 模型配置含 apiKey，属于敏感凭据：所有端点都要求 ai:config:list 权限，
 *  读取接口一律脱敏返回；编辑时客户端回传脱敏值则保留原密钥。 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/ai/config")
public class AiConfigController extends BaseController {

    private final AiConfigService aiConfigService;
    private final AiAgentPermissionService permissions;

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
}
