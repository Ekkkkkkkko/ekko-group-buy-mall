package cn.ekko.groupchat.document.util;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 使用 Apache Tika 按文件内容检测上传类型，并校验检测结果与扩展名是否兼容。
 * 客户端提交的 Content-Type 不参与可信类型判断。
 */
@Component
public class TikaFileTypeDetector {

    private static final int MAX_ZIP_ENTRIES_TO_SCAN = 10_000;

    private static final Map<String, String> CANONICAL_MIME_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("txt", "text/plain"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("csv", "text/csv")
    );

    private static final Set<String> TEXT_MIME_TYPES = Set.of(
            "text/plain", "text/markdown", "text/x-markdown", "application/markdown",
            "text/csv", "application/csv"
    );

    private static final Set<String> OLE_MIME_TYPES = Set.of(
            "application/x-tika-msoffice", "application/x-ole-storage",
            "application/msword", "application/vnd.ms-excel"
    );

    private final Tika tika = new Tika();

    /**
     * 返回按扩展名规范化后的可信 MIME；内容与扩展名不一致时拒绝上传。
     */
    public String detect(String fileName, byte[] content) {
        String extension = extension(fileName);
        String canonicalMime = CANONICAL_MIME_TYPES.get(extension);
        if (canonicalMime == null) {
            throw new IllegalArgumentException(
                    "当前支持 PDF、图片、Word、Markdown、TXT、Excel、CSV 文件"
            );
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String detectedMime = tika.detect(content).toLowerCase(Locale.ROOT);
        if (!isCompatible(extension, detectedMime, content)) {
            throw new IllegalArgumentException(
                    "文件内容与扩展名不匹配: extension=" + extension + ", detected=" + detectedMime
            );
        }
        return canonicalMime;
    }

    private boolean isCompatible(String extension, String detectedMime, byte[] content) {
        return switch (extension) {
            case "pdf" -> "application/pdf".equals(detectedMime);
            case "png" -> "image/png".equals(detectedMime);
            case "jpg", "jpeg" -> "image/jpeg".equals(detectedMime);
            case "doc" -> OLE_MIME_TYPES.contains(detectedMime);
            case "xls" -> OLE_MIME_TYPES.contains(detectedMime);
            case "docx" -> isOoxmlPackage(content, "word/document.xml");
            case "xlsx" -> isOoxmlPackage(content, "xl/workbook.xml");
            case "md", "markdown", "txt", "csv" -> TEXT_MIME_TYPES.contains(detectedMime);
            default -> false;
        };
    }

    /** Tika Core 能识别 ZIP，但不解析容器；补查核心条目以区分普通 ZIP、DOCX 与 XLSX。 */
    private boolean isOoxmlPackage(byte[] content, String requiredEntry) {
        boolean hasContentTypes = false;
        boolean hasRequiredEntry = false;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ZIP_ENTRIES_TO_SCAN) {
                    return false;
                }
                String name = entry.getName().replace('\\', '/');
                if ("[Content_Types].xml".equals(name)) {
                    hasContentTypes = true;
                } else if (requiredEntry.equals(name)) {
                    hasRequiredEntry = true;
                }
                if (hasContentTypes && hasRequiredEntry) {
                    return true;
                }
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    private String extension(String fileName) {
        String extension = StringUtils.getFilenameExtension(fileName);
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }
}
