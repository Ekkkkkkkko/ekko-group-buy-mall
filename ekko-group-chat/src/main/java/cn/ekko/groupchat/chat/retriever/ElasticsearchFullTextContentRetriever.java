package cn.ekko.groupchat.chat.retriever;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.ElasticsearchKnowledgeIndexManager;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.elasticsearch.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Elasticsearch BM25 全文检索通道，与向量通道并行召回。 */
@Component
@RequiredArgsConstructor
public class ElasticsearchFullTextContentRetriever implements RoutedContentRetriever {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchKnowledgeIndexManager indexManager;
    private final GroupChatProperties properties;
    private final ProductModelQueryFilter productModelQueryFilter;

    @Override
    public QueryRoute route() {
        return QueryRoute.KNOWLEDGE_BASE;
    }

    @Override
    public List<Content> retrieve(Query query) {
        indexManager.ensureReady();
        try {
            SearchResponse<Document> response = elasticsearchClient.search(request -> request
                            .index(properties.getElasticsearch().getIndexName())
                            .size(properties.getRetrieval().getFullTextCandidates())
                            .query(buildQuery(query)),
                    Document.class);
            return response.hits().hits().stream()
                    .filter(this::isRelevant)
                    .map(this::toContent)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch BM25 检索失败", exception);
        }
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildQuery(Query query) {
        var productModel = properties.getRag().isProductModelFilterEnabled()
                ? productModelQueryFilter.detect(query.text())
                : java.util.Optional.<String>empty();
        if (productModel.isEmpty()) {
            return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(builder -> builder
                    .match(match -> match.field("text").query(query.text())));
        }
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(builder -> builder
                .bool(bool -> bool
                        .must(must -> must.match(match -> match.field("text").query(query.text())))
                        .filter(filter -> filter.term(term -> term
                                .field("metadata.productModel.keyword")
                                .value(productModel.get())))));
    }

    private boolean isRelevant(Hit<Document> hit) {
        return hit.source() != null
                && hit.source().getText() != null
                && hit.score() != null
                && hit.score() >= properties.getRetrieval().getFullTextMinScore();
    }

    private Content toContent(Hit<Document> hit) {
        Document document = hit.source();
        Metadata metadata = document.getMetadata() == null
                ? new Metadata()
                : new Metadata(document.getMetadata());
        metadata.put("retrievalSource", "FULL_TEXT");
        Map<ContentMetadata, Object> contentMetadata = new EnumMap<>(ContentMetadata.class);
        contentMetadata.put(ContentMetadata.SCORE, hit.score());
        contentMetadata.put(ContentMetadata.EMBEDDING_ID, hit.id());
        return Content.from(TextSegment.from(document.getText(), metadata), contentMetadata);
    }
}
