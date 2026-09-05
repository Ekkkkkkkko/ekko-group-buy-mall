package cn.ekko.groupchat.document.service.image;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.AliyunOssClient;
import cn.ekko.groupchat.document.client.ImageDescriptionClient;
import cn.ekko.groupchat.document.client.MineruParsedArchive;
import cn.ekko.groupchat.document.client.MineruParsedImage;
import cn.ekko.groupchat.document.entity.KnowledgeChunkImage;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.entity.KnowledgeImage;
import cn.ekko.groupchat.document.entity.KnowledgeImageStatus;
import cn.ekko.groupchat.document.mapper.KnowledgeChunkImageMapper;
import cn.ekko.groupchat.document.mapper.KnowledgeImageMapper;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 图片上传、视觉描述、Markdown 改写、分片关联和聊天展示地址解析。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeImageService implements KnowledgeImageResolver {

    private static final int MAX_ALT_LENGTH = 500;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MAX_FAILURE_LENGTH = 1000;

    private final KnowledgeImageMapper imageMapper;
    private final KnowledgeChunkImageMapper chunkImageMapper;
    private final AliyunOssClient ossClient;
    private final ImageDescriptionClient descriptionClient;
    private final GroupChatProperties properties;

    /**
     * 将 MinerU ZIP 中的图片保存到私有 OSS，生成描述并把 Markdown 相对路径改成稳定图片 ID。
     * 原始 Markdown 由调用方另行保存，确保后续可追溯。
     */
    public ImageProcessingResult process(KnowledgeDocument document, MineruParsedArchive archive) {
        if (archive.images().size() > properties.getImage().getMaxImagesPerDocument()) {
            throw new IllegalStateException("文档图片数量超过配置限制: " + archive.images().size());
        }

        imageMapper.delete(Wrappers.<KnowledgeImage>lambdaQuery()
                .eq(KnowledgeImage::getDocumentId, document.getId()));

        Map<String, String> altBySourcePath = collectAltText(archive);
        Map<String, KnowledgeImage> storedBySourcePath = new LinkedHashMap<>();
        for (MineruParsedImage parsedImage : archive.images()) {
            KnowledgeImage stored = store(document, parsedImage, altBySourcePath.get(parsedImage.sourcePath()));
            storedBySourcePath.put(parsedImage.sourcePath(), stored);
        }

        String processed = KnowledgeImageMarkdown.rewrite(
                archive.markdown(),
                (alt, target) -> rewriteTag(
                        archive.markdownPath(), alt, target, storedBySourcePath
                )
        );
        return new ImageProcessingResult(processed, storedBySourcePath.size());
    }

    /** 分片整体替换后重建图片关联；外键会在旧分片删除时级联清理旧关联。 */
    @Transactional
    public void linkChunks(long documentId, List<DocumentChunk> chunks) {
        Set<Long> allowedImageIds = imageMapper.selectList(Wrappers.<KnowledgeImage>lambdaQuery()
                        .eq(KnowledgeImage::getDocumentId, documentId))
                .stream()
                .map(KnowledgeImage::getId)
                .collect(Collectors.toSet());
        if (allowedImageIds.isEmpty()) {
            return;
        }

        for (DocumentChunk chunk : chunks) {
            for (Long imageId : KnowledgeImageMarkdown.imageIds(chunk.getText())) {
                if (!allowedImageIds.contains(imageId)) {
                    throw new IllegalStateException("分片引用了不属于当前文档的图片: " + imageId);
                }
                KnowledgeChunkImage link = new KnowledgeChunkImage();
                link.setChunkId(chunk.getChunkId());
                link.setImageId(imageId);
                if (chunkImageMapper.insert(link) != 1) {
                    throw new IllegalStateException("保存分片图片关联失败: " + chunk.getChunkId());
                }
            }
        }
    }

    /** 问答返回时才生成短期签名 URL，避免把会过期的地址写入 MySQL、Markdown 或 ES。 */
    @Override
    public List<KnowledgeImageReference> resolve(String chunkText) {
        List<Long> ids = KnowledgeImageMarkdown.imageIds(chunkText);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeImage> images = imageMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(KnowledgeImage::getId, Function.identity()));
        List<KnowledgeImageReference> result = new ArrayList<>();
        for (Long id : ids) {
            KnowledgeImage image = images.get(id);
            if (image == null || isExcluded(image)) {
                continue;
            }
            try {
                result.add(new KnowledgeImageReference(
                        id,
                        displayDescription(image),
                        ossClient.presignGet(
                                image.getObjectKey(), properties.getImage().getSignedUrlExpiration()
                        ),
                        image.getSha256()
                ));
            } catch (RuntimeException exception) {
                log.warn("生成知识图片签名地址失败, imageId={}", id, exception);
            }
        }
        return List.copyOf(result);
    }

    private KnowledgeImage store(KnowledgeDocument document, MineruParsedImage parsedImage, String altText) {
        String objectKey = imageObjectKey(document, parsedImage);
        ossClient.put(objectKey, parsedImage.content(), parsedImage.contentType());

        KnowledgeImage entity = new KnowledgeImage();
        entity.setDocumentId(document.getId());
        entity.setSourcePath(parsedImage.sourcePath());
        entity.setObjectKey(objectKey);
        entity.setSha256(parsedImage.sha256());
        entity.setContentType(parsedImage.contentType());
        entity.setFileSize((long) parsedImage.content().length);
        entity.setAltText(truncate(normalize(altText), MAX_ALT_LENGTH));
        entity.setDescriptionModel(properties.getImage().getVisionModel());
        entity.setDescriptionVersion(properties.getImage().getDescriptionVersion());
        entity.setStatus(isExcludedSha256(entity.getSha256())
                || RedPlaceholderImageDetector.isPlaceholder(parsedImage.content())
                ? KnowledgeImageStatus.EXCLUDED
                : KnowledgeImageStatus.STORED);
        if (imageMapper.insert(entity) != 1 || entity.getId() == null) {
            throw new IllegalStateException("保存知识图片记录失败: " + parsedImage.sourcePath());
        }

        if (!isExcluded(entity) && properties.getImage().isDescriptionEnabled()) {
            describe(document, entity);
        }
        return entity;
    }

    private void describe(KnowledgeDocument document, KnowledgeImage image) {
        try {
            String signedUrl = ossClient.presignGet(
                    image.getObjectKey(), properties.getImage().getSignedUrlExpiration()
            );
            image.setDescription(truncate(
                    descriptionClient.describe(document, image.getAltText(), signedUrl),
                    MAX_DESCRIPTION_LENGTH
            ));
            image.setStatus(KnowledgeImageStatus.DESCRIBED);
            image.setFailureReason(null);
        } catch (RuntimeException exception) {
            image.setStatus(KnowledgeImageStatus.DESCRIPTION_FAILED);
            image.setFailureReason(truncate(rootMessage(exception), MAX_FAILURE_LENGTH));
            log.warn("生成知识图片描述失败, imageId={}", image.getId(), exception);
        }
        if (imageMapper.updateById(image) != 1) {
            throw new IllegalStateException("更新知识图片描述失败: " + image.getId());
        }
    }

    private Map<String, String> collectAltText(MineruParsedArchive archive) {
        Map<String, String> result = new HashMap<>();
        for (KnowledgeImageMarkdown.ImageTag tag : KnowledgeImageMarkdown.imageTags(archive.markdown())) {
            String resolved = resolveLocalPath(archive.markdownPath(), tag.target());
            if (resolved != null && StringUtils.hasText(tag.altText())) {
                result.putIfAbsent(resolved, tag.altText());
            }
        }
        return result;
    }

    private String rewriteTag(
            String markdownPath,
            String altText,
            String target,
            Map<String, KnowledgeImage> storedBySourcePath
    ) {
        String resolved = resolveLocalPath(markdownPath, target);
        KnowledgeImage image = resolved == null ? null : storedBySourcePath.get(resolved);
        if (image == null) {
            log.warn("Markdown 图片未在 MinerU ZIP 中找到: {}", target);
            return StringUtils.hasText(altText) ? "图片：" + normalize(altText) : "";
        }
        if (isExcluded(image)) {
            return "";
        }

        String description = displayDescription(image);
        String conciseAlt = StringUtils.hasText(altText)
                ? normalize(altText)
                : truncate(description, 120);
        String imageTag = "![" + markdownSafe(conciseAlt) + "](knowledge-image://" + image.getId() + ")";
        if (!StringUtils.hasText(description)) {
            return imageTag;
        }
        // 图片标识与说明保持在同一 Markdown 原子块，避免自然边界切分时彼此分离。
        return imageTag + "\n> 图片说明：" + description;
    }

    private boolean isExcluded(KnowledgeImage image) {
        return image.getStatus() == KnowledgeImageStatus.EXCLUDED || isExcludedSha256(image.getSha256());
    }

    private boolean isExcludedSha256(String sha256) {
        return StringUtils.hasText(sha256)
                && properties.getImage().getExcludedSha256().stream()
                .anyMatch(hash -> sha256.equalsIgnoreCase(hash.trim()));
    }

    private String resolveLocalPath(String markdownPath, String target) {
        if (!StringUtils.hasText(target)
                || target.startsWith("knowledge-image://")
                || target.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")) {
            return null;
        }
        try {
            Path reference = Path.of(target.replace('\\', '/'));
            Path markdown = Path.of(markdownPath);
            Path parent = markdown.getParent();
            Path resolved = (parent == null ? reference : parent.resolve(reference)).normalize();
            if (resolved.isAbsolute() || resolved.startsWith("..")) {
                return null;
            }
            return resolved.toString().replace('\\', '/');
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private String imageObjectKey(KnowledgeDocument document, MineruParsedImage image) {
        String prefix = properties.getOss().getParsedPrefix();
        String cleanPrefix = prefix == null ? "" : prefix.replaceAll("^/+|/+$", "");
        String base = cleanPrefix.isEmpty() ? "" : cleanPrefix + "/";
        return base + document.getId() + "/" + document.getSha256()
                + "/images/" + image.sha256() + "." + image.extension();
    }

    private String displayDescription(KnowledgeImage image) {
        if (StringUtils.hasText(image.getDescription())) {
            return normalize(image.getDescription());
        }
        return normalize(image.getAltText());
    }

    private String markdownSafe(String value) {
        return normalize(value).replace("[", "（").replace("]", "）");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim() : "";
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
