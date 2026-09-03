package cn.ekko.groupchat.chat.rag;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 在 RRF/重排完成时通知引用消费者，并在模型调用前发出生成进度。 */
public class ProgressAwareContentAggregator implements ContentAggregator {

    private final ContentAggregator delegate;
    private final Consumer<String> progress;
    private final Consumer<List<Content>> retrieved;

    public ProgressAwareContentAggregator(
            ContentAggregator delegate,
            Consumer<String> progress,
            Consumer<List<Content>> retrieved
    ) {
        this.delegate = delegate;
        this.progress = progress;
        this.retrieved = retrieved;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        progress.accept("正在融合、去重并重排检索结果...");
        List<Content> contents = delegate.aggregate(queryToContents);
        retrieved.accept(contents);
        progress.accept(contents.isEmpty()
                ? "未检索到可靠资料，正在生成谨慎答复..."
                : "检索与重排完成，正在生成回答...");
        return contents;
    }
}
