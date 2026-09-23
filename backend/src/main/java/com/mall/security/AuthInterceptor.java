package com.mall.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthInterceptor implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/api/user/register",
                        "/api/product/**",
                        "/api/category/**",
                        "/api/review/product/**",
                        "/api/review/avg/**",
                        "/api/recommend/**",
                        "/api/ai/knowledge",
                        "/api/ai/models",
                        "/swagger-**",
                        "/v3/**",
                        "/webjars/**"
                );
    }
}
