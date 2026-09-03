package cn.ekko.groupchat.chat.retriever;

import cn.ekko.groupchat.document.service.KnowledgeChunkService;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;

/**
 * 上下文补全装饰器：父子策略回溯父片，兄弟策略补齐同级分段。
 */
public class ParentAwareContentRetriever implements ContentRetriever {

    private final ContentRetriever delegate;
    private final KnowledgeContextExpander contextExpander;

    public ParentAwareContentRetriever(ContentRetriever delegate, KnowledgeChunkService chunkService) {
        this(delegate, new KnowledgeContextExpander(chunkService));
    }

    public ParentAwareContentRetriever(ContentRetriever delegate, KnowledgeContextExpander contextExpander) {
        this.delegate = delegate;
        this.contextExpander = contextExpander;
    }

    @Override
    public List<Content> retrieve(Query query) {
        return contextExpander.expand(delegate.retrieve(query));
    }
}
