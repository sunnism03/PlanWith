package com.JAI.chatbot.quality;
import com.JAI.chatbot.mapper.ChatbotMapper;
import com.JAI.chatbot.service.ChatGPTService;
import com.JAI.config.JacksonConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualityPipelineTest {
    final com.fasterxml.jackson.databind.ObjectMapper json = new JacksonConfig().objectMapper();
    final ChatbotMapper mapper = new ChatbotMapper(json);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneId.of("Asia/Seoul"));
    static final String TODO = "{\"status\":\"READY\",\"todos\":[{\"title\":\"독서\",\"date\":\"2026-10-25\",\"time\":null}]}";
    static String event(String start, String end, String st, String et) {
        return "{\"status\":\"READY\",\"events\":[{\"title\":\"회의\",\"startDate\":\""+start+"\",\"endDate\":\""+end+"\",\"startTime\":"+st+",\"endTime\":"+et+"}]}";
    }
    @Test void validDateAndNullableTime() { assertNull(mapper.todos(TODO).get(0).getTime()); }
    @Test void bracesInsideTitleAreNotRegexExtracted() { assertEquals("{독서}",mapper.todos(TODO.replace("독서","{독서}")).get(0).getTitle()); }
    @ParameterizedTest @ValueSource(strings={"2026-02-29","2026-13-01","2026-04-31","2026-1-01"})
    void impossibleDate(String value) { assertCode(QualityError.SCHEMA_INVALID,()->mapper.todos(TODO.replace("2026-10-25",value))); }
    @ParameterizedTest @ValueSource(strings={"25:00:00","12:60:00","12:30","24:00:00"})
    void invalidTime(String value) { assertCode(QualityError.SCHEMA_INVALID,()->mapper.todos(TODO.replace("null","\""+value+"\""))); }
    @ParameterizedTest @ValueSource(strings={"null","42","true","[]","{}"})
    void invalidTitleType(String value) { assertCode(QualityError.SCHEMA_INVALID,()->mapper.todos(TODO.replace("\"독서\"",value))); }
    @Test void missingField() { assertCode(QualityError.SCHEMA_INVALID,()->mapper.todos(TODO.replace(",\"time\":null",""))); }
    @Test void nullDateNeedsClarification() { assertCode(QualityError.AMBIGUOUS_INPUT,()->mapper.todos(TODO.replace("\"2026-10-25\"","null"))); }
    @Test void blankTitle() { assertCode(QualityError.REQUIRED_FIELD_MISSING,()->mapper.todos(TODO.replace("독서","  "))); }
    @Test void unknownField() { assertCode(QualityError.SCHEMA_INVALID,()->mapper.todos(TODO.replace("\"title\"","\"other\""))); }
    @Test void proseAndTrailingJsonRejected() {
        assertCode(QualityError.SCHEMA_INVALID,()->mapper.todos("Here: "+TODO));
        assertCode(QualityError.SCHEMA_INVALID,()->mapper.todos(TODO+" {}"));
    }
    @Test void reversedDateRange() { assertCode(QualityError.INVALID_DATE_RANGE,()->mapper.events(event("2026-10-25","2026-10-24","null","null"))); }
    @Test void reversedTimeRange() { assertCode(QualityError.INVALID_DATE_RANGE,()->mapper.events(event("2026-10-25","2026-10-25","\"17:00:00\"","\"09:00:00\""))); }
    @Test void overnightEventAllowed() { assertEquals(1,mapper.events(event("2026-10-25","2026-10-26","\"23:00:00\"","\"01:00:00\"")).size()); }
    @Test void endWithoutStartNeedsClarification() { assertCode(QualityError.AMBIGUOUS_INPUT,()->mapper.events(event("2026-10-25","2026-10-25","null","\"09:00:00\""))); }
    @Test void clarificationAndUnsupportedRecurrenceDoNotProduceItems() {
        assertCode(QualityError.AMBIGUOUS_INPUT,()->mapper.todos("{\"status\":\"AMBIGUOUS_INPUT\",\"todos\":[]}"));
        assertCode(QualityError.UNSUPPORTED_RECURRENCE,()->mapper.events("{\"status\":\"UNSUPPORTED_RECURRENCE\",\"events\":[]}"));
    }
    @Test void clockUsesSeoulDayAndChangesAcrossMidnight() {
        assertTrue(QualityPrompts.extraction("내일 독서","TODO",clock).get(0).getContent().contains("내일=2026-10-25"));
        assertTrue(QualityPrompts.extraction("내일 독서","TODO",Clock.offset(clock,Duration.ofDays(1))).get(0).getContent().contains("내일=2026-10-26"));
    }
    @Test void singleRelativeDateChecksMeaningButDoesNotGuessComplexRanges() {
        assertCode(QualityError.SEMANTIC_MISMATCH,()->RelativeDateGuard.validate("내일 독서",List.of(LocalDate.of(2026,10,24)),clock));
        assertDoesNotThrow(()->RelativeDateGuard.validate("내일부터 다음 주까지 독서",List.of(LocalDate.of(2026,10,24)),clock));
    }
    @Test void recoverySucceedsOnce() {
        var client=mock(ChatGPTService.class);
        when(client.complete(anyList(),anyMap(),any())).thenReturn("{\"intent\":\"TODO\"}",TODO.replace("2026-10-25","2026-10-24"),TODO);
        var result=new ExtractionService(client,mapper,json,clock).extract("내일 독서",null);
        assertEquals(1,result.regenerations()); assertEquals("SEMANTIC_MISMATCH",result.initialError());
        verify(client,times(3)).complete(anyList(),anyMap(),any());
    }
    @Test void recoveryStopsAfterSecondFailure() {
        var client=mock(ChatGPTService.class);
        when(client.complete(anyList(),anyMap(),any())).thenReturn("{\"intent\":\"TODO\"}",TODO.replace("2026-10-25","2026-10-24"));
        assertCode(QualityError.SEMANTIC_MISMATCH,()->new ExtractionService(client,mapper,json,clock).extract("내일 독서",null));
        verify(client,times(3)).complete(anyList(),anyMap(),any());
    }
    @Test void schemaFailureDoesNotRegenerate() {
        var client=mock(ChatGPTService.class);
        when(client.complete(anyList(),anyMap(),any())).thenReturn("{\"intent\":\"TODO\"}","invalid");
        assertCode(QualityError.SCHEMA_INVALID,()->new ExtractionService(client,mapper,json,clock).extract("내일 독서",null));
        verify(client,times(2)).complete(anyList(),anyMap(),any());
    }
    @Test void conflictingUserIntentIsNotSilentlyOverridden() {
        var client=mock(ChatGPTService.class); when(client.complete(anyList(),anyMap(),any())).thenReturn("{\"intent\":\"TODO\"}");
        assertCode(QualityError.AMBIGUOUS_INPUT,()->new ExtractionService(client,mapper,json,clock).extract("내일 독서","EVENT"));
        verify(client,times(1)).complete(anyList(),anyMap(),any());
    }
    @Test void knownUnsupportedRecurrenceAndBareHoursStopBeforeGeneration() {
        assertCode(QualityError.UNSUPPORTED_RECURRENCE,()->InputPolicy.validate("매주 금요일 회의"));
        assertCode(QualityError.AMBIGUOUS_INPUT,()->InputPolicy.validate("내일 3시 회의"));
        assertDoesNotThrow(()->InputPolicy.validate("내일 오후 3시 회의"));
        assertDoesNotThrow(()->InputPolicy.validate("내일 13시 회의"));
    }
    @Test void duplicateJsonKeysFailClosed() { assertCode(QualityError.SCHEMA_INVALID,()->mapper.todos(TODO.replace("\"title\":", "\"title\":\"hidden\",\"title\":"))); }
    @Test void callAndTimeBudget() {
        var ticker=new AtomicLong(); var budget=new CallBudget(1,Duration.ofSeconds(1),ticker::get);
        budget.acquire(); assertCode(QualityError.CALL_BUDGET_EXCEEDED,budget::acquire);
        var timed=new CallBudget(5,Duration.ofSeconds(1),ticker::get); ticker.set(1_000_000_000L);
        assertCode(QualityError.CALL_BUDGET_EXCEEDED,timed::acquire);
    }
    static void assertCode(QualityError code, org.junit.jupiter.api.function.Executable action) { assertEquals(code,assertThrows(QualityException.class,action).code()); }
}
