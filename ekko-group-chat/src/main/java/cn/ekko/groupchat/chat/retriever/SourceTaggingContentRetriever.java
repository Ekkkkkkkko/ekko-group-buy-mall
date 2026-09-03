package cn.ekko.groupchat.chat.retriever;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.List;

/** 为检索命中补充召回通道，供 RRF 融合和引用展示使用。 */
public class SourceTaggingContentRetriever implements RoutedContentRetriever {

    private final ContentRetriever delegate;
    private final String source;
    private final QueryRoute route;

    public SourceTaggingContentRetriever(ContentRetriever delegate, String source) {
        this(delegate, source, QueryRoute.KNOWLEDGE_BASE);
    }

    public SourceTaggingContentRetriever(ContentRetriever delegate, String source, QueryRoute route) {
        this.delegate = delegate;
        this.source = source;
        this.route = route;
    }

    @Override
    public QueryRoute route() {
        return route;
    }

    @Override
    public List<Content> retrieve(Query query) {
        return delegate.retrieve(query).stream()
                .map(content -> {
                    Metadata metadata = content.textSegment().metadata().copy()
                            .put("retrievalSource", source);
                    return Content.from(
                            TextSegment.from(content.textSegment().text(), metadata),
                            content.metadata()
                    );
                })
                .toList();
    }
}
