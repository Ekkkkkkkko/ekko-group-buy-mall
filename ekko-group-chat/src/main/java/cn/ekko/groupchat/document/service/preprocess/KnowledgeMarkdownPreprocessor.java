package cn.ekko.groupchat.document.service.preprocess;

import cn.ekko.groupchat.document.service.chunk.TextChunkSupport;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MinerU Markdown 的确定性预处理器。
 *
 * <p>原始 Markdown 仍保存在 OSS；该类只生成用于分片与索引的清洗文本。
 */
@Component
public class KnowledgeMarkdownPreprocessor {

    private static final Pattern HTML_TABLE = Pattern.compile(
            "(?is)<table\\b[^>]*>.*?</table>"
    );
    private static final Pattern LEADING_ORPHAN_TABLE = Pattern.compile(
            "(?is)^\\s*>?([^<]*</td>.*?</table>)"
    );
    private static final Map<String, String> OCR_REPLACEMENTS = Map.ofEntries(
            Map.entry("戶", "户"),
            Map.entry("無線", "无线"),
            Map.entry("端⼝", "端口"),
            Map.entry("客⼾端", "客户端"),
            Map.entry("⽀持", "支持"),
            Map.entry("⼀代", "一代")
    );

    public String preprocess(String rawMarkdown) {
        return preprocess(rawMarkdown, false);
    }

    /** Excel HTML 模式保留完整表格标签，其余格式仍扁平化为键值文本。 */
    public String preprocess(String rawMarkdown, boolean preserveHtmlTables) {
        if (!StringUtils.hasText(rawMarkdown)) {
            return "";
        }
        String unicodeNormalized = Normalizer.normalize(rawMarkdown, Normalizer.Form.NFKC);
        for (Map.Entry<String, String> replacement : OCR_REPLACEMENTS.entrySet()) {
            unicodeNormalized = unicodeNormalized.replace(replacement.getKey(), replacement.getValue());
        }
        return TextChunkSupport.normalize(
                preserveHtmlTables ? unicodeNormalized : flattenHtmlTables(unicodeNormalized)
        );
    }

    private String flattenHtmlTables(String markdown) {
        String repairedMarkdown = repairBoundaryTable(markdown);
        Matcher matcher = HTML_TABLE.matcher(repairedMarkdown);
        StringBuilder result = new StringBuilder(repairedMarkdown.length());
        int previousEnd = 0;
        while (matcher.find()) {
            result.append(repairedMarkdown, previousEnd, matcher.start());
            result.append('\n').append(tableToKeyValueText(matcher.group())).append('\n');
            previousEnd = matcher.end();
        }
        result.append(repairedMarkdown, previousEnd, repairedMarkdown.length());
        return result.toString();
    }

    /** 容错处理上游已经从表格中间截断、只剩开头闭合标签的历史文本。 */
    private String repairBoundaryTable(String markdown) {
        Matcher leadingOrphan = LEADING_ORPHAN_TABLE.matcher(markdown);
        String repaired = leadingOrphan.find()
                ? "<table><tr><td>" + leadingOrphan.group(1) + markdown.substring(leadingOrphan.end())
                : markdown;

        String lowerCase = repaired.toLowerCase(java.util.Locale.ROOT);
        int lastOpen = lowerCase.lastIndexOf("<table");
        int lastClose = lowerCase.lastIndexOf("</table>");
        return lastOpen > lastClose ? repaired + "</table>" : repaired;
    }

    private String tableToKeyValueText(String tableHtml) {
        Element table = Jsoup.parse(tableHtml, "", Parser.htmlParser()).selectFirst("table");
        if (table == null) {
            return "";
        }

        Map<Integer, RowSpan> activeSpans = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();
        for (Element row : table.select("tr")) {
            TreeMap<Integer, String> valuesByColumn = new TreeMap<>();
            activeSpans.forEach((column, span) -> valuesByColumn.put(column, span.value()));
            decrementRowSpans(activeSpans);

            int column = 0;
            boolean headerRow = true;
            for (Element cell : row.children()) {
                if (!cell.normalName().equals("td") && !cell.normalName().equals("th")) {
                    continue;
                }
                headerRow &= cell.normalName().equals("th");
                while (valuesByColumn.containsKey(column)) {
                    column++;
                }
                String value = normalizeCell(cell.text());
                int colspan = positiveInt(cell.attr("colspan"), 1);
                int rowspan = positiveInt(cell.attr("rowspan"), 1);
                for (int offset = 0; offset < colspan; offset++) {
                    valuesByColumn.put(column + offset, value);
                    if (rowspan > 1) {
                        activeSpans.put(column + offset, new RowSpan(value, rowspan - 1));
                    }
                }
                column += colspan;
            }

            List<String> values = valuesByColumn.values().stream()
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            if (!values.isEmpty()) {
                lines.add("- " + formatRow(values, headerRow));
            }
        }
        return String.join("\n", lines);
    }

    private void decrementRowSpans(Map<Integer, RowSpan> activeSpans) {
        activeSpans.replaceAll((column, span) -> new RowSpan(span.value(), span.remainingRows() - 1));
        activeSpans.entrySet().removeIf(entry -> entry.getValue().remainingRows() <= 0);
    }

    private String formatRow(List<String> values, boolean headerRow) {
        if (values.size() == 1) {
            return values.getFirst();
        }
        if (headerRow) {
            return String.join(" | ", values);
        }
        String label = String.join(" / ", values.subList(0, values.size() - 1));
        return label + "：" + values.getLast();
    }

    private String normalizeCell(String value) {
        return value.replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record RowSpan(String value, int remainingRows) {
    }
}
