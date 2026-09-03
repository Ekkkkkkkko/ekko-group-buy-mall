package cn.ekko.groupchat.document.client;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchKnowledgeIndexManagerTest {

    @Test
    void acceptsConfiguredDenseVectorDimension() {
        Property vector = Property.of(property -> property
                .denseVector(denseVector -> denseVector.dims(1536)));

        assertThatCode(() -> ElasticsearchKnowledgeIndexManager.validateVectorMapping(
                "group_chat_knowledge_chunk_v2_1536",
                vector,
                1536
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsOldDimensionEvenWhenIndexContainsNoDocuments() {
        Property vector = Property.of(property -> property
                .denseVector(denseVector -> denseVector.dims(1024)));

        assertThatThrownBy(() -> ElasticsearchKnowledgeIndexManager.validateVectorMapping(
                "group_chat_knowledge_chunk",
                vector,
                1536
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected=1536")
                .hasMessageContaining("actual=1024")
                .hasMessageContaining("清空文档不会删除 mapping");
    }

    @Test
    void rejectsIndexWithoutDenseVectorMapping() {
        Property text = Property.of(property -> property.text(value -> value));

        assertThatThrownBy(() -> ElasticsearchKnowledgeIndexManager.validateVectorMapping(
                "group_chat_knowledge_chunk_v2_1536",
                text,
                1536
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少 dense_vector");
    }
}
