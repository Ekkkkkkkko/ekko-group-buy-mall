package cn.ekko.config;

import cn.ekko.infrastructure.gateway.IGroupBuyMarketService;
import cn.ekko.infrastructure.gateway.IWeixinApiService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author Ekko
 * @description
 */
@Configuration
@Slf4j
public class Retrofit2Config {

    /**
     * 微信接口固定地址
     */
    private static final String WEIXIN_BASE_URL =
            "https://api.weixin.qq.com/";

    /**
     * 微信 HTTP 客户端
     */
    @Bean("weixinRetrofit")
    public Retrofit weixinRetrofit() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(WEIXIN_BASE_URL)
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
    }

    /**
     * 创建微信接口代理对象
     */
    @Bean
    public IWeixinApiService weixinApiService(
            @Qualifier("weixinRetrofit") Retrofit retrofit) {

        return retrofit.create(IWeixinApiService.class);
    }

    /**
     * 拼团营销 HTTP 客户端
     *
     * apiUrl 从商城配置文件读取。
     * 例如：http://127.0.0.1:8091/
     */
    @Bean("groupBuyMarketRetrofit")
    public Retrofit groupBuyMarketRetrofit(
            @Value("${group-buy-market.api-url}") String apiUrl) {

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(apiUrl)
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
    }

    /**
     * 创建拼团营销接口代理对象
     */
    @Bean
    public IGroupBuyMarketService groupBuyMarketService(
            @Qualifier("groupBuyMarketRetrofit") Retrofit retrofit) {

        return retrofit.create(IGroupBuyMarketService.class);
    }
}