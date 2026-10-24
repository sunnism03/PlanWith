package com.JAI.chatbot.controller.dto.request;

import com.JAI.chatbot.controller.dto.ChatGPTMessageDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatGPTReqDTO {

    @NotBlank
    private String model;
    @com.fasterxml.jackson.annotation.JsonProperty("response_format")
    private Map<String, Object> responseFormat;
    @Builder.Default
    private double temperature = 0;
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("max_completion_tokens")
    private int maxCompletionTokens = 4096;

    @NotBlank
    private List<ChatGPTMessageDTO> messages;

}

