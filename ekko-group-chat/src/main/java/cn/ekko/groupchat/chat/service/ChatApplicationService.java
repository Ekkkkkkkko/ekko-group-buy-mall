package cn.ekko.groupchat.chat.service;

import cn.ekko.groupchat.chat.ai.CommonChatAiService;
import cn.ekko.groupchat.chat.ai.GroupChatAiService;
import cn.ekko.groupchat.chat.ai.IntentRecognitionAiService;
import cn.ekko.groupchat.chat.ai.IntentRecognitionResult;
import cn.ekko.groupchat.chat.ai.PromptService;
import cn.ekko.groupchat.chat.ai.TitleSummaryAiService;
import cn.ekko.groupchat.chat.dto.ChatRequest;
import cn.ekko.groupchat.chat.dto.ChatResponse;
import cn.ekko.groupchat.chat.dto.ChatSourceResponse;
import cn.ekko.groupchat.chat.dto.ChatStreamEvent;
import cn.ekko.groupchat.chat.memory.DatabaseChatMemoryStore;
import cn.ekko.groupchat.chat.persistence.entity.ChatConversation;
import cn.ekko.groupchat.chat.persistence.entity.ChatMessage;
import cn.ekko.groupchat.chat.persistence.service.ChatConversationService;
import cn.ekko.groupchat.chat.persistence.service.ChatMessageService;
import cn.ekko.groupchat.chat.query.KnowEngineQueryRouter;
import cn.ekko.groupchat.chat.query.ParallelQueryTransformer;
import cn.ekko.groupchat.chat.rag.ProgressAwareContentAggregator;
import cn.ekko.groupchat.chat.rag.ProgressAwareQueryRouter;
import cn.ekko.groupchat.chat.rag.ProgressAwareQueryTransformer;
import cn.ekko.groupchat.chat.retriever.HybridRrfContentAggregator;
import cn.ekko.groupchat.config.GroupChatProperties;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 统一聊天应用服务：会话创建、标题、消息持久化、意图识别、普通聊天或动态 RAG、引用与流式事件。
 */
@Service
@Slf4j
public class ChatApplicationService {

    private static final String FAILURE_MESSAGE = "回答生成失败，请稍后重试。";

    private final IntentRecognitionAiService intentRecognitionAiService;
    private final CommonChatAiService commonChatAiService;
    private final TitleSummaryAiService titleSummaryAiService;
    private final ChatModel ragChatModel;
    private final StreamingChatModel ragStreamingChatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final DatabaseChatMemoryStore memoryStore;
    private final ParallelQueryTransformer queryTransformer;
    private final KnowEngineQueryRouter queryRouter;
    private final HybridRrfContentAggregator contentAggregator;
    private final ExecutorService retrievalExecutor;
    private final PromptService promptService;
    private final ChatConversationService conversationService;
    private final ChatMessageService messageService;
    private final ChatSourceMapper sourceMapper;
    private final GroupChatProperties properties;

    public ChatApplicationService(
            IntentRecognitionAiService intentRecognitionAiService,
            CommonChatAiService commonChatAiService,
            TitleSummaryAiService titleSummaryAiService,
            @Qualifier("ragChatModel") ChatModel ragChatModel,
            @Qualifier("ragStreamingChatModel") StreamingChatModel ragStreamingChatModel,
            ChatMemoryProvider chatMemoryProvider,
            DatabaseChatMemoryStore memoryStore,
            ParallelQueryTransformer queryTransformer,
            KnowEngineQueryRouter queryRouter,
            HybridRrfContentAggregator contentAggregator,
            @Qualifier("retrievalVirtualThreadExecutor") ExecutorService retrievalExecutor,
            PromptService promptService,
            ChatConversationService conversationService,
            ChatMessageService messageService,
            ChatSourceMapper sourceMapper,
            GroupChatProperties properties
    ) {
        this.intentRecognitionAiService = intentRecognitionAiService;
        this.commonChatAiService = commonChatAiService;
        this.titleSummaryAiService = titleSummaryAiService;
        this.ragChatModel = ragChatModel;
        this.ragStreamingChatModel = ragStreamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.memoryStore = memoryStore;
        this.queryTransformer = queryTransformer;
        this.queryRouter = queryRouter;
        this.contentAggregator = contentAggregator;
        this.retrievalExecutor = retrievalExecutor;
        this.promptService = promptService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.sourceMapper = sourceMapper;
        this.properties = properties;
    }

    public ChatResponse chat(ChatRequest request) {
        PreparedChat prepared = prepare(request);
        try {
            IntentRecognitionResult recognition = recognize(prepared);
            if (!recognition.related()) {
                String answer = commonChatAiService.chat(prepared.conversationId(), prepared.question());
                complete(prepared, answer, List.of(), commonModelName());
                return new ChatResponse(prepared.conversationId(), answer, List.of());
            }

            AtomicReference<List<Content>> retrieved = new AtomicReference<>(List.of());
            GroupChatAiService aiService = buildRagAiService(
                    prepared,
                    recognition,
                    ignored -> { },
                    contents -> {
                        retrieved.set(contents);
                        messageService.updateReferences(
                                prepared.assistantMessageId(), sourceMapper.toReferences(contents));
                    }
            );
            Result<String> result = aiService.chat(prepared.conversationId(), prepared.question());
            List<Content> contents = result.sources() == null ? retrieved.get() : result.sources();
            List<ChatSourceResponse> sources = sourceMapper.toResponses(contents);
            complete(prepared, result.content(), sources, ragModelName());
            return new ChatResponse(prepared.conversationId(), result.content(), sources);
        } catch (RuntimeException exception) {
            fail(prepared, exception);
            throw exception;
        }
    }

    public Flux<ChatStreamEvent> stream(ChatRequest request) {
        return Mono.fromCallable(() -> prepare(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(prepared -> Flux.concat(
                                Flux.just(ChatStreamEvent.progress("正在识别您的意图...")),
                                Mono.fromCallable(() -> recognize(prepared))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .flatMapMany(recognition -> recognition.related()
                                                ? streamRag(prepared, recognition)
                                                : streamCommon(prepared))
                        )
                        .onErrorResume(exception -> {
                            fail(prepared, exception);
                            return Flux.just(ChatStreamEvent.error(FAILURE_MESSAGE));
                        }));
    }

    public List<ChatConversation> listConversations(String clientId) {
        return conversationService.list(clientId);
    }

    public List<ChatMessage> listMessages(String clientId, String conversationId) {
        conversationService.requireOwned(clientId, conversationId);
        return messageService.list(conversationId);
    }

    public boolean deleteConversation(String clientId, String conversationId) {
        conversationService.requireOwned(clientId, conversationId);
        memoryStore.deleteMessages(conversationId);
        return conversationService.delete(clientId, conversationId);
    }

    private IntentRecognitionResult recognize(PreparedChat prepared) {
        IntentRecognitionResult recognition = intentRecognitionAiService.recognize(
                prepared.conversationId(), prepared.question());
        // 意图识别的结构化 AI 回复不应成为最终 RAG 对话历史。
        memoryStore.evictCache(prepared.conversationId());
        return recognition;
    }

    private Flux<ChatStreamEvent> streamCommon(PreparedChat prepared) {
        StringBuilder answer = new StringBuilder();
        Flux<ChatStreamEvent> tokens = commonChatAiService
                .streamChat(prepared.conversationId(), prepared.question())
                .doOnNext(answer::append)
                .map(ChatStreamEvent::answer);
        return Flux.concat(
                Flux.just(ChatStreamEvent.progress("正在为您生成回答...")),
                tokens,
                Mono.fromSupplier(() -> {
                    String completed = answer.toString();
                    complete(prepared, completed, List.of(), commonModelName());
                    return ChatStreamEvent.complete(
                            new ChatResponse(prepared.conversationId(), completed, List.of()));
                })
        );
    }

    private Flux<ChatStreamEvent> streamRag(PreparedChat prepared, IntentRecognitionResult recognition) {
        return Flux.<ChatStreamEvent>create(sink -> {
                    StringBuilder answer = new StringBuilder();
                    AtomicReference<List<ChatSourceResponse>> sources = new AtomicReference<>(List.of());
                    Consumer<String> progress = message -> sink.next(ChatStreamEvent.progress(message));
                    Consumer<List<Content>> retrieved = contents -> {
                        List<ChatSourceResponse> mapped = sourceMapper.toResponses(contents);
                        sources.set(mapped);
                        messageService.updateReferences(
                                prepared.assistantMessageId(), sourceMapper.toReferences(contents));
                        if (!mapped.isEmpty()) {
                            sink.next(ChatStreamEvent.reference(mapped));
                        }
                    };

                    try {
                        GroupChatAiService aiService = buildRagAiService(
                                prepared, recognition, progress, retrieved);
                        Disposable disposable = aiService
                                .streamChat(prepared.conversationId(), prepared.question())
                                .subscribe(token -> {
                                    answer.append(token);
                                    sink.next(ChatStreamEvent.answer(token));
                                }, sink::error, () -> {
                                    String completed = answer.toString();
                                    complete(prepared, completed, sources.get(), ragModelName());
                                    sink.next(ChatStreamEvent.complete(new ChatResponse(
                                            prepared.conversationId(), completed, sources.get())));
                                    sink.complete();
                                });
                        sink.onCancel(disposable::dispose);
                    } catch (RuntimeException exception) {
                        sink.error(exception);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.parallel());
    }

    private GroupChatAiService buildRagAiService(
            PreparedChat prepared,
            IntentRecognitionResult recognition,
            Consumer<String> progress,
            Consumer<List<Content>> retrieved
    ) {
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(new ProgressAwareQueryTransformer(
                        queryTransformer, messageService, prepared.userMessageId(), progress))
                .queryRouter(new ProgressAwareQueryRouter(queryRouter, progress))
                .contentAggregator(new ProgressAwareContentAggregator(
                        contentAggregator, progress, retrieved))
                .contentInjector(new DefaultContentInjector())
                .executor(retrievalExecutor)
                .build();

        return AiServices.builder(GroupChatAiService.class)
                .chatModel(ragChatModel)
                .streamingChatModel(ragStreamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .systemMessage(promptService.getPrompt(recognition))
                .retrievalAugmentor(retrievalAugmentor)
                .build();
    }

    private PreparedChat prepare(ChatRequest request) {
        boolean newConversation = !StringUtils.hasText(request.getConversationId());
        ChatConversation conversation = conversationService.resolve(
                request.getClientId(), request.getConversationId(), request.getQuestion());
        String userMessageId = messageService.saveUserMessage(
                conversation.getConversationId(), request.getQuestion());
        String assistantMessageId = messageService.saveAssistantPlaceholder(conversation.getConversationId());
        conversationService.touch(conversation.getConversationId());
        if (newConversation) {
            generateTitleAsync(conversation.getConversationId(), request.getQuestion());
        }
        return new PreparedChat(
                conversation.getConversationId(), request.getQuestion(), userMessageId, assistantMessageId);
    }

    private void generateTitleAsync(String conversationId, String question) {
        Thread.ofVirtual().name("chat-title-" + conversationId).start(() -> {
            try {
                conversationService.updateTitle(
                        conversationId, titleSummaryAiService.generateTitle(question));
            } catch (RuntimeException exception) {
                log.warn("生成会话标题失败, conversationId={}", conversationId, exception);
            }
        });
    }

    private void complete(
            PreparedChat prepared,
            String answer,
            List<ChatSourceResponse> sources,
            String modelName
    ) {
        messageService.updateContent(prepared.assistantMessageId(), answer, modelName);
        if (sources != null && !sources.isEmpty()) {
            // 引用通常已在聚合回调写入；同步结果在供应商实现不触发回调时仍由 Result.sources 兜底。
            conversationService.touch(prepared.conversationId());
        }
        memoryStore.evictCache(prepared.conversationId());
    }

    private void fail(PreparedChat prepared, Throwable exception) {
        log.error("聊天失败, conversationId={}", prepared.conversationId(), exception);
        messageService.updateContent(prepared.assistantMessageId(), FAILURE_MESSAGE, ragModelName());
        memoryStore.evictCache(prepared.conversationId());
    }

    private String ragModelName() {
        return configuredOrDefault(properties.getRag().getRagChatModel(), properties.getRag().getChatModel());
    }

    private String commonModelName() {
        return configuredOrDefault(properties.getRag().getCommonChatModel(), properties.getRag().getChatModel());
    }

    private String configuredOrDefault(String configured, String fallback) {
        return StringUtils.hasText(configured) ? configured : fallback;
    }

    private record PreparedChat(
            String conversationId,
            String question,
            String userMessageId,
            String assistantMessageId
    ) {
    }
}
