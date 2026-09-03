package cn.ekko.groupchat.chat.query;

import cn.ekko.groupchat.config.GroupChatProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelQueryTransformerTest {

    @Test
    void generatesFourCandidatesAndSelectsBestOne() {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getRetrieval().setQueryRewriteEnabled(true);
        RecordingRewriteModel model = new RecordingRewriteModel();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ParallelQueryTransformer transformer = new ParallelQueryTransformer(model, properties, executor);

            Collection<Query> transformed = transformer.transform(Query.from("这个TP link路由咋安装呀"));

            assertThat(transformed).singleElement()
                    .extracting(Query::text)
                    .isEqualTo("TP-LINK 路由器安装方法");
            assertThat(model.calls()).isEqualTo(5);
        }
    }

    private static class RecordingRewriteModel implements ChatModel {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public dev.langchain4j.model.chat.response.ChatResponse doChat(ChatRequest request) {
            calls.incrementAndGet();
            String prompt = ((UserMessage) request.messages().getLast()).singleText();
            String result;
            if (prompt.contains("策略：简化")) {
                result = "TP link 路由安装";
            } else if (prompt.contains("策略：抽象")) {
                result = "路由器部署流程";
            } else if (prompt.contains("策略：纠错")) {
                result = "TP-LINK 路由器怎么安装";
            } else if (prompt.contains("策略：标准化")) {
                result = "TP-LINK 路由器安装方法";
            } else {
                result = "TP-LINK 路由器安装方法";
            }
            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(AiMessage.from(result))
                    .build();
        }

        int calls() {
            return calls.get();
        }
    }
}
