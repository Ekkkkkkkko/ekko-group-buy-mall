package cn.ekko.groupchat.chat.service;

import cn.ekko.groupchat.chat.ai.CommonChatAiService;
import cn.ekko.groupchat.chat.ai.IntentRecognitionAiService;
import cn.ekko.groupchat.chat.ai.IntentRecognitionResult;
import cn.ekko.groupchat.chat.ai.PromptService;
import cn.ekko.groupchat.chat.ai.TitleSummaryAiService;
import cn.ekko.groupchat.chat.dto.ChatRequest;
import cn.ekko.groupchat.chat.dto.ChatResponse;
import cn.ekko.groupchat.chat.dto.ChatStreamEvent;
import cn.ekko.groupchat.chat.memory.DatabaseChatMemoryStore;
import cn.ekko.groupchat.chat.persistence.entity.ChatConversation;
import cn.ekko.groupchat.chat.persistence.service.ChatConversationService;
import cn.ekko.groupchat.chat.persistence.service.ChatMessageService;
import cn.ekko.groupchat.chat.query.KnowEngineQueryRouter;
import cn.ekko.groupchat.chat.query.ParallelQueryTransformer;
import cn.ekko.groupchat.chat.retriever.HybridRrfContentAggregator;
import cn.ekko.groupchat.config.GroupChatProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatApplicationServiceTest {

    private final IntentRecognitionAiService intentService = mock(IntentRecognitionAiService.class);
    private final CommonChatAiService commonService = mock(CommonChatAiService.class);
    private final TitleSummaryAiService titleService = mock(TitleSummaryAiService.class);
    private final DatabaseChatMemoryStore memoryStore = mock(DatabaseChatMemoryStore.class);
    private final ParallelQueryTransformer queryTransformer = mock(ParallelQueryTransformer.class);
    private final KnowEngineQueryRouter queryRouter = mock(KnowEngineQueryRouter.class);
    private final HybridRrfContentAggregator aggregator = mock(HybridRrfContentAggregator.class);
    private final PromptService promptService = mock(PromptService.class);
    private final ChatConversationService conversationService = mock(ChatConversationService.class);
    private final ChatMessageService messageService = mock(ChatMessageService.class);
    private final RecordingChatModel chatModel = new RecordingChatModel("支持 Wi-Fi 7。[资料1]");
    private final ImmediateStreamingModel streamingModel = new ImmediateStreamingModel();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private ChatApplicationService service;

    @BeforeEach
    void setUp() {
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId("conversation-1");
        when(conversationService.resolve(anyString(), anyString(), anyString())).thenReturn(conversation);
        when(messageService.saveUserMessage(anyString(), anyString())).thenReturn("user-message-1");
        when(messageService.saveAssistantPlaceholder(anyString())).thenReturn("assistant-message-1");
        when(intentService.recognize(anyString(), anyString())).thenReturn(relatedIntent());
        when(promptService.getPrompt(any(IntentRecognitionResult.class))).thenReturn("只依据资料回答");
        when(queryTransformer.transform(any())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0, Query.class);
            return List.of(query);
        });

        Content content = content();
        ContentRetriever retriever = query -> List.of(content);
        when(queryRouter.route(any())).thenReturn(List.of(retriever));
        when(aggregator.aggregate(any())).thenReturn(List.of(content));

        ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .build();
        GroupChatProperties properties = new GroupChatProperties();
        properties.getRag().setChatModel("test-model");

        service = new ChatApplicationService(
                intentService,
                commonService,
                titleService,
                chatModel,
                streamingModel,
                memoryProvider,
                memoryStore,
                queryTransformer,
                queryRouter,
                aggregator,
                executor,
                promptService,
                conversationService,
                messageService,
                new ChatSourceMapper(text -> List.of()),
                properties
        );
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void synchronousRagUsesAiServiceAndReturnsSources() {
        ChatResponse response = service.chat(request());

        assertThat(response.getConversationId()).isEqualTo("conversation-1");
        assertThat(response.getAnswer()).isEqualTo("支持 Wi-Fi 7。[资料1]");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().getFirst().getDocumentId()).isEqualTo(11L);
        assertThat(chatModel.calls()).isEqualTo(1);
        verify(messageService).updateReferences(anyString(), any());
        verify(messageService).updateContent(
                "assistant-message-1", "支持 Wi-Fi 7。[资料1]", "test-model");
    }

    @Test
    void emptyRetrievalStillCallsModelForCautiousAnswerLikeKnowEngine() {
        when(aggregator.aggregate(any())).thenReturn(List.of());
        chatModel.answer = "现有知识库无法确认";

        ChatResponse response = service.chat(request());

        assertThat(response.getAnswer()).isEqualTo("现有知识库无法确认");
        assertThat(response.getSources()).isEmpty();
        assertThat(chatModel.calls()).isEqualTo(1);
    }

    @Test
    void streamingRagPublishesProgressReferenceTokensAndComplete() {
        List<ChatStreamEvent> events = service.stream(request())
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ChatStreamEvent::type)
                .contains("PROGRESS", "REFERENCE", "ANSWER", "COMPLETE");
        ChatResponse completed = (ChatResponse) events.getLast().data();
        assertThat(completed.getConversationId()).isEqualTo("conversation-1");
        assertThat(completed.getAnswer()).isEqualTo("连接完成");
        assertThat(completed.getSources()).hasSize(1);
    }

    private cn.ekko.groupchat.chat.dto.ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("这款路由器支持 Wi-Fi 7 吗？");
        request.setConversationId("conversation-1");
        request.setClientId("client-1");
        return request;
    }

    private IntentRecognitionResult relatedIntent() {
        return new IntentRecognitionResult(
                "询问产品参数",
                true,
                "产品参数与选型",
                new IntentRecognitionResult.Entities("TL-7DR5130", null, null, "Wi-Fi 7", null)
        );
    }

    private Content content() {
        Metadata metadata = new Metadata()
                .put("documentId", 11L)
                .put("chunkId", "chunk-11")
                .put("title", "产品手册")
                .put("chunkIndex", 0)
                .put("retrievalSource", "VECTOR");
        return Content.from(TextSegment.from("支持 Wi-Fi 7。", metadata));
    }

    private static class RecordingChatModel implements ChatModel {
        private String answer;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public dev.langchain4j.model.chat.response.ChatResponse doChat(
                dev.langchain4j.model.chat.request.ChatRequest chatRequest
        ) {
            calls.incrementAndGet();
            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(AiMessage.from(answer))
                    .build();
        }

        int calls() {
            return calls.get();
        }
    }

    private static class ImmediateStreamingModel implements StreamingChatModel {
        @Override
        public void doChat(
                dev.langchain4j.model.chat.request.ChatRequest chatRequest,
                StreamingChatResponseHandler handler
        ) {
            handler.onPartialResponse("连接");
            handler.onPartialResponse("完成");
            handler.onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(AiMessage.from("连接完成"))
                    .build());
        }
    }
}
