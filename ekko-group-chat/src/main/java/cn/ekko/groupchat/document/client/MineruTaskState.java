package cn.ekko.groupchat.document.client;

import java.util.Locale;

/**
 * MinerU 解析任务状态枚举，涵盖待处理、进行中、转换中、完成、失败，
 * 并提供从服务商返回字符串安全解析的工厂方法。
 */
public enum MineruTaskState {
    PENDING,
    RUNNING,
    CONVERTING,
    DONE,
    FAILED;

    static MineruTaskState fromProvider(String value, String traceId) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("MinerU 响应缺少 state, trace_id=" + traceId);
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "未知 MinerU 任务状态: " + value + ", trace_id=" + traceId,
                    exception
            );
        }
    }
}
