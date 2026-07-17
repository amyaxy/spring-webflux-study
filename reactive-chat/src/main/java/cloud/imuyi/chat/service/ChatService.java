package cloud.imuyi.chat.service;

import cloud.imuyi.chat.model.ChatMessage;
import cloud.imuyi.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMessageRepository messageRepository;
    private final Map<String, Sinks.Many<ChatMessage>> roomSinks = new ConcurrentHashMap<>();

    private Sinks.Many<ChatMessage> sinkForRoom(String room) {
        return roomSinks.computeIfAbsent(room, k ->
                Sinks.many().multicast().onBackpressureBuffer(256));
    }

    public Flux<ChatMessage> streamByRoom(String room) {
        return sinkForRoom(room).asFlux();
    }

    public Flux<ChatMessage> historyByRoom(String room) {
        return messageRepository.findByChatRoomOrderByCreatedAtAsc(room);
    }

    @Transactional
    @Retryable(maxRetries = 3, delay = 100, multiplier = 2)
    public Mono<ChatMessage> sendMessage(String room, String sender, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setChatRoom(room);
        msg.setSender(sender);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        return messageRepository.save(msg)
                .doOnNext(saved -> sinkForRoom(room).tryEmitNext(saved));
    }
}