package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.entity.SysOss;
import cn.xinghuaerp.system.service.SysOssService;
import cn.xinghuaerp.system.storage.MinioStorageProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 图片上传的存储路径、分类校验与 sys_oss 台账契约测试，不触碰真实 MinIO 与数据库。 */
class ImageUploadControllerTest {

    private static final String BUCKET = "xinghua-erp";
    private static final String PUBLIC_URL = "http://localhost:19000";

    private MinioClient minioClient;
    private SysOssService sysOssService;
    private MinioStorageProperties properties;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        sysOssService = mock(SysOssService.class);
        properties = new MinioStorageProperties();
    }

    @Test
    void goodsImageGoesUnderGoodsPrefixInMonthFolderAndIsRecorded() throws Exception {
        var controller = controller();
        var result = controller.uploadImage(png("a.png"), "goods");

        assertEquals(200, result.get("code"));
        var putArgs = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putArgs.capture());
        String objectName = putArgs.getValue().object();
        String month = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        assertTrue(objectName.startsWith("goods/" + month + "/"), objectName);
        assertTrue(objectName.endsWith(".png"), objectName);
        assertEquals(PUBLIC_URL + "/" + BUCKET + "/" + objectName, result.get("url"));

        var ossArgs = ArgumentCaptor.forClass(SysOss.class);
        verify(sysOssService).save(ossArgs.capture());
        var oss = ossArgs.getValue();
        assertEquals("goods", oss.getCategory());
        assertEquals(objectName, oss.getObjectName());
        assertEquals(BUCKET, oss.getBucket());
        assertEquals("a.png", oss.getOriginalName());
        assertEquals("png", oss.getFileSuffix());
        assertEquals(result.get("url"), oss.getUrl());
        assertEquals("0", oss.getStatus());
        assertEquals("0", oss.getDelFlag());
        assertEquals("tester", oss.getCreateBy());
        assertNotNull(oss.getCreateTime());
        assertTrue(oss.getFileSize() > 0);
    }

    @Test
    void supplierLicenseAndAvatarUseTheirOwnPrefixes() throws Exception {
        var controller = controller();

        var supplier = ArgumentCaptor.forClass(PutObjectArgs.class);
        controller.uploadImage(png("lic.png"), "supplier-license");
        verify(minioClient).putObject(supplier.capture());
        assertTrue(supplier.getValue().object().startsWith("supplier/license/"));

        var avatar = ArgumentCaptor.forClass(PutObjectArgs.class);
        controller.uploadImage(png("me.png"), "avatar");
        verify(minioClient, times(2)).putObject(avatar.capture());
        assertTrue(avatar.getValue().object().startsWith("user/avatar/"));
    }

    @Test
    void unknownCategoryIsRejectedBeforeTouchingMinio() throws Exception {
        var result = controller().uploadImage(png("a.png"), "images");
        assertEquals(400, result.get("code"));
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        verify(sysOssService, never()).save(any(SysOss.class));
    }

    @Test
    void contentThatIsNotARealImageIsRejected() throws Exception {
        var fake = new FakeFile("a.png", "image/png", "not an image".getBytes());
        assertEquals(400, controller().uploadImage(fake, "goods").get("code"));
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        verify(sysOssService, never()).save(any(SysOss.class));
    }

    @Test
    void extensionThatLiesAboutContentIsRejected() throws Exception {
        var jpegBytes = image("jpg");
        var lying = new FakeFile("a.png", "image/png", jpegBytes);
        assertEquals(400, controller().uploadImage(lying, "goods").get("code"));
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        verify(sysOssService, never()).save(any(SysOss.class));
    }

    @Test
    void ledgerFailureRollsTheObjectBack() throws Exception {
        when(sysOssService.save(any(SysOss.class))).thenThrow(new IllegalStateException("db down"));
        var result = controller().uploadImage(png("a.png"), "goods");

        assertEquals(500, result.get("code"));
        var putArgs = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putArgs.capture());
        var removeArgs = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(removeArgs.capture());
        assertEquals(putArgs.getValue().object(), removeArgs.getValue().object());
    }

    @Test
    void customCategoriesFromConfigurationAreHonoured() throws Exception {
        properties.setCategories(java.util.Map.of("contract", "supplier/contract"));
        var putArgs = ArgumentCaptor.forClass(PutObjectArgs.class);
        var result = controller().uploadImage(png("c.png"), "contract");
        assertEquals(200, result.get("code"));
        verify(minioClient).putObject(putArgs.capture());
        assertTrue(putArgs.getValue().object().startsWith("supplier/contract/"));
    }

    private ImageUploadController controller() {
        return new ImageUploadController(minioClient, sysOssService, properties, BUCKET, PUBLIC_URL) {
            @Override public String getUsername() { return "tester"; }
        };
    }

    private MultipartFile png(String name) throws IOException {
        return new FakeFile(name, "image/png", image("png"));
    }

    /** 轻量 MultipartFile 替身，避免为测试引入 spring-test。 */
    private record FakeFile(String originalFilename, String contentType, byte[] content) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) { throw new UnsupportedOperationException(); }
    }

    private byte[] image(String format) throws IOException {
        var out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, out));
        return out.toByteArray();
    }
}
