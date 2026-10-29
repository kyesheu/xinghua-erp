package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.ai.service.AiConfigService;
import cn.xinghuaerp.ai.service.AiConversationHistoryService;
import cn.xinghuaerp.ai.service.impl.ChatService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatInputBoundaryTest {
    @Test void invalidRequestsAreRejectedBeforeAnyBusinessOrPaidCall() {
        var chat = mock(ChatService.class);
        var config = mock(AiConfigService.class);
        var history = mock(AiConversationHistoryService.class);
        var controller = new ChatController(chat,config,history);
        assertThrows(IllegalArgumentException.class, () -> controller.sendMessage(null));
        var input = new ChatController.ChatSendRequest();
        assertThrows(IllegalArgumentException.class, () -> controller.sendMessage(input));
        input.setMessage("   ");
        assertThrows(IllegalArgumentException.class, () -> controller.sendMessage(input));
        input.setMessage("a".repeat(8001));
        assertThrows(IllegalArgumentException.class, () -> controller.sendMessage(input));
        input.setMessage("查库存"); input.setSessionId("s".repeat(65));
        assertThrows(IllegalArgumentException.class, () -> controller.sendMessage(input));
        verifyNoInteractions(chat,config,history);
    }
    @Test void validBoundaryIsAcceptedWithoutCallingAConfiguredModel() {
        var chat = mock(ChatService.class);
        var config = mock(AiConfigService.class);
        var history = mock(AiConversationHistoryService.class);
        var user = new cn.xinghuaerp.system.security.LoginUser(); user.setUserId(7L);
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user,null,java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            var input = new ChatController.ChatSendRequest();
            input.setMessage("a".repeat(8000)); input.setSessionId("s".repeat(64));
            assertNotNull(new ChatController(chat,config,history).sendMessage(input));
            verify(history).saveMessage(input.getSessionId(),7L,"user",input.getMessage());
            verifyNoInteractions(chat);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
