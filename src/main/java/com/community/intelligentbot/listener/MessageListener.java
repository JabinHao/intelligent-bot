package com.community.intelligentbot.listener;

import com.community.intelligentbot.service.AssistantService;
import com.community.intelligentbot.service.ChatHistoryEmbeddingService;
import dev.langchain4j.guardrail.InputGuardrailException;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class MessageListener extends ListenerAdapter {

    private static final int DISCORD_MAX_LENGTH = 2000;
    private static final long EDIT_INTERVAL_MS = 1000;

    private final AssistantService assistantService;
    private final String botId;
    private final ChatHistoryEmbeddingService chatHistoryEmbeddingService;

    public MessageListener(AssistantService assistantService, String botId,
                           ChatHistoryEmbeddingService chatHistoryEmbeddingService) {
        this.assistantService = assistantService;
        this.botId = botId;
        this.chatHistoryEmbeddingService = chatHistoryEmbeddingService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentRaw();

        // Ignore @everyone and @here mentions
        if (message.getMentions().mentionsEveryone()) {
            return;
        }

        // Only respond when the bot is specifically mentioned or in DMs
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

        // Sandbox memory by bot + context
        String userId = event.getAuthor().getId();
        String memoryId = isDM
                ? botId + ":" + userId + ":dm"
                : botId + ":" + userId + ":guild:" + event.getGuild().getId();

        // Show typing indicator immediately
        event.getChannel().sendTyping().queue();

        try {
            streamResponse(event, memoryId, userMessage);
        } catch (InputGuardrailException e) {
            event.getChannel().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            log.error("[{}] Error processing message from user {}: {}", botId, userId, e.getMessage(), e);
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

                    if (currentText.length() > DISCORD_MAX_LENGTH) {
                        currentText = currentText.substring(0, DISCORD_MAX_LENGTH);
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastEditTime.get() < EDIT_INTERVAL_MS) {
                        return;
                    }
                    lastEditTime.set(now);

                    if (sentMessage.get() == null) {
                        Message msg = event.getChannel().sendMessage(currentText).complete();
                        sentMessage.set(msg);
                    } else {
                        sentMessage.get().editMessage(currentText).queue();
                    }
                })
                .onCompleteResponse(response -> {
                    String finalText = buffer.toString();
                    if (sentMessage.get() == null) {
                        sendResponse(event, finalText);
                    } else if (finalText.length() <= DISCORD_MAX_LENGTH) {
                        sentMessage.get().editMessage(finalText).queue();
                    } else {
                        sentMessage.get().editMessage(finalText.substring(0, DISCORD_MAX_LENGTH)).queue();
                        sendResponse(event, finalText.substring(DISCORD_MAX_LENGTH));
                    }

                    CompletableFuture.runAsync(() -> {
                        try {
                            chatHistoryEmbeddingService.embedConversationTurn(memoryId, userMessage, finalText);
                        } catch (Exception e) {
                            log.error("[{}] Failed to embed conversation turn: {}", botId, e.getMessage(), e);
                        }
                    });
                })
                .onError(error -> {
                    log.error("[{}] Streaming error: {}", botId, error.getMessage(), error);
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
