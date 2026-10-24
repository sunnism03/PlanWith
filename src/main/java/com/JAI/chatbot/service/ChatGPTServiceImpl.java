package com.JAI.chatbot.service;
import com.JAI.chatbot.controller.dto.ChatGPTMessageDTO;
import com.JAI.chatbot.controller.dto.request.ChatGPTReqDTO;
import com.JAI.chatbot.controller.dto.response.ChatGPTRespDTO;
import com.JAI.chatbot.quality.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import static com.JAI.chatbot.quality.QualityError.*;

@Service
@RequiredArgsConstructor
public class ChatGPTServiceImpl implements ChatGPTService {
    // A bounded, non-queuing pool also limits abandoned blocking socket reads after cancellation.
    private static final ExecutorService HTTP_CALLS = new ThreadPoolExecutor(0, 16, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), task -> {
                Thread thread = new Thread(task, "planwith-openai-http");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    @Value("${openai.model:gpt-4o-mini-2024-07-18}") private String model;
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}") private String apiURL;
    private final RestTemplate openAiRestTemplate;

    @Override
    public String complete(List<ChatGPTMessageDTO> messages, Map<String,Object> schema, CallBudget budget) {
        // Explicit tested model allowlist; do not silently downgrade unsupported configurations.
        if (!Set.of("gpt-4o-mini", "gpt-4o-mini-2024-07-18", "gpt-4o", "gpt-4o-2024-08-06", "gpt-4o-2024-11-20").contains(model))
            throw new QualityException(API_CONFIGURATION, "openai.model: use gpt-4o-mini-2024-07-18");
        var request = ChatGPTReqDTO.builder().model(model).messages(messages)
                .responseFormat(Map.of("type", "json_schema", "json_schema", Map.of("name", "planwith", "strict", true, "schema", schema))).build();
        for (int attempt = 0; ; attempt++) {
            budget.acquire();
            try {
                var response = awaitResponse(request, budget);
                if (budget.remainingMillis() == 0) throw new QualityException(CALL_BUDGET_EXCEEDED, "deadline");
                if (response != null) budget.usage(response.getUsage());
                return content(response);
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                QualityError code = status == 429 ? API_RATE_LIMIT : status >= 500 ? API_UNAVAILABLE : API_CONFIGURATION;
                budget.failure(code);
                if (attempt >= 1 || !(status == 429 || status == 500 || status == 502 || status == 503 || status == 504))
                    throw new QualityException(code, "upstream");
                long retryAfter = retryAfterMillis(e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After"), Instant.now());
                pause(Math.max(retryAfter, (250L << attempt) + ThreadLocalRandom.current().nextLong(251)), budget, code);
            } catch (ResourceAccessException e) {
                // Transport outcome is unknown. Stop rather than retrying every network failure.
                budget.failure(API_TIMEOUT);
                throw new QualityException(API_TIMEOUT, "upstream");
            } catch (RestClientException e) {
                throw new QualityException(API_UNAVAILABLE, "upstream");
            }
        }
    }
    private ChatGPTRespDTO awaitResponse(ChatGPTReqDTO request, CallBudget budget) {
        Future<ChatGPTRespDTO> call;
        try {
            RestTemplate client = boundedClient(budget);
            call = HTTP_CALLS.submit(() -> client.postForObject(apiURL, request, ChatGPTRespDTO.class));
        } catch (RejectedExecutionException e) {
            throw new QualityException(API_UNAVAILABLE, "upstream concurrency limit");
        }
        try {
            return call.get(Math.max(1, budget.remainingMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            budget.failure(API_TIMEOUT);
            throw new QualityException(API_TIMEOUT, "request deadline");
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new QualityException(API_UNAVAILABLE, "interrupted");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new QualityException(API_UNAVAILABLE, "upstream");
        }
    }
    private RestTemplate boundedClient(CallBudget budget) {
        var factory = new SimpleClientHttpRequestFactory();
        int half = (int)Math.max(1, Math.min(8000, budget.remainingMillis() / 2));
        factory.setConnectTimeout(Math.min(2000, half)); factory.setReadTimeout(half);
        var client = new RestTemplate(factory);
        client.setInterceptors(openAiRestTemplate.getInterceptors());
        client.setMessageConverters(openAiRestTemplate.getMessageConverters());
        return client;
    }
    public static String content(ChatGPTRespDTO response) {
        if (response == null || response.getChoices() == null || response.getChoices().size() != 1 || response.getChoices().get(0) == null)
            throw new QualityException(INCOMPLETE_RESPONSE, "choices");
        var choice = response.getChoices().get(0);
        if (choice.getMessage() != null && choice.getMessage().getRefusal() != null)
            throw new QualityException(MODEL_REFUSAL, "response");
        if (!"stop".equals(choice.getFinishReason()) || choice.getMessage() == null || choice.getMessage().getContent() == null)
            throw new QualityException(INCOMPLETE_RESPONSE, "finish_reason/content");
        return choice.getMessage().getContent();
    }
    public static long retryAfterMillis(String value, Instant now) {
        if (value == null) return 0;
        try { return Math.max(0, Math.multiplyExact(Long.parseLong(value), 1000)); }
        catch (NumberFormatException e) {
            try { return Math.max(0, Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis()); }
            catch (RuntimeException ignored) { return 0; }
        } catch (ArithmeticException e) { return Long.MAX_VALUE; }
    }
    private static void pause(long millis, CallBudget budget, QualityError code) {
        if (millis >= budget.remainingMillis()) throw new QualityException(code, "retry-after exceeds deadline");
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new QualityException(API_UNAVAILABLE, "interrupted"); }
    }
}
