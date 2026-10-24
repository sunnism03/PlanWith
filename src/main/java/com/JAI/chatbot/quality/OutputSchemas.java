package com.JAI.chatbot.quality;
import java.util.*;
public final class OutputSchemas {
    private OutputSchemas() { }
    public static Map<String,Object> object(Map<String,Object> fields) {
        return Map.of("type", "object", "properties", fields, "required", new ArrayList<>(fields.keySet()), "additionalProperties", false);
    }
    private static Map<String,Object> nullable() { return Map.of("type", List.of("string", "null")); }
    public static Map<String,Object> intent() {
        return object(Map.of("intent", Map.of("type", "string", "enum", List.of("EVENT", "TODO", "PLAN_RECOMMENDATION", "EXCEPTION"))));
    }
    public static Map<String,Object> extraction(String intent) {
        boolean event = "EVENT".equals(intent);
        Map<String,Object> item = new LinkedHashMap<>();
        item.put("title", Map.of("type", "string"));
        for (String f : event ? List.of("startDate", "startTime", "endDate", "endTime") : List.of("date", "time")) item.put(f, nullable());
        return object(Map.of("status", Map.of("type", "string", "enum", List.of("READY", "AMBIGUOUS_INPUT", "UNSUPPORTED_RECURRENCE")),
                event ? "events" : "todos", Map.of("type", "array", "items", object(item))));
    }
}
