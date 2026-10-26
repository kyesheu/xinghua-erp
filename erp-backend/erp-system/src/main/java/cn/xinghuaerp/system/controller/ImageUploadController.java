package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.entity.SysOss;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.system.service.SysOssService;
import cn.xinghuaerp.system.storage.MinioStorageProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
public class ImageUploadController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadController.class);
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "gif", "image/gif");
    /** 对象按年月分目录，便于归档、迁移与清理。 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MinioClient minioClient;
    private final SysOssService sysOssService;
    private final MinioStorageProperties storageProperties;
    private final String bucket;
    private final String publicUrl;

    public ImageUploadController(MinioClient minioClient,
                                 SysOssService sysOssService,
                                 MinioStorageProperties storageProperties,
                                 @Value("${minio.bucket}") String bucket,
                                 @Value("${minio.public-url}") String publicUrl) {
        this.minioClient = minioClient;
        this.sysOssService = sysOssService;
        this.storageProperties = storageProperties;
        this.bucket = bucket;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @PostMapping("/api/sys-api/images/upload")
    public AjaxResult uploadImage(@RequestParam("file") MultipartFile file,
                                  @RequestParam("category") String category) {
        String prefix = storageProperties.resolvePrefix(category);
        if (prefix == null) {
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

        String storedName = UUID.randomUUID() + "." + suffix;
        String objectName = prefix + "/" + YearMonth.now().format(MONTH_FORMAT) + "/" + storedName;
        String url = publicUrl + "/" + bucket + "/" + objectName;
        try (InputStream input = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(input, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 图片上传失败，bucket={}, object={}", bucket, objectName, e);
            return AjaxResult.error(500, "图片上传失败，请检查 MinIO 服务及存储桶配置");
        }

        SysOss oss = buildRecord(storedName, originalFilename, suffix, objectName, url, category, file.getSize());
        try {
            sysOssService.save(oss);
        } catch (RuntimeException e) {
            // 没有台账的文件不允许留在存储里，否则就是无人认领的孤儿对象。
            log.error("图片已上传但写入 sys_oss 失败，回滚对象 bucket={}, object={}", bucket, objectName, e);
            removeObjectQuietly(objectName);
            return AjaxResult.error(500, "图片保存失败，请稍后重试");
        }

        AjaxResult result = AjaxResult.success();
        result.put("ossId", oss.getOssId());
        result.put("url", url);
        result.put("fileName", originalFilename);
        result.put("newFileName", objectName);
        result.put("originalFilename", originalFilename);
        return result;
    }

    private SysOss buildRecord(String storedName, String originalFilename, String suffix,
                               String objectName, String url, String category, long fileSize) {
        SysOss oss = new SysOss();
        oss.setFileName(storedName);
        oss.setOriginalName(originalFilename);
        oss.setFileSuffix(suffix);
        oss.setUrl(url);
        oss.setObjectName(objectName);
        oss.setBucket(bucket);
        oss.setCategory(category);
        oss.setFileSize(fileSize);
        oss.setOrderNum(0);
        oss.setStatus("0");
        oss.setDelFlag("0");
        oss.setCreateBy(currentUsername());
        oss.setCreateTime(LocalDateTime.now());
        return oss;
    }

    private String currentUsername() {
        try {
            return getUsername();
        } catch (RuntimeException e) {
            return "system";
        }
    }

    private void removeObjectQuietly(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            log.error("回滚删除 MinIO 对象失败，需人工清理 bucket={}, object={}", bucket, objectName, e);
        }
    }
}
