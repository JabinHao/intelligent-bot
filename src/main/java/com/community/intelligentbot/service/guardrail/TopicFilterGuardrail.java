package com.community.intelligentbot.service.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class TopicFilterGuardrail implements InputGuardrail {

    private static final String REJECTION_MESSAGE =
            "I'm GameBot, a game community assistant. I can only help with game-related questions. " +
            "Try asking about game rules, strategies, guides, or patch notes!";

    // Off-topic patterns that clearly indicate non-game requests
    private static final List<Pattern> OFF_TOPIC_PATTERNS = List.of(
            Pattern.compile("write.*(essay|paper|report|article)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(help|do).*(homework|assignment|exam)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(code|program|debug|implement).*(python|java|javascript|sql|html)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(stock|invest|crypto|bitcoin|trading)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(recipe|cook|bake|ingredient)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(medical|diagnos|symptom|prescri)", Pattern.CASE_INSENSITIVE)
    );

    // Always allow: greetings, identity questions, short messages
    private static final List<Pattern> ALWAYS_ALLOW_PATTERNS = List.of(
            Pattern.compile("^(hi|hello|hey|yo|sup|thanks|thank you|bye|gm|gn)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("who (are|r) (you|u)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("what can you do", Pattern.CASE_INSENSITIVE),
            Pattern.compile("help$", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText().trim();

        // Allow short messages (likely greetings or simple queries)
        if (text.length() < 10) {
            return success();
        }

        // Allow explicitly safe patterns
        for (Pattern pattern : ALWAYS_ALLOW_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return success();
            }
        }

        // Reject clearly off-topic messages
        for (Pattern pattern : OFF_TOPIC_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return failure(REJECTION_MESSAGE);
            }
        }

        // Default: allow — the LLM + system message will handle edge cases
        return success();
    }
}
