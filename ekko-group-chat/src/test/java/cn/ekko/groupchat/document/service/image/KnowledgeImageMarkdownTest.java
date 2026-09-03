package cn.ekko.groupchat.document.service.image;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeImageMarkdownTest {

    @Test
    void extractsStableIdsAndRemovesTargetsFromEmbeddingText() {
        String markdown = "参数说明\n\n![背部接口](knowledge-image://12)\n\n> 图片说明：包含2.5G接口";

        assertThat(KnowledgeImageMarkdown.imageIds(markdown)).containsExactly(12L);
        assertThat(KnowledgeImageMarkdown.withoutImageTargets(markdown))
                .contains("图片：背部接口")
                .contains("图片说明：包含2.5G接口")
                .doesNotContain("knowledge-image://12");
    }
}
