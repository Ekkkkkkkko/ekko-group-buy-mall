package cn.ekko.groupchat.chat.rag;

import cn.ekko.groupchat.chat.persistence.service.ChatMessageService;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;

import java.util.Collection;
import java.util.function.Consumer;

/** 为查询改写增加进度通知和改写结果持久化。 */
public class ProgressAwareQueryTransformer implements QueryTransformer {

    private final QueryTransformer delegate;
    private final ChatMessageService messageService;
    private final String userMessageId;
    private final Consumer<String> progress;

    public ProgressAwareQueryTransformer(
            QueryTransformer delegate,
            ChatMessageService messageService,
            String userMessageId,
            Consumer<String> progress
    ) {
        this.delegate = delegate;
        this.messageService = messageService;
        this.userMessageId = userMessageId;
        this.progress = progress;
    }

    @Override
    public Collection<Query> transform(Query query) {
        progress.accept("正在改写并补全您的问题...");
        Collection<Query> transformed = delegate.transform(query);
        transformed.stream().findFirst()
                .ifPresent(value -> messageService.updateTransformContent(userMessageId, value.text()));
        return transformed;
    }
}
