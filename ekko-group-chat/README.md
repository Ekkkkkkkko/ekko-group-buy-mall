# ekko-group-chat

面向商品资料和常见问题的 RAG 服务。文档入库保留阿里云 OSS、MinerU 官方云 API
和解析完成后自动切片的现有入口，内部采用事务后事件驱动的分阶段流水线：

```text
上传 PDF/图片/Word/Markdown/TXT/Excel/CSV
  -> MySQL 建立文档记录
  -> 私有 OSS 保存原文件
  -> PDF/图片/Word: 签名 URL -> MinerU 异步解析 -> 轮询结果
  -> Markdown/TXT: UTF-8 直接转换
  -> Excel/CSV: 键值对或 HTML 表格转换，保持整行
  -> 转换结果写 OSS，并在事务提交后发布 DocumentConvertedEvent
  -> 异步切片、质量校验，完整分片快照写入 MySQL
  -> 事务提交后发布 DocumentChunkedEvent
  -> 普通/子/兄弟分片 Embedding 后写入 Elasticsearch
  -> 文档状态变为 PUBLISHED
  -> XXL-Job 定时扫表补偿失败或卡住的切片/向量化任务
```

问答检索链：

```text
原始问题
  -> Java 21 虚拟线程并行执行简化/抽象/纠错/标准化四种改写
  -> LLM 选择最适合检索的候选
  -> LLM 路由 knowledge_base / relational_db / graph_db
  -> knowledge_base: ES 向量 + BM25 并行召回
  -> relational_db: 受限只读 Text2SQL（无结果回退 ES）
  -> 以稳定 chunkId 执行 RRF 融合去重
  -> 可选本地 BGE-Reranker ONNX 精排
  -> Redis/MySQL 回填父片或补齐兄弟上下文
  -> 注入 Prompt，并通过 REST 或 SSE 返回答案与引用
```

## 1. 当前技术边界

- Java 21、Spring Boot 3.5、MyBatis-Plus 3.5.9。
- LangChain4j 负责模型适配、Embedding、ES 向量存储和检索抽象。
- 阿里云 OSS 保存原文件、MinerU 原始 Markdown、图片和处理后 Markdown；删除文档时按当前产品约定保留 OSS 文件。
- MySQL RDS 通过 MyBatis-Plus 保存文档元数据、阶段状态，以及全部普通/父/子/兄弟分片正文和关系。
- Elasticsearch 只保存可检索的普通分片/子分片、向量和引用元数据，父分片不做 Embedding。
- MinerU 通过官方开放平台异步 API 调用，提交参数默认使用 `model_version=vlm`。
- 上传入口使用 Apache Tika 2.9.1 按文件内容检测真实类型，并校验扩展名与内容是否兼容；不信任客户端声明的 `Content-Type`，DOCX/XLSX 还会检查 OOXML 核心条目。
- 单实例默认每 10 秒扫描一次 `PARSING` 文档；任务超过 1 小时仍未完成会标记为 `FAILED`。
- `ApplicationEvent` 在事务提交后异步串联切片与向量化，Redisson 按“文档 + 阶段”加分布式锁。
- XXL-Job 提供切片和向量化补偿 Handler；本地未启用调度中心时由 Spring Scheduler 调用同一逻辑。
- 父片正文先查 Redis，未命中时批量回源 MySQL，并使用 30 秒 TTL 与空值缓存。
- ES 知识库同时执行向量召回和 BM25 全文召回，按稳定 `chunkId` 做 RRF，避免不同召回分数导致同一分片无法去重。
- 查询路由保留 KnowEngine 的关系库、图数据库和知识库三路协议。当前 MySQL 路由只开放知识文档统计字段；尚未配置 Neo4j 时图路由自动回退知识库。
- BGE ONNX 重排能力已经接入，模型文件不提交到仓库，默认关闭；开启时必须提供模型和 tokenizer 文件。
- `POST /api/v1/chat/stream` 通过 `PROGRESS/REFERENCE/ANSWER/COMPLETE/ERROR` SSE 事件返回进度、引用和增量答案。
- 模型默认按阿里云百炼 OpenAI 兼容接口配置，默认 `qwen-plus`、1536 维 `text-embedding-v4` 和图片描述模型 `qwen3-vl-plus`。

当前尚未实现上传断点续传、复杂角色权限、Excel 动态建业务表、文档多版本、图片向量检索、会话记忆和真实 Neo4j 图查询。图片通过视觉模型转成描述后参与文本向量检索，不是原生多模态向量检索。文档上传、查询、重新索引和删除使用单管理员账号鉴权；问答接口保持公开。MinerU 的提交方式和定时轮询方式保持不变，新增分布式锁只覆盖切片与向量化阶段。

## 2. 为什么 MySQL 和 ES 都要保存

MySQL 的 `knowledge_document` 一行代表一个被管理的文档；`knowledge_chunk` 保存分片正文、顺序、标题路径和父子关系；`knowledge_image` 保存私有 OSS 图片定位与视觉描述；`knowledge_chunk_image` 保存分片和图片关联。它既回答文件处理到哪一步，也提供命中子分片后需要还原的完整父级上下文和相关图片。

当前数据库调用链是：

```text
DocumentService
  -> KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument>
  -> RDS MySQL

DocumentEventListener / ParentAwareContentRetriever
  -> KnowledgeChunkService
  -> KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk>
  -> RDS MySQL
```

`KnowledgeDocument` 是带有 `@TableName`、`@TableId` 的普通 Entity class；简单新增、主键查询和条件更新由 MyBatis-Plus 生成 SQL，不需要 Mapper XML。

ES 的一条记录代表“文档中的一个可召回片段”，由索引步骤写入，问答检索读取。它回答哪些子分片和问题语义最接近；完整父分片正文不重复存进 ES。

原文件本体不进入 MySQL 或 ES，保存在 OSS；ES 中只保存引用所需的 object key。

## 3. 初始化

1. 新数据库在 RDS MySQL 8 上手工执行 `src/main/resources/db/schema.sql`。已有数据库在原有迁移之后执行 `src/main/resources/db/migration/2026-08-30-know-engine-ingestion-pipeline.sql`，新增阶段补偿和兄弟分片字段。应用默认不会自动建表或自动执行迁移。
2. 创建私有 OSS Bucket，让运行应用的 RAM 用户具备指定前缀的 `PutObject` 和签名读取权限。签名 URL 的有效期必须覆盖 MinerU 获取源文件所需时间，默认 2 小时。
3. 在 MinerU 官网的“API 管理”页面创建 Token，填入 `application.yml` 的 `group-chat.mineru.api-key`；本地部署 MinerU 时可留空。
4. 创建 Elasticsearch 索引所需的访问账号或 API Key。首次向量化时应用会创建并校验 `dense_vector` 映射；当前 1536 维索引默认为 `group_chat_knowledge_chunk_v2_1536`。
5. 准备 Redis；本地默认地址为 `127.0.0.1:6380`。
6. 准备聊天模型和 Embedding 模型的 API Key。若接入 XXL-Job Admin，配置执行器后将 `group-chat.xxl-job.enabled` 改为 `true`。

所有外部服务配置统一写在 `application.yml` 中，不再依赖环境变量：

| 分类 | 配置前缀 |
| --- | --- |
| RDS | `spring.datasource.*` |
| OSS | `group-chat.oss.*`（`access-key-id`、`access-key-secret`、`region`、`bucket`、可选 `endpoint`） |
| MinerU | `group-chat.mineru.*`（`base-url`、`api-key`、`language`、`formula-enabled`、`table-enabled`、`ocr-enabled` 等） |
| 图片 | `group-chat.image.*`（`description-enabled`、`vision-model`、`description-version`、签名有效期和单文档图片上限） |
| ES | `group-chat.elasticsearch.*`（`url`、`index-name`，以及 `api-key` 或 `username`/`password`） |
| 模型 | `group-chat.rag.*`（`model-base-url`、`api-key`、`chat-model`、`embedding-model`、`embedding-dimension`、`embedding-version`、`embedding-batch-size`） |
| Redis | `spring.data.redis.*` 与 `group-chat.redis.*`（缓存、分布式锁和 TTL） |
| 流水线 | `group-chat.pipeline.*`（补偿间隔、卡住阈值、最大重试和批量数） |
| XXL-Job | `group-chat.xxl-job.*`（是否启用、Admin 地址、执行器名称和 Token） |
| 检索 | `group-chat.retrieval.*`（改写/路由开关、向量和 BM25 候选数、RRF K、最终结果数、SSE 超时与 BGE 配置） |
| Web | `group-chat.web.allowed-origins`，默认允许 `http://localhost:5173` |

管理员账号和 BCrypt 密码摘要保存在 MySQL 的 `admin_user` 表中，不再从环境变量读取。尚未确认的密钥类配置在 yml 中以 `TODO` 占位，部署前补充实际值。

### 切分策略

`group-chat.rag.chunk-strategy` 通过 `ChunkingStrategyFactory` 选择实现：

| 策略 | 行为 | 当前资料适配性 |
| --- | --- | --- |
| `SMART` | 固定使用标题父子切片，`overlap = chunkSize × 10%`；无标题时把全文视为一个逻辑章节 | 默认推荐，适合产品介绍、FAQ、安装指南混合入库 |
| `TITLE` | 按 1～`title-level` 级标题切章，超长章节生成父子分片 | 适合 MinerU 能稳定还原标题的教程、说明书 |
| `BROTHER` | 按标题切章，超长章节二次切割后共享兄弟组 ID；检索命中自动补齐同组上下文 | 适合分段连续、需要补全相邻步骤的资料 |
| `EXCEL` | 键值对或 HTML 表格模式按 `chunk-size` 聚合，保证同一数据行不拆分 | Excel/CSV 上传时自动强制选择 |
| `LENGTH` | 按段落、换行、句末和空格寻找自然断点，并保留重叠 | 适合图片 OCR、标题结构较差的资料 |
| `SEPARATOR` | 先按固定分隔符切分，超长部分再按长度切分 | 适合人工整理且有统一分隔符的 Markdown |
| `REGEX` | 先按自定义正则切分，超长部分再按长度切分 | 适合版式高度固定、能定义稳定规则的批量文档 |

默认参数为子分片最多约 600 字符、完整块重叠目标 80 字符、父分片 1800 字符、识别到三级标题。表格行不会为了满足上限而从中间截断，因此极端长的单行可能略超上限。这里的大小是字符数，不是模型 Token 数；投产前应以真实 MinerU Markdown 和问答集评估后调整。

向量化按 `group-chat.rag.embedding-batch-size` 分批请求。当前 `text-embedding-v4` 服务的实测单批上限为 10，因此项目默认且最大设置为 10。全部批次成功后才删除该文档的旧 ES 索引，避免中间批次失败时提前丢失已有检索数据；更换模型服务时需要按目标接口的实际限制重新确认该配置。

当前向量维度为 1536。ES 的 `dense_vector.dims` 属于固定 mapping：删除全部向量文档不会删除或修改原来的 1024 维 mapping，因此本次升级使用新索引名 `group_chat_knowledge_chunk_v2_1536`，不能继续复用旧索引。首次入库前应用会自动创建 1536 维 `vector` 字段；如果索引已存在但维度不一致，会在调用 Embedding 和替换旧数据前直接拒绝写入。每条 ES 数据同时记录 `embeddingModel`、`embeddingDimension` 和 `embeddingVersion`，模型或维度再次升级时继续使用新的索引名并对全部分片重新向量化。

### 检索优化

`ParallelQueryTransformer` 对同一问题并行生成简化、抽象、纠错和标准化候选，再调用模型选择一个候选。关闭 `group-chat.retrieval.query-rewrite-enabled` 时直接使用原问题。`KnowEngineQueryRouter` 输出 `knowledge_base`、`relational_db` 或 `graph_db`：知识库路由会同时调用向量与 BM25 检索器；关系库路由调用 `SafeSqlDatabaseContentRetriever`；未注册真实检索器的路由会回退知识库。

Text2SQL 当前只向模型暴露 `knowledge_document`、`knowledge_chunk`、`knowledge_image` 的非敏感统计字段。执行前使用 JSqlParser 验证单条 SELECT，再检查表白名单、敏感字段、`SELECT *`、SQL 注释、锁、会话函数和文件操作；执行连接设置为只读，查询超时 5 秒且最多返回 100 行。`admin_user`、哈希、OSS object key、正文和失败详情不进入 Text2SQL Prompt。

知识库的向量与 BM25 各召回最多 20 条，再以 `1 / (rrfK + rank)` 融合。去重优先使用入库时的稳定 `chunkId`，而不是包含各通道分数的 `Content.equals`。RRF 完成后才执行 BGE 和父子/兄弟扩展，避免同一父片的多个子片提前重复参与排序。引用同时返回原始相似度、`rrfScore`、可选 `rerankScore`、召回通道、`chunkId`、`matchedChunkId` 和 `headingPath`。

本地 BGE 默认关闭。启用示例：

```yaml
group-chat:
  retrieval:
    rerank:
      enabled: true
      model-path: /absolute/path/model_quantized.onnx
      tokenizer-path: /absolute/path/tokenizer.json
      max-tokens: 8192
      min-score: 0.0
```

启用时启动阶段会校验两个文件并初始化 `OnnxScoringModel`；加载或推理失败时单次请求回退 RRF 排序。仓库没有附带 KnowEngine 使用的 BGE 模型文件，因此默认配置不会声称已经完成真实模型推理。

### 图片处理

MinerU 完成响应提供 `full_zip_url`。应用下载结果 ZIP 后同时提取 `full.md` 与 JPG/JPEG/PNG/GIF/WebP 图片，校验 ZIP 路径、图片魔数、单文件大小、图片数量和累计解压大小。原始 Markdown 保存在 `parsed_object_key`，图片改写后的 Markdown 保存在 `processed_object_key`。

图片对象使用 `{parsed-prefix}/{documentId}/{documentSha}/images/{imageSha}.{ext}` 的确定性私有 OSS 路径。数据库只保存 object key，不保存会过期的签名 URL；视觉模型调用和聊天响应时才生成短期签名地址。Markdown 中的相对路径会改为 `knowledge-image://{imageId}`，Embedding 前再移除该地址，只保留图片描述。

`knowledge_image` 保存图片、描述模型、描述版本和失败状态，`knowledge_chunk_image` 保存图片与普通/父/子分片关联。单张图片描述失败不会阻止整篇文档发布：图片仍可随命中来源展示，但只有已有 alt 文本或成功生成描述时才能靠图片语义参与检索。

图片展示质量与入库保留分开处理：`group-chat.image.excluded-sha256`（可通过逗号分隔的 `CHAT_IMAGE_EXCLUDED_SHA256` 覆盖）过滤人工确认的无效占位图，默认包含复位说明 PDF 中的两张红叉图片。过滤覆盖既有分片的引用响应和新解析的 Markdown，不删除原始文件或图片记录，也不是通用图像识别。响应附带 `sha256` 供前端跨文档去重；同篇文档的章节合并到可展开的“参考资料”，保留原始资料编号。图片只在展开时加载，小于 96×96 的独立图标不生成预览；正常图片保持比例，加载失败则隐藏，文字来源始终保留。

旧文档的 MinerU ZIP 没有被保存，因此仅执行重新索引不能补回历史图片。执行图片迁移后，需要重新提交 MinerU 解析任务（或删除旧记录后重新上传）才能生成 `knowledge_image` 数据。

## 4. 最小接口

管理员登录：

```bash
curl -X POST http://localhost:8095/api/v1/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"<管理员账号>","password":"<管理员密码>"}'
```

登录成功后复制响应中的 `token`，以下文档管理接口都需要携带 `Authorization: Bearer <token>`。令牌在服务重启后失效，需要重新登录。
管理员账号由部署人员单独创建，数据库只保存 BCrypt 摘要；不要在 SQL、README 或 JAR 中提供默认密码。

上传并提交异步处理：

```bash
curl -X POST http://localhost:8095/api/v1/documents \
  -H 'Authorization: Bearer <token>' \
  -F 'file=@/path/to/TL-7DR5130.pdf' \
  -F 'title=TL-7DR5130 产品介绍' \
  -F 'productModel=TL-7DR5130' \
  -F 'chunkStrategy=SMART'
```

`chunkStrategy` 可选，不传时使用 yml 默认值；Excel/CSV 始终使用 `EXCEL`。PDF、图片和 Word 返回时通常为 `PARSING`，Markdown/TXT/Excel/CSV 通常为 `CHUNKING`。前端应持续查询到 `PUBLISHED` 或 `FAILED`；发布后响应还包含本次使用的 `preprocessVersion` 和 `chunkVersion`。

查询文档状态：

```bash
curl http://localhost:8095/api/v1/documents/1 \
  -H 'Authorization: Bearer <token>'
```

优先使用 OSS 中已有的处理后 Markdown（旧文档退回原始 Markdown）和当前规则版本重新建立 MySQL/ES 分片，不再次调用 MinerU：

```bash
curl -X POST http://localhost:8095/api/v1/documents/1/reindex \
  -H 'Authorization: Bearer <token>'
```

删除 MySQL 文档元数据/结构化分片和 ES 向量分片（OSS 原文件与 Markdown 保留）：

```bash
curl -X DELETE http://localhost:8095/api/v1/documents/1 \
  -H 'Authorization: Bearer <token>'
```

删除时先按 `documentId` 清理 ES，再删除 MySQL 的 `knowledge_chunk` 和 `knowledge_document`。ES 删除失败时 MySQL 记录会保留，便于重新发起删除；由于两个存储不在同一事务中，仍需要后续通过任务补偿进一步增强一致性。

提问：

```bash
curl -X POST http://localhost:8095/api/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"如何查看连接路由器的终端数量？"}'
```

流式提问：

```bash
curl -N -X POST http://localhost:8095/api/v1/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"question":"如何查看连接路由器的终端数量？"}'
```

SSE 会先发送 `PROGRESS`，检索完成后发送一次 `REFERENCE` 引用数组，再连续发送 `ANSWER` 增量文本，最后以 `COMPLETE` 返回完整答案和引用；失败时发送 `ERROR`。同步接口继续可用，并返回相同的引用字段。

## 5. 状态与失败语义

```text
UPLOADING -> PARSING -> CHUNKING -> CHUNKED -> INDEXING -> PUBLISHED
      \          \          \          \           \
       +----------+----------+-----------+------------+-> FAILED
```

提交或转换失败时上传接口返回 HTTP 502 并将文档标记为 `FAILED`。后台查询遇到临时网络错误时保留 `PARSING`，等待下一轮轮询；切片和向量化失败会记录 `failure_stage`。补偿任务扫描卡住的 `CHUNKING/CHUNKED/INDEXING` 和相应 `FAILED` 记录，最多重试配置次数。已有 Markdown 的 `PUBLISHED`/`FAILED` 文档可通过重新索引接口异步重试。系统仍没有跨 OSS、MySQL、Elasticsearch 的全局事务；阶段状态、幂等覆盖、锁和定时补偿提供最终一致性。

## 6. 建议的后续实现顺序

1. 为 MinerU 轮询任务增加数据库抢占；当前阶段锁只覆盖切片和向量化。
2. 增加重新解析，并为跨存储删除增加独立补偿。
3. 用真实问题集评估向量、BM25、RRF、BGE 各阶段的 Recall@5、MRR 和引用准确率，再调整候选数与阈值。
4. 用产品别名表扩展当前 `TL/ARCHER/DECO` 型号识别规则，并评估没有明确型号时的路由策略。
5. 图片信息占比提高后，保留 MinerU 图片/版面产物，并让支持视觉输入的回答模型按需读取图片。
6. 增加会话记忆，并在有真实实体关系数据后注册 `GRAPH_DB` 的 Neo4j Text2Cypher 检索器。

## 7. 本地编译

```bash
mvn test
```

这一步包含文档流水线和检索优化的 54 个单元测试，覆盖四路改写、查询路由、稳定主键 RRF、BGE 排序适配、Text2SQL 安全校验、父子/兄弟补全和 SSE 引用事件。单元测试仍不证明 OSS、MinerU、ES 或模型接口的外部端到端效果；本次仅另外验证了 RDS 表结构连接、必要增量迁移和应用完整启动。
