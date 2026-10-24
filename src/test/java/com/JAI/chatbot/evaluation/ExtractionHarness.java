package com.JAI.chatbot.evaluation;

import com.JAI.chatbot.quality.*;
import com.JAI.chatbot.service.*;
import com.JAI.chatbot.mapper.ChatbotMapper;
import com.JAI.chatbot.controller.dto.*;
import com.JAI.chatbot.controller.dto.response.*;
import com.JAI.chatbot.evaluation.baseline.*;
import com.JAI.config.JacksonConfig;
import com.fasterxml.jackson.databind.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.*;
import java.util.*;

/** Synthetic evaluation only. Standard input receives input/date/timezone/mode, NEVER labels. */
public class ExtractionHarness {
    static final ObjectMapper JSON = new JacksonConfig().objectMapper();
    public static void main(String[] args) throws Exception {
        JsonNode request = JSON.readTree(System.in);
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("OPENAI_API_KEY missing: real LLM evaluation NOT_RUN");
        String model = Optional.ofNullable(System.getenv("EVAL_MODEL")).orElse("gpt-4o-mini-2024-07-18");
        String input = request.get("user_input").asText();
        ZoneId zone = ZoneId.of(request.get("timezone").asText());
        Clock clock = Clock.fixed(LocalDate.parse(request.get("reference_date").asText()).atStartOfDay(zone).toInstant(),zone);
        var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(2000); factory.setReadTimeout(8000);
        var http=new RestTemplate(factory);
        http.getInterceptors().add((r,b,e)->{r.getHeaders().setBearerAuth(key);return e.execute(r,b);});
        String mode=request.path("mode").asText("candidate");
        long started=System.nanoTime();
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("model",model); out.put("prompt_version",mode.equals("baseline")?"cb3a501-controlled-clock":QualityPrompts.VERSION);
        out.put("mode",mode); out.put("cost",null); out.put("usage",null); out.put("intent",null);
        out.put("items",List.of()); out.put("schema_valid",null); out.put("business_valid",false);
        List<String> raw=new ArrayList<>();
        CallBudget[] observed=new CallBudget[1];
        int[] baselineCalls={0};
        CallBudget baselineUsage=new CallBudget(2,Duration.ofSeconds(40));
        try {
            if (mode.equals("baseline")) {
                String text=legacyCall(http,model,input+LegacyConstants.INTENTION,baselineCalls,baselineUsage);
                String intent=switch(text) {case "계획 추천"->"PLAN_RECOMMENDATION";case "일정 자동 기입"->"EVENT";case "투두 자동 기입"->"TODO";default->"EXCEPTION";};
                out.put("intent",intent);
                if (intent.equals("EXCEPTION")) throw new QualityException(QualityError.AMBIGUOUS_INPUT,"intent");
                String content=legacyCall(http,model,input+"오늘은 "+LocalDateTime.now(clock)+"이야"+(intent.equals("EVENT")?LegacyConstants.EVENT:LegacyConstants.TODO),baselineCalls,baselineUsage);
                raw.add(content);
                var response=ChatGPTRespDTO.builder().choices(List.of(ChatGPTChoiceDTO.builder().message(new ChatGPTMessageDTO("assistant",content)).build())).build();
                var legacy=new LegacyMapper(JSON);
                Object items=intent.equals("EVENT")?legacy.toChatbotEventResp(response):legacy.toChatbotTodoResp(response);
                out.put("items",items); out.put("status","READY");
                try {
                    if(intent.equals("EVENT")) BusinessValidator.events(legacy.toChatbotEventResp(response));
                    else BusinessValidator.todos(legacy.toChatbotTodoResp(response));
                    out.put("business_valid",true);
                } catch(RuntimeException ignored) { }
                // Schema validity is measured independently from permissive baseline mapper behavior.
                try { var node=JSON.readTree(content); ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("status","READY"); new ChatbotMapper(JSON).parse(node.toString(),intent); out.put("schema_valid",true); } catch(RuntimeException | java.io.IOException ignored) { }
            } else {
                var transport=new ChatGPTServiceImpl(http);
                ReflectionTestUtils.setField(transport,"model",model);
                ReflectionTestUtils.setField(transport,"apiURL","https://api.openai.com/v1/chat/completions");
                ChatGPTService recorder=(messages,schema,budget)->{
                    observed[0]=budget;
                    String content=transport.complete(messages,schema,budget);
                    if(raw.isEmpty()) {
                        try { out.put("intent",JSON.readTree(content).path("intent").asText()); } catch(Exception ignored) { }
                    }
                    raw.add(content); return content;
                };
                var result=new ExtractionService(recorder,new ChatbotMapper(JSON),JSON,clock).extract(input,null);
                out.put("intent",result.intent()); out.put("items",result.events()!=null?result.events():result.todos());
                out.put("status","READY"); out.put("schema_valid",true); out.put("business_valid",true);
                out.put("initial_error",result.initialError()); out.put("regenerations",result.regenerations());
            }
        } catch(QualityException e) {
            out.put("status",e.code().name()); out.put("error_category",e.code().name());
            if (Set.of(QualityError.AMBIGUOUS_INPUT,QualityError.UNSUPPORTED_RECURRENCE).contains(e.code())) out.put("business_valid",true);
        } catch(Exception e) { out.put("status","PIPELINE_FAILURE"); out.put("error_category",e instanceof org.springframework.web.client.RestClientException?"EVAL_INFRASTRUCTURE":"SCHEMA_INVALID"); }
        int first=mode.equals("baseline")?0:1;
        if(raw.size()>first) {
            out.put("schema_valid",false);
            try {
                var node=JSON.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw.get(first));
                out.put("raw_output",node);
                var check=node.deepCopy();
                if(mode.equals("baseline") && check.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode)check).put("status","READY");
                out.put("raw_schema_valid",SchemaCheck.valid(check,OutputSchemas.extraction(String.valueOf(out.get("intent"))),JSON));
                var last=JSON.readTree(raw.get(raw.size()-1));
                if(mode.equals("baseline") && last.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode)last).put("status","READY");
                out.put("schema_valid",SchemaCheck.valid(last,OutputSchemas.extraction(String.valueOf(out.get("intent"))),JSON));
                try {
                    var contract=new ChatbotMapper(JSON);
                    String intent=String.valueOf(out.get("intent"));
                    if("EVENT".equals(intent)) BusinessValidator.events(contract.events(check.toString()));
                    else BusinessValidator.todos(contract.todos(check.toString()));
                    out.put("raw_business_valid",true);
                } catch(QualityException e) {
                    out.put("raw_business_valid",e.code()==QualityError.AMBIGUOUS_INPUT || e.code()==QualityError.UNSUPPORTED_RECURRENCE);
                }
            } catch(Exception e) { out.put("raw_schema_valid",false); }
        }
        CallBudget measured=observed[0]==null?baselineUsage:observed[0];
        out.put("usage",measured.usage());
        if (measured.initialError()!=null) out.put("initial_error",measured.initialError());
        // Cost is measured only with caller-supplied prices and complete token accounting.
        String inputPrice=System.getenv("EVAL_INPUT_USD_PER_MILLION"),outputPrice=System.getenv("EVAL_OUTPUT_USD_PER_MILLION");
        if (inputPrice!=null && outputPrice!=null && Boolean.TRUE.equals(measured.usage().get("complete"))) {
            double cost=(((Number)measured.usage().get("prompt_tokens")).longValue()*Double.parseDouble(inputPrice)
                +((Number)measured.usage().get("completion_tokens")).longValue()*Double.parseDouble(outputPrice))/1_000_000;
            out.put("cost",cost);
        }
        out.put("calls",observed[0]==null?baselineCalls[0]:observed[0].calls());
        out.put("latency_ms",(System.nanoTime()-started)/1_000_000);
        System.out.println(JSON.writeValueAsString(out));
    }
    static String legacyCall(RestTemplate http,String model,String prompt,int[] calls,CallBudget usage) {
        calls[0]++; usage.acquire();
        var response=http.postForObject("https://api.openai.com/v1/chat/completions",Map.of("model",model,"temperature",0,"max_completion_tokens",4096,
                "messages",List.of(Map.of("role","user","content",prompt))),ChatGPTRespDTO.class);
        usage.usage(response.getUsage());
        return response.getChoices().get(0).getMessage().getContent();
    }
}
