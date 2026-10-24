package com.JAI.chatbot.quality;
import com.JAI.chatbot.RedisChatbotUtil;
import com.JAI.chatbot.service.ChatbotServiceImpl;
import com.JAI.chatbot.approval.service.ApprovalOrchestrator;
import com.JAI.chatbot.controller.dto.*;
import com.JAI.chatbot.controller.dto.request.*;
import com.JAI.chatbot.controller.dto.response.*;
import com.JAI.chatbot.mapper.ChatbotMapper;
import com.JAI.config.JacksonConfig;
import com.JAI.global.util.RedisUtil;
import com.JAI.user.domain.User;
import com.JAI.user.service.dto.CustomUserDetails;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class GenerationSafetyTest {
    @Test void failedGenerationCannotPublishOrApproveAndCleansPendingData() {
        var pipeline=mock(ExtractionService.class);var redis=mock(RedisChatbotUtil.class);var approval=mock(ApprovalOrchestrator.class);
        var service=new ChatbotServiceImpl(pipeline,redis,mock(ChatbotMapper.class),approval);
        var user=mock(CustomUserDetails.class);var entity=mock(User.class);var id=UUID.randomUUID();
        when(user.getUser()).thenReturn(entity);when(entity.getUserId()).thenReturn(id);
        var req=ChatbotReqDTO.builder().prompt("내일 독서").build();
        when(redis.getChatbotData("token")).thenReturn(ChatbotRedisDataDTO.builder().userId(id).chatbotReqDTO(req).build());
        when(pipeline.extract("내일 독서",null)).thenThrow(new QualityException(QualityError.SCHEMA_INVALID,"json"));
        assertThrows(QualityException.class,()->service.createResponseJson(user,TokenReqDTO.builder().token("token").build()));
        verify(redis).deleteChatbotData("token");verify(redis,never()).saveChatbotTodoResp(any(),any());verify(redis,never()).saveChatbotEventResp(any(),any());verifyNoInteractions(approval);
    }
    @Test void invalidMappedItemsCannotBecomeReady() throws Exception {
        var store=mock(RedisUtil.class);var json=new JacksonConfig().objectMapper();var redis=new RedisChatbotUtil(store,json);
        var data=ChatbotRedisDataDTO.builder().token("token").userId(UUID.randomUUID()).chatbotReqDTO(ChatbotReqDTO.builder().intention("TODO").build()).build();
        when(store.get("chatbot_token:token")).thenReturn("key");when(store.get("chatbot:key")).thenReturn(json.writeValueAsString(data));
        assertThrows(QualityException.class,()->redis.saveChatbotTodoResp("token",List.of(ChatbotTodoRespDTO.builder().title("invalid").build())));
        verify(store,never()).saveIfAbsent(anyString(),anyString(),anyLong());
    }
    @Test void cleanupRemovesMappingAndPrefixedData() {
        var store=mock(RedisUtil.class);when(store.get("chatbot_token:token")).thenReturn("key");
        new RedisChatbotUtil(store,new JacksonConfig().objectMapper()).deleteChatbotData("token");
        verify(store).delete("chatbot:key");verify(store).delete("chatbot_token:token");
    }
    @Test void schemaValidityAndBusinessValidityRemainSeparate() throws Exception {
        var json=new JacksonConfig().objectMapper();
        var raw=json.readTree(QualityPipelineTest.TODO.replace("2026-10-25","2026-02-30"));
        assertTrue(SchemaCheck.valid(raw,OutputSchemas.extraction("TODO"),json));
        assertThrows(QualityException.class,()->new ChatbotMapper(json).todos(raw.toString()));
    }
    @Test void schemaRequiresAllFieldsAndNoExtraProperties() {
        for(String intent:List.of("EVENT","TODO","PLAN_RECOMMENDATION")) {
            var schema=OutputSchemas.extraction(intent);assertEquals(false,schema.get("additionalProperties"));
            assertEquals(((Map<?,?>)schema.get("properties")).size(),((List<?>)schema.get("required")).size());
        }
    }
}
