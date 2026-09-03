package cn.ekko.groupchat.chat.query;

import cn.ekko.groupchat.config.GroupChatProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 四路并行问题改写器：简化、抽象、纠错、标准化分别生成候选，再选择最适合检索的一条。
 */
@Component
@Slf4j
public class ParallelQueryTransformer implements QueryTransformer {

    private final ChatModel chatModel;
    private final GroupChatProperties properties;
    private final ExecutorService executor;

    public ParallelQueryTransformer(
            ChatModel chatModel,
            GroupChatProperties properties,
            @Qualifier("retrievalVirtualThreadExecutor") ExecutorService executor
    ) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public Collection<Query> transform(Query query) {
        if (!properties.getRetrieval().isQueryRewriteEnabled()) {
            return List.of(query);
        }
        String memory = formatMemory(query);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (RewriteStrategy strategy : RewriteStrategy.values()) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> rewrite(query.text(), memory, strategy), executor
            ).exceptionally(exception -> {
                log.warn("查询改写候选生成失败, strategy={}", strategy, exception);
                return null;
            }));
        }

        LinkedHashSet<String> uniqueCandidates = new LinkedHashSet<>();
        for (CompletableFuture<String> future : futures) {
            String candidate = sanitize(future.join());
            if (StringUtils.hasText(candidate)) {
                uniqueCandidates.add(candidate);
            }
        }
        if (uniqueCandidates.isEmpty()) {
            return List.of(query);
        }

        String selected = select(query.text(), List.copyOf(uniqueCandidates));
        Query transformed = query.metadata() == null
                ? Query.from(selected)
                : Query.from(selected, query.metadata());
        log.info("查询改写完成, original={}, rewritten={}", query.text(), selected);
        return List.of(transformed);
    }

    private String rewrite(String question, String memory, RewriteStrategy strategy) {
        String prompt = """
                你是拼团商城产品知识库的检索查询优化器。
                请严格使用指定策略改写问题，使其更适合 Elasticsearch 关键词和向量检索。
                不得回答问题，不得添加原问题中没有的产品参数，只输出一行改写结果。

                策略：%s
                策略要求：%s
                历史对话：%s
                原始问题：%s
                """.formatted(strategy.label, strategy.instruction, memory, question);
        return chatModel.chat(prompt);
    }

    private String select(String original, List<String> candidates) {
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        StringBuilder listed = new StringBuilder();
        for (int index = 0; index < candidates.size(); index++) {
            listed.append(index + 1).append(". ").append(candidates.get(index)).append('\n');
        }
        String prompt = """
                你是检索查询评估器。请从候选中选择语义完整、产品型号准确、关键词明确，
                且最适合同时用于 BM25 和向量检索的一条。不得回答原问题，只能原样输出一个候选。

                原始问题：%s
                候选：
                %s
                """.formatted(original, listed);
        try {
            String selected = sanitize(chatModel.chat(prompt));
            if (StringUtils.hasText(selected)) {
                for (String candidate : candidates) {
                    if (selected.equals(candidate) || selected.contains(candidate)) {
                        return candidate;
                    }
                }
            }
        } catch (RuntimeException exception) {
            log.warn("查询改写候选评估失败，回退首个候选", exception);
        }
        return candidates.getFirst();
    }

    private String formatMemory(Query query) {
        if (query.metadata() == null || query.metadata().chatMemory() == null
                || query.metadata().chatMemory().isEmpty()) {
            return "无";
        }
        return query.metadata().chatMemory().stream()
                .map(this::formatMessage)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("无");
    }

    private String formatMessage(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return "用户：" + userMessage.singleText();
        }
        if (message instanceof AiMessage aiMessage && !aiMessage.hasToolExecutionRequests()) {
            return "助手：" + aiMessage.text();
        }
        return null;
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = value.trim()
                .replaceFirst("^```(?:text)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .replaceFirst("^(?:改写结果|输出|查询)[:：]\\s*", "")
                .trim();
        if ((sanitized.startsWith("\"") && sanitized.endsWith("\""))
                || (sanitized.startsWith("“") && sanitized.endsWith("”"))) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }
        return sanitized.lines().findFirst().orElse(sanitized).trim();
    }

    private enum RewriteStrategy {
        SIMPLE("简化", "删除语气词、重复表达和无关修饰，保留实体、型号、功能和故障关键词"),
        ABSTRACT("抽象", "把口语化的具体描述归纳为可检索的产品能力、操作流程或故障主题"),
        TYPO_CORRECTION("纠错", "纠正明显错别字、拼音和术语错误，不改变原有业务含义"),
        STANDARDIZE("标准化", "规范产品型号、英文大小写、单位和技术术语，并补全依赖历史对话的指代");

        private final String label;
        private final String instruction;

        RewriteStrategy(String label, String instruction) {
            this.label = label;
            this.instruction = instruction;
        }
    }
}
