package com.JAI.chatbot.quality;
import com.JAI.chatbot.evaluation.baseline.LegacyMapper;
import com.JAI.chatbot.mapper.ChatbotMapper;
import com.JAI.chatbot.controller.dto.ChatGPTMessageDTO;
import com.JAI.chatbot.controller.dto.response.*;
import com.JAI.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
/** These are deterministic baseline reproductions, never claimed as real-model accuracy. */
class BaselineRegressionTest {
    final ChatbotMapper candidate=new ChatbotMapper(new JacksonConfig().objectMapper());
    final LegacyMapper baseline=new LegacyMapper(new JacksonConfig().objectMapper());
    ChatGPTRespDTO response(String text){return ChatGPTRespDTO.builder().choices(List.of(ChatGPTChoiceDTO.builder().message(new ChatGPTMessageDTO("assistant",text)).build())).build();}
    @Test void proseWrappedJsonWasAcceptedNowRejected() {
        String text="설명: "+QualityPipelineTest.TODO;
        assertEquals(1,baseline.toChatbotTodoResp(response(text)).size());
        assertThrows(QualityException.class,()->candidate.todos(text));
    }
    @Test void reversedDatesWereApprovalEligibleNowBlockedBeforeRedis() {
        String text=QualityPipelineTest.event("2026-10-25","2026-10-24","null","null");
        assertEquals(1,baseline.toChatbotEventResp(response(text)).size());
        assertEquals(QualityError.INVALID_DATE_RANGE,assertThrows(QualityException.class,()->candidate.events(text)).code());
    }
    @Test void blankTitleWasPassedThroughNowRejected() {
        String text=QualityPipelineTest.TODO.replace("독서"," ");
        assertEquals(1,baseline.toChatbotTodoResp(response(text)).size());
        assertEquals(QualityError.REQUIRED_FIELD_MISSING,assertThrows(QualityException.class,()->candidate.todos(text)).code());
    }
}
