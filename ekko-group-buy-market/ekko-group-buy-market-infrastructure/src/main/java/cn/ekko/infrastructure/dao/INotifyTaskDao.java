package cn.ekko.infrastructure.dao;

import cn.ekko.domain.trade.model.entity.NotifyTaskEntity;
import cn.ekko.infrastructure.dao.po.NotifyTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author Ekko
 * @description 回调任务
 */
@Mapper
public interface INotifyTaskDao {

    void insert(NotifyTask notifyTask);

    List<NotifyTask> queryUnExecutedNotifyTaskList();

    List<NotifyTask> queryUnExecutedNotifyTaskByTeamId(String teamId);

    int claimNotifyTask(NotifyTask notifyTask);

    int updateNotifyTaskStatusSuccess(NotifyTask notifyTask);

    int updateNotifyTaskStatusError(NotifyTask notifyTask);

    int updateNotifyTaskStatusRetry(NotifyTask notifyTask);

}
