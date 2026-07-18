package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.AjaxResult;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class ImageUploadController {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadController.class);
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "gif", "image/gif");
    private static final Set<String> IMAGE_CATEGORIES = Set.of("supplier-license", "avatar", "goods");

    private final MinioClient minioClient;
    private final String bucket;
    private final String publicUrl;

    public ImageUploadController(MinioClient minioClient,
                                 @Value("${minio.bucket}") String bucket,
                                 @Value("${minio.public-url}") String publicUrl) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @PostMapping("/api/sys-api/images/upload")
    public AjaxResult uploadImage(@RequestParam("file") MultipartFile file,
                                  @RequestParam("category") String category) {
        if (!IMAGE_CATEGORIES.contains(category)) {
            return AjaxResult.error(400, "不支持的图片分类");
        }
        if (file.isEmpty()) {
            return AjaxResult.error(400, "请选择图片");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return AjaxResult.error(400, "不支持的格式");
        }
        String suffix = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String contentType = IMAGE_TYPES.get(suffix);
        if (contentType == null) {
            return AjaxResult.error(400, "仅支持 jpg、jpeg、png、gif 图片");
        }

        // 校验实际图片格式，不能仅信任扩展名或浏览器提供的 Content-Type。
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                return AjaxResult.error(400, "图片格式错误");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return AjaxResult.error(400, "图片格式错误");
            }
            ImageReader reader = readers.next();
            try {
                if (!contentType.equals(IMAGE_TYPES.get(reader.getFormatName().toLowerCase(Locale.ROOT)))) {
                    return AjaxResult.error(400, "图片内容与扩展名不匹配");
                }
                reader.setInput(imageInput);
                reader.getWidth(0);
                reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return AjaxResult.error(400, "图片格式错误");
        }

        String objectName = category + "/" + UUID.randomUUID() + "." + suffix;
        try (InputStream input = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(input, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            AjaxResult result = AjaxResult.success();
            result.put("url", publicUrl + "/" + bucket + "/" + objectName);
            result.put("fileName", originalFilename);
            result.put("newFileName", objectName);
            result.put("originalFilename", originalFilename);
            return result;
        } catch (Exception e) {
            log.error("MinIO 图片上传失败，bucket={}, object={}", bucket, objectName, e);
            return AjaxResult.error(500, "图片上传失败，请检查 MinIO 服务及存储桶配置");
        }
    }
}
