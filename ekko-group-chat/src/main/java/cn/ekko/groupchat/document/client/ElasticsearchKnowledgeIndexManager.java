package cn.ekko.groupchat.document.client;

import cn.ekko.groupchat.config.GroupChatProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 负责创建并校验知识库向量索引，防止模型输出维度与 ES dense_vector 映射不一致。
 *
 * <p>清空索引中的文档不会删除 mapping，因此维度升级必须使用新索引名。首次入库时本组件
 * 自动创建配置维度的索引；索引已经存在时会先校验 {@code vector.dims}，不一致则拒绝写入。
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchKnowledgeIndexManager {

    private final ElasticsearchClient elasticsearchClient;
    private final GroupChatProperties properties;

    private volatile boolean ready;

    public void ensureReady() {
        if (ready) {
            return;
        }
        synchronized (this) {
            if (ready) {
                return;
            }
            String indexName = properties.getElasticsearch().getIndexName();
            int expectedDimension = properties.getRag().getEmbeddingDimension();
            if (!StringUtils.hasText(indexName)) {
                throw new IllegalStateException("Elasticsearch 索引名不能为空");
            }
            if (expectedDimension < 1) {
                throw new IllegalStateException("embeddingDimension 必须大于 0");
            }

            try {
                if (!elasticsearchClient.indices().exists(request -> request.index(indexName)).value()) {
                    createIndex(indexName, expectedDimension);
                } else {
                    validateExistingIndex(indexName, expectedDimension);
                }
                ready = true;
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "初始化 Elasticsearch 向量索引失败: index=" + indexName,
                        exception
                );
            }
        }
    }

    private void createIndex(String indexName, int dimension) throws IOException {
        elasticsearchClient.indices().create(request -> request
                .index(indexName)
                .mappings(mapping -> mapping
                        .properties("vector", property -> property.denseVector(vector -> vector
                                .dims(dimension)
                                .index(true)
                                .similarity(DenseVectorSimilarity.Cosine)))
                        .properties("text", property -> property.text(text -> text))));
    }

    private void validateExistingIndex(String indexName, int expectedDimension) throws IOException {
        GetMappingResponse response = elasticsearchClient.indices()
                .getMapping(request -> request.index(indexName));
        IndexMappingRecord record = response.result().get(indexName);
        if (record == null && response.result().size() == 1) {
            record = response.result().values().iterator().next();
        }
        if (record == null) {
            throw new IllegalStateException("无法读取 Elasticsearch 索引映射: index=" + indexName);
        }
        Property vectorProperty = record.mappings().properties().get("vector");
        validateVectorMapping(indexName, vectorProperty, expectedDimension);
    }

    static void validateVectorMapping(String indexName, Property vectorProperty, int expectedDimension) {
        if (vectorProperty == null || !vectorProperty.isDenseVector()) {
            throw new IllegalStateException(
                    "Elasticsearch 索引缺少 dense_vector 类型的 vector 字段: index=" + indexName
            );
        }
        Integer actualDimension = vectorProperty.denseVector().dims();
        if (actualDimension == null || actualDimension != expectedDimension) {
            throw new IllegalStateException(
                    "Elasticsearch 向量维度不匹配: index=" + indexName
                            + ", expected=" + expectedDimension
                            + ", actual=" + actualDimension
                            + "。清空文档不会删除 mapping，请改用新的索引名并重新向量化"
            );
        }
    }
}
