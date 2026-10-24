package com.JAI.chatbot.quality;
import com.fasterxml.jackson.databind.*;
import java.util.Map;
/** Validates the small JSON Schema subset emitted by OutputSchemas (not semantic domain rules). */
public final class SchemaCheck {
    private SchemaCheck() { }
    public static boolean valid(JsonNode value, Map<String,Object> schema, ObjectMapper mapper) {
        return valid(value,mapper.valueToTree(schema));
    }
    private static boolean valid(JsonNode value,JsonNode schema) {
        if(value==null)return false;
        JsonNode type=schema.get("type");
        boolean typeOk=false;
        if(type.isArray()){for(var t:type)typeOk|=type(value,t.asText());}else typeOk=type(value,type.asText());
        if(!typeOk)return false;
        if(schema.has("enum")){boolean found=false;for(var allowed:schema.get("enum"))found|=allowed.equals(value);if(!found)return false;}
        if(value.isObject()) {
            var props=schema.get("properties");
            for(var required:schema.get("required"))if(!value.has(required.asText()))return false;
            var names=value.fieldNames();while(names.hasNext()){String name=names.next();if(!props.has(name)||!valid(value.get(name),props.get(name)))return false;}
        }
        if(value.isArray())for(var item:value)if(!valid(item,schema.get("items")))return false;
        return true;
    }
    private static boolean type(JsonNode v,String type) {
        return switch(type){case "null"->v.isNull();case "string"->v.isTextual();case "array"->v.isArray();case "object"->v.isObject();default->false;};
    }
}
