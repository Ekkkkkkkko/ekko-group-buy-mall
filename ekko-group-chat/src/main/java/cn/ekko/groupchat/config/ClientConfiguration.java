package cn.ekko.groupchat.config;

import cn.ekko.groupchat.chat.ai.IntentRecognitionAiService;
import cn.ekko.groupchat.chat.ai.TitleSummaryAiService;
import cn.ekko.groupchat.chat.retriever.ElasticsearchFullTextContentRetriever;
import cn.ekko.groupchat.chat.retriever.ProductModelQueryFilter;
import cn.ekko.groupchat.chat.retriever.RoutedContentRetriever;
import cn.ekko.groupchat.chat.retriever.SafeSqlDatabaseContentRetriever;
import cn.ekko.groupchat.chat.retriever.SourceTaggingContentRetriever;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/**
 * 外部依赖客户端 Bean 装配中心。
 *
 * <p>统一创建模块所需的全部外部服务客户端，配置均取自 {@link GroupChatProperties}：
 * <ul>
 *   <li>OSS：静态凭证构建 {@code OSSClient}，存储原始文件与解析结果；</li>
 *   <li>MinerU：两个 {@code RestClient}（API 调用与结果下载分离）；</li>
 *   <li>Elasticsearch：Transport/Client 及 langchain4j 的 {@code EmbeddingStore}；</li>
 *   <li>RAG：OpenAI 兼容协议的 ChatModel、EmbeddingModel、检索器及模块化检索编排器。</li>
 * </ul>
 */
@Configuration
public class ClientConfiguration {

    @Bean(destroyMethod = "close")
    OSSClient ossClient(GroupChatProperties properties) {
        GroupChatProperties.Oss oss = properties.getOss();
        StaticCredentialsProvider credentialsProvider =
                new StaticCredentialsProvider(oss.getAccessKeyId(), oss.getAccessKeySecret());
        if (StringUtils.hasText(oss.getEndpoint())) {
            return OSSClient.newBuilder()
                    .region(oss.getRegion())
                    .endpoint(oss.getEndpoint())
                    .credentialsProvider(credentialsProvider)
                    .build();
        }
        return OSSClient.newBuilder()
                .region(oss.getRegion())
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean("mineruRestClient")
    RestClient mineruRestClient(RestClient.Builder builder, GroupChatProperties properties) {
        GroupChatProperties.Mineru mineru = properties.getMineru();
        RestClient.Builder clientBuilder = builder
                .baseUrl(mineru.getBaseUrl())
                .requestInterceptor((request, body, execution) -> {
                    // MinerU 网关无法正确解析 chunked JSON，请求体必须带明确的 Content-Length。
                    if (body.length > 0) {
                        request.getHeaders().setContentLength(body.length);
                    }
                    return execution.execute(request, body);
                });
        if (StringUtils.hasText(mineru.getApiKey())) {
            clientBuilder.defaultHeader("Authorization", "Bearer " + mineru.getApiKey());
        }
        return clientBuilder.build();
    }

    @Bean("mineruResultRestClient")
    RestClient mineruResultRestClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ElasticsearchTransport elasticsearchTransport(GroupChatProperties properties) {
        GroupChatProperties.Elasticsearch elasticsearch = properties.getElasticsearch();
        RestClientBuilder builder = org.elasticsearch.client.RestClient.builder(HttpHost.create(elasticsearch.getUrl()));
        if (StringUtils.hasText(elasticsearch.getApiKey())) {
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + elasticsearch.getApiKey())
            });
        } else if (StringUtils.hasText(elasticsearch.getUsername())) {
            String value = elasticsearch.getUsername() + ":" + elasticsearch.getPassword();
            String token = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader(HttpHeaders.AUTHORIZATION, "Basic " + token)
            });
        }
        return new RestClientTransport(builder.build(), new JacksonJsonpMapper());
    }

    @Bean
    ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(
            ElasticsearchClient elasticsearchClient,
            GroupChatProperties properties
    ) {
        return ElasticsearchEmbeddingStore.builder()
                .client(elasticsearchClient)
                .indexName(properties.getElasticsearch().getIndexName())
                .build();
    }

    @Bean
    @Primary
    ChatModel chatModel(GroupChatProperties properties) {
        GroupChatProperties.Rag rag = properties.getRag();
        return OpenAiChatModel.builder()
                .baseUrl(rag.getModelBaseUrl())
                .apiKey(rag.getApiKey())
                .modelName(rag.getChatModel())
                .temperature(rag.getChatTemperature())
                .customParameters(chatCustomParameters(rag))
                .build();
    }

    @Bean("ragChatModel")
    ChatModel ragChatModel(GroupChatProperties properties) {
        GroupChatProperties.Rag rag = properties.getRag();
        return OpenAiChatModel.builder()
                .baseUrl(rag.getModelBaseUrl())
                .apiKey(rag.getApiKey())
                .modelName(modelOrDefault(rag.getRagChatModel(), rag.getChatModel()))
                .temperature(rag.getRagTemperature())
                .customParameters(chatCustomParameters(rag))
                .build();
    }

    @Bean("commonChatModel")
    ChatModel commonChatModel(GroupChatProperties properties) {
        GroupChatProperties.Rag rag = properties.getRag();
        return OpenAiChatModel.builder()
                .baseUrl(rag.getModelBaseUrl())
                .apiKey(rag.getApiKey())
                .modelName(modelOrDefault(rag.getCommonChatModel(), rag.getChatModel()))
                .temperature(rag.getCommonTemperature())
                .customParameters(chatCustomParameters(rag))
                .build();
    }

    @Bean("visionChatModel")
    ChatModel visionChatModel(GroupChatProperties properties) {
        GroupChatProperties.Rag rag = properties.getRag();
        return OpenAiChatModel.builder()
                .baseUrl(rag.getModelBaseUrl())
                .apiKey(rag.getApiKey())
                .modelName(properties.getImage().getVisionModel())
                .temperature(0.1)
                .build();
    }

    @Bean
    StreamingChatModel streamingChatModel(GroupChatProperties properties) {
        GroupChatProperties.Rag rag = properties.getRag();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(rag.getModelBaseUrl())
                .apiKey(rag.getApiKey())
                .modelName(rag.getChatModel())
                .temperature(rag.getChatTemperature())
                .customParameters(chatCustomParameters(rag))
                .build();
    }

    @Bean("ragStreamingChatModel")
    StreamingChatModel ragStreamingChatModel(GroupChatProperties properties) {
        GroupChatProperties.Rag rag = properties.getRag();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(rag.getModelBaseUrl())
                .apiKey(rag.getApiKey())
                .modelName(modelOrDefault(rag.getRagChatModel(), rag.getChatModel()))
                .temperature(rag.getRagTemperature())
                .customParameters(chatCustomParameters(rag))
                .build();
    }

    @Bean("commonStreamingChatModel")
    StreamingChatModel commonStreamingChatModel(GroupChatProperties properties) {
        GroupChatProperties.Rag rag = properties.getRag();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(rag.getModelBaseUrl())
                .apiKey(rag.getApiKey())
                .modelName(modelOrDefault(rag.getCommonChatModel(), rag.getChatModel()))
                .temperature(rag.getCommonTemperature())
                .customParameters(chatCustomParameters(rag))
                .build();
    }

    @Bean("titleChatModel")
    ChatModel titleChatModel(GroupChatProperties properties) {
        GroupChatProperties.Rag rag = properties.getRag();
        return OpenAiChatModel.builder()
                .baseUrl(rag.getModelBaseUrl())
                .apiKey(rag.getApiKey())
                .modelName(modelOrDefault(rag.getTitleModel(), rag.getChatModel()))
                .temperature(rag.getTitleTemperature())
                .customParameters(chatCustomParameters(rag))
                .build();
    }

    @Bean
    IntentRecognitionAiService intentRecognitionAiService(
            ChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider
    ) {
        return AiServices.builder(IntentRecognitionAiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    @Bean
    TitleSummaryAiService titleSummaryAiService(@Qualifier("titleChatModel") ChatModel titleChatModel) {
        return AiServices.builder(TitleSummaryAiService.class)
                .chatModel(titleChatModel)
                .build();
    }

    @Bean
    EmbeddingModel embeddingModel(GroupChatProperties properties) {
        GroupChatProperties.Rag rag = properties.getRag();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(rag.getModelBaseUrl())
                .apiKey(rag.getApiKey())
                .modelName(rag.getEmbeddingModel())
                .dimensions(rag.getEmbeddingDimension())
                .build();
    }

    @Bean("vectorContentRetriever")
    RoutedContentRetriever vectorContentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            GroupChatProperties properties,
            ProductModelQueryFilter productModelQueryFilter
    ) {
        GroupChatProperties.Rag rag = properties.getRag();
        var retrieverBuilder = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(properties.getRetrieval().getVectorCandidates())
                .minScore(rag.getMinScore());
        if (rag.isProductModelFilterEnabled()) {
            retrieverBuilder.dynamicFilter(productModelQueryFilter::filter);
        }
        ContentRetriever vectorRetriever = retrieverBuilder.build();
        return new SourceTaggingContentRetriever(vectorRetriever, "VECTOR");
    }

    /** 只读 Text2SQL；当前只开放知识文档统计字段，无结果时降级为 ES 混合检索。 */
    @Bean
    RoutedContentRetriever relationalDatabaseContentRetriever(
            DataSource dataSource,
            ChatModel chatModel,
            @Qualifier("vectorContentRetriever") RoutedContentRetriever vectorRetriever,
            ElasticsearchFullTextContentRetriever fullTextRetriever
    ) {
        return new SafeSqlDatabaseContentRetriever(
                dataSource,
                chatModel,
                List.of(vectorRetriever, fullTextRetriever)
        );
    }

    private String modelOrDefault(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private Map<String, Object> chatCustomParameters(GroupChatProperties.Rag rag) {
        return Map.of("enable_thinking", rag.isEnableThinking());
    }
}
