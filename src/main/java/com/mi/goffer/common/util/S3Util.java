package com.mi.goffer.common.util;

import com.mi.goffer.common.config.S3Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.net.URI;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/12
 * @Description: S3 兼容存储工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3Util {

    private final S3Config s3Config;

    /**
     * 创建 S3 客户端
     */
    public S3Client createClient() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Config.getAccessKey(),
                s3Config.getSecretKey());

        return S3Client.builder()
                .endpointOverride(URI.create(s3Config.getEndpoint()))
                .region(Region.of(s3Config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(s3Config.isPathStyleAccess())
                .build();
    }

    /**
     * 上传文件到 S3
     *
     * @param file     要上传的文件
     * @param filePath 文件在 S3 中的存储路径
     * @return 文件访问 URL，如果上传失败则返回 null
     */
    public String uploadFile(MultipartFile file, String filePath) {
        S3Client s3Client = createClient();
        try {
            log.info("开始上传文件到 S3，Bucket: {}, 文件路径: {}", s3Config.getBucketName(), filePath);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(filePath)
                    .contentType(file.getContentType())
                    .build();

            PutObjectResponse response = s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("文件上传完成，ETag: {}", response.eTag());

            String fileUrl = s3Config.getEndpoint() + "/" + s3Config.getBucketName() + "/" + filePath;

            log.info("文件上传成功，URL: {}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("文件上传失败，Bucket: {}, 文件路径: {}", s3Config.getBucketName(), filePath, e);
            return null;
        } finally {
            s3Client.close();
        }
    }

    /**
     * 上传用户头像
     *
     * @param file   要上传的文件
     * @param userId 用户 ID
     * @return 文件访问 URL，如果上传失败则返回 null
     */
    public String uploadAvatar(MultipartFile file, String userId) {
        try {
            String originalFilename = file.getOriginalFilename();
            String fileExtension = ".jpg";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filePath = "avatar/" + userId + fileExtension;
            return uploadFile(file, filePath);
        } catch (Exception e) {
            log.error("上传用户头像失败", e);
            return null;
        }
    }

    /**
     * 上传特定类型文件
     *
     * @param file 要上传的文件
     * @param type 文件类型（如 avatar、document 等）
     * @param id   关联的 ID
     * @return 文件访问 URL，如果上传失败则返回 null
     */
    public String uploadFileWithType(MultipartFile file, String type, String id) {
        try {
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filePath = type + "/" + id + fileExtension;
            return uploadFile(file, filePath);
        } catch (Exception e) {
            log.error("上传文件失败，类型: {}, ID: {}", type, id, e);
            return null;
        }
    }

    /**
     * 删除文件
     *
     * @param filePath 文件路径
     * @return 是否删除成功
     */
    public boolean deleteFile(String filePath) {
        S3Client s3Client = createClient();
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(filePath)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("文件删除成功，路径: {}", filePath);
            return true;
        } catch (Exception e) {
            log.error("文件删除失败，路径: {}", filePath, e);
            return false;
        } finally {
            s3Client.close();
        }
    }
}
