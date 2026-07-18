package cn.xinghuaerp.system.notify;

import tools.jackson.databind.node.ObjectNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.utils.http.OkHttpClientHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
public class WeChatNotifier {

    public boolean notify(String webhook, String title, String content) {
        try {
            ObjectNode markdown = JsonUtils.object();
            markdown.put("content", "## " + title + "\n" + content);

            ObjectNode body = JsonUtils.object();
            body.put("msgtype", "markdown");
            body.set("markdown", markdown);

            String result = OkHttpClientHelper.post(webhook, body.toString());
            boolean ok = result != null && result.contains("\"errcode\":0");
            if (!ok) log.warn("企微推送返回异常: {}", result);
            return ok;
        } catch (Exception e) {
            log.error("企微推送失败", e);
            return false;
        }
    }
}
