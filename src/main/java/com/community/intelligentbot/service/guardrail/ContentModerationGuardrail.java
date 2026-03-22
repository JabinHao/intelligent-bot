package com.community.intelligentbot.service.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContentModerationGuardrail implements InputGuardrail {

    private static final List<String> BLOCKED_PATTERNS = List.of(
            "kill yourself", "kys",
            "hate speech", "racial slur",
            "bomb threat", "shoot up"
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText().toLowerCase();

        for (String pattern : BLOCKED_PATTERNS) {
            if (text.contains(pattern)) {
                return failure("Your message was flagged for inappropriate content. Please keep the conversation respectful.");
            }
        }

        return success();
    }
}
