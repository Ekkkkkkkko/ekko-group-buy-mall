package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.AliyunOssClient;
import cn.ekko.groupchat.document.entity.DocumentStatus;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.event.DocumentChunkedEvent;
import cn.ekko.groupchat.document.mapper.KnowledgeDocumentMapper;
import cn.ekko.groupchat.document.service.chunk.ChunkType;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentPipelineServiceTest {

    @Mock
    private KnowledgeDocumentMapper documentMapper;
    @Mock
    private AliyunOssClient ossClient;
    @Mock
    private KnowledgeIndexingService indexingService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DocumentPipelineService pipelineService;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "pipeline-service-test"),
                KnowledgeDocument.class
        );
    }

    @BeforeEach
    void setUp() {
        pipelineService = new DocumentPipelineService(
                documentMapper,
                ossClient,
                indexingService,
                eventPublisher,
                new GroupChatProperties()
        );
    }

    @Test
    void chunksMarkdownBeforePublishingEmbeddingEvent() {
        KnowledgeDocument document = document(DocumentStatus.CHUNKING);
        document.setProcessedObjectKey("knowledge/parsed/1.processed.md");
        List<DocumentChunk> chunks = List.of(
                new DocumentChunk("parent", null, ChunkType.PARENT, "产品", -1, "完整父片"),
                new DocumentChunk("child", "parent", ChunkType.CHILD, "产品", 0, "检索子片")
        );
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(ossClient.getText("knowledge/parsed/1.processed.md")).thenReturn("# 产品\n正文");
        when(indexingService.chunk(document, "# 产品\n正文")).thenReturn(chunks);
        when(documentMapper.update(any())).thenReturn(1);

        pipelineService.chunkAndPublish(1L);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new DocumentChunkedEvent(1L));
    }

    @Test
    void publishesDocumentOnlyAfterEmbeddingSnapshotSucceeds() {
        KnowledgeDocument document = document(DocumentStatus.CHUNKED);
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(documentMapper.update(any())).thenReturn(1);
        when(indexingService.embed(document)).thenReturn(3);

        pipelineService.embedAndPublish(1L);

        verify(indexingService).embed(document);
        verify(documentMapper, org.mockito.Mockito.times(2)).update(any());
    }

    private KnowledgeDocument document(DocumentStatus status) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(1L);
        document.setStatus(status);
        document.setTitle("产品说明");
        return document;
    }
}
