package cn.ekko.groupchat.chat.model;

import lombok.Getter;

/**
 * 检索命中的知识分块领域模型，封装分块所属文档、原文、来源路径及相似度得分，
 * 作为检索层与问答层之间的传递对象。
 */
@Getter
public class RetrievedChunk {

    private final Long documentId;
    private final String chunkId;
    private final String matchedChunkId;
    private final String title;
    private final Integer chunkIndex;
    private final String headingPath;
    private final String sourceObjectKey;
    private final String text;
    private final Double score;
    private final Double rrfScore;
    private final Double rerankScore;
    private final String retrievalSources;
    private final String contextExpansion;

    public RetrievedChunk(
            long documentId,
            String title,
            int chunkIndex,
            String sourceObjectKey,
            String text,
            Double score
    ) {
        this(documentId, null, null, title, chunkIndex, null, sourceObjectKey, text,
                score, null, null, null, null);
    }

    public RetrievedChunk(
            Long documentId,
            String chunkId,
            String matchedChunkId,
            String title,
            Integer chunkIndex,
            String headingPath,
            String sourceObjectKey,
            String text,
            Double score,
            Double rrfScore,
            Double rerankScore,
            String retrievalSources,
            String contextExpansion
    ) {
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.matchedChunkId = matchedChunkId;
        this.title = title;
        this.chunkIndex = chunkIndex;
        this.headingPath = headingPath;
        this.sourceObjectKey = sourceObjectKey;
        this.text = text;
        this.score = score;
        this.rrfScore = rrfScore;
        this.rerankScore = rerankScore;
        this.retrievalSources = retrievalSources;
        this.contextExpansion = contextExpansion;
    }

}
