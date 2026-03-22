package com.community.intelligentbot.listener;

import com.community.intelligentbot.service.AssistantService;
import dev.langchain4j.guardrail.InputGuardrailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener extends ListenerAdapter {

    private static final int DISCORD_MAX_LENGTH = 2000;
    private static final long EDIT_INTERVAL_MS = 1000; // Rate-limit message edits

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

        // Show typing indicator immediately
        event.getChannel().sendTyping().queue();

        try {
            streamResponse(event, memoryId, userMessage);
        } catch (InputGuardrailException e) {
            event.getChannel().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Error processing message from user {}: {}", userId, e.getMessage(), e);
            event.getChannel().sendMessage("Sorry, something went wrong. Please try again later.").queue();
        }
    }

    private void streamResponse(MessageReceivedEvent event, String memoryId, String userMessage) {
        StringBuilder buffer = new StringBuilder();
        AtomicReference<Message> sentMessage = new AtomicReference<>();
        AtomicLong lastEditTime = new AtomicLong(0);

        assistantService.chatStream(memoryId, userMessage)
                .onPartialResponse(token -> {
                    buffer.append(token);
                    String currentText = buffer.toString();

                    // Truncate if exceeding Discord limit
                    if (currentText.length() > DISCORD_MAX_LENGTH) {
                        currentText = currentText.substring(0, DISCORD_MAX_LENGTH);
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastEditTime.get() < EDIT_INTERVAL_MS) {
                        return;
                    }
                    lastEditTime.set(now);

                    if (sentMessage.get() == null) {
                        // Send initial message
                        Message msg = event.getChannel().sendMessage(currentText).complete();
                        sentMessage.set(msg);
                    } else {
                        // Edit existing message with accumulated content
                        sentMessage.get().editMessage(currentText).queue();
                    }
                })
                .onCompleteResponse(response -> {
                    String finalText = buffer.toString();
                    if (sentMessage.get() == null) {
                        // Never sent a message (very short response)
                        sendResponse(event, finalText);
                    } else if (finalText.length() <= DISCORD_MAX_LENGTH) {
                        // Final edit with complete content
                        sentMessage.get().editMessage(finalText).queue();
                    } else {
                        // Response exceeded limit — edit first chunk, send overflow
                        sentMessage.get().editMessage(finalText.substring(0, DISCORD_MAX_LENGTH)).queue();
                        sendResponse(event, finalText.substring(DISCORD_MAX_LENGTH));
                    }
                })
                .onError(error -> {
                    log.error("Streaming error: {}", error.getMessage(), error);
                    if (sentMessage.get() == null) {
                        event.getChannel().sendMessage("Sorry, something went wrong. Please try again later.").queue();
                    }
                })
                .start();
    }

    private void sendResponse(MessageReceivedEvent event, String response) {
        int start = 0;
        while (start < response.length()) {
            int end = Math.min(start + DISCORD_MAX_LENGTH, response.length());
            event.getChannel().sendMessage(response.substring(start, end)).queue();
            start = end;
        }
    }
}
