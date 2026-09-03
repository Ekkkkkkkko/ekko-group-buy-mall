package cn.ekko.groupchat.document.client;

/**
 * MinerU 任务查询结果，包含任务 ID、状态、结果 ZIP 下载地址、
 * 错误信息及 trace_id。
 */
public record MineruTaskResult(
        String taskId,
        MineruTaskState state,
        String fullZipUrl,
        String errorMessage,
        String traceId
) {
}
