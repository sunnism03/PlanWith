package com.JAI.chatbot.quality;
import com.JAI.chatbot.controller.dto.response.*;
import java.util.List;
import static com.JAI.chatbot.quality.QualityError.*;
/** Shared at generation, READY publication and the transactional approval boundary. */
public final class BusinessValidator {
    private BusinessValidator() { }
    private static void title(String title) {
        if (title == null || title.isBlank()) throw new QualityException(REQUIRED_FIELD_MISSING, "title");
        if (title.length() > 255) throw new QualityException(SCHEMA_INVALID, "title.length");
    }
    public static void events(List<ChatbotEventRespDTO> items) {
        size(items);
        for (var e : items) {
            if (e == null) throw new QualityException(SCHEMA_INVALID, "events.item");
            title(e.getTitle());
            if (e.getStartDate() == null || e.getEndDate() == null)
                throw new QualityException(AMBIGUOUS_INPUT, "startDate/endDate");
            if (e.getStartDate().isAfter(e.getEndDate()) || (e.getStartDate().equals(e.getEndDate())
                && e.getStartTime() != null && e.getEndTime() != null && e.getStartTime().isAfter(e.getEndTime())))
                throw new QualityException(INVALID_DATE_RANGE, "start/end");
            if (e.getStartTime() == null && e.getEndTime() != null)
                throw new QualityException(AMBIGUOUS_INPUT, "startTime");
        }
    }
    public static void todos(List<ChatbotTodoRespDTO> items) {
        size(items);
        for (var t : items) {
            if (t == null) throw new QualityException(SCHEMA_INVALID, "todos.item");
            title(t.getTitle());
            if (t.getDate() == null) throw new QualityException(AMBIGUOUS_INPUT, "date");
        }
    }
    private static void size(List<?> items) {
        if (items == null || items.isEmpty() || items.size() > 100)
            throw new QualityException(REQUIRED_FIELD_MISSING, "items(1..100)");
    }
}
