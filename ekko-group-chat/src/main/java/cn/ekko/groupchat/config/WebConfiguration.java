package cn.ekko.groupchat.config;

import cn.ekko.groupchat.auth.interceptor.AdminAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置。
 *
 * <p>两项职责：
 * <ol>
 *   <li>为 {@code /api/**} 配置跨域，允许的来源由 {@link GroupChatProperties.Web} 提供；</li>
 *   <li>注册 {@link AdminAuthInterceptor}，拦截文档管理接口 {@code /api/v1/documents/**} 做管理员鉴权。</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {

    private final GroupChatProperties properties;
    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = properties.getWeb().getAllowedOrigins();
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/v1/documents/**");
    }
}
