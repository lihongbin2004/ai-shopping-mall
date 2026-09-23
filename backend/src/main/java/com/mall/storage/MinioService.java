package com.mall.storage;

import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储服务
 * - 启动时自动确保 bucket 存在并设为 public read（商品图要直接给浏览器访问）
 * - upload() 返回可访问的 URL
 * - 失败抛出异常由调用方降级到本地
 */
@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

    @Autowired(required = false)
    private MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.public-base-url}")
    private String publicBaseUrl;

    private boolean available = false;

    @PostConstruct
    public void init() {
        if (minioClient == null) {
            log.warn("[MinIO] 未启用或配置缺失，上传将走本地 fallback");
            return;
        }
        try {
            ensureBucket();
            available = true;
            log.info("[MinIO] 已连接，bucket={}", bucket);
        } catch (Exception e) {
            log.warn("[MinIO] 初始化失败，将走本地 fallback: {}", e.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("[MinIO] 自动创建 bucket: {}", bucket);
        }
        // 设为 public read（商品图片需要直接 URL 访问）
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
        minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * 上传文件，返回可访问的 URL
     */
    public String upload(MultipartFile file, String dir) throws Exception {
        if (!available) throw new IllegalStateException("MinIO 不可用");
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String objectName = (dir == null || dir.isEmpty() ? "" : dir + "/")
                + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }
        return publicBaseUrl + "/" + objectName;
    }

    /**
     * 预签名直传 URL（前端直传到 MinIO，不经过后端，省流量）
     */
    public String presignedPutUrl(String objectName, int expirySeconds) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .object(objectName)
                .expiry(expirySeconds, TimeUnit.SECONDS)
                .build());
    }

    /**
     * 删除文件
     */
    public void delete(String objectName) throws Exception {
        if (!available) return;
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
    }
}
