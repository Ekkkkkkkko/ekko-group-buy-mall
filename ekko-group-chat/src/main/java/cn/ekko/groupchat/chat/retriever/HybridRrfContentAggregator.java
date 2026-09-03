package cn.ekko.groupchat.chat.retriever;

import cn.ekko.groupchat.config.GroupChatProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于稳定 chunkId 的 RRF 融合器，可选用本地 BGE ONNX 对融合候选二次重排。
 *
 * <p>不使用 Content.equals 去重：向量与 BM25 命中的分数元数据不同，按对象相等判断会把
 * 同一个知识分片误认为两条结果。这里优先使用 chunkId，其次使用 ES 文档 id。
 */
@Component
@Slf4j
public class HybridRrfContentAggregator implements ContentAggregator {

    private final GroupChatProperties properties;
    private final KnowledgeContextExpander contextExpander;
    private final ScoringModel scoringModel;

    @Autowired
    public HybridRrfContentAggregator(
            GroupChatProperties properties,
            KnowledgeContextExpander contextExpander,
            ObjectProvider<ScoringModel> scoringModelProvider
    ) {
        this(properties, contextExpander, scoringModelProvider.getIfAvailable());
    }

    HybridRrfContentAggregator(
            GroupChatProperties properties,
            KnowledgeContextExpander contextExpander,
            ScoringModel scoringModel
    ) {
        this.properties = properties;
        this.contextExpander = contextExpander;
        this.scoringModel = scoringModel;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        Map<String, FusedCandidate> fused = new LinkedHashMap<>();
        int rrfK = properties.getRetrieval().getRrfK();
        for (Collection<List<Content>> rankedLists : queryToContents.values()) {
            for (List<Content> rankedList : rankedLists) {
                Set<String> seenInList = new HashSet<>();
                for (int index = 0; index < rankedList.size(); index++) {
                    Content content = rankedList.get(index);
                    String identity = stableIdentity(content);
                    if (!seenInList.add(identity)) {
                        continue;
                    }
                    FusedCandidate candidate = fused.computeIfAbsent(
                            identity, ignored -> new FusedCandidate(content)
                    );
                    candidate.rrfScore += 1.0 / (rrfK + index + 1.0);
                    candidate.addSource(sourceOf(content));
                }
            }
        }

        List<Content> candidates = fused.values().stream()
                .sorted(Comparator.comparingDouble(FusedCandidate::rrfScore).reversed())
                .map(FusedCandidate::toContent)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Content> structuredResults = candidates.stream()
                .filter(this::skipRerank)
                .toList();
        if (!structuredResults.isEmpty()) {
            int limit = Math.min(properties.getRetrieval().getFinalMaxResults(), structuredResults.size());
            return structuredResults.subList(0, limit);
        }

        List<Content> ranked = scoringModel == null
                ? candidates
                : rerank(candidates, firstQueryText(queryToContents));
        int limit = Math.min(properties.getRetrieval().getFinalMaxResults(), ranked.size());
        return contextExpander.expand(ranked.subList(0, limit));
    }

    private List<Content> rerank(List<Content> candidates, String query) {
        try {
            List<Double> scores = scoringModel.scoreAll(
                    candidates.stream().map(Content::textSegment).toList(), query
            ).content();
            if (scores.size() != candidates.size()) {
                throw new IllegalStateException(
                        "BGE 重排分数数量不匹配: expected=" + candidates.size() + ", actual=" + scores.size()
                );
            }
            List<ScoredContent> scored = new ArrayList<>();
            for (int index = 0; index < candidates.size(); index++) {
                double score = scores.get(index);
                if (score >= properties.getRetrieval().getRerank().getMinScore()) {
                    scored.add(new ScoredContent(withRerankedScore(candidates.get(index), score), score));
                }
            }
            return scored.stream()
                    .sorted(Comparator.comparingDouble(ScoredContent::score).reversed())
                    .map(ScoredContent::content)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("BGE 重排失败，回退 RRF 排序", exception);
            return candidates;
        }
    }

    private Content withRerankedScore(Content content, double score) {
        Map<ContentMetadata, Object> metadata = new EnumMap<>(ContentMetadata.class);
        metadata.putAll(content.metadata());
        metadata.put(ContentMetadata.RERANKED_SCORE, score);
        Metadata segmentMetadata = content.textSegment().metadata().copy().put("rerankScore", score);
        return Content.from(TextSegment.from(content.textSegment().text(), segmentMetadata), metadata);
    }

    private String firstQueryText(Map<Query, Collection<List<Content>>> queryToContents) {
        return queryToContents.keySet().stream().findFirst().map(Query::text).orElse("");
    }

    private String stableIdentity(Content content) {
        Metadata metadata = content.textSegment().metadata();
        String chunkId = metadata.getString("chunkId");
        if (StringUtils.hasText(chunkId)) {
            return "chunk:" + chunkId;
        }
        Object embeddingId = content.metadata().get(ContentMetadata.EMBEDDING_ID);
        if (embeddingId != null && StringUtils.hasText(embeddingId.toString())) {
            return "embedding:" + embeddingId;
        }
        Long documentId = metadata.getLong("documentId");
        Integer chunkIndex = metadata.getInteger("chunkIndex");
        if (documentId != null && chunkIndex != null) {
            return "position:" + documentId + ":" + chunkIndex;
        }
        return "text:" + content.textSegment().text();
    }

    private String sourceOf(Content content) {
        String source = content.textSegment().metadata().getString("retrievalSource");
        return StringUtils.hasText(source) ? source : "UNKNOWN";
    }

    private boolean skipRerank(Content content) {
        return "true".equalsIgnoreCase(content.textSegment().metadata().getString("skipRerank"));
    }

    private record ScoredContent(Content content, double score) {
    }

    private static class FusedCandidate {

        private Content content;
        private double rrfScore;
        private final Set<String> sources = new LinkedHashSet<>();

        private FusedCandidate(Content content) {
            this.content = content;
        }

        private double rrfScore() {
            return rrfScore;
        }

        private void addSource(String source) {
            sources.add(source);
        }

        private Content toContent() {
            Metadata segmentMetadata = content.textSegment().metadata().copy()
                    .put("retrievalSources", String.join(",", sources))
                    .put("rrfScore", rrfScore);
            Map<ContentMetadata, Object> contentMetadata = new EnumMap<>(ContentMetadata.class);
            contentMetadata.putAll(content.metadata());
            return Content.from(
                    TextSegment.from(content.textSegment().text(), segmentMetadata),
                    contentMetadata
            );
        }
    }
}
