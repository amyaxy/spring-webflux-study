package cloud.imuyi.chat.ws;

import cloud.imuyi.chat.model.ChatMessage;
import cloud.imuyi.chat.service.ChatService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String room = extractRoom(session);

        Mono<Void> input = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .flatMap(text -> {
                    try {
                        Map<String, String> map = objectMapper.readValue(text, Map.class);
                        return chatService.sendMessage(room,
                                map.getOrDefault("sender", "anonymous"),
                                map.getOrDefault("content", ""));
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .then();

        Flux<String> output = chatService.historyByRoom(room)
                .concatWith(chatService.streamByRoom(room))
                .map(this::toJson);

        return Mono.when(input, session.send(output.map(session::textMessage)));
    }

    private String extractRoom(WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @SneakyThrows
    private String toJson(ChatMessage msg) {
        return objectMapper.writeValueAsString(msg);
    }
}