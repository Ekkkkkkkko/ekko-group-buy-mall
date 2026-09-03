package cn.ekko.groupchat.document.client;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.service.chunk.ChunkType;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchKnowledgeClientTest {

    private static final int EMBEDDING_DIMENSION = 1536;

    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;
    @Mock
    private ElasticsearchKnowledgeIndexManager indexManager;

    @Test
    @SuppressWarnings("unchecked")
    void indexesOnlySearchableChunksAndKeepsParentLocator() {
        when(embeddingModel.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            List<Embedding> embeddings = segments.stream()
                    .map(segment -> Embedding.from(new float[EMBEDDING_DIMENSION]))
                    .toList();
            return Response.from(embeddings);
        });
        ElasticsearchKnowledgeClient client = client(properties());
        DocumentChunk parent = new DocumentChunk(
                "doc-11-parent-0", null, ChunkType.PARENT, "说明书 > 组网", -1, "父分片正文"
        );
        DocumentChunk child = new DocumentChunk(
                "doc-11-parent-0-child-0",
                "doc-11-parent-0",
                ChunkType.CHILD,
                "说明书 > 组网",
                0,
                "子分片正文\n\n![接口图](knowledge-image://9)"
        );

        int count = client.index(
                11L,
                "易展组网说明",
                "tl-test",
                "knowledge/parsed/11.md",
                "preprocess-v2",
                "chunk-v2",
                List.of(parent, child)
        );

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<List<String>> idCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<TextSegment>> segmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(embeddingStore).addAll(idCaptor.capture(), anyList(), segmentCaptor.capture());
        assertThat(idCaptor.getValue()).containsExactly("doc-11-parent-0-child-0");
        TextSegment indexed = segmentCaptor.getValue().getFirst();
        assertThat(indexed.text()).isEqualTo("子分片正文\n\n![接口图](knowledge-image://9)");
        assertThat(indexed.metadata().getString("parentChunkId")).isEqualTo("doc-11-parent-0");
        assertThat(indexed.metadata().getString("headingPath")).isEqualTo("说明书 > 组网");
        assertThat(indexed.metadata().getString("productModel")).isEqualTo("TL-TEST");
        assertThat(indexed.metadata().getString("preprocessVersion")).isEqualTo("preprocess-v2");
        assertThat(indexed.metadata().getString("embeddingModel")).isEqualTo("text-embedding-v4");
        assertThat(indexed.metadata().getInteger("embeddingDimension")).isEqualTo(1536);
        assertThat(indexed.metadata().getString("embeddingVersion"))
                .isEqualTo("text-embedding-v4-1536-v1");

        ArgumentCaptor<List<TextSegment>> embeddingCaptor = ArgumentCaptor.forClass(List.class);
        verify(embeddingModel).embedAll(embeddingCaptor.capture());
        assertThat(embeddingCaptor.getValue().getFirst().text())
                .contains("产品型号：TL-TEST")
                .contains("文档标题：易展组网说明")
                .contains("章节：说明书 > 组网")
                .contains("图片：接口图")
                .doesNotContain("knowledge-image://9");
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitsEmbeddingRequestsIntoBatchesOfAtMostTen() {
        when(embeddingModel.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            return Response.from(segments.stream()
                    .map(segment -> Embedding.from(new float[EMBEDDING_DIMENSION]))
                    .toList());
        });
        ElasticsearchKnowledgeClient client = client(properties());
        List<DocumentChunk> chunks = IntStream.range(0, 21)
                .mapToObj(index -> new DocumentChunk(
                        "doc-12-chunk-" + index,
                        null,
                        ChunkType.NORMAL,
                        null,
                        index,
                        "分片正文 " + index
                ))
                .toList();

        int count = client.index(
                12L,
                "批量向量化文档",
                null,
                "knowledge/parsed/12.md",
                "preprocess-v2",
                "chunk-v2",
                chunks
        );

        assertThat(count).isEqualTo(21);
        ArgumentCaptor<List<TextSegment>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(embeddingModel, times(3)).embedAll(batchCaptor.capture());
        assertThat(batchCaptor.getAllValues()).extracting(List::size).containsExactly(10, 10, 1);
        verify(embeddingStore).addAll(anyList(), anyList(), anyList());
    }

    @Test
    void keepsOldIndexWhenAEmbeddingBatchFails() {
        when(embeddingModel.embedAll(anyList()))
                .thenAnswer(invocation -> {
                    List<TextSegment> segments = invocation.getArgument(0);
                    return Response.from(segments.stream()
                            .map(segment -> Embedding.from(new float[EMBEDDING_DIMENSION]))
                            .toList());
                })
                .thenThrow(new IllegalStateException("第二批向量化失败"));
        ElasticsearchKnowledgeClient client = client(properties());
        List<DocumentChunk> chunks = IntStream.range(0, 21)
                .mapToObj(index -> new DocumentChunk(
                        "doc-13-chunk-" + index,
                        null,
                        ChunkType.NORMAL,
                        null,
                        index,
                        "分片正文 " + index
                ))
                .toList();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.index(
                        13L,
                        "失败保护文档",
                        null,
                        "knowledge/parsed/13.md",
                        "preprocess-v2",
                        "chunk-v2",
                        chunks
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("第二批向量化失败");

        verify(embeddingModel, times(2)).embedAll(anyList());
        verifyNoInteractions(embeddingStore);
    }

    @Test
    void rejectsUnexpectedEmbeddingDimensionBeforeReplacingOldIndex() {
        when(embeddingModel.embedAll(anyList()))
                .thenReturn(Response.from(List.of(Embedding.from(new float[1024]))));
        ElasticsearchKnowledgeClient client = client(properties());
        DocumentChunk chunk = new DocumentChunk(
                "doc-14-chunk-0", null, ChunkType.NORMAL, null, 0, "分片正文"
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.index(
                        14L,
                        "维度校验文档",
                        null,
                        "knowledge/parsed/14.md",
                        "preprocess-v2",
                        "chunk-v2",
                        List.of(chunk)
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected=1536")
                .hasMessageContaining("actual=1024");

        verifyNoInteractions(embeddingStore);
    }

    private ElasticsearchKnowledgeClient client(GroupChatProperties properties) {
        return new ElasticsearchKnowledgeClient(
                embeddingModel,
                embeddingStore,
                new EmbeddingTextBuilder(),
                properties,
                indexManager
        );
    }

    private GroupChatProperties properties() {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getRag().setEmbeddingModel("text-embedding-v4");
        properties.getRag().setEmbeddingDimension(EMBEDDING_DIMENSION);
        properties.getRag().setEmbeddingVersion("text-embedding-v4-1536-v1");
        properties.getRag().setEmbeddingBatchSize(10);
        return properties;
    }
}
