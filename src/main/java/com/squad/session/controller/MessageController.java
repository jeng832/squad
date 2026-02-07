package com.squad.session.controller;

import com.squad.common.api.ApiResponse;
import com.squad.session.domain.MessageType;
import com.squad.session.dto.MessageResponse;
import com.squad.session.service.MessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<List<MessageResponse>> findBySession(
            @PathVariable Long sessionId,
            @RequestParam(required = false) MessageType type
    ) {
        return ApiResponse.success(messageService.findBySession(sessionId, type));
    }
}
