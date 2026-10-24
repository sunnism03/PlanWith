package com.JAI.chatbot.quality;
import java.time.Duration;
import java.util.function.LongSupplier;
public final class CallBudget {
    private final LongSupplier ticker;
    private final long deadline;
    private final int maxCalls;
    private int calls;
    private long promptTokens, completionTokens;
    private int measuredCalls;
    private String initialError;
    public void failure(QualityError code) { if (initialError == null) initialError = code.name(); }
    public String initialError() { return initialError; }
    public void usage(com.fasterxml.jackson.databind.JsonNode usage) {
        if (usage != null && usage.path("prompt_tokens").canConvertToLong() && usage.path("completion_tokens").canConvertToLong()) {
            promptTokens += usage.get("prompt_tokens").asLong(); completionTokens += usage.get("completion_tokens").asLong(); measuredCalls++;
        }
    }
    public java.util.Map<String,Object> usage() { return java.util.Map.of("prompt_tokens",promptTokens,"completion_tokens",completionTokens,"measured_calls",measuredCalls,"complete",measuredCalls==calls); }

    public CallBudget(int maxCalls, Duration duration) { this(maxCalls, duration, System::nanoTime); }
    public CallBudget(int maxCalls, Duration duration, LongSupplier ticker) {
        this.maxCalls = maxCalls; this.ticker = ticker; deadline = ticker.getAsLong() + duration.toNanos();
    }
    public void acquire() {
        if (calls >= maxCalls || remainingMillis() <= 0) throw new QualityException(QualityError.CALL_BUDGET_EXCEEDED, "request");
        calls++;
    }
    public long remainingMillis() { return Math.max(0, (deadline - ticker.getAsLong()) / 1_000_000); }
    public int calls() { return calls; }
}
