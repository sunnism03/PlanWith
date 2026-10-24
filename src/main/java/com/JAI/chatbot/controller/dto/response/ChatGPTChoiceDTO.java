package com.JAI.chatbot.controller.dto.response;

import com.JAI.chatbot.controller.dto.ChatGPTMessageDTO;

import lombok.*;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatGPTChoiceDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("finish_reason")
    private String finishReason;
    private int index;
    private ChatGPTMessageDTO message;

}
