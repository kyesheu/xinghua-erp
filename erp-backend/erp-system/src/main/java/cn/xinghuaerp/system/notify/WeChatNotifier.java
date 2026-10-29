package cn.xinghuaerp.system.notify;

import tools.jackson.databind.node.ObjectNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.utils.http.OkHttpClientHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
public class WeChatNotifier implements ChannelNotifier {

    public boolean notify(String webhook, String title, String content) {
        try {
            ObjectNode markdown = JsonUtils.object();
            markdown.put("content", "## " + title + "\n" + content);

            ObjectNode body = JsonUtils.object();
            body.put("msgtype", "markdown");
            body.set("markdown", markdown);

            String result = OkHttpClientHelper.post(webhook, body.toString());
            boolean ok = result != null && result.contains("\"errcode\":0");
            if (!ok) log.warn("通知渠道返回失败，详情请在服务商控制台核对");
            return ok;
        } catch (Exception e) {
            log.warn("企微推送失败" + " errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }
    @Override public String type() { return "WECHAT"; }
    @Override public boolean send(cn.xinghuaerp.system.entity.SysAlertChannel channel,String title,String content) {return notify(channel.getWebhookUrl(), title, content);}
}
