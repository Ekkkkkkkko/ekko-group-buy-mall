package cn.ekko.groupchat.document.client;

import cn.ekko.groupchat.config.GroupChatProperties;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.nio.charset.StandardCharsets;

/**
 * 阿里云 OSS 客户端封装，提供对象上传与预签名下载链接生成能力。
 *
 * <p>用于存储上传的原始文档与 MinerU 解析后的 Markdown 文件；
 * 预签名链接供 MinerU 拉取待解析文件。
 */
@Component
@RequiredArgsConstructor
public class AliyunOssClient {

    private final OSSClient ossClient;
    private final GroupChatProperties properties;

    public void put(String objectKey, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.newBuilder()
                .bucket(properties.getOss().getBucket())
                .key(objectKey)
                .contentType(contentType)
                .body(BinaryData.fromBytes(content))
                .build();
        ossClient.putObject(request);
    }

    public String presignGet(String objectKey, Duration expiration) {
        GetObjectRequest request = GetObjectRequest.newBuilder()
                .bucket(properties.getOss().getBucket())
                .key(objectKey)
                .build();
        PresignOptions options = PresignOptions.newBuilder()
                .expiration(expiration)
                .build();
        return ossClient.presign(request, options).url();
    }

    /** 读取已保存的 MinerU 原始 Markdown，供不重新解析文件的索引重建使用。 */
    public String getText(String objectKey) {
        GetObjectRequest request = GetObjectRequest.newBuilder()
                .bucket(properties.getOss().getBucket())
                .key(objectKey)
                .build();
        try (GetObjectResult result = ossClient.getObject(request)) {
            return new String(result.body().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("读取 OSS Markdown 失败: " + objectKey, exception);
        }
    }
}
