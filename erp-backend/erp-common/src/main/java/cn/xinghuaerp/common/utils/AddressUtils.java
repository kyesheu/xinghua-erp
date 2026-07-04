package cn.xinghuaerp.common.utils;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.common.constant.Constants;
import cn.xinghuaerp.common.utils.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 获取地址类
 *
 * @author xinghua
 */
public class AddressUtils
{
    private static final Logger log = LoggerFactory.getLogger(AddressUtils.class);

    // IP地址查询
    public static final String IP_URL = "http://whois.pconline.com.cn/ipJson.jsp";

    // 未知地址
    public static final String UNKNOWN = "XX XX";

    public static String getRealAddressByIP(String ip)
    {
        // 内网不查询
        if (IpUtils.internalIp(ip))
        {
            return "内网IP";
        }

            try
            {
                String rspStr = HttpUtils.sendGet(IP_URL, "ip=" + ip + "&json=true", Constants.GBK);
                if (!StringUtils.hasLength(rspStr))
                {
                    log.error("获取地理位置异常 {}", ip);
                    return UNKNOWN;
                }
                JsonNode obj = JsonUtils.readTree(rspStr);
                String region = JsonUtils.text(obj, "pro");
                String city = JsonUtils.text(obj, "city");
                return String.format("%s %s", region, city);
            }
            catch (Exception e)
            {
                log.error("获取地理位置异常 {}", ip);
            }

        return UNKNOWN;
    }
}
