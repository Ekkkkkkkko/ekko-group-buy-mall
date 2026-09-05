package cn.ekko.groupchat.chat.service;

import cn.ekko.groupchat.chat.dto.ChatImageResponse;
import cn.ekko.groupchat.chat.dto.ChatSourceResponse;
import cn.ekko.groupchat.chat.model.RetrievedChunk;
import cn.ekko.groupchat.chat.persistence.entity.ChatMessage;
import cn.ekko.groupchat.document.service.image.KnowledgeImageMarkdown;
import cn.ekko.groupchat.document.service.image.KnowledgeImageResolver;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatSourceMapper {

    private final KnowledgeImageResolver imageResolver;

    public List<ChatSourceResponse> toResponses(List<Content> contents) {
        return contents.stream().map(this::toChunk).map(this::toResponse).toList();
    }

    public List<ChatMessage.RagReference> toReferences(List<Content> contents) {
        return contents.stream().map(this::toChunk).map(chunk -> new ChatMessage.RagReference(
                chunk.getDocumentId(), chunk.getChunkId(), chunk.getMatchedChunkId(), chunk.getTitle(),
                chunk.getChunkIndex(), chunk.getHeadingPath(),
                KnowledgeImageMarkdown.withoutImageTargets(chunk.getText()), chunk.getScore(),
                chunk.getRrfScore(), chunk.getRerankScore(), chunk.getRetrievalSources(),
                chunk.getContextExpansion()
        )).toList();
    }

    private ChatSourceResponse toResponse(RetrievedChunk source) {
        List<ChatImageResponse> images = imageResolver.resolve(source.getText()).stream()
                .map(image -> new ChatImageResponse(image.imageId(), image.description(), image.url(), image.sha256()))
                .toList();
        return new ChatSourceResponse(
                source.getDocumentId(), source.getChunkId(), source.getMatchedChunkId(), source.getTitle(),
                source.getChunkIndex(), source.getHeadingPath(),
                KnowledgeImageMarkdown.withoutImageTargets(source.getText()), source.getScore(),
                source.getRrfScore(), source.getRerankScore(), source.getRetrievalSources(),
                source.getContextExpansion(), images
        );
    }

    private RetrievedChunk toChunk(Content content) {
        Metadata metadata = content.textSegment().metadata();
        Object score = content.metadata().get(ContentMetadata.SCORE);
        Object rerankScore = content.metadata().get(ContentMetadata.RERANKED_SCORE);
        return new RetrievedChunk(
                metadata.getLong("documentId"), metadata.getString("chunkId"),
                metadata.getString("matchedChunkId"), metadata.getString("title"),
                metadata.getInteger("chunkIndex"), metadata.getString("headingPath"),
                metadata.getString("sourceObjectKey"), content.textSegment().text(),
                score instanceof Number number ? number.doubleValue() : null,
                metadata.getDouble("rrfScore"),
                rerankScore instanceof Number number ? number.doubleValue() : null,
                metadata.containsKey("retrievalSources")
                        ? metadata.getString("retrievalSources")
                        : metadata.getString("retrievalSource"),
                metadata.getString("contextExpansion")
        );
    }
}
