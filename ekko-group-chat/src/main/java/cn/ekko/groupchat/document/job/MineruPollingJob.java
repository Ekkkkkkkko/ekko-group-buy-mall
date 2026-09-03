package cn.ekko.groupchat.document.job;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.entity.DocumentStatus;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.mapper.KnowledgeDocumentMapper;
import cn.ekko.groupchat.document.service.MineruTaskProcessor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MinerU 任务轮询定时作业。
 *
 * <p>按配置间隔扫描处于 PARSING 状态且已提交 MinerU 任务的文档
 * （按提交时间先进先出、批量取数），逐个交由 {@link MineruTaskProcessor} 处理；
 * 单个任务查询异常只记录日志，不中断整批轮询。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MineruPollingJob {

    private final KnowledgeDocumentMapper documentMapper;
    private final MineruTaskProcessor processor;
    private final GroupChatProperties properties;

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

    private LambdaQueryWrapper<KnowledgeDocument> taskQuery() {
        int limit = Math.max(1, properties.getMineru().getPollBatchSize());
        return Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getStatus, DocumentStatus.PARSING)
                .isNotNull(KnowledgeDocument::getMineruTaskId)
                .orderByAsc(KnowledgeDocument::getMineruSubmittedAt)
                .last("LIMIT " + limit);
    }
}
