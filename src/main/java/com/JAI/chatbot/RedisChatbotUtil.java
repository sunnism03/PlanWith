package com.JAI.chatbot;

import com.JAI.chatbot.controller.dto.ChatbotRedisDataDTO;
import com.JAI.chatbot.controller.dto.request.ChatbotReqDTO;
import com.JAI.chatbot.controller.dto.response.ChatbotEventRespDTO;
import com.JAI.chatbot.controller.dto.response.ChatbotTodoRespDTO;
import com.JAI.chatbot.exception.ChatbotNotFoundException;
import com.JAI.chatbot.exception.ChatbotUnprocessableEntityException;
import com.JAI.global.util.RedisUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisChatbotUtil {

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    private static final String PREFIX_CHATBOT = "chatbot:";
    private static final String PREFIX_KEY_TOKEN = "chatbot_token:";
    private static final Long ttlSeconds = 3600L;


    // token을 이용해 저장된 key값을 조회
    public String getKeyByToken(String token) {
        String key = redisUtil.get(PREFIX_KEY_TOKEN + token);
        if (key == null) {
            throw new ChatbotNotFoundException("토큰에 해당하는 키가 Redis에 존재하지 않습니다");
        }
        return key;
    }

    // ChatbotRedisData를 Redis에 저장
    public void saveChatbotData(String key, String token, UUID userId, ChatbotReqDTO chatbotReqDTO) {
        try {
            ChatbotRedisDataDTO chatbotData = ChatbotRedisDataDTO.builder()
                    .token(token)
                    .userId(userId)
                    .chatbotReqDTO(chatbotReqDTO)
                    .chatbotEventRespDTO(null)
                    .chatbotTodoRespDTO(null)
                    .build();

            String json = objectMapper.writeValueAsString(chatbotData);     // DTO -> JSON 변환
            redisUtil.save(PREFIX_CHATBOT + key, json, ttlSeconds);    // Redis 저장

            // token을 사용하여 Redis에서 key를 찾을 수 있도록 매핑
            redisUtil.save(PREFIX_KEY_TOKEN + token, key, ttlSeconds); // 토큰에 대한 key 저장
        } catch (JsonProcessingException e) {
            throw new ChatbotUnprocessableEntityException("ChatbotRedisData 직렬화 실패");
        }
    }

    // Redis에서 ChatbotRedisData를 조회
    public ChatbotRedisDataDTO getChatbotData(String token) {

        String key = getKeyByToken(token);


        try {
            String json = redisUtil.get(PREFIX_CHATBOT + key); // Redis에서 JSON 가져오기
            // 데이터가 없으면 에러 처리
            if (json == null) {
                throw new ChatbotNotFoundException("키에 해당하는 데이터가 없습니다", key);
            }
            return objectMapper.readValue(json, ChatbotRedisDataDTO.class); // JSON -> DTO 변환
        } catch (JsonProcessingException e) {
            throw new ChatbotUnprocessableEntityException("ChatbotRedisData 역직렬화 실패");
        }
    }

    public void saveChatbotReq(String token, ChatbotReqDTO chatbotReqDTO) {

        String key = getKeyByToken(token);

        ChatbotRedisDataDTO existingData = getChatbotData(token);
        if(existingData == null) {
            throw new ChatbotNotFoundException("키에 해당하는 데이터가 없습니다", key);
        }

        existingData = ChatbotRedisDataDTO.builder()
                .token(existingData.getToken())
                .userId(existingData.getUserId())
                .chatbotReqDTO(chatbotReqDTO)
                .chatbotEventRespDTO(existingData.getChatbotEventRespDTO())
                .chatbotTodoRespDTO(existingData.getChatbotTodoRespDTO())
                .build();

        saveChatbotData(token, existingData);
    }

    // ChatbotEventResp 저장/수정 (기존 ChatbotRedisData 업데이트)
    public void saveChatbotEventResp(String token, List<ChatbotEventRespDTO> chatbotEventRespDTO) {

        // token을 통해 key 조회
        String key = getKeyByToken(token);


        // 기존 데이터 가져오기
        ChatbotRedisDataDTO existingData = getChatbotData(token);
        if (existingData == null) {
            throw new ChatbotNotFoundException("키에 해당하는 데이터가 없습니다", key);
        }

        // ChatbotEventResp를 기존 데이터에 추가
        existingData = ChatbotRedisDataDTO.builder()
                .token(existingData.getToken())
                .userId(existingData.getUserId())
                .chatbotReqDTO(existingData.getChatbotReqDTO())
                .chatbotEventRespDTO(chatbotEventRespDTO)
                .chatbotTodoRespDTO(existingData.getChatbotTodoRespDTO())
                .build();

        // 수정된 데이터 다시 Redis에 저장
        saveApprovalSnapshot(token, existingData);
        saveChatbotData(token, existingData);
    }

    // ChatbotTodoResp 저장/수정 (기존 ChatbotRedisData 업데이트)
    public void saveChatbotTodoResp(String token, List<ChatbotTodoRespDTO> chatbotTodoRespDTO) {

        // token을 통해 key 조회
        String key = getKeyByToken(token);


        // 기존 데이터 가져오기
        ChatbotRedisDataDTO existingData = getChatbotData(token);
        if (existingData == null) {
            throw new ChatbotNotFoundException("키에 해당하는 데이터가 없습니다", key);
        }

        // ChatbotTodoResp를 기존 데이터에 추가
        existingData = ChatbotRedisDataDTO.builder()
                .token(existingData.getToken())
                .userId(existingData.getUserId())
                .chatbotReqDTO(existingData.getChatbotReqDTO())
                .chatbotEventRespDTO(existingData.getChatbotEventRespDTO())
                .chatbotTodoRespDTO(chatbotTodoRespDTO)
                .build();

        // 수정된 데이터 다시 Redis에 저장
        saveApprovalSnapshot(token, existingData);
        saveChatbotData(token, existingData);
    }

    // ChatbotRedisData를 Redis에 저장하는 내부 메서드
    private void saveChatbotData(String token, ChatbotRedisDataDTO chatbotData) {
        try {

            String key = getKeyByToken(token);

            String json = objectMapper.writeValueAsString(chatbotData); // DTO -> JSON 변환
            redisUtil.save(PREFIX_CHATBOT + key, json, ttlSeconds);    // Redis 저장
        } catch (JsonProcessingException e) {
            throw new ChatbotUnprocessableEntityException("ChatbotRedisData 직렬화 실패");
        }
    }


    // Immutable READY snapshot, written only after the LLM output has been mapped.
    // Changes to a recommendation must receive a new token.
    private void saveApprovalSnapshot(String token, ChatbotRedisDataDTO data) {
        if (data.getChatbotReqDTO() == null) throw new ChatbotUnprocessableEntityException("추천 유형이 없습니다");
        String intent = data.getChatbotReqDTO().getIntention();
        if ("EVENT".equals(intent) && data.getChatbotTodoRespDTO() == null) {
            com.JAI.chatbot.quality.BusinessValidator.events(data.getChatbotEventRespDTO());
        } else if (("TODO".equals(intent) || "PLAN_RECOMMENDATION".equals(intent)) && data.getChatbotEventRespDTO() == null) {
            com.JAI.chatbot.quality.BusinessValidator.todos(data.getChatbotTodoRespDTO());
        } else throw new ChatbotUnprocessableEntityException("추천 유형과 항목이 일치하지 않습니다");
        try {
            String json = objectMapper.writeValueAsString(com.JAI.chatbot.approval.dto.ProposalIdentity.ready(data, objectMapper));
            if (!redisUtil.saveIfAbsent("approval:{" + token + "}:proposal", json, ttlSeconds)) {
                throw new ChatbotUnprocessableEntityException("이미 확정된 추천안입니다. 새 요청을 생성해 주세요");
            }
        } catch (JsonProcessingException e) {
            throw new ChatbotUnprocessableEntityException("승인 추천안 직렬화 실패");
        }
    }

    public void deleteChatbotData(String token) {
        // Remove both the mapping AND the prefixed data key; the previous version
        // called delete(key), although the actual data lives at "chatbot:" + key.
        String key = redisUtil.get(PREFIX_KEY_TOKEN + token);
        if (key != null) redisUtil.delete(PREFIX_CHATBOT + key);
        redisUtil.delete(PREFIX_KEY_TOKEN + token);
        // The owner-checked Lua script removes the approval proposal and lease.
    }

}
