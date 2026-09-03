package cn.ekko.groupchat.document.dto;

import cn.ekko.groupchat.document.service.chunk.ChunkingStrategyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传请求体（multipart）：文件必填，标题必填且限长 200，
 * 产品型号和切分策略选填；策略不传时使用系统默认值。
 */
@Getter
@Setter
public class DocumentUploadRequest {

    @NotNull(message = "上传文件不能为空")
    private MultipartFile file;

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题不能超过200个字符")
    private String title;

    @Size(max = 100, message = "产品型号不能超过100个字符")
    private String productModel;

    /** 可选；不传时使用 application.yml 中配置的默认策略。 */
    private ChunkingStrategyType chunkStrategy;

}
