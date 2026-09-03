package cn.ekko.groupchat.document.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU 解析结果 ZIP 包解析器，从压缩包中提取 {@code full.md} 与图片资源。
 *
 * <p>内置安全防护：限制压缩包与 Markdown 体积、校验 ZIP 条目路径
 * （防 Zip Slip 目录穿越）。
 */
@Component
public class MineruArchiveParser {

    private static final int DEFAULT_MAX_ARCHIVE_BYTES = 100 * 1024 * 1024;
    private static final int DEFAULT_MAX_MARKDOWN_BYTES = 20 * 1024 * 1024;
    private static final int DEFAULT_MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    private static final int DEFAULT_MAX_IMAGE_COUNT = 100;
    private static final long DEFAULT_MAX_EXTRACTED_BYTES = 200L * 1024 * 1024;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final int maxArchiveBytes;
    private final int maxMarkdownBytes;

    /**
     * ZIP 内图片数量上限与图片处理阶段共用同一配置，避免解析器固定阈值
     * 导致外部 {@code CHAT_MAX_IMAGES_PER_DOCUMENT} 配置无法生效。
     */
    @Value("${group-chat.image.max-images-per-document:100}")
    private int maxImageCount = DEFAULT_MAX_IMAGE_COUNT;

    public MineruArchiveParser() {
        this(DEFAULT_MAX_ARCHIVE_BYTES, DEFAULT_MAX_MARKDOWN_BYTES);
    }

    MineruArchiveParser(int maxArchiveBytes, int maxMarkdownBytes) {
        this.maxArchiveBytes = maxArchiveBytes;
        this.maxMarkdownBytes = maxMarkdownBytes;
    }

    public MineruParsedArchive parse(byte[] archive) {
        if (archive == null || archive.length == 0) {
            throw new IllegalStateException("MinerU ZIP 文件为空");
        }
        if (archive.length > maxArchiveBytes) {
            throw new IllegalStateException("MinerU ZIP 文件过大");
        }

        String markdownPath = null;
        String markdown = null;
        long extractedBytes = 0;
        List<MineruParsedImage> images = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path normalized = safePath(entry.getName());
                if (entry.isDirectory() || normalized.getFileName() == null) {
                    zip.closeEntry();
                    continue;
                }
                String fileName = normalized.getFileName().toString();
                if ("full.md".equals(fileName)) {
                    if (markdown != null) {
                        throw new IllegalStateException("MinerU ZIP 中存在多个 full.md");
                    }
                    markdownPath = portablePath(normalized);
                    ByteArrayOutputStream content = readEntry(
                            zip, maxMarkdownBytes, "MinerU Markdown 文件过大"
                    );
                    extractedBytes = checkedExtractedBytes(extractedBytes, content.size());
                    markdown = content.toString(StandardCharsets.UTF_8);
                } else if (isSupportedImage(fileName)) {
                    if (images.size() >= maxImageCount) {
                        throw new IllegalStateException("MinerU ZIP 图片数量超过限制");
                    }
                    byte[] content = readEntry(zip, DEFAULT_MAX_IMAGE_BYTES, "MinerU 图片文件过大")
                            .toByteArray();
                    extractedBytes = checkedExtractedBytes(extractedBytes, content.length);
                    ImageType type = detectImageType(content);
                    if (type == null) {
                        throw new IllegalStateException("MinerU ZIP 包含格式不受支持的图片: " + normalized);
                    }
                    images.add(new MineruParsedImage(
                            portablePath(normalized),
                            fileName,
                            type.contentType(),
                            type.extension(),
                            content,
                            sha256(content)
                    ));
                }
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取 MinerU ZIP 失败", exception);
        }
        if (!StringUtils.hasText(markdown)) {
            throw new IllegalStateException(markdown == null
                    ? "MinerU ZIP 中缺少 full.md"
                    : "MinerU full.md 为空");
        }
        return new MineruParsedArchive(markdownPath, markdown, images);
    }

    /** 兼容只需要 Markdown 的调用方；新入库流程应使用 {@link #parse(byte[])}。 */
    public String extractMarkdown(byte[] archive) {
        return parse(archive).markdown();
    }

    private Path safePath(String entryName) {
        try {
            Path normalized = Path.of(entryName.replace('\\', '/')).normalize();
            if (normalized.isAbsolute()
                    || normalized.getNameCount() == 0
                    || normalized.startsWith("..")) {
                throw new IllegalStateException("非法 ZIP 路径: " + entryName);
            }
            return normalized;
        } catch (InvalidPathException exception) {
            throw new IllegalStateException("非法 ZIP 路径: " + entryName, exception);
        }
    }

    private ByteArrayOutputStream readEntry(ZipInputStream zip, int maxBytes, String errorMessage)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (output.size() + read > maxBytes) {
                throw new IllegalStateException(errorMessage);
            }
            output.write(buffer, 0, read);
        }
        return output;
    }

    private boolean isSupportedImage(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && IMAGE_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private ImageType detectImageType(byte[] content) {
        if (content.length >= 8
                && (content[0] & 0xff) == 0x89
                && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
            return new ImageType("image/png", "png");
        }
        if (content.length >= 3
                && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) {
            return new ImageType("image/jpeg", "jpg");
        }
        if (content.length >= 6) {
            String header = new String(content, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(header) || "GIF89a".equals(header)) {
                return new ImageType("image/gif", "gif");
            }
        }
        if (content.length >= 12
                && "RIFF".equals(new String(content, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(content, 8, 4, StandardCharsets.US_ASCII))) {
            return new ImageType("image/webp", "webp");
        }
        return null;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private String portablePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private long checkedExtractedBytes(long current, int next) {
        long total = current + next;
        if (total > DEFAULT_MAX_EXTRACTED_BYTES) {
            throw new IllegalStateException("MinerU ZIP 解压后的 Markdown 与图片总大小超过限制");
        }
        return total;
    }

    private record ImageType(String contentType, String extension) {
    }
}
