package com.community.intelligentbot.listener;

import com.community.intelligentbot.service.AssistantService;
import dev.langchain4j.guardrail.InputGuardrailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener extends ListenerAdapter {

    private static final int DISCORD_MAX_LENGTH = 2000;

    private final AssistantService assistantService;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentRaw();

        // Only respond when the bot is mentioned or in DMs
        boolean isMentioned = message.getMentions().isMentioned(event.getJDA().getSelfUser());
        boolean isDM = !event.isFromGuild();

        if (!isMentioned && !isDM) {
            return;
        }

        // Remove the bot mention from the message
        String userMessage = content.replaceAll("<@!?\\d+>", "").trim();
        if (userMessage.isEmpty()) {
            return;
        }

        // Sandbox memory by context: DM conversations are isolated from guild channels
        String userId = event.getAuthor().getId();
        String memoryId = isDM
                ? userId + ":dm"
                : userId + ":guild:" + event.getGuild().getId();

        try {
            String response = assistantService.chat(memoryId, userMessage);
            sendResponse(event, response);
        } catch (InputGuardrailException e) {
            event.getChannel().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Error processing message from user {}: {}", userId, e.getMessage(), e);
            event.getChannel().sendMessage("Sorry, something went wrong. Please try again later.").queue();
        }
    }

    private void sendResponse(MessageReceivedEvent event, String response) {
        if (response.length() <= DISCORD_MAX_LENGTH) {
            event.getChannel().sendMessage(response).queue();
            return;
        }

        // Split long responses into chunks
        int start = 0;
        while (start < response.length()) {
            int end = Math.min(start + DISCORD_MAX_LENGTH, response.length());
            event.getChannel().sendMessage(response.substring(start, end)).queue();
            start = end;
        }
    }
}
