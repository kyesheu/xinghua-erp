package cn.xinghuaerp.system.notify;

import tools.jackson.databind.node.ObjectNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.utils.http.OkHttpClientHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class DingTalkNotifier implements ChannelNotifier {

    public boolean notify(String webhook, String secret, String title, String content) {
        try {
            long timestamp = System.currentTimeMillis();
            String sign = "";

            if (StringUtils.hasText(secret)) {
                String stringToSign = timestamp + "\n" + secret;
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
                sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), "UTF-8");
            }

            String url = webhook + (webhook.contains("?") ? "&" : "?")
                    + "timestamp=" + timestamp + "&sign=" + sign;

            ObjectNode body = JsonUtils.object();
            body.put("msgtype", "markdown");
            body.set("markdown", JsonUtils.object(Map.of("title", title, "text", "## " + title + "\n\n" + content)));

            String result = OkHttpClientHelper.post(url, body.toString());
            boolean ok = result != null && result.contains("\"ok\"");
            if (!ok) log.warn("通知渠道返回失败，详情请在服务商控制台核对");
            return ok;
        } catch (Exception e) {
            log.warn("钉钉推送失败" + " errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }
    @Override public String type() { return "DINGTALK"; }
    @Override public boolean send(cn.xinghuaerp.system.entity.SysAlertChannel channel,String title,String content) {return notify(channel.getWebhookUrl(), channel.getSecret(), title, content);}
}
