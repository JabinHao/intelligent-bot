package com.community.intelligentbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties
public class BotProperties {

    private List<BotConfig> bots;

    @Data
    public static class BotConfig {
        private String id;
        private String token;
        private String persona;
        private String knowledgePath;
    }
}
