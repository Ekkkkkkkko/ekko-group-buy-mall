package cn.ekko.groupchat.document.service.chunk;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.service.chunk.strategy.LengthChunkingStrategy;
import cn.ekko.groupchat.document.service.chunk.strategy.ExcelChunkingStrategy;
import cn.ekko.groupchat.document.service.chunk.strategy.MarkdownBrotherChunkingStrategy;
import cn.ekko.groupchat.document.service.chunk.strategy.MarkdownTitleChunkingStrategy;
import cn.ekko.groupchat.document.service.chunk.strategy.RegexChunkingStrategy;
import cn.ekko.groupchat.document.service.chunk.strategy.SeparatorChunkingStrategy;
import cn.ekko.groupchat.document.service.chunk.strategy.SmartChunkingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingStrategyFactoryTest {

    private GroupChatProperties properties;
    private ChunkingStrategyFactory factory;

    @BeforeEach
    void setUp() {
        properties = new GroupChatProperties();
        LengthChunkingStrategy length = new LengthChunkingStrategy();
        MarkdownTitleChunkingStrategy title = new MarkdownTitleChunkingStrategy();
        factory = new ChunkingStrategyFactory(
                List.of(
                        new SmartChunkingStrategy(title, length),
                        title,
                        length,
                        new SeparatorChunkingStrategy(),
                        new RegexChunkingStrategy(),
                        new MarkdownBrotherChunkingStrategy(),
                        new ExcelChunkingStrategy()
                ),
                properties
        );
    }

    @Test
    void selectsConfiguredSeparatorStrategy() {
        properties.getRag().setChunkStrategy("separator");
        properties.getRag().setChunkSeparator("---CUT---");
        properties.getRag().setChunkSize(100);
        properties.getRag().setChunkOverlap(10);
        properties.getRag().setParentChunkSize(200);

        List<DocumentChunk> chunks = factory.split(7L, "第一部分---CUT---第二部分");

        assertThat(chunks).extracting(DocumentChunk::getText)
                .containsExactly("第一部分", "第二部分");
        assertThat(chunks).extracting(DocumentChunk::getChunkId)
                .containsExactly("doc-7-chunk-0", "doc-7-chunk-1");
    }

    @Test
    void smartUsesTitleHierarchyAndParentChildChunksForLongChapter() {
        properties.getRag().setChunkStrategy("SMART");
        properties.getRag().setChunkSize(80);
        properties.getRag().setChunkOverlap(10);
        properties.getRag().setParentChunkSize(180);
        properties.getRag().setTitleLevel(3);
        String markdown = """
                # 易展路由器

                ## 组网步骤

                第一步连接主路由器并确认指示灯常亮。第二步打开手机应用进入设备页面。
                第三步添加新的易展路由器，并等待自动同步无线名称和密码。
                第四步将子路由移动到需要扩展覆盖的位置，再次确认信号强度。
                第五步如果组网失败，恢复出厂设置后重新执行以上步骤。
                """;

        List<DocumentChunk> chunks = factory.split(8L, markdown);

        List<DocumentChunk> parents = chunks.stream()
                .filter(chunk -> chunk.getType() == ChunkType.PARENT)
                .toList();
        List<DocumentChunk> children = chunks.stream()
                .filter(chunk -> chunk.getType() == ChunkType.CHILD)
                .toList();
        assertThat(parents).isNotEmpty();
        assertThat(children).isNotEmpty();
        assertThat(children).allSatisfy(child -> {
            assertThat(child.isSearchable()).isTrue();
            assertThat(child.getParentChunkId()).isNotBlank();
            assertThat(child.getHeadingPath()).contains("易展路由器");
            assertThat(parents).extracting(DocumentChunk::getChunkId)
                    .contains(child.getParentChunkId());
        });
        assertThat(parents).allSatisfy(parent -> assertThat(parent.isSearchable()).isFalse());
    }

    @Test
    void smartStillUsesParentChildStrategyWhenMarkdownHasNoHeading() {
        properties.getRag().setChunkStrategy("SMART");
        properties.getRag().setChunkSize(20);
        properties.getRag().setChunkOverlap(5);
        properties.getRag().setParentChunkSize(60);

        List<DocumentChunk> chunks = factory.split(
                9L,
                "这是一段没有任何Markdown标题的路由器说明文字，需要按照自然边界和长度进行切分。"
        );

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).anyMatch(chunk -> chunk.getType() == ChunkType.PARENT);
        assertThat(chunks).anyMatch(chunk -> chunk.getType() == ChunkType.CHILD);
    }

    @Test
    void documentStrategyOverridesSystemDefault() {
        properties.getRag().setChunkStrategy("LENGTH");
        properties.getRag().setChunkSeparator("---CUT---");
        properties.getRag().setChunkSize(100);
        properties.getRag().setChunkOverlap(10);
        properties.getRag().setParentChunkSize(200);

        List<DocumentChunk> chunks = factory.split(
                10L,
                "第一部分---CUT---第二部分",
                ChunkingStrategyType.SEPARATOR
        );

        assertThat(chunks).extracting(DocumentChunk::getText)
                .containsExactly("第一部分", "第二部分");
    }

    @Test
    void removesHeadingOnlySectionsAndMergesShortPreambleIntoNextChapter() {
        properties.getRag().setChunkStrategy("TITLE");
        properties.getRag().setChunkSize(200);
        properties.getRag().setChunkOverlap(20);
        properties.getRag().setParentChunkSize(400);
        properties.getRag().setSmallChunkMergeThreshold(30);

        List<DocumentChunk> chunks = factory.split(12L, """
                产品型号：TL-7DR6560

                # 产品介绍
                ## 技术参数
                ### 硬件规格

                以太网端口：4个2.5Gbps接口
                """);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getHeadingPath()).isEqualTo("产品介绍 > 技术参数 > 硬件规格");
        assertThat(chunks.getFirst().getText())
                .contains("产品型号：TL-7DR6560")
                .contains("以太网端口：4个2.5Gbps接口")
                .doesNotContain("## 技术参数");
    }

    @Test
    void blockAwareLengthSplitNeverStartsFromMiddleOfTableRow() {
        String longRow = "- 以太网端口：" + "4个2.5Gbps接口，".repeat(20);

        List<String> chunks = TextChunkSupport.splitNaturally(
                "产品参数\n\n" + longRow + "\n\n支持 EasyMesh。",
                80,
                20
        );

        assertThat(chunks).anyMatch(chunk -> chunk.equals(longRow));
        assertThat(chunks).noneMatch(chunk -> chunk.startsWith("2.5Gbps接口"));
    }

    @Test
    void brotherStrategyMarksAllPartsOfLongSectionAsOneOrderedGroup() {
        properties.getRag().setChunkSize(30);
        properties.getRag().setChunkOverlap(3);
        properties.getRag().setParentChunkSize(100);

        List<DocumentChunk> chunks = factory.split(
                13L,
                "# 安装步骤\n\n" + "连接设备并检查指示灯。".repeat(12),
                ChunkingStrategyType.BROTHER
        );

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).extracting(DocumentChunk::getBrotherChunkId)
                .containsOnly("doc-13-brother-0");
        assertThat(chunks).extracting(DocumentChunk::getBrotherChunkIndex)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, chunks.size())
                        .boxed().toList());
        assertThat(chunks).extracting(DocumentChunk::getBrotherChunkTotal)
                .containsOnly(chunks.size());
    }

    @Test
    void excelStrategyNeverSplitsOneRow() {
        properties.getRag().setChunkSize(35);
        properties.getRag().setChunkOverlap(0);
        properties.getRag().setParentChunkSize(100);
        String oversizedRow = "- 型号：TL-7；描述：" + "超长字段".repeat(20);

        List<DocumentChunk> chunks = factory.split(
                14L,
                oversizedRow + "\n\n- 型号：TL-8；描述：普通字段",
                ChunkingStrategyType.EXCEL
        );

        assertThat(chunks.getFirst().getText()).isEqualTo(oversizedRow);
        assertThat(chunks).extracting(DocumentChunk::getText)
                .contains("- 型号：TL-8；描述：普通字段");
    }
}
