package cn.xinghuaerp.open.common;

import okhttp3.*;

import java.io.IOException;
import java.util.Map;

/**
 * @program: zinBaaS
 * @description
 * @author: qi
 * @create: 2024-08-28 15:20
 **/
public class OkHttpClientHelper {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build();

    // 执行 GET 请求
    public static String get(String url) throws IOException {
        // 平台级限流统一收口在这里，49 个调用方不必各自关心配额。
        PlatformRateLimiter.acquire(url);
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("平台HTTP状态异常：" + response.code());
            return readBounded(response.body());
        }
    }

    // 执行 POST 请求
    public static String post(String url, String json) throws IOException {
        PlatformRateLimiter.acquire(url);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("平台HTTP状态异常：" + response.code());
            return readBounded(response.body());
        }
    }


//    /**
//    * @Description: 实名认证post
//    * @Param: [appCode, url, params]
//    * @return: java.lang.String
//    * @Author: qi
//    * @Date: 2024/9/11 10:38
//    */
//    public static String postForm(String appCode, String url, Map<String, String> params) throws IOException {
//
//        FormBody.Builder formbuilder = new FormBody.Builder();
//        Iterator<String> it = params.keySet().iterator();
//        while (it.hasNext()) {
//            String key = it.next();
//            formbuilder.add(key, params.get(key));
//        }
//        FormBody body = formbuilder.build();
//        Request request = new Request.Builder().url(url).addHeader("Authorization", "APPCODE " + appCode).post(body).build();
//        Response response = client.newCall(request).execute();
//        System.out.println("返回状态码" + response.code() + ",message:" + response.message());
//        String result = response.body().string();
//        return result;
//    }

    /**
     * 发送 POST 请求并返回响应结果
     *
     * @param url      请求的 URL
     * @param params   表单参数
     * @param headers  HTTP 请求头
     * @return 响应结果
     * @throws IOException
     */
    public static String postForm(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        PlatformRateLimiter.acquire(url);

        FormBody.Builder formBuilder = new FormBody.Builder();

        // 添加表单参数
        for (Map.Entry<String, String> entry : params.entrySet()) {
            formBuilder.add(entry.getKey(), entry.getValue());
        }

        FormBody body = formBuilder.build();

        Request.Builder requestBuilder = new Request.Builder().url(url).post(body);

        // 添加 HTTP 请求头
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }

        Request request = requestBuilder.build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("平台HTTP状态异常：" + response.code());
            return readBounded(response.body());
        }
    }
    static String readBounded(ResponseBody body) throws IOException {
        if(body==null)throw new IOException("平台响应为空");
        int limit=8*1024*1024;
        if(body.contentLength()>limit)throw new IOException("平台响应超过8MiB，请缩小查询范围");
        try(var stream=body.byteStream()) {
            byte[] bytes=stream.readNBytes(limit+1);
            if(bytes.length>limit)throw new IOException("平台响应超过8MiB，请缩小查询范围");
            var type=body.contentType();var charset=type==null?java.nio.charset.StandardCharsets.UTF_8:type.charset(java.nio.charset.StandardCharsets.UTF_8);
            return new String(bytes,charset);
        }
    }
}
