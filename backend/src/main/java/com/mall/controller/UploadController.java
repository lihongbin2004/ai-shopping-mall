package com.mall.controller;

import com.mall.common.result.R;
import com.mall.storage.MinioService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@Api(tags = "文件上传接口")
public class UploadController {

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads";

    @Autowired(required = false)
    private MinioService minioService;

    @PostMapping("/image")
    @ApiOperation("上传图片（优先 MinIO，失败降级本地）")
    public R<String> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return R.error("文件不能为空");
        }
        String original = file.getOriginalFilename();
        if (original == null) {
            return R.error("文件名无效");
        }

        // 1) 优先走 MinIO
        if (minioService != null && minioService.isAvailable()) {
            try {
                String dateDir = new SimpleDateFormat("yyyyMMdd").format(new Date());
                String url = minioService.upload(file, "products/" + dateDir);
                return R.ok(url);
            } catch (Exception e) {
                System.err.println("[Upload] MinIO 上传失败，降级本地: " + e.getMessage());
            }
        }

        // 2) 降级：保存到本地 uploads/ 目录
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > -1) ext = original.substring(dot);

        String dateDir = new SimpleDateFormat("yyyyMMdd").format(new Date());
        File dir = new File(UPLOAD_DIR + File.separator + dateDir);
        if (!dir.exists() && !dir.mkdirs()) {
            return R.error("目录创建失败");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        File dest = new File(dir, filename);
        file.transferTo(dest);

        String url = "/uploads/" + dateDir + "/" + filename;
        return R.ok(url);
    }

    @GetMapping("/storage/info")
    @ApiOperation("查询当前存储后端（MinIO / 本地）")
    public R<java.util.Map<String, Object>> storageInfo() {
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("backend", (minioService != null && minioService.isAvailable()) ? "minio" : "local");
        info.put("minioEnabled", minioService != null);
        return R.ok(info);
    }
}
