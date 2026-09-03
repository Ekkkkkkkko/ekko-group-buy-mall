package cn.ekko.groupchat.document.service.chunk;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 文档切分策略类型。
 */
public enum ChunkingStrategyType {
    /** 标题父子切分，overlap 自动取 chunkSize 的 10%。 */
    SMART,
    /** 按 Markdown 标题组织章节，超长章节生成父子分片。 */
    TITLE,
    /** 按配置长度和重叠窗口切分。 */
    LENGTH,
    /** 按固定分隔符切分，超长部分再按长度切分。 */
    SEPARATOR,
    /** 按正则表达式切分，超长部分再按长度切分。 */
    REGEX,
    /** 超长标题段切为一组兄弟分片，检索命中后按序补全整组。 */
    BROTHER,
    /** Excel/CSV 键值行切片，保证同一行不被拆分。 */
    EXCEL;

    public static ChunkingStrategyType from(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : SMART.name();
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的切分策略: " + value, exception);
        }
    }
}
