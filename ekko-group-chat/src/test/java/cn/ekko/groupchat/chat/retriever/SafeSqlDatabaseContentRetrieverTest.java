package cn.ekko.groupchat.chat.retriever;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeSqlDatabaseContentRetrieverTest {

    private final SafeSqlDatabaseContentRetriever retriever = new SafeSqlDatabaseContentRetriever(
            new StubDataSource(),
            new NoopChatModel(),
            List.of()
    );

    @Test
    void acceptsReadOnlyAggregationOnWhitelistedTable() {
        assertThatCode(() -> retriever.validate(
                "SELECT status, COUNT(*) AS total FROM knowledge_document GROUP BY status LIMIT 100"
        )).doesNotThrowAnyException();
    }

    private static class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("not used");
        }

        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not used"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @Test
    void rejectsSensitiveTableSelectAllAndMultipleStatements() {
        assertThatThrownBy(() -> retriever.validate("SELECT username FROM admin_user"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.validate("SELECT * FROM knowledge_document"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.validate(
                "SELECT title FROM knowledge_document; SELECT title FROM knowledge_document;"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static class NoopChatModel implements ChatModel {
        @Override
        public dev.langchain4j.model.chat.response.ChatResponse doChat(ChatRequest request) {
            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(AiMessage.from("SELECT COUNT(*) FROM knowledge_document"))
                    .build();
        }
    }
}
