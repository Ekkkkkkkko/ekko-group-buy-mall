package cn.ekko.groupchat.config;

import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/** 本地重排模型装配；仅在显式启用且外部模型文件有效时加载 ONNX。 */
@Configuration
public class RetrievalConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "group-chat.retrieval.rerank", name = "enabled", havingValue = "true")
    ScoringModel scoringModel(GroupChatProperties properties) {
        GroupChatProperties.Rerank rerank = properties.getRetrieval().getRerank();
        Path modelPath = requiredFile(rerank.getModelPath(), "model-path");
        Path tokenizerPath = requiredFile(rerank.getTokenizerPath(), "tokenizer-path");
        return new OnnxScoringModel(
                modelPath.toString(),
                tokenizerPath.toString(),
                rerank.getMaxTokens()
        );
    }

    private Path requiredFile(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "启用 BGE 重排时必须配置 group-chat.retrieval.rerank." + propertyName
            );
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("BGE 重排文件不存在: " + path);
        }
        return path;
    }
}
