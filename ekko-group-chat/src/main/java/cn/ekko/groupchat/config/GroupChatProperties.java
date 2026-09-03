package cn.ekko.groupchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 智能客服模块统一配置属性类，绑定 application.yml 中 {@code group-chat} 前缀的全部配置。
 *
 * <p>按外部依赖分组：
 * <ul>
 *   <li>{@link Oss}：阿里云 OSS 存储（原始文件与解析后 Markdown）</li>
 *   <li>{@link Mineru}：MinerU 文档解析 API 及轮询策略</li>
 *   <li>{@link Elasticsearch}：ES 连接与知识库索引</li>
 *   <li>{@link Rag}：embedding/chat 模型及分块、检索参数</li>
 *   <li>{@link Web}：跨域等 Web 配置</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "group-chat")
@Getter
@Setter
public class GroupChatProperties {

    private Oss oss = new Oss();
    private Mineru mineru = new Mineru();
    private Image image = new Image();
    private Elasticsearch elasticsearch = new Elasticsearch();
    private Rag rag = new Rag();
    private Redis redis = new Redis();
    private Pipeline pipeline = new Pipeline();
    private XxlJob xxlJob = new XxlJob();
    private Retrieval retrieval = new Retrieval();
    private Web web = new Web();

    /** 阿里云 OSS 配置：区域、桶、凭证及对象路径前缀。 */
    @Getter
    @Setter
    public static class Oss {

        private String region;
        private String bucket;
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String originalPrefix;
        private String parsedPrefix;

    }

    /** MinerU 解析服务配置：接口地址、解析参数、轮询间隔与超时策略。 */
    @Getter
    @Setter
    public static class Mineru {

        private String baseUrl = "https://mineru.net";
        private String createTaskPath = "/api/v4/extract/task";
        private String taskResultPath = "/api/v4/extract/task/{taskId}";
        private String apiKey;
        private String modelVersion = "vlm";
        private String language = "ch";
        private boolean formulaEnabled = true;
        private boolean tableEnabled = true;
        private boolean ocrEnabled;
        private Duration pollInterval = Duration.ofSeconds(10);
        private Duration taskTimeout = Duration.ofHours(1);
        private Duration sourceUrlExpiration = Duration.ofHours(2);
        private int pollBatchSize = 20;

    }

    /** MinerU 图片持久化、视觉描述和临时访问地址配置。 */
    @Getter
    @Setter
    public static class Image {

        private boolean descriptionEnabled = true;
        private String visionModel = "qwen3-vl-plus";
        private String descriptionVersion = "image-v1";
        private Duration signedUrlExpiration = Duration.ofMinutes(15);
        private int maxImagesPerDocument = 50;

    }

    /** Elasticsearch 连接配置：地址、认证方式（账号密码或 ApiKey）及索引名。 */
    @Getter
    @Setter
    public static class Elasticsearch {

        private String url;
        private String username;
        private String password;
        private String apiKey;
        private String indexName;

    }

    /** RAG 配置：模型、切分策略、父子分片以及检索参数。 */
    @Getter
    @Setter
    public static class Rag {

        private String modelBaseUrl;
        private String apiKey;
        private String chatModel;
        private String ragChatModel;
        private String commonChatModel;
        private String titleModel;
        private boolean enableThinking;
        private String embeddingModel;
        private int embeddingDimension;
        private String embeddingVersion = "text-embedding-v4-1536-v1";
        private int embeddingBatchSize = 10;
        private String chunkStrategy = "SMART";
        private int chunkSize = 600;
        private int chunkOverlap = 80;
        private int parentChunkSize = 1800;
        private int titleLevel = 3;
        private String chunkSeparator = "\n\n";
        private String chunkRegex = "(?m)^-{3,}\\s*$";
        private int smallChunkMergeThreshold = 120;
        private int minMeaningfulChars = 4;
        private boolean excelHtmlMode;
        private String preprocessVersion = "preprocess-v2";
        private String chunkVersion = "chunk-v2";
        private boolean productModelFilterEnabled = true;
        private int maxResults;
        private double minScore;
        private double chatTemperature = 0.1;
        private double ragTemperature = 0.1;
        private double commonTemperature = 0.3;
        private double titleTemperature = 0.5;

    }

    /** Redis/Redisson：父片缓存以及同一文档处理阶段的分布式互斥。 */
    @Getter
    @Setter
    public static class Redis {

        private String address = "redis://127.0.0.1:6380";
        private String password;
        private int database;
        private Duration lockWaitTime = Duration.ZERO;
        private Duration lockLeaseTime = Duration.ofMinutes(10);
        private Duration parentCacheTtl = Duration.ofSeconds(30);

    }

    /** 文档阶段补偿参数；正常链路由事务后事件推进。 */
    @Getter
    @Setter
    public static class Pipeline {

        private Duration compensationInterval = Duration.ofMinutes(1);
        private Duration staleAfter = Duration.ofMinutes(5);
        private int maxRetries = 5;
        private int compensationBatchSize = 20;

    }

    /** XXL-Job 可选执行器；关闭时由本地 Spring Scheduler 调用同一补偿逻辑。 */
    @Getter
    @Setter
    public static class XxlJob {

        private boolean enabled;
        private String adminAddresses = "http://127.0.0.1:8080/xxl-job-admin";
        private String appName = "ekko-group-chat-executor";
        private String address = "";
        private String accessToken = "";

    }

    /** 查询改写、混合召回、RRF、重排与流式引用配置。 */
    @Getter
    @Setter
    public static class Retrieval {

        private boolean queryRewriteEnabled = true;
        private boolean queryRoutingEnabled = true;
        private int vectorCandidates = 20;
        private int fullTextCandidates = 20;
        private double fullTextMinScore;
        private int rrfK = 60;
        private int finalMaxResults = 5;
        private Duration sseTimeout = Duration.ofMinutes(5);
        private int memoryMaxMessages = 10;
        private Duration memoryTtl = Duration.ofHours(1);
        private Rerank rerank = new Rerank();

    }

    /** 本地 BGE ONNX 重排；模型文件不随仓库提交，默认关闭。 */
    @Getter
    @Setter
    public static class Rerank {

        private boolean enabled;
        private String modelPath = "";
        private String tokenizerPath = "";
        private int maxTokens = 8192;
        private double minScore;

    }

    /** Web 配置：允许跨域的前端来源列表。 */
    @Getter
    @Setter
    public static class Web {

        private List<String> allowedOrigins = List.of("http://localhost:5173");

    }

}
