package cn.ekko.groupchat.chat.retriever;

import cn.ekko.groupchat.document.entity.KnowledgeChunk;
import cn.ekko.groupchat.document.service.KnowledgeChunkService;
import cn.ekko.groupchat.document.service.chunk.ChunkType;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentAwareContentRetrieverTest {

    @Test
    void replacesSiblingChildHitsWithOneParentChunk() {
        Content firstChild = child("parent-1-child-0", "子块一", 0.95);
        Content secondChild = child("parent-1-child-1", "子块二", 0.90);
        ContentRetriever delegate = query -> List.of(firstChild, secondChild);
        KnowledgeChunkService chunkService = mock(KnowledgeChunkService.class);
        KnowledgeChunk parent = new KnowledgeChunk();
        parent.setChunkId("parent-1");
        parent.setChunkType(ChunkType.PARENT);
        parent.setContent("这是包含完整操作步骤的父分片正文。");
        when(chunkService.findByChunkIds(anyCollection()))
                .thenReturn(Map.of("parent-1", parent));
        when(chunkService.findByBrotherChunkIds(anyCollection())).thenReturn(Map.of());

        ParentAwareContentRetriever retriever = new ParentAwareContentRetriever(delegate, chunkService);

        List<Content> result = retriever.retrieve(Query.from("如何组网易展路由器？"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().textSegment().text())
                .isEqualTo("这是包含完整操作步骤的父分片正文。");
        assertThat(result.getFirst().textSegment().metadata().getString("chunkId"))
                .isEqualTo("parent-1");
        assertThat(result.getFirst().textSegment().metadata().getString("matchedChunkId"))
                .isEqualTo("parent-1-child-0");
        assertThat(result.getFirst().metadata().get(ContentMetadata.SCORE)).isEqualTo(0.95);
        verify(chunkService).findByChunkIds(anyCollection());
    }

    @Test
    void restoresOrderedBrotherContextAndDeduplicatesHitsFromSameGroup() {
        Content first = brother("chunk-1", "第一段", 0.95);
        Content second = brother("chunk-2", "第二段", 0.91);
        ContentRetriever delegate = query -> List.of(first, second);
        KnowledgeChunkService chunkService = mock(KnowledgeChunkService.class);
        KnowledgeChunk firstStored = storedBrother("chunk-1", "第一段", 1);
        KnowledgeChunk secondStored = storedBrother("chunk-2", "第二段", 2);
        when(chunkService.findByChunkIds(anyCollection())).thenReturn(Map.of());
        when(chunkService.findByBrotherChunkIds(anyCollection()))
                .thenReturn(Map.of("brother-1", List.of(firstStored, secondStored)));

        ParentAwareContentRetriever retriever = new ParentAwareContentRetriever(delegate, chunkService);

        List<Content> result = retriever.retrieve(Query.from("安装步骤"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().textSegment().text()).isEqualTo("第一段\n\n第二段");
        assertThat(result.getFirst().textSegment().metadata().getString("contextExpansion"))
                .isEqualTo("BROTHER");
        assertThat(result.getFirst().textSegment().metadata().getString("matchedChunkId"))
                .isEqualTo("chunk-1");
    }

    private Content child(String chunkId, String text, double score) {
        Metadata metadata = new Metadata()
                .put("documentId", 11L)
                .put("chunkId", chunkId)
                .put("parentChunkId", "parent-1")
                .put("chunkType", ChunkType.CHILD.name())
                .put("chunkIndex", 0);
        return Content.from(
                TextSegment.from(text, metadata),
                Map.of(ContentMetadata.SCORE, score)
        );
    }

    private Content brother(String chunkId, String text, double score) {
        Metadata metadata = new Metadata()
                .put("documentId", 11L)
                .put("chunkId", chunkId)
                .put("brotherChunkId", "brother-1")
                .put("chunkType", ChunkType.NORMAL.name())
                .put("chunkIndex", 0);
        return Content.from(
                TextSegment.from(text, metadata),
                Map.of(ContentMetadata.SCORE, score)
        );
    }

    private KnowledgeChunk storedBrother(String chunkId, String content, int index) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setChunkId(chunkId);
        chunk.setBrotherChunkId("brother-1");
        chunk.setBrotherChunkIndex(index);
        chunk.setBrotherChunkTotal(2);
        chunk.setContent(content);
        chunk.setChunkType(ChunkType.NORMAL);
        return chunk;
    }
}
