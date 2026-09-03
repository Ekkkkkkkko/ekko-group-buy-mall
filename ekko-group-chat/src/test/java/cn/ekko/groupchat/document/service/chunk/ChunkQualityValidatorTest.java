package cn.ekko.groupchat.document.service.chunk;

import cn.ekko.groupchat.config.GroupChatProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkQualityValidatorTest {

    private final ChunkQualityValidator validator = new ChunkQualityValidator(new GroupChatProperties());

    @Test
    void rejectsHeadingOnlyChunk() {
        DocumentChunk chunk = new DocumentChunk(
                "doc-1-chunk-0", null, ChunkType.NORMAL, "技术参数", 0, "## 技术参数"
        );

        assertThatThrownBy(() -> validator.validate(List.of(chunk)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只有 Markdown 标题");
    }

    @Test
    void rejectsUnconvertedTableTags() {
        DocumentChunk chunk = new DocumentChunk(
                "doc-1-chunk-0", null, ChunkType.NORMAL, null, 0,
                "</td></tr><tr><td>以太网端口</td>"
        );

        assertThatThrownBy(() -> validator.validate(List.of(chunk)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTML 表格");
    }

    @Test
    void acceptsCompleteTableOnlyWhenExcelHtmlModeIsExplicit() {
        DocumentChunk chunk = new DocumentChunk(
                "doc-1-chunk-0", null, ChunkType.NORMAL, "表格数据", 0,
                "<table><tr><td>TL-7</td></tr></table>"
        );

        assertThatCode(() -> validator.validate(List.of(chunk), true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(List.of(chunk), false))
                .hasMessageContaining("HTML 表格");
    }
}
