package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DirectDocumentConverterTest {

    @Test
    void convertsCsvToKeyValueRowsByDefault() {
        GroupChatProperties properties = new GroupChatProperties();
        DirectDocumentConverter converter = new DirectDocumentConverter(properties);

        String result = converter.convert(
                "型号,描述\nTL-7,<支持EasyMesh>\nTL-8,第二行".getBytes(StandardCharsets.UTF_8),
                "csv"
        );

        assertThat(result)
                .contains("型号：TL-7；描述：<支持EasyMesh>")
                .contains("型号：TL-8；描述：第二行")
                .doesNotContain("<table>");
    }

    @Test
    void convertsCsvToEscapedHtmlTablesWithoutSplittingRows() {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getRag().setExcelHtmlMode(true);
        properties.getRag().setChunkSize(80);
        DirectDocumentConverter converter = new DirectDocumentConverter(properties);

        String result = converter.convert(
                "型号,描述\nTL-7,<支持EasyMesh>\nTL-8,第二行".getBytes(StandardCharsets.UTF_8),
                "csv"
        );

        assertThat(result).contains("<table>").contains("&lt;支持EasyMesh&gt;");
        assertThat(result.split("</table>", -1)).hasSize(3);
        assertThat(result.split("<thead>", -1)).hasSize(3);
    }
}
