package cn.ekko.groupchat.chat.retriever;

import cn.ekko.groupchat.document.entity.KnowledgeChunk;
import cn.ekko.groupchat.document.service.KnowledgeChunkService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在最终候选完成 RRF/重排后，按父子或兄弟关系补齐完整上下文。 */
@Component
@RequiredArgsConstructor
public class KnowledgeContextExpander {

    private final KnowledgeChunkService chunkService;

    public List<Content> expand(List<Content> hits) {
        Set<String> parentIds = new LinkedHashSet<>();
        Set<String> brotherIds = new LinkedHashSet<>();
        for (Content hit : hits) {
            String parentId = metadataString(hit.textSegment().metadata(), "parentChunkId");
            if (StringUtils.hasText(parentId)) {
                parentIds.add(parentId);
            }
            String brotherId = metadataString(hit.textSegment().metadata(), "brotherChunkId");
            if (StringUtils.hasText(brotherId)) {
                brotherIds.add(brotherId);
            }
        }

        Map<String, KnowledgeChunk> parents = chunkService.findByChunkIds(parentIds);
        Map<String, List<KnowledgeChunk>> brothers = chunkService.findByBrotherChunkIds(brotherIds);
        Map<String, Content> expanded = new LinkedHashMap<>();
        for (Content hit : hits) {
            Metadata hitMetadata = hit.textSegment().metadata();
            String parentId = metadataString(hitMetadata, "parentChunkId");
            KnowledgeChunk parent = StringUtils.hasText(parentId) ? parents.get(parentId) : null;
            if (parent != null) {
                Metadata parentMetadata = hitMetadata.copy()
                        .put("chunkId", parent.getChunkId())
                        .put("chunkType", parent.getChunkType().name())
                        .put("contextExpansion", "PARENT");
                putMatchedChunkId(parentMetadata, hitMetadata);
                expanded.putIfAbsent("parent:" + parentId, Content.from(
                        TextSegment.from(parent.getContent(), parentMetadata), hit.metadata()
                ));
                continue;
            }

            String brotherId = metadataString(hitMetadata, "brotherChunkId");
            List<KnowledgeChunk> siblingChunks = StringUtils.hasText(brotherId)
                    ? brothers.get(brotherId)
                    : null;
            if (siblingChunks != null && !siblingChunks.isEmpty()) {
                String siblingText = siblingChunks.stream()
                        .map(KnowledgeChunk::getContent)
                        .filter(StringUtils::hasText)
                        .reduce((left, right) -> left + "\n\n" + right)
                        .orElse(hit.textSegment().text());
                Metadata siblingMetadata = hitMetadata.copy()
                        .put("chunkId", brotherId)
                        .put("contextExpansion", "BROTHER");
                putMatchedChunkId(siblingMetadata, hitMetadata);
                expanded.putIfAbsent("brother:" + brotherId, Content.from(
                        TextSegment.from(siblingText, siblingMetadata), hit.metadata()
                ));
                continue;
            }

            String chunkId = metadataString(hitMetadata, "chunkId");
            String key = StringUtils.hasText(chunkId)
                    ? "chunk:" + chunkId
                    : "text:" + hit.textSegment().text();
            expanded.putIfAbsent(key, hit);
        }
        return List.copyOf(expanded.values());
    }

    private void putMatchedChunkId(Metadata target, Metadata source) {
        String matchedChunkId = metadataString(source, "chunkId");
        if (StringUtils.hasText(matchedChunkId)) {
            target.put("matchedChunkId", matchedChunkId);
        }
    }

    private String metadataString(Metadata metadata, String key) {
        return metadata.containsKey(key) ? metadata.getString(key) : null;
    }
}
