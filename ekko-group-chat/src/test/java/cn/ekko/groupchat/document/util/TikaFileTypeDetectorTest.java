package cn.ekko.groupchat.document.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikaFileTypeDetectorTest {

    private final TikaFileTypeDetector detector = new TikaFileTypeDetector();

    @Test
    void acceptsPdfByContentAndReturnsCanonicalMime() {
        assertThat(detector.detect("guide.pdf", "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("application/pdf");
    }

    @Test
    void rejectsPlainTextRenamedToPdf() {
        assertThatThrownBy(() -> detector.detect(
                "fake.pdf", "这不是 PDF".getBytes(StandardCharsets.UTF_8)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容与扩展名不匹配")
                .hasMessageContaining("detected=text/plain");
    }

    @Test
    void rejectsPdfRenamedToText() {
        assertThatThrownBy(() -> detector.detect(
                "fake.txt", "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extension=txt")
                .hasMessageContaining("detected=application/pdf");
    }

    @Test
    void acceptsMarkdownAndCsvAsTextBasedFormats() {
        assertThat(detector.detect("guide.md", "# 标题\n正文".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("text/markdown");
        assertThat(detector.detect("products.csv", "型号,价格\nTL-7,999".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("text/csv");
    }

    @Test
    void distinguishesOoxmlPackagesFromGenericZip() throws Exception {
        assertThat(detector.detect("guide.docx", ooxml("word/document.xml")))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(detector.detect("products.xlsx", ooxml("xl/workbook.xml")))
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThatThrownBy(() -> detector.detect("fake.docx", ooxml("payload.bin")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容与扩展名不匹配");
    }

    @Test
    void rejectsUnsupportedExtensionEvenWhenContentIsValid() {
        assertThatThrownBy(() -> detector.detect(
                "archive.zip", "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前支持");
    }

    private byte[] ooxml(String coreEntry) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            add(zip, "[Content_Types].xml", "<Types/>");
            add(zip, coreEntry, "<document/>");
        }
        return output.toByteArray();
    }

    private void add(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
