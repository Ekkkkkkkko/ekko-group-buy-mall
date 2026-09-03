package cn.ekko.groupchat.document.service.preprocess;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeMarkdownPreprocessorTest {

    private final KnowledgeMarkdownPreprocessor preprocessor = new KnowledgeMarkdownPreprocessor();

    @Test
    void normalizesCompatibilityCharactersAndFlattensHtmlTableByCompleteRows() {
        String markdown = """
                ## 硬件规格

                <table>
                  <tr><th>参数</th><th>规格</th></tr>
                  <tr><td>以太网端⼝</td><td>4个2.5Gbps接⼝</td></tr>
                  <tr><td>功能</td><td>⽀持 MLO</td></tr>
                </table>
                """;

        String result = preprocessor.preprocess(markdown);

        assertThat(result)
                .contains("- 参数 | 规格")
                .contains("- 以太网端口：4个2.5Gbps接口")
                .contains("- 功能：支持 MLO")
                .doesNotContain("<table>", "</td>", "⼝", "⽀");
    }

    @Test
    void preservesRowspanCategoryInFollowingRows() {
        String markdown = """
                <table>
                  <tr><td rowspan="2">无线</td><td>频段</td><td>双频</td></tr>
                  <tr><td>协议</td><td>Wi-Fi 7</td></tr>
                </table>
                """;

        assertThat(preprocessor.preprocess(markdown))
                .contains("- 无线 / 频段：双频")
                .contains("- 无线 / 协议：Wi-Fi 7");
    }

    @Test
    void repairsHistoricalChunkThatStartsInsideATableCell() {
        String broken = """
                >以太网端口</td><td>4个2.5Gbps接口</td></tr>
                <tr><td>天线</td><td>四根</td></tr></table>

                ## 软件规格
                """;

        assertThat(preprocessor.preprocess(broken))
                .contains("- 以太网端口：4个2.5Gbps接口")
                .contains("- 天线：四根")
                .doesNotContain("</td>", "</tr>", "</table>");
    }

    @Test
    void preservesCompleteTableForExcelHtmlMode() {
        String html = "<table><tr><th>型号</th></tr><tr><td>TL-7</td></tr></table>";

        assertThat(preprocessor.preprocess(html, true))
                .contains("<table>", "<th>型号</th>", "<td>TL-7</td>", "</table>");
    }
}
