package cn.xinghuaerp.system.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片存储分类配置。
 *
 * <p>配置形如 minio.categories.{分类编码}: {对象前缀}，前端上传时传分类编码，
 * 后端用它定位对象存储前缀。新增分类只需改配置，不需要改代码。
 */
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioStorageProperties
{
    /** 分类编码 -> 对象存储前缀 */
    private Map<String, String> categories = defaultCategories();

    public Map<String, String> getCategories()
    {
        return categories;
    }

    public void setCategories(Map<String, String> categories)
    {
        this.categories = categories;
    }

    /**
     * 配置缺失时的兜底，避免上传接口整体不可用。
     */
    private static Map<String, String> defaultCategories()
    {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("goods", "goods");
        defaults.put("supplier-license", "supplier/license");
        defaults.put("avatar", "user/avatar");
        return defaults;
    }

    /**
     * 解析分类对应的对象前缀，未配置的分类返回 null。
     */
    public String resolvePrefix(String category)
    {
        if (category == null || categories == null)
        {
            return null;
        }
        String prefix = categories.get(category);
        if (prefix == null || prefix.isBlank())
        {
            return null;
        }
        String normalized = prefix.trim().replaceAll("^/+", "").replaceAll("/+$", "");
        return normalized.isEmpty() ? null : normalized;
    }
}
