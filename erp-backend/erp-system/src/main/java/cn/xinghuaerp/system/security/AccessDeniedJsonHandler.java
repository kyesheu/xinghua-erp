package cn.xinghuaerp.system.security;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.json.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

/** 已登录但权限不足时返回 403 + JSON 中文提示；
 *  控制器内抛出的 {@link AccessDeniedException} 也会经 ExceptionTranslationFilter 走到这里。 */
@Component
public class AccessDeniedJsonHandler implements AccessDeniedHandler {
    Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        PrintWriter writer = null;
        try {
            String message = accessDeniedException != null && accessDeniedException.getMessage() != null
                    ? accessDeniedException.getMessage() : "没有访问权限，请联系管理员授权";
            AjaxResult res = AjaxResult.error(403, message);
            writer = response.getWriter();
            writer.append(JsonUtils.write(res));
        } catch (IOException e) {
            log.error(e.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
