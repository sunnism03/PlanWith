package com.JAI.chatbot.controller.dto;

import lombok.*;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatGPTMessageDTO {

    private String role;
    private String content;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String refusal;
    public ChatGPTMessageDTO(String role, String content) { this.role = role; this.content = content; }

}
