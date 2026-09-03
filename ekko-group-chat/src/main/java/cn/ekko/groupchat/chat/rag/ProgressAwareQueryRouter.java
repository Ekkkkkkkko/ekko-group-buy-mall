package cn.ekko.groupchat.chat.rag;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;

import java.util.Collection;
import java.util.function.Consumer;

/** 为数据源路由增加进度通知。 */
public class ProgressAwareQueryRouter implements QueryRouter {

    private final QueryRouter delegate;
    private final Consumer<String> progress;

    public ProgressAwareQueryRouter(QueryRouter delegate, Consumer<String> progress) {
        this.delegate = delegate;
        this.progress = progress;
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        progress.accept("正在选择知识库、关系库或图数据源...");
        return delegate.route(query);
    }
}
