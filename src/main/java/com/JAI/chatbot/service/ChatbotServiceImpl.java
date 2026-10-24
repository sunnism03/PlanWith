package com.JAI.chatbot.service;

import com.JAI.chatbot.RedisChatbotUtil;
import com.JAI.chatbot.approval.service.ApprovalOrchestrator;
import com.JAI.chatbot.controller.dto.ChatbotRedisDataDTO;
import com.JAI.chatbot.controller.dto.request.ChatbotReqDTO;
import com.JAI.chatbot.controller.dto.request.TokenReqDTO;
import com.JAI.chatbot.controller.dto.response.ChatbotEventRespDTO;
import com.JAI.chatbot.controller.dto.response.ChatbotResponseWrapper;
import com.JAI.chatbot.controller.dto.response.ChatbotTodoRespDTO;
import com.JAI.chatbot.exception.ChatbotBadRequestException;
import com.JAI.chatbot.exception.ChatbotForbiddenException;
import com.JAI.chatbot.mapper.ChatbotMapper;
import com.JAI.user.service.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    private final com.JAI.chatbot.quality.ExtractionService extractionService;

    private final RedisChatbotUtil redisChatbotUtil;

    private final ChatbotMapper chatbotMapper;
    private final ApprovalOrchestrator approvalOrchestrator;



    @Override
    public UUID validateUser(CustomUserDetails user, ChatbotRedisDataDTO chatbotRedisDataDTO) {

        // 레디스에 저장된 userId와 현재 로그인한 userId가 동일해야 접근 가능
        UUID userId = chatbotRedisDataDTO.getUserId(); // 레디스에 저장되어 있는 userId(데이터 입력한 유저)
        UUID currUserId = user.getUser().getUserId();  // 현재 로그인한 userId


        if (!currUserId.toString().equals(userId.toString())) {
            throw new ChatbotForbiddenException("해당 유저는 접근할 수 없는 데이터입니다");
        }

        return currUserId;
    }

    @Override
    public TokenReqDTO saveRequest (CustomUserDetails user, ChatbotReqDTO request) {

        if (request.getPrompt() == null || request.getPrompt().isBlank() || request.getPrompt().length() > 10000) {
            throw new ChatbotBadRequestException("프롬프트를 입력해주세요");
        }

        // userId 불러오기
        UUID userId = user.getUser().getUserId();

        // 레디스 키, 토큰 생성
        String redisKey = UUID.randomUUID().toString();  // 레디스 키 생성
        String redisToken = UUID.randomUUID().toString();  // 레디스 토큰 생성
        TokenReqDTO token = TokenReqDTO.builder().token(redisToken).build(); // 레디스 토큰 DTO에 담기

        redisChatbotUtil.saveChatbotData(redisKey, redisToken, userId, request);
        return token;
    }

    @Override
    public ChatbotResponseWrapper<?> createResponseJson(CustomUserDetails user,  TokenReqDTO token) throws ChatbotForbiddenException{

        ChatbotRedisDataDTO data = redisChatbotUtil.getChatbotData(token.getToken());
        validateUser(user, data);
        try {
            var result = extractionService.extract(data.getChatbotReqDTO().getPrompt(), data.getChatbotReqDTO().getIntention());
            var req = data.getChatbotReqDTO();
            redisChatbotUtil.saveChatbotReq(token.getToken(), ChatbotReqDTO.builder()
                    .prompt(req.getPrompt()).categoryId(req.getCategoryId()).intention(result.intent()).build());
            if ("EVENT".equals(result.intent())) {
                redisChatbotUtil.saveChatbotEventResp(token.getToken(), result.events());
                return ChatbotResponseWrapper.<ChatbotEventRespDTO>builder().responses(result.events()).tokenReqDTO(token).build();
            }
            redisChatbotUtil.saveChatbotTodoResp(token.getToken(), result.todos());
            return ChatbotResponseWrapper.<ChatbotTodoRespDTO>builder().responses(result.todos()).tokenReqDTO(token).build();
        } catch (com.JAI.chatbot.quality.QualityException e) {
            redisChatbotUtil.deleteChatbotData(token.getToken());
            throw e;
        }
    }

    @Override
    public void analyzeIntention(CustomUserDetails user, TokenReqDTO token) {
        // Classification now runs inside extract(), sharing its bounded call budget.
        validateUser(user, redisChatbotUtil.getChatbotData(token.getToken()));
    }

    @Override
    public void validateAcceptAlarm(CustomUserDetails user, Boolean accept, Boolean alarm, TokenReqDTO token) {
        if (token == null) throw new ChatbotBadRequestException("토큰을 넣어주세요");
        approvalOrchestrator.process(user.getUser().getUserId(), token.getToken(),
                Boolean.TRUE.equals(accept), Boolean.TRUE.equals(alarm));
    }

    @Override
    public void saveEvent(CustomUserDetails user, ChatbotRedisDataDTO ignored,
                          Boolean alarm, TokenReqDTO token) {
        if (token == null) throw new ChatbotBadRequestException("토큰을 넣어주세요");
        approvalOrchestrator.process(user.getUser().getUserId(), token.getToken(), true,
                Boolean.TRUE.equals(alarm));
    }

    @Override
    public void saveTodo(CustomUserDetails user, ChatbotRedisDataDTO ignored,
                         Boolean alarm, TokenReqDTO token) {
        if (token == null) throw new ChatbotBadRequestException("토큰을 넣어주세요");
        approvalOrchestrator.process(user.getUser().getUserId(), token.getToken(), true,
                Boolean.TRUE.equals(alarm));
    }

}
