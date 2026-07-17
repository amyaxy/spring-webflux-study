package cloud.imuyi.chat.controller;

import cloud.imuyi.chat.model.ChatMessage;
import cloud.imuyi.chat.service.AiChatService;
import cloud.imuyi.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final AiChatService aiChatService;

    @GetMapping(value = "/rooms/{room}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatMessage> streamRoom(@PathVariable String room) {
        return chatService.historyByRoom(room)
                .concatWith(chatService.streamByRoom(room));
    }

    @PostMapping("/rooms/{room}/messages")
    public Mono<ChatMessage> sendMessage(@PathVariable String room,
                                         @RequestParam String sender,
                                         @RequestParam String content) {
        return chatService.sendMessage(room, sender, content);
    }

    @PostMapping(value = "/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithAI(@RequestParam String conversationId,
                                   @RequestParam String message) {
        return aiChatService.chatStream(conversationId, message);
    }
}