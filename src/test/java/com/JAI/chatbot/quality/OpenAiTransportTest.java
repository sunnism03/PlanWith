package com.JAI.chatbot.quality;
import com.JAI.chatbot.service.ChatGPTServiceImpl;
import com.JAI.chatbot.controller.dto.ChatGPTMessageDTO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.JAI.chatbot.quality.QualityPipelineTest.assertCode;

class OpenAiTransportTest {
    HttpServer server; ChatGPTServiceImpl client; AtomicInteger calls;
    String response="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"{}\"}}]}";
    @BeforeEach void setup() throws Exception {
        calls=new AtomicInteger(); server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        client=new ChatGPTServiceImpl(new RestTemplate());
        ReflectionTestUtils.setField(client,"model","gpt-4o-mini-2024-07-18");
        ReflectionTestUtils.setField(client,"apiURL","http://localhost:"+server.getAddress().getPort()+"/chat");
    }
    @AfterEach void stop() { server.stop(0); }
    void stub(int status, boolean recover, String retryAfter) {
        server.createContext("/chat", exchange->{
            String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(request.contains("\"strict\":true")); assertTrue(request.contains("json_schema"));
            int current=calls.incrementAndGet(); int code=recover && current>1?200:status;
            if(retryAfter!=null) exchange.getResponseHeaders().add("Retry-After",retryAfter);
            byte[] bytes=(code==200?response:"{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(code,bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
    }
    String call(CallBudget b) { return client.complete(List.of(new ChatGPTMessageDTO("user","test")),OutputSchemas.intent(),b); }
    CallBudget budget() { return new CallBudget(5,Duration.ofSeconds(10)); }
    @ParameterizedTest @ValueSource(ints={429,500,502,503,504}) void transientErrorRecovers(int status) { stub(status,true,"0"); assertEquals("{}",call(budget())); assertEquals(2,calls.get()); }
    @ParameterizedTest @ValueSource(ints={400,401,403,404,422}) void permanentErrorNeverRetries(int status) { stub(status,false,null); assertCode(QualityError.API_CONFIGURATION,()->call(budget())); assertEquals(1,calls.get()); }
    @Test void repeatedRateLimitStops() { stub(429,false,null); assertCode(QualityError.API_RATE_LIMIT,()->call(budget())); assertEquals(2,calls.get()); }
    @Test void retryAfterBeyondDeadlineStopsWithoutSleeping() { stub(503,false,"120"); assertCode(QualityError.API_UNAVAILABLE,()->call(budget())); assertEquals(1,calls.get()); }
    @Test void sharedCallCapCannotBeExceeded() { stub(503,false,"0"); assertCode(QualityError.CALL_BUDGET_EXCEEDED,()->call(new CallBudget(1,Duration.ofSeconds(10)))); assertEquals(1,calls.get()); }
    @Test void expiredBudgetNeverCallsApi() { stub(200,false,null); assertCode(QualityError.CALL_BUDGET_EXCEEDED,()->call(new CallBudget(5,Duration.ZERO))); assertEquals(0,calls.get()); }
    @Test void refusal() { response="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"no\"}}]}"; stub(200,false,null); assertCode(QualityError.MODEL_REFUSAL,()->call(budget())); assertEquals(1,calls.get()); }
    @Test void incomplete() { response=response.replace("stop","length"); stub(200,false,null); assertCode(QualityError.INCOMPLETE_RESPONSE,()->call(budget())); }
    @Test void absentChoice() { response="{\"choices\":[]}"; stub(200,false,null); assertCode(QualityError.INCOMPLETE_RESPONSE,()->call(budget())); }
    @Test void timeoutIsNotBlindlyRetried() {
        server.createContext("/chat",exchange->{ calls.incrementAndGet(); try { Thread.sleep(200); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } exchange.close(); }); server.start();
        assertCode(QualityError.API_TIMEOUT,()->call(new CallBudget(5,Duration.ofMillis(100)))); assertEquals(1,calls.get());
    }
    @Test void wallClockDeadlineStopsContinuouslyStreamingResponse() {
        server.createContext("/chat", exchange -> {
            calls.incrementAndGet(); exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,0);
            try {
                for (int i=0;i<30;i++) { exchange.getResponseBody().write(' '); exchange.getResponseBody().flush(); Thread.sleep(10); }
            } catch(Exception ignored) { } finally { exchange.close(); }
        }); server.start();
        long start=System.nanoTime();
        assertCode(QualityError.API_TIMEOUT,()->call(new CallBudget(5,Duration.ofMillis(120))));
        assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<500);
        assertEquals(1,calls.get());
    }
    @Test void unsupportedModelFailsExplicitly() { ReflectionTestUtils.setField(client,"model","gpt-3.5-turbo"); assertCode(QualityError.API_CONFIGURATION,()->call(budget())); }
    @Test void retryAfterHttpDateAndSeconds() {
        var now=Instant.parse("2026-10-23T00:00:00Z");
        assertEquals(2000,ChatGPTServiceImpl.retryAfterMillis("2",now));
        assertEquals(3000,ChatGPTServiceImpl.retryAfterMillis("Fri, 23 Oct 2026 00:00:03 GMT",now));
        assertEquals(0,ChatGPTServiceImpl.retryAfterMillis("bad",now));
    }
}
