package cn.ekko.groupchat.document.controller;

import cn.ekko.groupchat.document.dto.DocumentResponse;
import cn.ekko.groupchat.document.dto.DocumentUploadRequest;
import cn.ekko.groupchat.document.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识文档管理控制器（需管理员登录），提供文档上传、详情查询、重新索引和删除接口：
 * <ul>
 *   <li>{@code POST /api/v1/documents}：multipart 上传文档，触发解析入库流水线；</li>
 *   <li>{@code GET /api/v1/documents/{id}}：查询文档处理状态与元信息；</li>
 *   <li>{@code POST /api/v1/documents/{id}/reindex}：使用已有 Markdown 重建索引；</li>
 *   <li>{@code DELETE /api/v1/documents/{id}}：删除文档记录与 ES 分块（OSS 文件保留）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传知识文档。
     * <p>以 multipart 表单接收文件、标题与产品型号，落库后触发
     * 按格式选择 MinerU 或本地转换，再以事件驱动切片和向量化，返回 201。
     *
     * @param request 上传请求，含文件、标题、产品型号和可选切分策略
     * @return 新建文档的元信息与初始处理状态
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@Valid @ModelAttribute DocumentUploadRequest request) {
        return DocumentResponse.from(
                documentService.upload(
                        request.getFile(),
                        request.getTitle(),
                        request.getProductModel(),
                        request.getChunkStrategy()
                )
        );
    }

    /**
     * 查询文档详情。
     * <p>返回指定文档的元信息与当前处理状态（如 PARSING、CHUNKING、INDEXING、PUBLISHED、FAILED）。
     *
     * @param documentId 文档 ID
     * @return 文档元信息与处理状态
     */
    @GetMapping("/{documentId}")
    public DocumentResponse detail(@PathVariable long documentId) {
        return DocumentResponse.from(documentService.get(documentId));
    }

    /** 使用 OSS 中已有 Markdown 和当前规则版本重建 MySQL/ES 分片。 */
    @PostMapping("/{documentId}/reindex")
    public DocumentResponse reindex(@PathVariable long documentId) {
        return DocumentResponse.from(documentService.reindex(documentId));
    }

    /**
     * 删除知识文档。
     * <p>先清理 ES 中的向量分块，再删除 MySQL 分片和文档记录，返回 204；
     * OSS 上的原始文件与解析结果保留，用于溯源或重建索引。
     *
     * @param documentId 待删除的文档 ID
     */
    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long documentId) {
        documentService.delete(documentId);
    }
}
