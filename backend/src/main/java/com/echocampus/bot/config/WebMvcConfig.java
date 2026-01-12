package com.echocampus.bot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    public WebMvcConfig() {
        log.info("WebMvcConfig: JWT认证已由Spring Security处理");
    }
}
