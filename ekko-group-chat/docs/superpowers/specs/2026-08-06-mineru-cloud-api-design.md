# MinerU 官网精准解析 API 异步接入设计

## 目标

把智能客服知识文档入库流程从本地同步 `POST /file_parse` 改为 MinerU 官网精准解析 API，同时保持现有 MySQL、OSS、Markdown 分块、Embedding、Elasticsearch 和问答检索边界不变。

已确认的产品选择：

- 使用 MinerU 官网精准解析 API，而不是 Agent 轻量解析 API。
- 默认模型为 `vlm`。
- 文档上传接口只负责提交任务并立即返回，解析和索引在后台完成。
- 前端自动查询文档状态，直到 `PUBLISHED` 或 `FAILED`。

## 官方协议边界

单文件创建任务：

```text
POST https://mineru.net/api/v4/extract/task
Authorization: Bearer <MINERU_API_KEY>
Content-Type: application/json
```

请求使用可公网访问的文件 URL，不直接上传文件。成功响应返回 `data.task_id`。

查询任务：

```text
GET https://mineru.net/api/v4/extract/task/{taskId}
Authorization: Bearer <MINERU_API_KEY>
```

任务状态包括 `pending`、`running`、`converting`、`done` 和 `failed`。完成后从 `data.full_zip_url` 下载结果压缩包，使用其中的 `full.md` 继续现有知识库入库流程。

官方文档：<https://mineru.net/apiManage/docs>

## 总体数据流

```text
管理员上传 PDF/图片
  -> MySQL 创建 knowledge_document
  -> 原文件上传私有 OSS
  -> 生成两小时有效的 OSS GET 签名 URL
  -> MinerU 创建 vlm 解析任务
  -> 保存 mineru_task_id 和 mineru_submitted_at
  -> 状态设置为 PARSING
  -> HTTP 201 立即返回文档信息

后台定时任务
  -> 查询 PARSING 文档
  -> 查询 MinerU task_id
  -> pending/running/converting: 保持 PARSING
  -> failed: 记录 err_msg，状态改为 FAILED
  -> done: 下载 full_zip_url
           -> 从 ZIP 读取 full.md
           -> Markdown 上传 OSS
           -> 状态改为 INDEXING
           -> Markdown 分块并写入 Elasticsearch
           -> 状态改为 PUBLISHED
```

MinerU 只参与文档入库阶段。用户提问仍直接使用 Elasticsearch 召回和大模型生成，不调用 MinerU。

## 组件设计

### `AliyunOssClient`

保留现有 `put`，新增私有对象 GET 签名 URL 生成能力。签名默认有效两小时，Bucket 不改为公共读。MinerU 只获得单个对象的限时访问权。

### `MineruClient`

从本地 multipart 客户端改为官网 JSON 客户端，职责限定为：

- `createTask(sourceUrl, dataId)`：提交 `url`、`data_id`、`model_version=vlm`、语言、公式和表格选项，返回 `task_id`。
- `queryTask(taskId)`：返回标准化任务状态、结果 ZIP URL和失败原因。
- 所有 MinerU 请求统一携带 `Authorization: Bearer ${MINERU_API_KEY}`。
- MinerU 返回的 HTTP 非成功状态、`code != 0`、字段缺失都转换为明确异常，并保留 `trace_id` 方便排查。

### `MineruArchiveParser`

下载逻辑与 ZIP 内容解析分离。解析器只从内存中的 ZIP 数据查找并读取 `full.md`：

- 拒绝路径穿越条目。
- Markdown 不存在或为空时失败。
- 设置 ZIP 下载体积和解压后 Markdown 大小上限，降低异常压缩包耗尽内存的风险。

### `DocumentService`

上传方法改成“上传 OSS + 创建 MinerU 任务 + 返回 PARSING 文档”，不再同步解析和索引。

新增后台完成入口：收到 `done` 结果后下载并提取 Markdown，复用现有 OSS 保存、分块、ES 索引和发布逻辑。处理完成过程保持单向状态流转，失败统一记录到 `failure_reason`。

### `MineruPollingJob`

启用 Spring Scheduling，默认每 10 秒执行一次：

- 只查询 `status=PARSING` 且 `mineru_task_id` 非空的文档。
- 单次按固定数量处理，避免文档增长后一次扫描过多。
- 临时 HTTP/网络错误仅记录日志，保留 `PARSING`，下一轮重试。
- MinerU 明确返回 `failed` 时标记 `FAILED`。
- `mineru_submitted_at` 超过一小时仍未完成时标记 `FAILED`。
- 当前按单应用实例设计；多实例抢占和分布式锁不在本次范围。

## 数据库变更

`knowledge_document` 新增：

```sql
mineru_task_id VARCHAR(100) DEFAULT NULL COMMENT 'MinerU 官网解析任务 ID',
mineru_submitted_at DATETIME(3) DEFAULT NULL COMMENT 'MinerU 任务提交时间'
```

增加任务扫描索引：

```sql
KEY idx_knowledge_document_mineru_task (status, mineru_submitted_at)
```

项目 `spring.sql.init.mode=never`，因此除更新 `schema.sql` 外，还提供针对现有库的增量迁移 SQL；迁移须由管理员手工执行后才能启动新版本。

## 配置设计

```yaml
group-chat:
  mineru:
    base-url: ${MINERU_BASE_URL:https://mineru.net}
    create-task-path: ${MINERU_CREATE_TASK_PATH:/api/v4/extract/task}
    task-result-path: ${MINERU_TASK_RESULT_PATH:/api/v4/extract/task/{taskId}}
    api-key: ${MINERU_API_KEY}
    model-version: ${MINERU_MODEL_VERSION:vlm}
    language: ${MINERU_LANGUAGE:ch}
    formula-enabled: ${MINERU_FORMULA_ENABLED:true}
    table-enabled: ${MINERU_TABLE_ENABLED:true}
    ocr-enabled: ${MINERU_OCR_ENABLED:false}
    poll-interval: ${MINERU_POLL_INTERVAL:10s}
    task-timeout: ${MINERU_TASK_TIMEOUT:1h}
    source-url-expiration: ${MINERU_SOURCE_URL_EXPIRATION:2h}
    poll-batch-size: ${MINERU_POLL_BATCH_SIZE:20}
```

`MINERU_API_KEY` 必须由运行环境注入，不进入 Git。现有数据库、OSS和模型密钥也应改为环境变量，本次只修改与当前功能直接相关的配置引用，不在设计中扩展新的密钥管理系统。

## API 与前端行为

`POST /api/v1/documents` 保持请求格式和 HTTP 201，返回值中的状态从原来的最终 `PUBLISHED` 改为 `PARSING`。

前端上传成功后：

- 显示“文档已提交解析”。
- 使用现有 `GET /api/v1/documents/{documentId}` 每 3 秒刷新一次。
- 状态达到 `PUBLISHED` 或 `FAILED` 时停止。
- 页面卸载、重新上传或用户退出时取消旧轮询。
- `FAILED` 时展示后端返回的失败原因；`PUBLISHED` 时提示知识库已可检索。

## 错误处理与恢复

- OSS 上传失败或创建 MinerU 任务失败：文档标记 `FAILED`。
- 创建任务成功但应用在保存 `task_id` 前退出：文档可能停留在 `UPLOADING`，本次不实现跨系统事务；失败记录可由管理员删除后重新上传。
- MinerU 查询临时失败：保留任务并自动重试。
- MinerU 明确失败、任务超时、ZIP 无效、缺少 `full.md`、Markdown 为空、OSS 写入失败或 ES 索引失败：标记 `FAILED` 并保存根因。
- 完成处理先保存 Markdown，再进入 `INDEXING`；ES 写入成功后才标记 `PUBLISHED`。
- 删除文档仍先删 ES、再删 MySQL，OSS 文件继续保留，行为不变。

## 测试策略

遵循测试先行：

1. `MineruClientTest`：验证创建任务 JSON、Bearer Token、`vlm` 参数、任务状态映射、业务错误和字段缺失。
2. `MineruArchiveParserTest`：验证正常 `full.md`、缺失、空内容、路径穿越和大小限制。
3. `AliyunOssClientTest`：验证签名请求使用正确 Bucket、object key 和过期时间。
4. `DocumentService` 测试：验证上传后立即返回 `PARSING`、保存 task ID，以及完成后的 Markdown/ES/状态流转。
5. `MineruPollingJobTest`：验证等待、完成、失败、超时和临时网络错误。
6. 前端测试或构建验证：确认状态轮询启动、停止和提示文本。
7. 执行全部 Maven 测试和前端 `npm run build`。

单元测试和 Mock HTTP 只能证明协议适配与业务编排。只有使用真实 `MINERU_API_KEY`、真实 OSS 签名 URL 和样例 PDF 完成一次 `PUBLISHED` 流程，才算端到端联通。

## 不在本次范围

- MinerU callback 回调及 checksum 校验。
- 批量文件上传接口。
- 多实例分布式任务锁。
- 自动重建失败文档、手工重试接口。
- 修改 RAG 检索、模型回答或 Elasticsearch 索引结构。
