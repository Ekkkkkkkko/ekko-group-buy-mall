package cn.ekko.groupchat.document.client;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import cn.ekko.groupchat.document.service.image.KnowledgeImageMarkdown;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 知识库向量存储客户端，封装对 Elasticsearch 的向量写入与删除。
 *
 * <p>基于 langchain4j：{@link EmbeddingModel} 负责文本向量化，
 * {@link EmbeddingStore} 负责向量与原文段的存取；写入与问答检索共用同一索引。
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchKnowledgeClient {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingTextBuilder embeddingTextBuilder;
    private final GroupChatProperties properties;
    private final ElasticsearchKnowledgeIndexManager indexManager;

    /**
     * 将文档分块向量化后批量写入 Elasticsearch。
     *
     * <p>整个方法同步执行，包含三个阶段：
     * <ol>
     *   <li>组装阶段：为每个分块构造确定性文档 ID 和携带业务元数据的 {@link TextSegment}；</li>
     *   <li>向量化阶段：按模型允许的批次大小分批计算全部分块的向量；</li>
     *   <li>写入阶段：将 ID、向量、原文段批量存入 ES 索引。</li>
     * </ol>
     *
     * @param documentId      文档主键，用于生成 ES 文档 ID 及后续按文档删除
     * @param title           文档标题，作为元数据供检索过滤与问答溯源
     * @param productModel    产品型号（可为 null，归一化为空字符串）
     * @param sourceObjectKey 解析后 Markdown 在 OSS 上的对象路径
     * @param preprocessVersion 预处理规则版本
     * @param chunkVersion    分片规则版本
     * @param chunks          结构化分片列表；父分片只存 MySQL，不生成向量
     * @return 实际写入的分块数量
     */
    public int index(
            long documentId,
            String title,
            String productModel,
            String sourceObjectKey,
            String preprocessVersion,
            String chunkVersion,
            List<DocumentChunk> chunks
    ) {
        List<DocumentChunk> searchableChunks = chunks.stream()
                .filter(DocumentChunk::isSearchable)
                .toList();
        if (searchableChunks.isEmpty()) {
            return 0;
        }
        // 在调用模型和替换旧数据前确认 ES 映射与模型配置一致，避免维度错误造成半成功。
        indexManager.ensureReady();
        // === 阶段一：组装 TextSegment ===
        // 遍历每个分块，生成确定性文档 ID 并附加业务元数据
        List<String> ids = new ArrayList<>(searchableChunks.size());
        List<TextSegment> embeddingSegments = new ArrayList<>(searchableChunks.size());
        List<TextSegment> displaySegments = new ArrayList<>(searchableChunks.size());
        String normalizedProductModel = productModel == null
                ? ""
                : productModel.trim().toUpperCase(Locale.ROOT);
        for (DocumentChunk chunk : searchableChunks) {
            // 元数据随向量一起存入 ES，检索命中后可用于过滤、展示来源和回溯原文档
            Metadata metadata = new Metadata()
                    .put("documentId", documentId)
                    .put("title", title)
                    // 可选字段归一化：null 转为空字符串，避免 ES 中字段缺失
                    .put("productModel", normalizedProductModel)
                    .put("sourceObjectKey", sourceObjectKey)
                    .put("preprocessVersion", preprocessVersion)
                    .put("chunkVersion", chunkVersion)
                    .put("embeddingModel", properties.getRag().getEmbeddingModel())
                    .put("embeddingDimension", properties.getRag().getEmbeddingDimension())
                    .put("embeddingVersion", properties.getRag().getEmbeddingVersion())
                    .put("chunkId", chunk.getChunkId())
                    .put("chunkType", chunk.getType().name())
                    .put("chunkIndex", chunk.getChunkIndex());
            if (chunk.getParentChunkId() != null) {
                metadata.put("parentChunkId", chunk.getParentChunkId());
            }
            if (chunk.getBrotherChunkId() != null) {
                metadata.put("brotherChunkId", chunk.getBrotherChunkId());
                metadata.put("brotherChunkIndex", chunk.getBrotherChunkIndex());
                metadata.put("brotherChunkTotal", chunk.getBrotherChunkTotal());
            }
            if (chunk.getHeadingPath() != null) {
                metadata.put("headingPath", chunk.getHeadingPath());
            }
            List<Long> imageIds = KnowledgeImageMarkdown.imageIds(chunk.getText());
            if (!imageIds.isEmpty()) {
                metadata.put("imageIds", imageIds.stream()
                        .map(String::valueOf)
                        .reduce((left, right) -> left + "," + right)
                        .orElse(""));
            }
            ids.add(chunk.getChunkId());
            displaySegments.add(TextSegment.from(chunk.getText(), metadata));
            embeddingSegments.add(TextSegment.from(
                    embeddingTextBuilder.build(
                            title,
                            normalizedProductModel,
                            chunk.getHeadingPath(),
                            chunk.getText()
                    ),
                    metadata
            ));
        }

        // === 阶段二：分批向量化 ===
        // 当前模型服务限制 input.contents 单批最多 10 条。先完成全部批次，再删除旧 ES 索引，
        // 避免后续批次失败时提前丢失原有可用索引。
        List<Embedding> embeddings = embedInBatches(embeddingSegments);

        // === 阶段三：替换 ES 中该文档的旧索引 ===
        // 先按 documentId 清理旧分片，避免切分策略变化后残留多余向量。
        deleteByDocumentId(documentId);
        embeddingStore.addAll(ids, embeddings, displaySegments);
        return displaySegments.size();
    }

    private List<Embedding> embedInBatches(List<TextSegment> segments) {
        int batchSize = properties.getRag().getEmbeddingBatchSize();
        if (batchSize < 1 || batchSize > 10) {
            throw new IllegalStateException("embeddingBatchSize 必须在 1 到 10 之间");
        }

        List<Embedding> embeddings = new ArrayList<>(segments.size());
        for (int fromIndex = 0; fromIndex < segments.size(); fromIndex += batchSize) {
            int toIndex = Math.min(fromIndex + batchSize, segments.size());
            List<TextSegment> batch = segments.subList(fromIndex, toIndex);
            List<Embedding> batchEmbeddings = embeddingModel.embedAll(batch).content();
            if (batchEmbeddings == null || batchEmbeddings.size() != batch.size()) {
                int actualSize = batchEmbeddings == null ? 0 : batchEmbeddings.size();
                throw new IllegalStateException(
                        "Embedding 返回数量与请求数量不一致: requested="
                                + batch.size() + ", actual=" + actualSize
                );
            }
            int expectedDimension = properties.getRag().getEmbeddingDimension();
            for (int index = 0; index < batchEmbeddings.size(); index++) {
                int actualDimension = batchEmbeddings.get(index).vector().length;
                if (actualDimension != expectedDimension) {
                    throw new IllegalStateException(
                            "Embedding 向量维度不匹配: expected=" + expectedDimension
                                    + ", actual=" + actualDimension
                                    + ", batchOffset=" + (fromIndex + index)
                    );
                }
            }
            embeddings.addAll(batchEmbeddings);
        }
        return embeddings;
    }

    public void deleteByDocumentId(long documentId) {
        indexManager.ensureReady();
        embeddingStore.removeAll(metadataKey("documentId").isEqualTo(documentId));
    }
}
