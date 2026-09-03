package cn.ekko.groupchat.document.job;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 未接入 XXL-Job 调度中心时，开发环境仍按相同间隔执行补偿。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "group-chat.xxl-job",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DocumentCompensationScheduler {

    private final DocumentCompensationJob compensationJob;

    @Scheduled(fixedDelayString = "${group-chat.pipeline.compensation-interval:1m}")
    public void compensate() {
        compensationJob.compensateAll();
    }
}
