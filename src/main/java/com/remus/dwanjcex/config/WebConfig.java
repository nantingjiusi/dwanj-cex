package com.remus.dwanjcex.config;

import com.remus.dwanjcex.config.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web相关配置，用于注册过滤器等。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 18:25
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(jwtAuthenticationFilter);

        // 配置过滤器应用的URL模式
        // 这里我们拦截所有API请求，除了登录和Swagger文档等公开路径
        registrationBean.addUrlPatterns("/api/*"); // 假设您的所有受保护API都在/api/下

        // 设置过滤器的顺序，可以根据需要调整
        registrationBean.setOrder(1);

        return registrationBean;
    }
}
