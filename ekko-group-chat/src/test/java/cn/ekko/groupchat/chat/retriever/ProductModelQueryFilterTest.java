package cn.ekko.groupchat.chat.retriever;

import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductModelQueryFilterTest {

    private final ProductModelQueryFilter filter = new ProductModelQueryFilter();

    @Test
    void detectsModelCaseInsensitivelyAndBuildsMetadataFilter() {
        assertThat(filter.detect("tl-7dr6560 有几个 2.5G 端口？"))
                .contains("TL-7DR6560");
        assertThat(filter.filter(Query.from("tl-7dr6560 有几个 2.5G 端口？")))
                .isNotNull();
    }

    @Test
    void doesNotMistakeWifiForProductModel() {
        assertThat(filter.detect("支持 Wi-Fi 7 吗？")).isEmpty();
        assertThat(filter.filter(Query.from("支持 Wi-Fi 7 吗？"))).isNull();
    }
}
