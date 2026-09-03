package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.entity.KnowledgeChunk;
import cn.ekko.groupchat.document.mapper.KnowledgeChunkMapper;
import cn.ekko.groupchat.document.service.chunk.ChunkType;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeChunkServiceTest {

    @Mock
    private KnowledgeChunkMapper chunkMapper;
    @Mock
    private StringRedisTemplate redisTemplate;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "knowledge-chunk-service-test"),
                KnowledgeChunk.class
        );
    }

    @Test
    void replacesAllChunksAndKeepsParentRelationship() {
        when(chunkMapper.insert(any(KnowledgeChunk.class))).thenReturn(1);
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        KnowledgeChunkService service = new KnowledgeChunkService(
                chunkMapper, redisTemplate, new GroupChatProperties()
        );
        DocumentChunk parent = new DocumentChunk(
                "doc-11-parent-0", null, ChunkType.PARENT, "说明书 > 设置", -1, "完整父块"
        );
        DocumentChunk child = new DocumentChunk(
                "doc-11-parent-0-child-0",
                "doc-11-parent-0",
                ChunkType.CHILD,
                "说明书 > 设置",
                0,
                "可检索子块"
        );

        service.replaceAll(11L, List.of(parent, child));

        verify(chunkMapper).delete(any());
        ArgumentCaptor<KnowledgeChunk> captor = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(KnowledgeChunk::getChunkId)
                .containsExactly("doc-11-parent-0", "doc-11-parent-0-child-0");
        assertThat(captor.getAllValues().get(0).getSearchable()).isFalse();
        assertThat(captor.getAllValues().get(1).getParentChunkId())
                .isEqualTo("doc-11-parent-0");
        assertThat(captor.getAllValues().get(1).getSearchable()).isTrue();
    }
}
