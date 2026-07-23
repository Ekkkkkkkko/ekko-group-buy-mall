package cn.ekko.api;

import cn.ekko.api.response.Response;

/**
 * @author Ekko
 * @description DCC 动态配置中心
 */
public interface IDCCService {

    Response<Boolean> updateConfig(String key, String value);

}
