package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

/** MySQL 原子准入与审计：不同应用实例共用额度、频率及在途租约。 */
@Service
@RequiredArgsConstructor
public class AiRequestAdmission {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final ToolToken tokens;
    private final BusinessAccessService access;
    @Value("${erp.ai.reservation-tokens:220000}") private long reservation=220000;
    @Value("${erp.ai.daily-user-tokens:1000000}") private long userBudget=1000000;
    @Value("${erp.ai.daily-merchant-tokens:3000000}") private long merchantBudget=3000000;
    @Value("${erp.ai.daily-global-tokens:10000000}") private long globalBudget=10000000;
    @Value("${erp.ai.user-per-minute:10}") private int userRate=10;
    @Value("${erp.ai.merchant-per-minute:30}") private int merchantRate=30;
    @Value("${erp.ai.global-per-minute:60}") private int globalRate=60;
    @Value("${erp.ai.user-concurrency:2}") private int userConcurrency=2;
    @Value("${erp.ai.merchant-concurrency:4}") private int merchantConcurrency=4;
    @Value("${erp.ai.global-concurrency:8}") private int globalConcurrency=8;
    @Value("${erp.ai.ingress-user-per-minute:60}") private int ingressUserRate=60;
    @Value("${erp.ai.ingress-global-per-minute:300}") private int ingressGlobalRate=300;
    @Value("${erp.ai.enabled:true}") private boolean enabled=true;
    @Value("${erp.ai.lease-seconds:180}") private int leaseSeconds=180;

    public record Permit(String id, long userId, long merchantId, String day, long reserved) {}

    public Permit begin(String toolToken,String path,String model) {
        if(!enabled)throw new AiAdmissionRejected("AI 模型调用已关闭");
        if(reservation<=0 || userBudget<=0 || merchantBudget<=0 || globalBudget<=0 || leaseSeconds<90)
            throw new IllegalStateException("AI 资源配额配置无效");
        long user=toolToken==null || toolToken.isBlank()?SecurityUtils.getUserId():tokens.verify(toolToken);
        var scope=access.scope(user);
        if(scope==null)throw new IllegalStateException("无法确定 AI 调用主体");
        long merchant=scope.merchantId()==null?0:scope.merchantId();
        return new TransactionTemplate(transactionManager).execute(status -> {
            LocalDateTime now=now();String day=now.toLocalDate().toString();String minute=now.withSecond(0).withNano(0).toString();
            jdbc.update("INSERT INTO ai_circuit_state(circuit_key,failures) VALUES ('python',0) ON DUPLICATE KEY UPDATE circuit_key=circuit_key");
            var circuit=jdbc.queryForMap("SELECT failures,open_until,probe_until,manual_disabled FROM ai_circuit_state WHERE circuit_key='python' FOR UPDATE");
            if(Boolean.TRUE.equals(circuit.get("manual_disabled")) || circuit.get("manual_disabled") instanceof Number number && number.intValue()!=0)throw new AiAdmissionRejected("AI 模型调用已被管理员暂停");
            var open=time(circuit.get("open_until"));
            var probe=time(circuit.get("probe_until"));
            if(open!=null && open.isAfter(now) || probe!=null && probe.isAfter(now))
                throw new AiAdmissionRejected("AI 服务暂时熔断，请稍后重试或使用规则汇总");
            if(open!=null)jdbc.update("UPDATE ai_circuit_state SET probe_until=? WHERE circuit_key='python'",now.plusSeconds(leaseSeconds));
            var budgets=budgetKeys(user,merchant,day);
            var rates=Map.of("r:g:"+minute,globalRate,"r:m:"+merchant+":"+minute,merchantRate,"r:u:"+user+":"+minute,userRate);
            var keys=new ArrayList<String>(budgets);keys.addAll(rates.keySet());Collections.sort(keys);
            for(String key:keys) {
                jdbc.update("INSERT INTO ai_usage_bucket(bucket_key,used_tokens,requests) VALUES (?,0,0) ON DUPLICATE KEY UPDATE bucket_key=bucket_key",key);
                jdbc.queryForObject("SELECT used_tokens FROM ai_usage_bucket WHERE bucket_key=? FOR UPDATE",Long.class,key);
            }
            for(String key:budgets) {
                long limit=key.startsWith("b:g:")?globalBudget:key.startsWith("b:m:")?merchantBudget:userBudget;
                long used=jdbc.queryForObject("SELECT used_tokens FROM ai_usage_bucket WHERE bucket_key=?",Long.class,key);
                if(used>limit-reservation)throw new AiAdmissionRejected("AI 当日 Token 预算不足");
            }
            for(var entry:rates.entrySet()) {
                long calls=jdbc.queryForObject("SELECT requests FROM ai_usage_bucket WHERE bucket_key=?",Long.class,entry.getKey());
                if(entry.getValue()<=0 || calls>=entry.getValue())throw new AiAdmissionRejected("AI 请求过于频繁，请稍后重试");
            }
            long global=countActive("",now), mine=countActive(" AND user_id="+user,now), tenant=countActive(" AND merchant_id="+merchant,now);
            if(global>=globalConcurrency || mine>=userConcurrency || tenant>=merchantConcurrency)throw new AiAdmissionRejected("AI 在途请求已满，请稍后重试");
            for(String key:budgets)jdbc.update("UPDATE ai_usage_bucket SET used_tokens=used_tokens+? WHERE bucket_key=?",reservation,key);
            for(String key:rates.keySet())jdbc.update("UPDATE ai_usage_bucket SET requests=requests+1 WHERE bucket_key=?",key);
            String id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO ai_call_request(request_id,trace_id,user_id,merchant_id,bucket_day,path,model_name,reserved_tokens,status,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?,'RUNNING',?,?)",id,java.util.Objects.requireNonNullElse(org.slf4j.MDC.get("requestId"),id),user,merchant,day,path,model==null?"":model.substring(0,Math.min(128,model.length())),reservation,now,now.plusSeconds(leaseSeconds));
            return new Permit(id,user,merchant,day,reservation);
        });
    }

    /** usage 缺失保持预占，不能通过断连或超时无条件退回预算。重复结算无副作用。 */
    public void finish(Permit permit, Long input, Long output, boolean known, String outcome) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var rows=jdbc.queryForList("SELECT status FROM ai_call_request WHERE request_id=? FOR UPDATE",permit.id());
            if(rows.size()!=1 || !"RUNNING".equals(rows.get(0).get("status")))return;
            jdbc.queryForMap("SELECT failures FROM ai_circuit_state WHERE circuit_key='python' FOR UPDATE");
            boolean valid=known && input!=null && output!=null && input>=0 && output>=0 && input<=Long.MAX_VALUE-output;
            long charged=valid?input+output:permit.reserved();
            var keys=budgetKeys(permit.userId(),permit.merchantId(),permit.day());Collections.sort(keys);
            for(String key:keys)jdbc.queryForObject("SELECT used_tokens FROM ai_usage_bucket WHERE bucket_key=? FOR UPDATE",Long.class,key);
            for(String key:keys)jdbc.update("UPDATE ai_usage_bucket SET used_tokens=used_tokens-?+? WHERE bucket_key=?",permit.reserved(),charged,key);
            if("UPSTREAM_ERROR".equals(outcome)) {
                jdbc.update("UPDATE ai_circuit_state SET failures=failures+1 WHERE circuit_key='python'");
                Integer failures=jdbc.queryForObject("SELECT failures FROM ai_circuit_state WHERE circuit_key='python'",Integer.class);
                if(failures!=null && failures>=3)jdbc.update("UPDATE ai_circuit_state SET open_until=?,probe_until=NULL WHERE circuit_key='python'",now().plusSeconds(30));
            } else if("COMPLETED".equals(outcome)) {
                jdbc.update("UPDATE ai_circuit_state SET failures=0,open_until=NULL,probe_until=NULL WHERE circuit_key='python'");
            }
            jdbc.update("UPDATE ai_call_request SET status=?,input_tokens=?,output_tokens=?,outcome=?,finished_at=? WHERE request_id=?",valid?"SETTLED":"UNKNOWN",valid?input:null,valid?output:null,outcome,now(),permit.id());
        });
    }
    public void checkIngress(long userId) {
        if(userId<=0)throw new AiAdmissionRejected("AI请求需要有效用户");
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            String minute=now().withSecond(0).withNano(0).toString();
            var limits=Map.of("i:g:"+minute,ingressGlobalRate,"i:u:"+userId+":"+minute,ingressUserRate);
            var keys=new ArrayList<>(limits.keySet());Collections.sort(keys);
            for(String key:keys) {
                jdbc.update("INSERT INTO ai_usage_bucket(bucket_key,used_tokens,requests) VALUES (?,0,0) ON DUPLICATE KEY UPDATE bucket_key=bucket_key",key);
                Long count=jdbc.queryForObject("SELECT requests FROM ai_usage_bucket WHERE bucket_key=? FOR UPDATE",Long.class,key);
                if(count==null || count>=limits.get(key))throw new AiAdmissionRejected("AI入口请求过于频繁，请稍后重试");
            }
            for(String key:keys)jdbc.update("UPDATE ai_usage_bucket SET requests=requests+1 WHERE bucket_key=?",key);
        });
    }

    public void setEnabled(boolean enabled) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            jdbc.update("INSERT INTO ai_circuit_state(circuit_key,failures) VALUES ('python',0) ON DUPLICATE KEY UPDATE circuit_key=circuit_key");
            jdbc.update("UPDATE ai_circuit_state SET manual_disabled=? WHERE circuit_key='python'",!enabled);
        });
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${erp.ai.expired-lease-check-ms:60000}")
    public void markExpired() {
        jdbc.update("UPDATE ai_call_request SET status='UNKNOWN',outcome='LEASE_EXPIRED',finished_at=CURRENT_TIMESTAMP WHERE status='RUNNING' AND expires_at<CURRENT_TIMESTAMP LIMIT 500");
    }
    private ArrayList<String> budgetKeys(long user,long merchant,String day) {
        return new ArrayList<>(List.of("b:g:"+day,"b:m:"+merchant+":"+day,"b:u:"+user+":"+day));
    }
    private long countActive(String suffix,LocalDateTime now) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_call_request WHERE status='RUNNING' AND expires_at>?"+suffix,Long.class,now);
    }
    private LocalDateTime time(Object value) {return value==null?null:value instanceof LocalDateTime local?local:((java.sql.Timestamp)value).toLocalDateTime();}
    private LocalDateTime now() {return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}
}
