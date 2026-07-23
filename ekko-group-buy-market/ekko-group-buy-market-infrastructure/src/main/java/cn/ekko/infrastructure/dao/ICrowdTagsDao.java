package cn.ekko.infrastructure.dao;

import cn.ekko.infrastructure.dao.po.CrowdTags;
import cn.ekko.infrastructure.dao.po.CrowdTagsJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Ekko
 * @description 人群标签
 */
@Mapper
public interface ICrowdTagsDao {

    void updateCrowdTagsStatistics(CrowdTags crowdTagsReq);

}
