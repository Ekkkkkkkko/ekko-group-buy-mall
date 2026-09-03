package cn.ekko.groupchat.chat.retriever;

import dev.langchain4j.rag.content.retriever.ContentRetriever;

/**
 * 可路由检索器扩展点。
 *
 * <p>当前内置 ES 知识库的向量与 BM25 实现；后续 Text2SQL、Text2Cypher 检索器只需实现
 * 本接口并声明对应 route，即可接入同一查询改写、RRF、重排与引用链路。
 */
public interface RoutedContentRetriever extends ContentRetriever {

    QueryRoute route();
}
