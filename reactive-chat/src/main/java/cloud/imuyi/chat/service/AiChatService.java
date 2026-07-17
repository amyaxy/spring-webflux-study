package cloud.imuyi.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AiChatService {
    private final ChatService chatService;

    @Retryable(maxRetries = 3, delay = 200, multiplier = 2)
    public Flux<String> chatStream(String conversationId, String message) {
        String[] tokens = {
            "[AGENT_START]",
            "你好！我是 AI 助手。",
            "你问的问题是：" + message,
            "让我来帮你分析一下……",
            "根据你的需求，我建议从以下几个方面入手：",
            "1. 首先理解核心概念",
            "2. 然后搭建基础框架",
            "3. 最后逐步完善功能",
            "希望这个回答对你有帮助！",
            "[AGENT_END]"
        };
        return Flux.fromArray(tokens)
                .delayElements(Duration.ofMillis(300))
                .doOnComplete(() ->
                    chatService.sendMessage(conversationId, "ai-bot",
                        String.join("\n", tokens)).subscribe()
                );
    }
}