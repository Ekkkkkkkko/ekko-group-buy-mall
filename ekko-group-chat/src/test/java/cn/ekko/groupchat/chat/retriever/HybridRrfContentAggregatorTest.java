package cn.ekko.groupchat.chat.retriever;

import cn.ekko.groupchat.config.GroupChatProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRrfContentAggregatorTest {

    @Test
    void fusesSameChunkByStableChunkIdAcrossVectorAndBm25() {
        GroupChatProperties properties = properties();
        KnowledgeContextExpander expander = passThroughExpander();
        HybridRrfContentAggregator aggregator = new HybridRrfContentAggregator(
                properties, expander, (ScoringModel) null
        );
        Query query = Query.from("Wi-Fi 7");
        Content vector = content("chunk-1", "VECTOR", "支持 Wi-Fi 7", 0.91);
        Content fullText = content("chunk-1", "FULL_TEXT", "支持 Wi-Fi 7", 8.7);
        Map<Query, Collection<List<Content>>> input = new LinkedHashMap<>();
        input.put(query, List.of(List.of(vector), List.of(fullText)));

        List<Content> result = aggregator.aggregate(input);

        assertThat(result).hasSize(1);
        Content fused = result.getFirst();
        assertThat(fused.textSegment().metadata().getString("chunkId")).isEqualTo("chunk-1");
        assertThat(fused.textSegment().metadata().getString("retrievalSources"))
                .isEqualTo("VECTOR,FULL_TEXT");
        assertThat(fused.textSegment().metadata().getDouble("rrfScore"))
                .isEqualTo(2.0 / 61.0);
        assertThat(fused.metadata().get(ContentMetadata.SCORE)).isEqualTo(0.91);
    }

    @Test
    void reranksFusedCandidatesWithScoringModel() {
        GroupChatProperties properties = properties();
        KnowledgeContextExpander expander = passThroughExpander();
        ScoringModel scoringModel = (segments, query) -> Response.from(List.of(0.15, 0.92));
        HybridRrfContentAggregator aggregator = new HybridRrfContentAggregator(
                properties, expander, scoringModel
        );
        Query query = Query.from("安装方法");
        Map<Query, Collection<List<Content>>> input = Map.of(query, List.of(List.of(
                content("chunk-1", "VECTOR", "不相关", 0.9),
                content("chunk-2", "VECTOR", "安装步骤", 0.8)
        )));

        List<Content> result = aggregator.aggregate(input);

        assertThat(result).extracting(content -> content.textSegment().metadata().getString("chunkId"))
                .containsExactly("chunk-2", "chunk-1");
        assertThat(result.getFirst().metadata().get(ContentMetadata.RERANKED_SCORE)).isEqualTo(0.92);
    }

    private GroupChatProperties properties() {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getRetrieval().setRrfK(60);
        properties.getRetrieval().setFinalMaxResults(5);
        return properties;
    }

    private KnowledgeContextExpander passThroughExpander() {
        return new KnowledgeContextExpander(null) {
            @Override
            public List<Content> expand(List<Content> hits) {
                return List.copyOf(hits);
            }
        };
    }

    private Content content(String chunkId, String source, String text, double score) {
        Metadata metadata = new Metadata()
                .put("chunkId", chunkId)
                .put("documentId", 1L)
                .put("chunkIndex", 0)
                .put("retrievalSource", source);
        return Content.from(TextSegment.from(text, metadata), Map.of(ContentMetadata.SCORE, score));
    }
}
