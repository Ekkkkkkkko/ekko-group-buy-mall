# MinerU Cloud API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the local synchronous MinerU `/file_parse` adapter with the official asynchronous precise-parsing API using `vlm`, durable database task state, OSS signed source URLs, background polling, ZIP result extraction, and frontend status polling.

**Architecture:** The upload request stores the private OSS object, submits its two-hour signed GET URL to MinerU, persists the returned task ID, and immediately returns a `PARSING` document. A scheduled single-instance poller resumes tasks from MySQL, downloads completed result archives with a credential-free HTTP client, extracts `full.md`, then reuses the existing OSS/chunk/embedding/Elasticsearch publication path.

**Tech Stack:** Java 21, Spring Boot 3.5.6, Spring `RestClient`, Spring Scheduling, MyBatis-Plus 3.5.9, Alibaba Cloud OSS SDK v2 0.5.0, JUnit 5, Mockito, AssertJ, Vue 3, Node built-in test runner, Vite.

## Global Constraints

- Use MinerU precise parsing endpoints `POST /api/v4/extract/task` and `GET /api/v4/extract/task/{taskId}`.
- The default `model_version` is exactly `vlm`; language is `ch`; formula and table extraction are enabled; OCR defaults to disabled.
- The upload endpoint remains `POST /api/v1/documents` with HTTP 201 but returns a `PARSING` document without waiting for parsing.
- The source OSS object remains private and is shared only through a two-hour signed GET URL.
- The MinerU token is read only from `MINERU_API_KEY` and must never be logged, committed, or forwarded to the result CDN.
- The poll interval is 10 seconds, task timeout is one hour, frontend refresh interval is 3 seconds, and one backend scan handles at most 20 documents.
- Temporary query/network errors keep a task in `PARSING`; provider failure, timeout, invalid ZIP, empty Markdown, OSS failure, or Elasticsearch failure changes it to `FAILED`.
- Current deployment is single-instance; distributed locking, callbacks, batch API, and manual retry endpoints are excluded.
- All production behavior changes follow red-green-refactor and preserve unrelated user changes in the dirty root worktree.

---

## File Map

**Backend files to create**

- `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruTaskState.java`: normalized provider states.
- `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruTaskResult.java`: normalized query result.
- `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruArchiveParser.java`: safe in-memory `full.md` extraction.
- `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruResultDownloader.java`: credential-free result ZIP download.
- `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/service/MineruTaskProcessor.java`: one persisted task's state transition and publication.
- `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/job/MineruPollingJob.java`: scheduled bounded task scan.
- `ekko-group-chat/src/main/resources/db/migration/2026-08-06-add-mineru-cloud-task.sql`: manual migration for the live schema.

**Backend files to modify**

- `GroupChatProperties.java`: official API paths, model, durations, and batch limit.
- `ClientConfiguration.java`: authenticated API client and unauthenticated CDN client.
- `AliyunOssClient.java`: signed GET URL creation.
- `MineruClient.java`: JSON create/query API.
- `KnowledgeDocument.java`: task ID and submission time.
- `DocumentService.java`: submit-and-return upload behavior.
- `GroupChatApplication.java`: scheduling enablement.
- `application.yml`: official API environment-backed configuration.
- `db/schema.sql`: complete schema for new deployments.
- `README.md`: official API setup and operational flow.

**Backend tests to create/modify**

- `AliyunOssClientTest.java`
- `MineruClientTest.java`
- `MineruArchiveParserTest.java`
- `MineruResultDownloaderTest.java`
- `DocumentServiceUploadTest.java`
- `MineruTaskProcessorTest.java`
- `MineruPollingJobTest.java`
- existing constructor-based service tests as required by dependency changes.

**Frontend files to create/modify**

- Create `ekko-group-buy-web/src/utils/pollKnowledgeDocument.js`.
- Create `ekko-group-buy-web/tests/pollKnowledgeDocument.test.js`.
- Modify `ekko-group-buy-web/src/components/DocumentManager.vue`.
- Modify `ekko-group-buy-web/src/api/knowledge.js`.
- Modify `ekko-group-buy-web/package.json`.

---

### Task 1: Persisted Task State and Environment-Backed Configuration

**Files:**
- Modify: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/entity/KnowledgeDocument.java`
- Modify: `ekko-group-chat/src/main/java/cn/ekko/groupchat/config/GroupChatProperties.java`
- Modify: `ekko-group-chat/src/main/resources/application.yml`
- Modify: `ekko-group-chat/src/main/resources/db/schema.sql`
- Create: `ekko-group-chat/src/main/resources/db/migration/2026-08-06-add-mineru-cloud-task.sql`
- Test: `ekko-group-chat/src/test/java/cn/ekko/groupchat/config/GroupChatPropertiesTest.java`

**Interfaces:**
- Produces: `KnowledgeDocument#getMineruTaskId()`, `getMineruSubmittedAt()`.
- Produces: `GroupChatProperties.Mineru` getters for `createTaskPath`, `taskResultPath`, `modelVersion`, `ocrEnabled`, `pollInterval`, `taskTimeout`, `sourceUrlExpiration`, and `pollBatchSize`.

- [ ] **Step 1: Write the failing configuration binding test**

```java
class GroupChatPropertiesTest {
    @Test
    void shouldBindMineruCloudSettings() {
        Map<String, String> values = Map.ofEntries(
                Map.entry("group-chat.mineru.base-url", "https://mineru.net"),
                Map.entry("group-chat.mineru.create-task-path", "/api/v4/extract/task"),
                Map.entry("group-chat.mineru.task-result-path", "/api/v4/extract/task/{taskId}"),
                Map.entry("group-chat.mineru.model-version", "vlm"),
                Map.entry("group-chat.mineru.poll-interval", "10s"),
                Map.entry("group-chat.mineru.task-timeout", "1h"),
                Map.entry("group-chat.mineru.source-url-expiration", "2h"),
                Map.entry("group-chat.mineru.poll-batch-size", "20")
        );
        Binder binder = new Binder(new MapConfigurationPropertySource(values));

        GroupChatProperties properties = binder.bind("group-chat", Bindable.of(GroupChatProperties.class)).get();

        assertThat(properties.getMineru().getModelVersion()).isEqualTo("vlm");
        assertThat(properties.getMineru().getPollInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getMineru().getTaskTimeout()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.getMineru().getSourceUrlExpiration()).isEqualTo(Duration.ofHours(2));
        assertThat(properties.getMineru().getPollBatchSize()).isEqualTo(20);
    }
}
```

- [ ] **Step 2: Run the test and verify the cloud properties are missing**

Run:

```bash
cd /Users/ekko/JavaCode/group-buy/ekko-group-chat
MAVEN_SKIP_RC=true JAVA_HOME=/Users/ekko/Library/Java/JavaVirtualMachines/ms-21.0.12/Contents/Home \
  PATH=/Users/ekko/Library/Java/JavaVirtualMachines/ms-21.0.12/Contents/Home/bin:$PATH \
  mvn -Dtest=GroupChatPropertiesTest test
```

Expected: compilation fails because the new MinerU property getters do not exist.

- [ ] **Step 3: Add the entity fields and exact cloud properties**

```java
public static class Mineru {
    private String baseUrl;
    private String createTaskPath;
    private String taskResultPath;
    private String apiKey;
    private String modelVersion;
    private String language;
    private boolean formulaEnabled;
    private boolean tableEnabled;
    private boolean ocrEnabled;
    private Duration pollInterval;
    private Duration taskTimeout;
    private Duration sourceUrlExpiration;
    private int pollBatchSize;
}
```

Add to `KnowledgeDocument`:

```java
private String mineruTaskId;
private LocalDateTime mineruSubmittedAt;
```

Replace the MinerU YML block with the exact environment-backed block from the approved design. Do not put a default token value in `api-key`.

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

- [ ] **Step 4: Add complete-schema and live migration SQL**

Migration contents:

```sql
ALTER TABLE knowledge_document
    ADD COLUMN mineru_task_id VARCHAR(100) DEFAULT NULL COMMENT 'MinerU 官网解析任务 ID' AFTER failure_reason,
    ADD COLUMN mineru_submitted_at DATETIME(3) DEFAULT NULL COMMENT 'MinerU 任务提交时间' AFTER mineru_task_id,
    ADD KEY idx_knowledge_document_mineru_task (status, mineru_submitted_at);
```

Mirror the two columns and index in `db/schema.sql` for new installations.

- [ ] **Step 5: Run the binding test and all existing tests**

Expected: `GroupChatPropertiesTest` passes and the existing 10 tests remain green.

- [ ] **Step 6: Commit only Task 1 files**

```bash
git add ekko-group-chat/src/main/java/cn/ekko/groupchat/config/GroupChatProperties.java \
        ekko-group-chat/src/main/java/cn/ekko/groupchat/document/entity/KnowledgeDocument.java \
        ekko-group-chat/src/main/resources/application.yml \
        ekko-group-chat/src/main/resources/db/schema.sql \
        ekko-group-chat/src/main/resources/db/migration/2026-08-06-add-mineru-cloud-task.sql \
        ekko-group-chat/src/test/java/cn/ekko/groupchat/config/GroupChatPropertiesTest.java
git commit -m "feat: configure MinerU cloud task state"
```

---

### Task 2: Private OSS Signed Source URL

**Files:**
- Modify: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/AliyunOssClient.java`
- Test: `ekko-group-chat/src/test/java/cn/ekko/groupchat/document/client/AliyunOssClientTest.java`

**Interfaces:**
- Consumes: `GroupChatProperties.Oss#getBucket()`.
- Produces: `String AliyunOssClient.presignGet(String objectKey, Duration expiration)`.

- [ ] **Step 1: Write the failing signed-URL test**

```java
@Test
void shouldPresignPrivateGetObjectForRequestedDuration() {
    OSSClient sdk = mock(OSSClient.class);
    GroupChatProperties properties = new GroupChatProperties();
    properties.getOss().setBucket("ekko-group-buy-mall");
    when(sdk.presign(any(GetObjectRequest.class), any(PresignOptions.class)))
            .thenReturn(PresignResult.newBuilder()
                    .url("https://signed.example/router.pdf?signature=ok")
                    .method("GET")
                    .build());
    AliyunOssClient client = new AliyunOssClient(sdk, properties);

    String url = client.presignGet("knowledge/original/7/router.pdf", Duration.ofHours(2));

    assertThat(url).isEqualTo("https://signed.example/router.pdf?signature=ok");
    ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
    ArgumentCaptor<PresignOptions> options = ArgumentCaptor.forClass(PresignOptions.class);
    verify(sdk).presign(request.capture(), options.capture());
    assertThat(request.getValue().bucket()).isEqualTo("ekko-group-buy-mall");
    assertThat(request.getValue().key()).isEqualTo("knowledge/original/7/router.pdf");
    assertThat(options.getValue().expiration()).hasValueSatisfying(expiration ->
            assertThat(expiration).isBetween(
                    Instant.now().plus(Duration.ofMinutes(119)),
                    Instant.now().plus(Duration.ofMinutes(121))));
}
```

- [ ] **Step 2: Run the test and verify `presignGet` is missing**

Run the single test with the Java 21 Maven command from Task 1. Expected: compilation failure on `presignGet`.

- [ ] **Step 3: Implement the minimal presign method**

```java
public String presignGet(String objectKey, Duration expiration) {
    GetObjectRequest request = GetObjectRequest.newBuilder()
            .bucket(properties.getOss().getBucket())
            .key(objectKey)
            .build();
    PresignOptions options = PresignOptions.newBuilder()
            .expiration(expiration)
            .build();
    return ossClient.presign(request, options).url();
}
```

- [ ] **Step 4: Run `AliyunOssClientTest` and the existing suite**

Expected: signed URL test passes; no existing test regresses.

- [ ] **Step 5: Commit Task 2**

```bash
git add ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/AliyunOssClient.java \
        ekko-group-chat/src/test/java/cn/ekko/groupchat/document/client/AliyunOssClientTest.java
git commit -m "feat: presign private OSS source documents"
```

---

### Task 3: Official MinerU Create and Query Protocol

**Files:**
- Create: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruTaskState.java`
- Create: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruTaskResult.java`
- Replace: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruClient.java`
- Modify: `ekko-group-chat/src/main/java/cn/ekko/groupchat/config/ClientConfiguration.java`
- Replace test: `ekko-group-chat/src/test/java/cn/ekko/groupchat/document/client/MineruResponseParserTest.java` with `MineruClientTest.java`
- Delete: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruResponseParser.java`

**Interfaces:**
- Produces: `String MineruClient.createTask(String sourceUrl, String dataId)`.
- Produces: `MineruTaskResult MineruClient.queryTask(String taskId)`.
- Produces: `record MineruTaskResult(String taskId, MineruTaskState state, String fullZipUrl, String errorMessage, String traceId)`.

- [ ] **Step 1: Write a failing HTTP contract test for task creation**

Use `MockRestServiceServer.bindTo(RestClient.Builder)` and assert this exact body:

```json
{
  "url":"https://signed.example/router.pdf?signature=ok",
  "model_version":"vlm",
  "language":"ch",
  "enable_formula":true,
  "enable_table":true,
  "is_ocr":false,
  "data_id":"document-7"
}
```

The test must also expect `Authorization: Bearer test-token` and return:

```json
{"code":0,"msg":"ok","trace_id":"trace-create","data":{"task_id":"task-7"}}
```

Assert `createTask(...)` returns `task-7`.

- [ ] **Step 2: Run the creation test and verify the old multipart implementation fails**

Expected: the server reports request method/body mismatch because the production client still posts multipart to `/file_parse`.

- [ ] **Step 3: Add a failing parameterized task-state test**

```java
@ParameterizedTest
@CsvSource({
        "pending,PENDING",
        "running,RUNNING",
        "converting,CONVERTING",
        "done,DONE",
        "failed,FAILED"
})
void shouldMapOfficialTaskStates(String providerState, MineruTaskState expected) {
    server.expect(requestTo("https://mineru.net/api/v4/extract/task/task-7"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                    {"code":0,"msg":"ok","trace_id":"trace-query","data":{
                      "task_id":"task-7","state":"%s",
                      "full_zip_url":"https://cdn.example/result.zip","err_msg":""
                    }}
                    """.formatted(providerState), MediaType.APPLICATION_JSON));

    assertThat(client.queryTask("task-7").state()).isEqualTo(expected);
}
```

Add separate tests that `code != 0`, missing `task_id`, and an unknown state produce clear exceptions containing `trace_id` where available. A provider `failed` state without `err_msg` must still map to `FAILED` with a stable fallback message such as `MinerU 解析失败`, so the persisted task does not remain stuck in `PARSING`.

- [ ] **Step 4: Implement normalized records and the JSON client**

Use small private records for serialization:

```java
private record CreateTaskRequest(
        String url,
        @JsonProperty("model_version") String modelVersion,
        String language,
        @JsonProperty("enable_formula") boolean enableFormula,
        @JsonProperty("enable_table") boolean enableTable,
        @JsonProperty("is_ocr") boolean ocr,
        @JsonProperty("data_id") String dataId
) {}
```

Parse provider responses with `JsonNode`, require `code == 0`, and centralize required-field checks. Do not log request headers or the API key.

- [ ] **Step 5: Configure only the MinerU API client with Bearer authentication**

`mineruRestClient` keeps the official base URL and Bearer header. Keep `@Qualifier("mineruRestClient")` on the `MineruClient` constructor. Fail application startup with a clear message when `apiKey` is blank, instead of sending an empty or placeholder token.

- [ ] **Step 6: Run `MineruClientTest` and the full backend suite**

Expected: all contract/error tests and existing tests pass.

- [ ] **Step 7: Commit Task 3**

```bash
git add ekko-group-chat/src/main/java/cn/ekko/groupchat/config/ClientConfiguration.java \
        ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruClient.java \
        ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruTaskState.java \
        ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruTaskResult.java \
        ekko-group-chat/src/test/java/cn/ekko/groupchat/document/client/MineruClientTest.java
git rm ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruResponseParser.java \
       ekko-group-chat/src/test/java/cn/ekko/groupchat/document/client/MineruResponseParserTest.java
git commit -m "feat: call official MinerU task API"
```

---

### Task 4: Credential-Free ZIP Download and Safe Markdown Extraction

**Files:**
- Create: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruArchiveParser.java`
- Create: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruResultDownloader.java`
- Modify: `ekko-group-chat/src/main/java/cn/ekko/groupchat/config/ClientConfiguration.java`
- Test: `ekko-group-chat/src/test/java/cn/ekko/groupchat/document/client/MineruArchiveParserTest.java`
- Test: `ekko-group-chat/src/test/java/cn/ekko/groupchat/document/client/MineruResultDownloaderTest.java`

**Interfaces:**
- Produces: `String MineruArchiveParser.extractMarkdown(byte[] archive)`.
- Produces: `byte[] MineruResultDownloader.download(String fullZipUrl)`.
- Security invariant: the CDN request never includes the MinerU Authorization header.

- [ ] **Step 1: Write failing archive tests**

Create ZIP bytes in the test with `ZipOutputStream`. Cover:

```java
assertThat(parser.extractMarkdown(zip(Map.of("full.md", "# Router"))))
        .isEqualTo("# Router");
assertThatThrownBy(() -> parser.extractMarkdown(zip(Map.of("content.json", "{}"))))
        .hasMessageContaining("full.md");
assertThatThrownBy(() -> parser.extractMarkdown(zip(Map.of("../full.md", "bad"))))
        .hasMessageContaining("非法 ZIP 路径");
```

Also cover blank Markdown, archive byte limit, and extracted Markdown byte limit.

- [ ] **Step 2: Verify archive tests fail because the parser is absent**

Expected: test compilation failure.

- [ ] **Step 3: Implement streaming ZIP extraction**

Use `ZipInputStream`, normalize entry names with `Path.of(entry.getName()).normalize()`, reject absolute paths and entries beginning with `..`, and return only the UTF-8 `full.md`. Use fixed limits of 100 MB archive bytes and 20 MB Markdown bytes for this version.

- [ ] **Step 4: Write a failing downloader test that forbids token forwarding**

Bind `MockRestServiceServer` to a second builder, expect the absolute CDN URL, and assert no `Authorization` header is present. Return `application/zip` bytes and assert they are preserved.

- [ ] **Step 5: Add `mineruResultRestClient` without default authentication**

```java
@Bean("mineruResultRestClient")
RestClient mineruResultRestClient(RestClient.Builder builder) {
    return builder.build();
}
```

`MineruResultDownloader` injects only this bean with `@Qualifier("mineruResultRestClient")` and requests the absolute result URI. It must never reuse `mineruRestClient`.

- [ ] **Step 6: Run archive/downloader tests and the complete suite**

Expected: all pass; Mock server proves the token is not sent to the CDN.

- [ ] **Step 7: Commit Task 4**

```bash
git add ekko-group-chat/src/main/java/cn/ekko/groupchat/config/ClientConfiguration.java \
        ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruArchiveParser.java \
        ekko-group-chat/src/main/java/cn/ekko/groupchat/document/client/MineruResultDownloader.java \
        ekko-group-chat/src/test/java/cn/ekko/groupchat/document/client/MineruArchiveParserTest.java \
        ekko-group-chat/src/test/java/cn/ekko/groupchat/document/client/MineruResultDownloaderTest.java
git commit -m "feat: extract MinerU result markdown safely"
```

---

### Task 5: Submit-and-Return Document Upload

**Files:**
- Modify: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/service/DocumentService.java`
- Create: `ekko-group-chat/src/test/java/cn/ekko/groupchat/document/service/DocumentServiceUploadTest.java`
- Modify: `ekko-group-chat/src/test/java/cn/ekko/groupchat/document/service/DocumentServiceDeleteTest.java`

**Interfaces:**
- Consumes: `AliyunOssClient.presignGet`, `MineruClient.createTask`.
- Produces: `DocumentService.upload(...)` returning a persisted `PARSING` document with task metadata.

- [ ] **Step 1: Write a failing upload-state test**

The test should use mocks and capture mapper writes:

```java
when(documentMapper.insert(any())).thenAnswer(invocation -> {
    KnowledgeDocument row = invocation.getArgument(0);
    row.setId(7L);
    return 1;
});
when(ossClient.presignGet(anyString(), eq(Duration.ofHours(2))))
        .thenReturn("https://signed.example/router.pdf");
when(mineruClient.createTask("https://signed.example/router.pdf", "document-7"))
        .thenReturn("task-7");
KnowledgeDocument stored = new KnowledgeDocument();
stored.setId(7L);
stored.setStatus(DocumentStatus.PARSING);
stored.setMineruTaskId("task-7");
stored.setMineruSubmittedAt(LocalDateTime.parse("2026-08-06T10:00:00"));
when(documentMapper.selectById(7L)).thenReturn(stored);

KnowledgeDocument result = service.upload(file("router.pdf"), "Router", "TL-7DR5130");

assertThat(result.getStatus()).isEqualTo(DocumentStatus.PARSING);
assertThat(result.getMineruTaskId()).isEqualTo("task-7");
verify(documentMapper).update(isNull(), argThat(wrapper ->
        wrapper.getSqlSet().contains("mineru_task_id")
                && wrapper.getSqlSet().contains("mineru_submitted_at")));
verify(elasticsearchClient, never()).index(anyLong(), anyString(), anyString(), anyString(), anyList());
```

Also verify the original object is uploaded before the signed URL and task submission, and that task submission failure marks the document `FAILED`.

- [ ] **Step 2: Run the test and confirm existing synchronous indexing violates it**

Expected: failure because current upload calls the old parse method and Elasticsearch before returning.

- [ ] **Step 3: Implement the minimal asynchronous submission path**

After OSS upload:

```java
String sourceUrl = ossClient.presignGet(
        originalObjectKey,
        properties.getMineru().getSourceUrlExpiration()
);
String taskId = mineruClient.createTask(sourceUrl, "document-" + documentId);
markParsing(documentId, originalObjectKey, parsedObjectKey, taskId, LocalDateTime.now());
return get(documentId);
```

Remove synchronous Markdown parsing/indexing from `upload`. Keep validation, hashing, object key generation, detail, and deletion behavior.

- [ ] **Step 4: Update constructor-based existing tests**

Adjust `DocumentServiceDeleteTest` constructor arguments only; do not weaken its operation-order assertions.

- [ ] **Step 5: Run upload/delete tests and complete suite**

Expected: upload returns `PARSING`; delete behavior remains unchanged.

- [ ] **Step 6: Commit Task 5**

```bash
git add ekko-group-chat/src/main/java/cn/ekko/groupchat/document/service/DocumentService.java \
        ekko-group-chat/src/test/java/cn/ekko/groupchat/document/service/DocumentServiceUploadTest.java \
        ekko-group-chat/src/test/java/cn/ekko/groupchat/document/service/DocumentServiceDeleteTest.java
git commit -m "feat: submit document parsing asynchronously"
```

---

### Task 6: Durable Polling, Completion, Failure, and Timeout

**Files:**
- Create: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/service/MineruTaskProcessor.java`
- Create: `ekko-group-chat/src/main/java/cn/ekko/groupchat/document/job/MineruPollingJob.java`
- Modify: `ekko-group-chat/src/main/java/cn/ekko/groupchat/GroupChatApplication.java`
- Test: `ekko-group-chat/src/test/java/cn/ekko/groupchat/document/service/MineruTaskProcessorTest.java`
- Test: `ekko-group-chat/src/test/java/cn/ekko/groupchat/document/job/MineruPollingJobTest.java`

**Interfaces:**
- Produces: `void MineruTaskProcessor.process(KnowledgeDocument document)`.
- Produces: package-visible `void MineruPollingJob.poll()` for deterministic tests; scheduled wrapper delegates to it.

- [ ] **Step 1: Write failing processor tests for nonterminal states**

For `PENDING`, `RUNNING`, and `CONVERTING`, call `process(document)` and verify no database update, download, OSS Markdown upload, or ES index occurs.

- [ ] **Step 2: Write the failing success-path test**

```java
when(mineruClient.queryTask("task-7")).thenReturn(new MineruTaskResult(
        "task-7", MineruTaskState.DONE, "https://cdn.example/result.zip", "", "trace-7"));
when(downloader.download("https://cdn.example/result.zip")).thenReturn(zipBytes);
when(archiveParser.extractMarkdown(zipBytes)).thenReturn("# Router manual");
when(chunker.split("# Router manual")).thenReturn(List.of("# Router manual"));
when(elasticsearchClient.index(7L, "Router", "TL-7DR5130",
        "knowledge/parsed/7/hash.md", List.of("# Router manual"))).thenReturn(1);

processor.process(document);

verify(ossClient).put("knowledge/parsed/7/hash.md",
        "# Router manual".getBytes(StandardCharsets.UTF_8),
        "text/markdown; charset=UTF-8");
ArgumentCaptor<LambdaUpdateWrapper<KnowledgeDocument>> updates =
        ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
verify(documentMapper, times(2)).update(isNull(), updates.capture());
assertThat(updates.getAllValues().stream()
        .map(this::statusFrom)
        .toList())
        .containsExactly(DocumentStatus.INDEXING, DocumentStatus.PUBLISHED);
```

Define the test helper explicitly so the assertion does not depend on an imaginary fixture:

```java
private DocumentStatus statusFrom(LambdaUpdateWrapper<KnowledgeDocument> update) {
    return update.getParamNameValuePairs().values().stream()
            .filter(DocumentStatus.class::isInstance)
            .map(DocumentStatus.class::cast)
            .findFirst()
            .orElseThrow();
}
```

- [ ] **Step 3: Write failing failure and timeout tests**

- Provider `FAILED` stores provider `err_msg`.
- A document submitted more than one hour ago becomes `FAILED` without querying MinerU.
- A document with a task ID but missing submission time is marked `FAILED` as inconsistent persisted state.
- Temporary exception from `queryTask` leaves the database untouched and propagates to the job for warning logging.
- ZIP/Markdown/OSS/ES failure stores the root cause and changes status to `FAILED`.

Inject `Clock` into `MineruTaskProcessor` so the timeout test does not depend on wall-clock time.

- [ ] **Step 4: Implement `MineruTaskProcessor` minimally**

Check the one-hour timeout before querying. Call `queryTask` outside the completion-path `try/catch` so a temporary query/network exception propagates without changing MySQL. Then use a `switch` on `MineruTaskState`: nonterminal states return unchanged, provider `FAILED` stores its error, and `DONE` enters a separate `try/catch` that downloads, extracts, uploads Markdown, marks `INDEXING`, chunks, indexes, and marks `PUBLISHED`. Only exceptions inside that `DONE` completion branch mark the document `FAILED`; do not expose the API token in messages.

- [ ] **Step 5: Write a failing bounded-scan job test**

Capture the MyBatis-Plus query wrapper and verify it expresses:

```text
status = PARSING
mineru_task_id IS NOT NULL
ORDER BY mineru_submitted_at ASC
LIMIT 20
```

Return two documents and verify `processor.process` is called for both even when the first throws a temporary exception.

- [ ] **Step 6: Implement `MineruPollingJob` and enable scheduling**

```java
@Scheduled(fixedDelayString = "${group-chat.mineru.poll-interval:10s}")
public void poll() {
    List<KnowledgeDocument> tasks = documentMapper.selectList(taskQuery());
    for (KnowledgeDocument task : tasks) {
        try {
            processor.process(task);
        } catch (RuntimeException exception) {
            log.warn("MinerU task query failed, documentId={}", task.getId(), exception);
        }
    }
}
```

Add `@EnableScheduling` to `GroupChatApplication`.

- [ ] **Step 7: Run processor/job tests and complete backend suite**

Expected: all state, failure, timeout, and bounded-scan tests pass.

- [ ] **Step 8: Commit Task 6**

```bash
git add ekko-group-chat/src/main/java/cn/ekko/groupchat/GroupChatApplication.java \
        ekko-group-chat/src/main/java/cn/ekko/groupchat/document/service/MineruTaskProcessor.java \
        ekko-group-chat/src/main/java/cn/ekko/groupchat/document/job/MineruPollingJob.java \
        ekko-group-chat/src/test/java/cn/ekko/groupchat/document/service/MineruTaskProcessorTest.java \
        ekko-group-chat/src/test/java/cn/ekko/groupchat/document/job/MineruPollingJobTest.java
git commit -m "feat: poll and publish MinerU cloud tasks"
```

---

### Task 7: Frontend Automatic Document Status Polling

**Files:**
- Create: `ekko-group-buy-web/src/utils/pollKnowledgeDocument.js`
- Create: `ekko-group-buy-web/tests/pollKnowledgeDocument.test.js`
- Modify: `ekko-group-buy-web/src/components/DocumentManager.vue`
- Modify: `ekko-group-buy-web/src/api/knowledge.js`
- Modify: `ekko-group-buy-web/package.json`

**Interfaces:**
- Produces: `pollKnowledgeDocument({ documentId, query, onUpdate, signal, intervalMs })`.
- Terminal states: exactly `PUBLISHED` and `FAILED`.

- [ ] **Step 1: Add the Node test script and write failing polling tests**

`package.json`:

```json
"scripts": {
  "dev": "vite",
  "test": "node --test tests/*.test.js",
  "build": "vite build",
  "preview": "vite preview"
}
```

Core test:

```js
test('polls until the document is published', async () => {
  const responses = [
    { id: 7, status: 'PARSING' },
    { id: 7, status: 'INDEXING' },
    { id: 7, status: 'PUBLISHED' },
  ]
  const updates = []

  const result = await pollKnowledgeDocument({
    documentId: 7,
    query: async () => responses.shift(),
    onUpdate: document => updates.push(document.status),
    intervalMs: 0,
  })

  assert.equal(result.status, 'PUBLISHED')
  assert.deepEqual(updates, ['PARSING', 'PARSING', 'INDEXING', 'PUBLISHED'])
})
```

Add tests that `FAILED` stops immediately and an aborted signal stops without another query.

- [ ] **Step 2: Run `npm test` and verify the module is missing**

Expected: Node reports it cannot import `src/utils/pollKnowledgeDocument.js`.

- [ ] **Step 3: Implement the dependency-injected poll loop**

```js
const TERMINAL_STATUSES = new Set(['PUBLISHED', 'FAILED'])

export async function pollKnowledgeDocument({ documentId, query, onUpdate, signal, intervalMs = 3000 }) {
  while (!signal?.aborted) {
    const document = await query(documentId)
    onUpdate(document)
    if (TERMINAL_STATUSES.has(document.status)) return document
    await new Promise((resolve, reject) => {
      const onAbort = () => {
        clearTimeout(timer)
        reject(new DOMException('Aborted', 'AbortError'))
      }
      const timer = setTimeout(() => {
        signal?.removeEventListener('abort', onAbort)
        resolve()
      }, intervalMs)
      signal?.addEventListener('abort', onAbort, { once: true })
    })
  }
  throw new DOMException('Aborted', 'AbortError')
}
```

- [ ] **Step 4: Integrate polling into `DocumentManager.vue`**

- Import `onBeforeUnmount` and `pollKnowledgeDocument`.
- Keep one `AbortController`; abort it before a new upload, logout, delete, and component unmount. Replace the template's direct `emit('logout')` call with a small handler that aborts first and emits second.
- After upload returns, show `文档 #<id> 已提交解析` and start polling with `queryKnowledgeDocument`.
- On `PUBLISHED`, show `文档 #<id> 已发布，可以用于智能客服检索`.
- On `FAILED`, display `failureReason`.
- Change the button's busy text from “正在解析并建立索引…” to “正在上传并提交解析…”.
- Reduce upload API timeout in `knowledge.js` from 240 seconds to 60 seconds because parsing is no longer inside the request.

- [ ] **Step 5: Run frontend tests and build**

```bash
cd /Users/ekko/JavaCode/group-buy/ekko-group-buy-web
npm test
npm run build
```

Expected: all Node tests pass and Vite builds successfully.

- [ ] **Step 6: Commit Task 7**

```bash
git add ekko-group-buy-web/package.json \
        ekko-group-buy-web/src/api/knowledge.js \
        ekko-group-buy-web/src/components/DocumentManager.vue \
        ekko-group-buy-web/src/utils/pollKnowledgeDocument.js \
        ekko-group-buy-web/tests/pollKnowledgeDocument.test.js
git commit -m "feat: track asynchronous document ingestion"
```

---

### Task 8: Documentation, Static Secret Check, and Final Verification

**Files:**
- Modify: `ekko-group-chat/README.md`
- Modify: `/Users/ekko/JavaCode/group-buy/接口文档.md`
- Verify only: all files changed by Tasks 1-7.

**Interfaces:**
- Documents the exact environment variables, migration command, status behavior, and evidence boundary.

- [ ] **Step 1: Update operational documentation**

Document:

- Create a MinerU token in API management and export it as `MINERU_API_KEY`.
- Run `2026-08-06-add-mineru-cloud-task.sql` manually because SQL init is disabled.
- Ensure the OSS RAM user can `PutObject` and sign `GetObject` for the configured Bucket.
- `POST /api/v1/documents` now returns `PARSING`; use the detail endpoint to observe completion.
- MinerU source URL expiration, polling interval, timeout, and `vlm` default.
- Unit/Mock evidence is not live-provider evidence.

- [ ] **Step 2: Run a static secret and obsolete-endpoint scan**

```bash
cd /Users/ekko/JavaCode/group-buy
rg -n "file_parse|127\.0\.0\.1:8000|api-key:\s+T[O]DO|Bearer T[O]DO" ekko-group-chat \
  --glob '!docs/superpowers/**' --glob '!target/**'
rg -n "MINERU_API_KEY.*[^}]$|sk-[A-Za-z0-9._-]{20,}|access-key-secret:\s+[^$]" \
  ekko-group-chat/src ekko-group-chat/README.md
```

Expected: no production reference to local `/file_parse` or placeholder Bearer token; no literal MinerU token is present. Existing unrelated credentials discovered by the second scan must be reported and rotated, not copied into output.

- [ ] **Step 3: Run the complete backend test suite with Java 21**

```bash
cd /Users/ekko/JavaCode/group-buy/ekko-group-chat
MAVEN_SKIP_RC=true JAVA_HOME=/Users/ekko/Library/Java/JavaVirtualMachines/ms-21.0.12/Contents/Home \
  PATH=/Users/ekko/Library/Java/JavaVirtualMachines/ms-21.0.12/Contents/Home/bin:$PATH \
  mvn test -DargLine='-javaagent:/Users/ekko/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar'
```

Expected: `BUILD SUCCESS`, zero failures, zero errors.

- [ ] **Step 4: Run complete frontend verification**

```bash
cd /Users/ekko/JavaCode/group-buy/ekko-group-buy-web
npm test
npm run build
```

Expected: Node tests pass and Vite production build succeeds.

- [ ] **Step 5: Review only intended diffs**

```bash
cd /Users/ekko/JavaCode/group-buy
git status --short
git diff --check
git diff -- ekko-group-chat ekko-group-buy-web/src/components/DocumentManager.vue \
  ekko-group-buy-web/src/api/knowledge.js ekko-group-buy-web/src/utils \
  ekko-group-buy-web/tests ekko-group-buy-web/package.json 接口文档.md
```

Verify unrelated pre-existing payment/market changes are not staged or altered.

- [ ] **Step 6: Commit documentation and final cleanup**

```bash
git add ekko-group-chat/README.md 接口文档.md
git commit -m "docs: explain MinerU cloud ingestion"
```

- [ ] **Step 7: Optional real-provider smoke test after the user supplies runtime secrets**

Do not execute this step without `MINERU_API_KEY`, OSS credentials, database migration, Elasticsearch, and model credentials. Upload one router PDF through `POST /api/v1/documents`, record the returned document ID, poll the detail endpoint until `PUBLISHED`, and verify the parsed OSS Markdown plus ES chunk count. Report this separately as live integration evidence.
