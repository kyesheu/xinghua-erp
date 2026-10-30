package cn.xinghuaerp.platform.controller;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.open.common.PlatformRateLimiter;
import cn.xinghuaerp.system.entity.OShopPlatform;
import cn.xinghuaerp.platform.bo.JdTokenCreateBo;
import cn.xinghuaerp.system.facade.OShopPlatformFacade;
import cn.xinghuaerp.system.facade.OShopFacade;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RequestMapping("/api/oms-api/jd")
@AllArgsConstructor
@RestController
public class JdOAuthController {
    private final OShopPlatformFacade platformService;
    private final OShopFacade shopService;
    private static Logger log = LoggerFactory.getLogger(JdOAuthController.class);

    @GetMapping("/oauth")
    public AjaxResult OAuth(@RequestParam Long shopId) throws IOException, InterruptedException {
        //查询店铺信息
        var shop = shopService.selectShopById(shopId);
        OShopPlatform platform = platformService.selectById(shop.getType());

        String url = "https://open-oauth.jd.com/oauth2/to_login?app_key=" + platform.getAppKey()+
                "&response_type=code&redirect_uri=" + URLEncoder.encode(platform.getRedirectUri(), "UTF-8")+
                "&state="+shopId+"&scope=snsapi_base";
       return AjaxResult.success("SUCCESS",url);
    }

    @PostMapping("/tokenCreate")
    public AjaxResult callback(@RequestBody JdTokenCreateBo bo) throws IOException, InterruptedException {
        log.info("京东授权返回code");
        if (!StringUtils.hasText(bo.getCode())) return AjaxResult.error("code不能为空");
        OShopPlatform platform = platformService.getById(bo.getShopType());
//        String code = request.getParameter("code");//gwide6
//        JdClient client=new DefaultJdClient(serverUrl,accessToken,appKey,appSecret);

        String url = "https://open-oauth.jd.com/oauth2/access_token?app_key=" + platform.getAppKey() + "&app_secret=" + platform.getAppSecret() +
                "&grant_type=authorization_code&code=" + bo.getCode();

        // 这是唯一没走 OkHttpClientHelper 的京东出口，同样先向限流器申请令牌；
        // 本方法已声明 throws IOException，超限时直接冒泡给调用方（换 token 失败要重试，不能静默成功）。
        PlatformRateLimiter.acquire(url);
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url)).header("Content-Type", "application/json").GET()
                .build();
        HttpResponse<String> result = client.send(request, HttpResponse.BodyHandlers.ofString());
        String s = result.body();
        JsonNode resultJson = JsonUtils.readTree(result.body());
        if(JsonUtils.integer(resultJson, "code")==0){
            //保存accessToken
            String token = JsonUtils.text(resultJson, "access_token");
            shopService.updateSessionKey(bo.getShopId(),token);
            return AjaxResult.success("SUCCESS");
        }else {
            return AjaxResult.error(JsonUtils.text(resultJson, "msg"));
        }

    }
}
