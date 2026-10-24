package com.JAI.chatbot.mapper;

import com.JAI.chatbot.controller.dto.response.ChatGPTRespDTO;
import com.JAI.chatbot.controller.dto.response.ChatbotEventRespDTO;
import com.JAI.chatbot.controller.dto.response.ChatbotTodoRespDTO;
import com.JAI.event.DTO.request.PersonalEventCreateReqDTO;
import com.JAI.todo.controller.request.PersonalTodoCreateReq;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatbotMapper {

    private final ObjectMapper mapper;

    public List<ChatbotEventRespDTO> toChatbotEventResp(ChatGPTRespDTO response) {
        return events(com.JAI.chatbot.service.ChatGPTServiceImpl.content(response));
    }
    public List<ChatbotTodoRespDTO> toChatbotTodoResp(ChatGPTRespDTO response) {
        return todos(com.JAI.chatbot.service.ChatGPTServiceImpl.content(response));
    }
    public List<ChatbotEventRespDTO> events(String content) {
        var tree = parse(content, "EVENT");
        try {
            List<ChatbotEventRespDTO> items = mapper.convertValue(tree.get("events"), new TypeReference<>() {});
            com.JAI.chatbot.quality.BusinessValidator.events(items);
            return items;
        } catch (com.JAI.chatbot.quality.QualityException e) { throw e; }
        catch (IllegalArgumentException e) { throw invalid("events.date/time"); }
    }
    public List<ChatbotTodoRespDTO> todos(String content) {
        var tree = parse(content, "TODO");
        try {
            List<ChatbotTodoRespDTO> items = mapper.convertValue(tree.get("todos"), new TypeReference<>() {});
            com.JAI.chatbot.quality.BusinessValidator.todos(items);
            return items;
        } catch (com.JAI.chatbot.quality.QualityException e) { throw e; }
        catch (IllegalArgumentException e) { throw invalid("todos.date/time"); }
    }
    public com.fasterxml.jackson.databind.JsonNode parse(String content, String intent) {
        try {
            var tree = mapper.reader().with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(content);
            String key = "EVENT".equals(intent) ? "events" : "todos";
            if (tree == null || !tree.isObject() || tree.size() != 2 || !tree.has("status") || !tree.get("status").isTextual()
                    || !tree.has(key) || !tree.get(key).isArray()) throw invalid("root");
            String status = tree.get("status").asText();
            if (java.util.Set.of("AMBIGUOUS_INPUT", "UNSUPPORTED_RECURRENCE").contains(status)) {
                if (!tree.get(key).isEmpty()) throw invalid("status/items");
                throw new com.JAI.chatbot.quality.QualityException(com.JAI.chatbot.quality.QualityError.valueOf(status), "input");
            }
            if (!"READY".equals(status)) throw invalid("status");
            var fields = "EVENT".equals(intent) ? java.util.Set.of("title", "startDate", "endDate", "startTime", "endTime") : java.util.Set.of("title", "date", "time");
            for (var item : tree.get(key)) {
                if (!item.isObject() || item.size() != fields.size()) throw invalid("item.fields");
                for (String field : fields) {
                    var value = item.get(field);
                    if (value == null || !(value.isTextual() || (!field.equals("title") && value.isNull()))) throw invalid(field);
                    if (value.isTextual() && !field.equals("title")) {
                        String text = value.asText();
                        if (field.toLowerCase().contains("date")) {
                            if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid(field);
                            java.time.LocalDate.parse(text);
                        } else {
                            if (!text.matches("[0-9]{2}:[0-9]{2}:[0-9]{2}")) throw invalid(field);
                            java.time.LocalTime.parse(text);
                        }
                    }
                }
            }
            return tree;
        } catch (com.JAI.chatbot.quality.QualityException e) { throw e; }
        catch (Exception e) { throw invalid("json/date/time"); }
    }
    private static com.JAI.chatbot.quality.QualityException invalid(String field) {
        return new com.JAI.chatbot.quality.QualityException(com.JAI.chatbot.quality.QualityError.SCHEMA_INVALID, field);
    }

    public PersonalEventCreateReqDTO toPersonalEventCreateReqDTO (
            UUID categoryId, Boolean alarm,
            ChatbotEventRespDTO chatbotEventRespDTO
            ) {

        PersonalEventCreateReqDTO personalEventCreateReqDTO = PersonalEventCreateReqDTO.builder()
                .title(chatbotEventRespDTO.getTitle())
                .startDate(chatbotEventRespDTO.getStartDate())
                .endDate(chatbotEventRespDTO.getEndDate())
                .startTime(chatbotEventRespDTO.getStartTime())
                .endTime(chatbotEventRespDTO.getEndTime())
                .isAlarmed(alarm)
                .categoryId(categoryId)
                .build();

        return personalEventCreateReqDTO;
    }

    public PersonalTodoCreateReq toPersonalTodoCreateReq(ChatbotTodoRespDTO chatbotTodoRespDTO, UUID categoryId) {
        PersonalTodoCreateReq personalTodoCreateReq = PersonalTodoCreateReq.builder()
                .title(chatbotTodoRespDTO.getTitle())
                .date(chatbotTodoRespDTO.getDate())
                .startTime(chatbotTodoRespDTO.getTime())
                .categoryId(categoryId)
                .build();

        return personalTodoCreateReq;
    }

}
