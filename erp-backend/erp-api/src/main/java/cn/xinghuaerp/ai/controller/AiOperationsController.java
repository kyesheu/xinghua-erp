package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.ai.service.AiRequestAdmission;
import cn.xinghuaerp.order.facade.OrderNotificationOperations;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.common.AjaxResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;

@RestController
@RequestMapping("/api/ai/operations")
@RequiredArgsConstructor
public class AiOperationsController extends BaseController {
    private final BusinessAccessService access;
    private final AiAgentPermissionService permissions;
    private final JdbcTemplate jdbc;
    private final OrderNotificationOperations notifications;
    private final AiRequestAdmission admission;
    @org.springframework.beans.factory.annotation.Autowired private javax.sql.DataSource dataSource;
    private void requireAccess() {
        access.requireHeadquarters(getUserId());
        if(!permissions.allows(getUserId(),"ai:config:list"))throw new AccessDeniedException("无AI运行管理权限");
    }
    @GetMapping("/summary")
    public AjaxResult summary() {
        requireAccess();
        var usage=jdbc.queryForMap("SELECT COUNT(*) AS requests,COALESCE(SUM(input_tokens),0) AS inputTokens,COALESCE(SUM(output_tokens),0) AS outputTokens,SUM(CASE WHEN status='UNKNOWN' OR (status='RUNNING' AND expires_at<CURRENT_TIMESTAMP) THEN 1 ELSE 0 END) AS unknownRequests FROM ai_call_request WHERE created_at>=?",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).atStartOfDay());
        return AjaxResult.success(java.util.Map.of("databasePool",databasePool(),"usage",usage,"circuit",jdbc.queryForList("SELECT failures,open_until,probe_until,manual_disabled FROM ai_circuit_state WHERE circuit_key='python'"),"outbox",notifications.summary()));
    }
    private java.util.Map<String,Object> databasePool() {
        try {
            var hikari=dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class);
            var pool=hikari.getHikariPoolMXBean();
            if(pool==null)return java.util.Map.of("available",false);
            return java.util.Map.of("available",true,"active",pool.getActiveConnections(),"idle",pool.getIdleConnections(),"waiting",pool.getThreadsAwaitingConnection());
        } catch(Exception unavailable){return java.util.Map.of("available",false);}
    }
    public record EnabledRequest(Boolean enabled) {}
    @PostMapping("/enabled")
    public AjaxResult enabled(@RequestBody EnabledRequest request) {
        requireAccess();
        if(request==null || request.enabled()==null)throw new IllegalArgumentException("请指定enabled");
        admission.setEnabled(request.enabled());return AjaxResult.success();
    }
}
