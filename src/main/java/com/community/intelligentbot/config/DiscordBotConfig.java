package com.community.intelligentbot.config;

import com.community.intelligentbot.listener.MessageListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordBotConfig {

    @Value("${discord.bot.token}")
    private String botToken;

    @Bean
    public JDA jda(MessageListener messageListener) throws InterruptedException {
        return JDABuilder.createDefault(botToken,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(messageListener)
                .build()
                .awaitReady();
    }
}
