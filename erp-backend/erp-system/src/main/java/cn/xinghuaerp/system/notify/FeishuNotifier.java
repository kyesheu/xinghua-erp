package cn.xinghuaerp.system.notify;

import tools.jackson.databind.node.ObjectNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.utils.http.OkHttpClientHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
public class FeishuNotifier {

    public boolean notify(String webhook, String title, String content) {
        try {
            ObjectNode body = JsonUtils.object();
            body.put("msg_type", "text");
            body.set("content", JsonUtils.object(Map.of("text", title + "\n\n" + content)));

            String result = OkHttpClientHelper.post(webhook, body.toString());
            boolean ok = result != null && (result.contains("\"code\":0") || result.contains("\"StatusCode\":0"));
            if (!ok) log.warn("飞书推送返回异常: {}", result);
            return ok;
        } catch (Exception e) {
            log.error("飞书推送失败", e);
            return false;
        }
    }
}
