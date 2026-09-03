package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.AliyunOssClient;
import cn.ekko.groupchat.document.client.ElasticsearchKnowledgeClient;
import cn.ekko.groupchat.document.client.MineruClient;
import cn.ekko.groupchat.document.entity.DocumentStatus;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.exception.DocumentNotFoundException;
import cn.ekko.groupchat.document.exception.DocumentProcessingException;
import cn.ekko.groupchat.document.mapper.KnowledgeDocumentMapper;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategyType;
import cn.ekko.groupchat.document.util.TikaFileTypeDetector;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 知识文档服务：文档入库的核心编排逻辑。
 * <p>
 * 负责校验上传、保存原文件，并按格式选择 MinerU 或本地转换器：
 * <ul>
 *     <li>MySQL：存储文档元信息与处理状态（knowledge_document 表）</li>
 *     <li>OSS：上传原始文件并生成供 MinerU 拉取的短期签名 URL</li>
 *     <li>MinerU：创建异步解析任务并返回 taskId</li>
 * </ul>
 * PDF/图片/Word 继续提交 MinerU；Markdown/TXT/Excel/CSV 本地转换后，
 * 统一通过事务后事件推进 CHUNKING → CHUNKED → INDEXING → PUBLISHED。
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    /** MySQL 文档记录操作（MyBatis-Plus Mapper） */
    private final KnowledgeDocumentMapper documentMapper;
    /** 阿里云 OSS 客户端，负责保存原文件并生成签名下载 URL */
    private final AliyunOssClient ossClient;
    /** MinerU 官方云 API 客户端，负责创建异步解析任务 */
    private final MineruClient mineruClient;
    /** Elasticsearch 客户端，负责知识分块的索引与删除 */
    private final ElasticsearchKnowledgeClient elasticsearchClient;
    /** MySQL 分片服务，负责保存父子分片正文和删除文档分片 */
    private final KnowledgeChunkService chunkService;
    /** Markdown/TXT/Excel/CSV 的本地格式转换器。 */
    private final DirectDocumentConverter directDocumentConverter;
    /** 基于文件内容检测真实类型，禁止仅靠扩展名或客户端 MIME 路由。 */
    private final TikaFileTypeDetector fileTypeDetector;
    /** 事务后事件驱动的切片与向量化流水线。 */
    private final DocumentPipelineService pipelineService;
    /** 业务配置（如 OSS 路径前缀） */
    private final GroupChatProperties properties;

    /**
     * 文档提交主流程：校验 → 建档 → 上传 OSS → 按格式本地转换或创建 MinerU 任务。
     *
     * @param file         上传的文件（PDF/图片/Word/Markdown/TXT/Excel/CSV）
     * @param title        文档标题（必填）
     * @param productModel 产品型号（可选，如 TL-7DR5130，用于 AI 检索时按型号过滤）
     * @return 已进入解析或切片阶段的文档记录
     */
    public KnowledgeDocument upload(MultipartFile file, String title, String productModel) {
        return upload(file, title, productModel, null);
    }

    /**
     * 上传文档并可为本篇文档选择切分策略；传 null 时使用系统默认策略。
     */
    public KnowledgeDocument upload(
            MultipartFile file,
            String title,
            String productModel,
            ChunkingStrategyType chunkStrategy
    ) {
        // ---------- 阶段 1：校验与预处理 ----------
        // 校验文件与标题合法性，读取文件内容，计算 SHA-256 指纹并规范化标题/型号
        validate(file, title);
        byte[] content = readBytes(file);
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        String contentType = fileTypeDetector.detect(fileName, content);
        String sha256 = sha256(content);
        String normalizedTitle = title.trim();
        String normalizedProductModel = normalizeNullable(productModel);
        String extension = extension(fileName);
        ChunkingStrategyType selectedStrategy = chunkStrategy == null
                ? ChunkingStrategyType.from(properties.getRag().getChunkStrategy())
                : chunkStrategy;
        if (directDocumentConverter.isSpreadsheet(extension)) {
            selectedStrategy = ChunkingStrategyType.EXCEL;
        }

        // ---------- 阶段 2：幂等建档或认领失败记录 ----------
        // 相同 SHA-256 已存在时不重复插入：成功/处理中直接返回，FAILED 原子认领后重试。
        DocumentClaim claim = claimDocument(
                normalizedTitle,
                fileName,
                contentType,
                content.length,
                sha256,
                normalizedProductModel,
                selectedStrategy
        );
        if (!claim.shouldProcess()) {
            return claim.document();
        }
        long documentId = claim.document().getId();

        // ---------- 阶段 3：规划 OSS 存储路径 ----------
        // 原始文件与解析后 Markdown 均以 SHA-256 命名，路径形如 {前缀}/{documentId}/{sha256}.{扩展名}
        String originalObjectKey = objectKey(
                properties.getOss().getOriginalPrefix(), documentId, sha256 + "." + extension
        );
        String parsedObjectKey = objectKey(
                properties.getOss().getParsedPrefix(), documentId, sha256 + ".md"
        );
        String processedObjectKey = objectKey(
                properties.getOss().getParsedPrefix(), documentId, sha256 + ".processed.md"
        );

        try {
            // ---------- 阶段 4：上传原文件，并按格式选择本地转换或 MinerU ----------
            ossClient.put(originalObjectKey, content, contentType);
            if (directDocumentConverter.supports(extension)) {
                String markdown = directDocumentConverter.convert(content, extension);
                byte[] markdownBytes = markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ossClient.put(parsedObjectKey, markdownBytes, "text/markdown; charset=UTF-8");
                ossClient.put(processedObjectKey, markdownBytes, "text/markdown; charset=UTF-8");
                pipelineService.startDirectChunking(
                        documentId, originalObjectKey, parsedObjectKey, processedObjectKey
                );
                return get(documentId);
            }
            String sourceUrl = ossClient.presignGet(
                    originalObjectKey,
                    properties.getMineru().getSourceUrlExpiration()
            );
            String taskId = mineruClient.createTask(sourceUrl, "document-" + documentId);
            markParsing(
                    documentId,
                    originalObjectKey,
                    parsedObjectKey,
                    processedObjectKey,
                    taskId,
                    LocalDateTime.now()
            );
            return get(documentId);
        } catch (RuntimeException exception) {
            // 任意环节失败：记录根因并置为 FAILED，再包装为业务异常向上抛出
            markFailed(documentId, rootMessage(exception));
            throw new DocumentProcessingException(documentId, "文档处理失败", exception);
        }
    }

    /**
     * 根据 SHA-256 幂等获取文档处理权。
     * <ul>
     *     <li>不存在：创建 UPLOADING 记录并处理；</li>
     *     <li>FAILED：通过“WHERE status = FAILED”原子更新抢占重试权；</li>
     *     <li>其他状态：直接返回已有记录，不重复上传 OSS 或创建 MinerU 任务。</li>
     * </ul>
     * 唯一键兜底两个并发请求同时查不到记录的竞争：插入失败的一方重新读取已有记录。
     */
    private DocumentClaim claimDocument(
            String title,
            String fileName,
            String contentType,
            long fileSize,
            String sha256,
            String productModel,
            ChunkingStrategyType chunkStrategy
    ) {
        KnowledgeDocument existing = findBySha256(sha256);
        if (existing != null) {
            return claimExisting(
                    existing, title, fileName, contentType, fileSize, productModel, chunkStrategy
            );
        }

        try {
            KnowledgeDocument created = createDocument(
                    title, fileName, contentType, fileSize, sha256, productModel, chunkStrategy
            );
            return new DocumentClaim(created, true);
        } catch (DuplicateKeyException exception) {
            // 并发请求可能都在首次查询时未发现记录，唯一键只允许其中一个插入成功。
            KnowledgeDocument concurrentDocument = findBySha256(sha256);
            if (concurrentDocument == null) {
                throw exception;
            }
            return claimExisting(
                    concurrentDocument,
                    title,
                    fileName,
                    contentType,
                    fileSize,
                    productModel,
                    chunkStrategy
            );
        }
    }

    /** FAILED 记录允许重试；其余状态说明已成功或正在处理，直接复用已有结果。 */
    private DocumentClaim claimExisting(
            KnowledgeDocument existing,
            String title,
            String fileName,
            String contentType,
            long fileSize,
            String productModel,
            ChunkingStrategyType chunkStrategy
    ) {
        if (existing.getStatus() != DocumentStatus.FAILED) {
            return new DocumentClaim(existing, false);
        }

        int affectedRows = documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, existing.getId())
                .eq(KnowledgeDocument::getStatus, DocumentStatus.FAILED)
                .set(KnowledgeDocument::getTitle, title)
                .set(KnowledgeDocument::getFileName, fileName)
                .set(KnowledgeDocument::getContentType, contentType)
                .set(KnowledgeDocument::getFileSize, fileSize)
                .set(KnowledgeDocument::getProductModel, productModel)
                .set(KnowledgeDocument::getChunkStrategy, chunkStrategy)
                .set(KnowledgeDocument::getStatus, DocumentStatus.UPLOADING)
                .set(KnowledgeDocument::getChunkCount, 0)
                .set(KnowledgeDocument::getPreprocessVersion, "")
                .set(KnowledgeDocument::getChunkVersion, "")
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getFailureStage, null)
                .set(KnowledgeDocument::getRetryCount, 0)
                .set(KnowledgeDocument::getLastRetryAt, null)
                .set(KnowledgeDocument::getOriginalObjectKey, null)
                .set(KnowledgeDocument::getParsedObjectKey, null)
                .set(KnowledgeDocument::getProcessedObjectKey, null)
                .set(KnowledgeDocument::getImageCount, 0)
                .set(KnowledgeDocument::getImageProcessVersion, "")
                .set(KnowledgeDocument::getMineruTaskId, null)
                .set(KnowledgeDocument::getMineruSubmittedAt, null));

        if (affectedRows != 1) {
            // 另一并发请求已经抢到重试权，本请求只返回最新状态，不重复提交外部任务。
            return new DocumentClaim(get(existing.getId()), false);
        }

        existing.setTitle(title);
        existing.setFileName(fileName);
        existing.setContentType(contentType);
        existing.setFileSize(fileSize);
        existing.setProductModel(productModel);
        existing.setChunkStrategy(chunkStrategy);
        existing.setStatus(DocumentStatus.UPLOADING);
        existing.setChunkCount(0);
        existing.setPreprocessVersion("");
        existing.setChunkVersion("");
        existing.setFailureReason(null);
        existing.setFailureStage(null);
        existing.setRetryCount(0);
        existing.setLastRetryAt(null);
        existing.setOriginalObjectKey(null);
        existing.setParsedObjectKey(null);
        existing.setProcessedObjectKey(null);
        existing.setImageCount(0);
        existing.setImageProcessVersion("");
        existing.setMineruTaskId(null);
        existing.setMineruSubmittedAt(null);
        return new DocumentClaim(existing, true);
    }

    /** SHA-256 在表上有唯一索引，因此最多返回一条记录。 */
    private KnowledgeDocument findBySha256(String sha256) {
        return documentMapper.selectOne(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getSha256, sha256));
    }

    /**
     * 按 ID 查询文档记录，不存在时抛出 DocumentNotFoundException。
     */
    public KnowledgeDocument get(long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new DocumentNotFoundException(documentId);
        }
        return document;
    }

    /**
     * 基于 OSS 中已有 Markdown 异步重建切片与向量，不重新上传文件或调用 MinerU。
     */
    public KnowledgeDocument reindex(long documentId) {
        pipelineService.restartChunking(documentId);
        return get(documentId);
    }

    /**
     * 删除文档：清理 Elasticsearch 向量、MySQL 分片，再删除 MySQL 文档记录。
     * 注意：OSS 中的原始文件与 Markdown 未删除，可用于后续溯源或重建索引。
     */
    @Transactional
    public void delete(long documentId) {
        get(documentId);
        elasticsearchClient.deleteByDocumentId(documentId);
        chunkService.deleteByDocumentId(documentId);
        int affectedRows = documentMapper.deleteById(documentId);
        if (affectedRows != 1) {
            throw new IllegalStateException("删除文档记录失败: " + documentId);
        }
    }

    /**
     * 在 MySQL 中创建文档记录，初始状态为 UPLOADING、块数为 0。
     */
    private KnowledgeDocument createDocument(
            String title,
            String fileName,
            String contentType,
            long fileSize,
            String sha256,
            String productModel,
            ChunkingStrategyType chunkStrategy
    ) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(title);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setFileSize(fileSize);
        document.setSha256(sha256);
        document.setProductModel(productModel);
        document.setChunkStrategy(chunkStrategy);
        document.setPreprocessVersion("");
        document.setChunkVersion("");
        document.setStatus(DocumentStatus.UPLOADING);
        document.setChunkCount(0);
        document.setImageCount(0);
        document.setImageProcessVersion("");
        document.setRetryCount(0);

        int affectedRows = documentMapper.insert(document);
        if (affectedRows != 1 || document.getId() == null) {
            throw new IllegalStateException("创建文档记录失败");
        }
        return document;
    }

    /** 保存云解析任务和 OSS 路径，并把文档推进到 PARSING。 */
    private void markParsing(
            long documentId,
            String originalObjectKey,
            String parsedObjectKey,
            String processedObjectKey,
            String taskId,
            LocalDateTime submittedAt
    ) {
        documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .set(KnowledgeDocument::getOriginalObjectKey, originalObjectKey)
                .set(KnowledgeDocument::getParsedObjectKey, parsedObjectKey)
                .set(KnowledgeDocument::getProcessedObjectKey, processedObjectKey)
                .set(KnowledgeDocument::getMineruTaskId, taskId)
                .set(KnowledgeDocument::getMineruSubmittedAt, submittedAt)
                .set(KnowledgeDocument::getStatus, DocumentStatus.PARSING)
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getFailureStage, null)
                .set(KnowledgeDocument::getRetryCount, 0)
                .set(KnowledgeDocument::getLastRetryAt, null));
    }

    /** 标记文档为 FAILED，并记录截断后的失败根因（最多 1000 字符） */
    private void markFailed(long documentId, String reason) {
        documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .set(KnowledgeDocument::getStatus, DocumentStatus.FAILED)
                .set(KnowledgeDocument::getFailureStage, "UPLOAD_OR_PARSE")
                .set(KnowledgeDocument::getFailureReason, truncate(reason, 1000)));
    }

    /** 校验上传基础入参；扩展名和真实内容类型由 TikaFileTypeDetector 统一校验。 */
    private void validate(MultipartFile file, String title) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("文档标题不能为空");
        }
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        if (!StringUtils.hasText(fileName)) {
            throw new IllegalArgumentException("上传文件名不能为空");
        }
    }

    /** 读取上传文件的全部字节，IO 异常时转为非法参数异常 */
    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取上传文件失败", exception);
        }
    }

    /** 计算文件内容的 SHA-256 哈希（十六进制小写），用于 OSS 对象命名与去重 */
    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    /** 提取文件扩展名并转为小写，无扩展名时返回空串 */
    private String extension(String fileName) {
        String extension = StringUtils.getFilenameExtension(fileName);
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }

    /** 拼接 OSS 对象路径：{前缀}/{documentId}/{fileName}，前缀首尾多余斜杠会被清理 */
    private String objectKey(String prefix, long documentId, String fileName) {
        String cleanPrefix = prefix == null ? "" : prefix.replaceAll("^/+|/+$", "");
        String base = cleanPrefix.isEmpty() ? "" : cleanPrefix + "/";
        return base + documentId + "/" + fileName;
    }

    /** 可选字段空值归一化：有内容则 trim，否则统一返回空串，避免 null 与空串差异 */
    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /** 超长字符串截断，用于失败原因入库时防止超出列长度 */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** 沿异常链提取最底层根因的消息文本，用于记录 failureReason */
    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /** 文档记录及本次请求是否获得后续 OSS/MinerU 处理权。 */
    private record DocumentClaim(KnowledgeDocument document, boolean shouldProcess) {
    }
}
