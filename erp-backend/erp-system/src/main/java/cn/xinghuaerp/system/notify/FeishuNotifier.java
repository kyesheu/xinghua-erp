package cn.xinghuaerp.system.notify;

import tools.jackson.databind.node.ObjectNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.utils.http.OkHttpClientHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
public class FeishuNotifier implements ChannelNotifier {

    public boolean notify(String webhook, String title, String content) {
        try {
            ObjectNode body = JsonUtils.object();
            body.put("msg_type", "text");
            body.set("content", JsonUtils.object(Map.of("text", title + "\n\n" + content)));

            String result = OkHttpClientHelper.post(webhook, body.toString());
            boolean ok = result != null && (result.contains("\"code\":0") || result.contains("\"StatusCode\":0"));
            if (!ok) log.warn("通知渠道返回失败，详情请在服务商控制台核对");
            return ok;
        } catch (Exception e) {
            log.warn("飞书推送失败" + " errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }
    @Override public String type() { return "FEISHU"; }
    @Override public boolean send(cn.xinghuaerp.system.entity.SysAlertChannel channel,String title,String content) {return notify(channel.getWebhookUrl(), title, content);}
}
