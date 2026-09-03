package cn.ekko.groupchat.document.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MineruArchiveParserTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
    };

    @Test
    void extractsMarkdownAndReferencedImagesFromZip() throws Exception {
        byte[] archive = zip(Map.of(
                "result/full.md", "# 产品\n\n![](images/router.png)".getBytes(StandardCharsets.UTF_8),
                "result/images/router.png", PNG
        ));

        MineruParsedArchive parsed = new MineruArchiveParser().parse(archive);

        assertThat(parsed.markdownPath()).isEqualTo("result/full.md");
        assertThat(parsed.markdown()).contains("images/router.png");
        assertThat(parsed.images()).hasSize(1);
        assertThat(parsed.images().getFirst().sourcePath()).isEqualTo("result/images/router.png");
        assertThat(parsed.images().getFirst().contentType()).isEqualTo("image/png");
        assertThat(parsed.images().getFirst().sha256()).hasSize(64);
    }

    @Test
    void rejectsZipSlipEntry() throws Exception {
        byte[] archive = zip(Map.of(
                "full.md", "# 产品".getBytes(StandardCharsets.UTF_8),
                "../images/router.png", PNG
        ));

        assertThatThrownBy(() -> new MineruArchiveParser().parse(archive))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法 ZIP 路径");
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
