package cn.ekko.groupchat.chat.retriever;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.experimental.rag.content.retriever.sql.SqlDatabaseContentRetriever;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.util.TablesNamesFinder;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 受限 Text2SQL 检索器：仅开放知识文档统计字段，SQL 失败或无数据时降级到 ES 混合检索。
 */
@Slf4j
public class SafeSqlDatabaseContentRetriever extends SqlDatabaseContentRetriever
        implements RoutedContentRetriever {

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "knowledge_document", "knowledge_chunk", "knowledge_image"
    );
    private static final Pattern FORBIDDEN_TOKEN = Pattern.compile(
            "(?i)\\b(?:admin_user|password_hash|sha256|original_object_key|parsed_object_key|"
                    + "processed_object_key|object_key|failure_reason|mineru_task_id|description|content|"
                    + "information_schema|mysql|performance_schema|sys|sleep|benchmark|load_file|outfile|dumpfile)\\b"
    );
    private static final Pattern SELECT_ALL = Pattern.compile(
            "(?i)(?:\\bselect|,)\\s*(?:distinct\\s+)?(?:[a-z_][a-z0-9_]*\\.)?\\*"
    );

    private static final String SAFE_DDL = """
            CREATE TABLE knowledge_document (
              id BIGINT PRIMARY KEY,
              title VARCHAR(200),
              file_name VARCHAR(255),
              content_type VARCHAR(100),
              file_size BIGINT,
              product_model VARCHAR(100),
              chunk_strategy VARCHAR(16),
              status VARCHAR(32),
              chunk_count INT,
              image_count INT,
              created_at DATETIME,
              updated_at DATETIME
            );
            CREATE TABLE knowledge_chunk (
              chunk_id VARCHAR(120) PRIMARY KEY,
              document_id BIGINT,
              parent_chunk_id VARCHAR(120),
              chunk_type VARCHAR(16),
              heading_path VARCHAR(1000),
              chunk_index INT,
              searchable TINYINT,
              created_at DATETIME,
              FOREIGN KEY (document_id) REFERENCES knowledge_document(id)
            );
            CREATE TABLE knowledge_image (
              id BIGINT PRIMARY KEY,
              document_id BIGINT,
              content_type VARCHAR(100),
              file_size BIGINT,
              status VARCHAR(32),
              created_at DATETIME,
              updated_at DATETIME,
              FOREIGN KEY (document_id) REFERENCES knowledge_document(id)
            );
            """;

    private static final PromptTemplate SQL_PROMPT = PromptTemplate.from("""
            你是 MySQL 只读查询生成器。数据库只向你开放以下结构：
            {{databaseStructure}}

            根据用户问题只输出一条 SELECT 查询，不得输出解释或 Markdown。
            规则：
            1. 只能查询给出的三个表和列，禁止 SELECT *，禁止访问系统表。
            2. 明细查询必须 LIMIT 100；统计查询可使用 COUNT/SUM/AVG/MIN/MAX/GROUP BY。
            3. 不得生成 INSERT、UPDATE、DELETE、DDL、锁、变量、存储过程或文件操作。
            4. 当前时间为 {{current_date_time}}。
            """);

    private final List<ContentRetriever> fallbackRetrievers;

    public SafeSqlDatabaseContentRetriever(
            DataSource dataSource,
            ChatModel chatModel,
            List<ContentRetriever> fallbackRetrievers
    ) {
        super(dataSource, "MySQL", SAFE_DDL, SQL_PROMPT, chatModel, 1);
        this.fallbackRetrievers = List.copyOf(fallbackRetrievers);
    }

    @Override
    public QueryRoute route() {
        return QueryRoute.RELATIONAL_DB;
    }

    @Override
    public List<Content> retrieve(Query query) {
        try {
            List<Content> results = super.retrieve(query);
            if (!results.isEmpty() && !isHeaderOnly(results.getFirst().textSegment().text())) {
                return results.stream().map(this::markStructuredResult).toList();
            }
        } catch (RuntimeException exception) {
            log.warn("Text2SQL 检索失败，降级到知识库, query={}", query.text(), exception);
        }
        log.info("Text2SQL 无有效结果，降级到知识库, query={}", query.text());
        return fallback(query);
    }

    @Override
    protected void validate(String sqlQuery) {
        String normalized = sqlQuery.trim();
        long semicolonCount = normalized.chars().filter(character -> character == ';').count();
        if (semicolonCount > 1
                || (semicolonCount == 1 && !normalized.matches("(?s).*;\\s*$"))) {
            throw new IllegalArgumentException("只允许执行一条 SELECT");
        }
        if (normalized.contains("--") || normalized.contains("/*")
                || normalized.contains("#") || normalized.contains("@")) {
            throw new IllegalArgumentException("SQL 注释不被允许");
        }
        if (normalized.matches("(?is).*(?:\\bfor\\s+update\\b|\\block\\s+in\\s+share\\s+mode\\b|"
                + "\\bget_lock\\s*\\(|\\brelease_lock\\s*\\().*")) {
            throw new IllegalArgumentException("SQL 锁与会话函数不被允许");
        }
        if (FORBIDDEN_TOKEN.matcher(normalized).find() || SELECT_ALL.matcher(normalized).find()) {
            throw new IllegalArgumentException("SQL 包含未开放的表、字段或操作");
        }
        try {
            Set<String> tables = TablesNamesFinder.findTables(normalized);
            if (tables.isEmpty() || tables.stream()
                    .map(table -> table.replace("`", "").toLowerCase(Locale.ROOT))
                    .anyMatch(table -> !ALLOWED_TABLES.contains(table))) {
                throw new IllegalArgumentException("SQL 只能查询知识文档白名单表");
            }
        } catch (JSQLParserException exception) {
            throw new IllegalArgumentException("SQL 语法校验失败", exception);
        }
    }

    @Override
    protected String execute(String sqlQuery, Statement statement) throws SQLException {
        statement.getConnection().setReadOnly(true);
        statement.setMaxRows(100);
        statement.setQueryTimeout(5);
        return super.execute(sqlQuery, statement);
    }

    private Content markStructuredResult(Content content) {
        Metadata metadata = new Metadata()
                .put("title", "MySQL 结构化查询结果")
                .put("retrievalSource", "RELATIONAL_DB")
                .put("retrievalSources", "RELATIONAL_DB")
                .put("skipRerank", "true");
        return Content.from(TextSegment.from(content.textSegment().text(), metadata), content.metadata());
    }

    private boolean isHeaderOnly(String text) {
        int marker = text.indexOf(":\n");
        if (marker < 0) {
            return false;
        }
        int headerEnd = text.indexOf('\n', marker + 2);
        return headerEnd < 0 || text.substring(headerEnd + 1).trim().isEmpty();
    }

    private List<Content> fallback(Query query) {
        Map<String, Content> unique = new LinkedHashMap<>();
        for (ContentRetriever retriever : fallbackRetrievers) {
            for (Content content : retriever.retrieve(query)) {
                String chunkId = content.textSegment().metadata().getString("chunkId");
                String key = chunkId == null ? content.textSegment().text() : chunkId;
                unique.putIfAbsent(key, content);
            }
        }
        return new ArrayList<>(unique.values());
    }
}
