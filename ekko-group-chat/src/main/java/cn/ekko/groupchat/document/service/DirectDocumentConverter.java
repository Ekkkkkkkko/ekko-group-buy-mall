package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 不需要 MinerU 的轻量格式转换器。Markdown/TXT 保留正文；Excel/CSV 可转成
 * 键值对段落或完整 HTML 表格，供 ExcelChunkingStrategy 在不拆行的前提下分块。
 */
@Component
@RequiredArgsConstructor
public class DirectDocumentConverter {

    private static final Set<String> DIRECT_EXTENSIONS = Set.of("md", "markdown", "txt", "xls", "xlsx", "csv");
    private static final Set<String> SPREADSHEET_EXTENSIONS = Set.of("xls", "xlsx", "csv");

    private final GroupChatProperties properties;

    public boolean supports(String extension) {
        return DIRECT_EXTENSIONS.contains(normalize(extension));
    }

    public boolean isSpreadsheet(String extension) {
        return SPREADSHEET_EXTENSIONS.contains(normalize(extension));
    }

    public String convert(byte[] content, String extension) {
        String normalizedExtension = normalize(extension);
        if ("md".equals(normalizedExtension) || "markdown".equals(normalizedExtension)
                || "txt".equals(normalizedExtension)) {
            return decodeUtf8(content);
        }
        if ("csv".equals(normalizedExtension)) {
            return rowsToMarkdown(readCsv(content));
        }
        if ("xls".equals(normalizedExtension) || "xlsx".equals(normalizedExtension)) {
            return rowsToMarkdown(readExcel(content));
        }
        throw new IllegalArgumentException("不支持直接转换的文件格式: " + extension);
    }

    private List<List<String>> readExcel(byte[] content) {
        List<List<String>> rows = new ArrayList<>();
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            EasyExcel.read(input, new ReadListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    int maxIndex = data.keySet().stream().max(Integer::compareTo).orElse(-1);
                    List<String> row = new ArrayList<>(maxIndex + 1);
                    for (int index = 0; index <= maxIndex; index++) {
                        row.add(normalizeCell(data.get(index)));
                    }
                    rows.add(row);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 数据已逐行收集。
                }
            }).headRowNumber(0).sheet().doRead();
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取 Excel 文件失败", exception);
        }
        return rows;
    }

    private List<List<String>> readCsv(byte[] content) {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取 CSV 文件失败", exception);
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (value == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (value == ',' && !quoted) {
                fields.add(normalizeCell(current.toString()));
                current.setLength(0);
            } else {
                current.append(value);
            }
        }
        fields.add(normalizeCell(current.toString()));
        return fields;
    }

    private String rowsToMarkdown(List<List<String>> rows) {
        return properties.getRag().isExcelHtmlMode()
                ? rowsToHtmlTables(rows, properties.getRag().getChunkSize())
                : rowsToKeyValueMarkdown(rows);
    }

    private String rowsToKeyValueMarkdown(List<List<String>> rows) {
        if (rows.size() < 2) {
            throw new IllegalArgumentException("Excel/CSV 至少需要表头和一行数据");
        }
        List<String> headers = rows.getFirst();
        List<String> result = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            List<String> pairs = new ArrayList<>();
            int width = Math.max(headers.size(), row.size());
            for (int column = 0; column < width; column++) {
                String header = column < headers.size() ? headers.get(column) : "列" + (column + 1);
                String value = column < row.size() ? row.get(column) : "";
                if (!StringUtils.hasText(header) && !StringUtils.hasText(value)) {
                    continue;
                }
                pairs.add((StringUtils.hasText(header) ? header : "列" + (column + 1)) + "：" + value);
            }
            if (!pairs.isEmpty()) {
                result.add("- " + String.join("；", pairs));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Excel/CSV 没有可入库的数据行");
        }
        return String.join("\n\n", result);
    }

    /** HTML 模式按完整行累积表格；单行即使超长也会作为整体保留。 */
    private String rowsToHtmlTables(List<List<String>> rows, int chunkSize) {
        if (rows.size() < 2) {
            throw new IllegalArgumentException("Excel/CSV 至少需要表头和一行数据");
        }
        List<String> headers = rows.getFirst();
        List<String> tables = new ArrayList<>();
        List<List<String>> currentRows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.stream().noneMatch(StringUtils::hasText)) {
                continue;
            }
            List<List<String>> candidate = new ArrayList<>(currentRows);
            candidate.add(row);
            if (!currentRows.isEmpty()
                    && buildHtmlTable(headers, candidate).length() > chunkSize) {
                tables.add(buildHtmlTable(headers, currentRows));
                currentRows = new ArrayList<>();
            }
            currentRows.add(row);
        }
        if (!currentRows.isEmpty()) {
            tables.add(buildHtmlTable(headers, currentRows));
        }
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("Excel/CSV 没有可入库的数据行");
        }
        return String.join("\n\n", tables);
    }

    private String buildHtmlTable(List<String> headers, List<List<String>> rows) {
        StringBuilder html = new StringBuilder("<table>\n<thead><tr>");
        for (String header : headers) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead>\n<tbody>\n");
        for (List<String> row : rows) {
            html.append("<tr>");
            for (int column = 0; column < headers.size(); column++) {
                String value = column < row.size() ? row.get(column) : "";
                html.append("<td>").append(escapeHtml(value)).append("</td>");
            }
            html.append("</tr>\n");
        }
        return html.append("</tbody>\n</table>").toString();
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String decodeUtf8(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private String normalizeCell(String value) {
        return value == null ? "" : value.replace('\uFEFF', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalize(String extension) {
        return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
    }
}
