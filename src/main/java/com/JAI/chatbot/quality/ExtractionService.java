package com.JAI.chatbot.quality;
import com.JAI.chatbot.service.ChatGPTService;
import com.JAI.chatbot.mapper.ChatbotMapper;
import com.JAI.chatbot.controller.dto.ChatGPTMessageDTO;
import com.JAI.chatbot.controller.dto.response.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExtractionService {
    private final ChatGPTService client;
    private final ChatbotMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    public record Result(String intent, List<ChatbotEventRespDTO> events, List<ChatbotTodoRespDTO> todos,
                         int calls, int regenerations, String initialError) { }
    public String classify(String input, CallBudget budget) {
        String raw = client.complete(QualityPrompts.intent(input), OutputSchemas.intent(), budget);
        try {
            var node = objectMapper.reader().with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
            if (node == null || !node.isObject() || node.size() != 1 || !node.has("intent") || !node.get("intent").isTextual()
                || !Set.of("EVENT", "TODO", "PLAN_RECOMMENDATION", "EXCEPTION").contains(node.get("intent").asText()))
                throw new IllegalArgumentException();
            return node.get("intent").asText();
        } catch (Exception e) { throw new QualityException(QualityError.SCHEMA_INVALID, "intent"); }
    }
    public Result extract(String input, String preferredIntent) {
        return extract(input, preferredIntent, clock);
    }
    public Result extract(String input, String preferredIntent, Clock reference) {
        if (input == null || input.isBlank() || input.length() > 10000) throw new QualityException(QualityError.AMBIGUOUS_INPUT, "prompt");
        // One monotonic budget shared by classification, transient retries and output regeneration.
        var budget = new CallBudget(5, Duration.ofSeconds(40));
        Clock requestClock = Clock.fixed(reference.instant(), reference.getZone());
        String classified = classify(input, budget);
        if ("EXCEPTION".equals(classified)) throw new QualityException(QualityError.AMBIGUOUS_INPUT, "intent");
        if (preferredIntent != null && !preferredIntent.equals(classified)) throw new QualityException(QualityError.AMBIGUOUS_INPUT, "intent conflicts with request");
        return generate(input, classified, requestClock, budget);
    }
    public Result generate(String input, String intent, Clock reference, CallBudget budget) {
        InputPolicy.validate(input);
        var messages = new ArrayList<>(QualityPrompts.extraction(input, intent, reference));
        String initialError = null;
        for (int attempt = 0; ; attempt++) {
            String raw = client.complete(messages, OutputSchemas.extraction(intent), budget);
            try {
                if ("EVENT".equals(intent)) {
                    var events = mapper.events(raw);
                    RelativeDateGuard.validate(input, events.stream().map(ChatbotEventRespDTO::getStartDate).toList(), reference);
                    return new Result(intent, events, null, budget.calls(), attempt, initialError);
                }
                var todos = mapper.todos(raw);
                if (!"PLAN_RECOMMENDATION".equals(intent)) RelativeDateGuard.validate(input, todos.stream().map(ChatbotTodoRespDTO::getDate).toList(), reference);
                return new Result(intent, null, todos, budget.calls(), attempt, initialError);
            } catch (QualityException e) {
                if (e.code() != QualityError.AMBIGUOUS_INPUT && e.code() != QualityError.UNSUPPORTED_RECURRENCE) budget.failure(e.code());
                if (attempt == 1 || !e.repairable()) throw e;
                initialError = e.code().name();
                // Only sanitized validator metadata; do not echo potentially hostile generated text.
                messages.add(new ChatGPTMessageDTO("system", "Previous output failed " + e.code() + " at " + e.field()
                        + ". Re-extract from the original input. If contradictory or missing information, return AMBIGUOUS_INPUT and empty items."));
            }
        }
    }
}
