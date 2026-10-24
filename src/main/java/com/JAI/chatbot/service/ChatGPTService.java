package com.JAI.chatbot.service;
import com.JAI.chatbot.controller.dto.ChatGPTMessageDTO;
import com.JAI.chatbot.quality.CallBudget;
import java.util.*;
public interface ChatGPTService {
    String complete(List<ChatGPTMessageDTO> messages, Map<String,Object> schema, CallBudget budget);
}
