package cn.ekko.groupchat.chat.dto;

import lombok.Getter;

import java.util.List;

/**
 * 问答引用来源，描述命中的单个知识分块：
 * 所属文档、标题、分块序号、原文及相似度得分。
 */
@Getter
public class ChatSourceResponse {

    private final Long documentId;
    private final String chunkId;
    private final String matchedChunkId;
    private final String title;
    private final Integer chunkIndex;
    private final String headingPath;
    private final String text;
    private final Double score;
    private final Double rrfScore;
    private final Double rerankScore;
    private final String retrievalSources;
    private final String contextExpansion;
    private final List<ChatImageResponse> images;

    public ChatSourceResponse(
            long documentId,
            String title,
            int chunkIndex,
            String text,
            Double score,
            List<ChatImageResponse> images
    ) {
        this(documentId, null, null, title, chunkIndex, null, text, score,
                null, null, null, null, images);
    }

    public ChatSourceResponse(
            Long documentId,
            String chunkId,
            String matchedChunkId,
            String title,
            Integer chunkIndex,
            String headingPath,
            String text,
            Double score,
            Double rrfScore,
            Double rerankScore,
            String retrievalSources,
            String contextExpansion,
            List<ChatImageResponse> images
    ) {
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.matchedChunkId = matchedChunkId;
        this.title = title;
        this.chunkIndex = chunkIndex;
        this.headingPath = headingPath;
        this.text = text;
        this.score = score;
        this.rrfScore = rrfScore;
        this.rerankScore = rerankScore;
        this.retrievalSources = retrievalSources;
        this.contextExpansion = contextExpansion;
        this.images = images;
    }

}
