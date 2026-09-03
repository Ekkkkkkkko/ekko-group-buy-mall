package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.AliyunOssClient;
import cn.ekko.groupchat.document.client.ElasticsearchKnowledgeClient;
import cn.ekko.groupchat.document.client.MineruClient;
import cn.ekko.groupchat.document.entity.DocumentStatus;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.mapper.KnowledgeDocumentMapper;
import cn.ekko.groupchat.document.util.TikaFileTypeDetector;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private KnowledgeDocumentMapper documentMapper;
    @Mock
    private AliyunOssClient ossClient;
    @Mock
    private MineruClient mineruClient;
    @Mock
    private ElasticsearchKnowledgeClient elasticsearchClient;
    @Mock
    private KnowledgeChunkService chunkService;
    @Mock
    private DirectDocumentConverter directDocumentConverter;
    @Mock
    private DocumentPipelineService pipelineService;

    private DocumentService documentService;
    private MockMultipartFile file;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "document-service-test"),
                KnowledgeDocument.class
        );
    }

    @BeforeEach
    void setUp() {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getOss().setOriginalPrefix("knowledge/original");
        properties.getOss().setParsedPrefix("knowledge/parsed");
        properties.getMineru().setSourceUrlExpiration(Duration.ofMinutes(30));
        documentService = new DocumentService(
                documentMapper, ossClient, mineruClient, elasticsearchClient, chunkService,
                directDocumentConverter, new TikaFileTypeDetector(), pipelineService, properties
        );
        file = new MockMultipartFile(
                "file", "router.pdf", "application/pdf", "%PDF-1.7\nsame-content".getBytes()
        );
    }

    @Test
    void returnsPublishedDocumentWithoutRepeatingExternalCalls() {
        KnowledgeDocument published = document(11L, DocumentStatus.PUBLISHED);
        when(documentMapper.selectOne(any())).thenReturn(published);

        KnowledgeDocument result = documentService.upload(file, "路由器说明书", "TL-TEST");

        assertThat(result).isSameAs(published);
        verify(documentMapper, never()).insert(any(KnowledgeDocument.class));
        verify(ossClient, never()).put(anyString(), any(byte[].class), anyString());
        verify(mineruClient, never()).createTask(anyString(), anyString());
    }

    @Test
    void retriesFailedDocumentByReusingItsId() {
        KnowledgeDocument failed = document(22L, DocumentStatus.FAILED);
        KnowledgeDocument parsing = document(22L, DocumentStatus.PARSING);
        when(documentMapper.selectOne(any())).thenReturn(failed);
        when(documentMapper.update(any())).thenReturn(1);
        when(ossClient.presignGet(anyString(), any(Duration.class))).thenReturn("https://signed.example/file");
        when(mineruClient.createTask(anyString(), anyString())).thenReturn("task-22");
        when(documentMapper.selectById(22L)).thenReturn(parsing);

        KnowledgeDocument result = documentService.upload(file, "重试说明书", "TL-RETRY");

        assertThat(result.getId()).isEqualTo(22L);
        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PARSING);
        verify(documentMapper, never()).insert(any(KnowledgeDocument.class));
        verify(ossClient).put(anyString(), any(byte[].class), anyString());
        verify(mineruClient).createTask("https://signed.example/file", "document-22");
    }

    @Test
    void createsAndProcessesANewDocument() {
        file = new MockMultipartFile(
                "file", "router.pdf", "text/plain", "%PDF-1.7\nsame-content".getBytes()
        );
        KnowledgeDocument parsing = document(44L, DocumentStatus.PARSING);
        when(documentMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument inserted = invocation.getArgument(0);
            inserted.setId(44L);
            return 1;
        });
        when(ossClient.presignGet(anyString(), any(Duration.class))).thenReturn("https://signed.example/new");
        when(mineruClient.createTask(anyString(), anyString())).thenReturn("task-44");
        when(documentMapper.selectById(44L)).thenReturn(parsing);

        KnowledgeDocument result = documentService.upload(file, "新说明书", "TL-NEW");

        assertThat(result.getId()).isEqualTo(44L);
        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PARSING);
        verify(documentMapper).insert(any(KnowledgeDocument.class));
        verify(ossClient).put(anyString(), any(byte[].class), eq("application/pdf"));
        verify(mineruClient).createTask("https://signed.example/new", "document-44");
    }

    @Test
    void handlesConcurrentInsertByReturningTheWinningDocument() {
        KnowledgeDocument processing = document(33L, DocumentStatus.UPLOADING);
        when(documentMapper.selectOne(any())).thenReturn(null, processing);
        when(documentMapper.insert(any(KnowledgeDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate sha256"));

        KnowledgeDocument result = documentService.upload(file, "并发说明书", "TL-RACE");

        assertThat(result).isSameAs(processing);
        verify(ossClient, never()).put(anyString(), any(byte[].class), anyString());
        verify(mineruClient, never()).createTask(anyString(), anyString());
    }

    @Test
    void rejectsMismatchedFileContentBeforePersistenceOrUpload() {
        MockMultipartFile disguisedFile = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "plain text".getBytes()
        );

        assertThatThrownBy(() -> documentService.upload(disguisedFile, "伪装文件", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容与扩展名不匹配");

        verify(documentMapper, never()).selectOne(any());
        verify(documentMapper, never()).insert(any(KnowledgeDocument.class));
        verify(ossClient, never()).put(anyString(), any(byte[].class), anyString());
        verify(mineruClient, never()).createTask(anyString(), anyString());
    }

    @Test
    void deletesEsChunksMysqlChunksAndDocumentRecord() {
        when(documentMapper.selectById(55L)).thenReturn(document(55L, DocumentStatus.PUBLISHED));
        when(documentMapper.deleteById(55L)).thenReturn(1);

        documentService.delete(55L);

        verify(elasticsearchClient).deleteByDocumentId(55L);
        verify(chunkService).deleteByDocumentId(55L);
        verify(documentMapper).deleteById(55L);
    }

    @Test
    void reindexesThroughAsynchronousPipelineWithoutSubmittingMineru() {
        KnowledgeDocument existing = document(66L, DocumentStatus.PUBLISHED);
        existing.setParsedObjectKey("knowledge/parsed/66.md");
        KnowledgeDocument chunking = document(66L, DocumentStatus.CHUNKING);
        when(documentMapper.selectById(66L)).thenReturn(chunking);

        KnowledgeDocument result = documentService.reindex(66L);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.CHUNKING);
        verify(pipelineService).restartChunking(66L);
        verify(ossClient, never()).getText(anyString());
        verify(mineruClient, never()).createTask(anyString(), anyString());
    }

    @Test
    void convertsMarkdownDirectlyAndSkipsMineru() {
        MockMultipartFile markdownFile = new MockMultipartFile(
                "file", "guide.md", "text/markdown", "# 产品\n支持 Wi-Fi 7".getBytes()
        );
        KnowledgeDocument chunking = document(77L, DocumentStatus.CHUNKING);
        when(documentMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument inserted = invocation.getArgument(0);
            inserted.setId(77L);
            return 1;
        });
        when(directDocumentConverter.supports("md")).thenReturn(true);
        when(directDocumentConverter.convert(any(byte[].class), anyString()))
                .thenReturn("# 产品\n支持 Wi-Fi 7");
        when(documentMapper.selectById(77L)).thenReturn(chunking);

        KnowledgeDocument result = documentService.upload(markdownFile, "产品说明", null);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.CHUNKING);
        verify(pipelineService).startDirectChunking(
                77L,
                "knowledge/original/77/bcd39e5778a060105cee88a97220ac6e6ec19c68cbc758f219352e7f17deeb04.md",
                "knowledge/parsed/77/bcd39e5778a060105cee88a97220ac6e6ec19c68cbc758f219352e7f17deeb04.md",
                "knowledge/parsed/77/bcd39e5778a060105cee88a97220ac6e6ec19c68cbc758f219352e7f17deeb04.processed.md"
        );
        verify(mineruClient, never()).createTask(anyString(), anyString());
    }

    private KnowledgeDocument document(long id, DocumentStatus status) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setStatus(status);
        document.setSha256("sha256");
        document.setChunkCount(0);
        return document;
    }
}
