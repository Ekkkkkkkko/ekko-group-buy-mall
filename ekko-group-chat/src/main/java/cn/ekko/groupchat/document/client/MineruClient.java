package cn.ekko.groupchat.document.client;

import cn.ekko.groupchat.config.GroupChatProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * MinerU 文档解析 API 客户端，提供创建解析任务与查询任务状态两个操作。
 *
 * <p>对响应做严格校验：业务码非 0、缺少关键字段、完成但无下载地址等情况
 * 均抛异常，并在消息中携带 trace_id 便于排查。
 */
@Component
public class MineruClient {

    private final RestClient restClient;
    private final GroupChatProperties properties;

    public MineruClient(
            @Qualifier("mineruRestClient") RestClient restClient,
            GroupChatProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public String createTask(String sourceUrl, String dataId) {
        GroupChatProperties.Mineru mineru = properties.getMineru();
        JsonNode response = restClient.post()
                .uri(mineru.getCreateTaskPath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTaskRequest(
                        sourceUrl,
                        mineru.getModelVersion(),
                        mineru.getLanguage(),
                        mineru.isFormulaEnabled(),
                        mineru.isTableEnabled(),
                        mineru.isOcrEnabled(),
                        dataId
                ))
                .retrieve()
                .body(JsonNode.class);
        JsonNode data = successfulData(response);
        return requiredText(data, "task_id", traceId(response));
    }

    public MineruTaskResult queryTask(String taskId) {
        GroupChatProperties.Mineru mineru = properties.getMineru();
        JsonNode response = restClient.get()
                .uri(mineru.getTaskResultPath(), taskId)
                .retrieve()
                .body(JsonNode.class);
        String traceId = traceId(response);
        JsonNode data = successfulData(response);
        String responseTaskId = requiredText(data, "task_id", traceId);
        MineruTaskState state = MineruTaskState.fromProvider(
                requiredText(data, "state", traceId), traceId
        );
        String fullZipUrl = optionalText(data, "full_zip_url");
        if (state == MineruTaskState.DONE && !StringUtils.hasText(fullZipUrl)) {
            throw new IllegalStateException("MinerU 完成响应缺少 full_zip_url, trace_id=" + traceId);
        }
        String errorMessage = optionalText(data, "err_msg");
        if (state == MineruTaskState.FAILED && !StringUtils.hasText(errorMessage)) {
            errorMessage = "MinerU 解析失败";
        }
        return new MineruTaskResult(responseTaskId, state, fullZipUrl, errorMessage, traceId);
    }

    private JsonNode successfulData(JsonNode response) {
        if (response == null || !response.isObject()) {
            throw new IllegalStateException("MinerU 返回空响应");
        }
        String traceId = traceId(response);
        if (response.path("code").asInt(Integer.MIN_VALUE) != 0) {
            String message = response.path("msg").asText("MinerU 请求失败");
            throw new IllegalStateException(message + ", trace_id=" + traceId);
        }
        JsonNode data = response.get("data");
        if (data == null || !data.isObject()) {
            throw new IllegalStateException("MinerU 响应缺少 data, trace_id=" + traceId);
        }
        return data;
    }

    private String requiredText(JsonNode node, String field, String traceId) {
        String value = optionalText(node, field);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("MinerU 响应缺少 " + field + ", trace_id=" + traceId);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String traceId(JsonNode response) {
        if (response == null) {
            return "unknown";
        }
        String value = response.path("trace_id").asText("");
        return StringUtils.hasText(value) ? value : "unknown";
    }

    private record CreateTaskRequest(
            String url,
            @JsonProperty("model_version") String modelVersion,
            String language,
            @JsonProperty("enable_formula") boolean enableFormula,
            @JsonProperty("enable_table") boolean enableTable,
            @JsonProperty("is_ocr") boolean ocr,
            @JsonProperty("data_id") String dataId
    ) {
    }
}
