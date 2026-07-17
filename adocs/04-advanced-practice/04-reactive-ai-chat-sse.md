# 04 · AI Chat SSE

## 概述

Server-Sent Events（SSE）通过 `Flux<ServerSentEvent>` 实现 AI 响应内容的流式输出，与 WebSocket 不同，SSE 是单向的（服务器→客户端）。

## SSE 端点

```java
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final AiChatService aiChatService;

    @PostMapping(value = "/ai/stream",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithAI(@RequestParam String conversationId,
                                   @RequestParam String message) {
        return aiChatService.chatStream(conversationId, message);
    }
}
```

## AI 流式服务

模拟 AI 对话的流式输出，使用 `delayElements` 模拟逐 token 输出：

```java
@Service
@RequiredArgsConstructor
public class AiChatService {

    @Retryable(maxRetries = 3, delay = 200, multiplier = 2)
    public Flux<String> chatStream(String conversationId, String message) {
        String[] tokens = { "你好！", "你问的是：" + message, "让我分析一下……" };
        return Flux.fromArray(tokens)
                .delayElements(Duration.ofMillis(300))
                .doOnComplete(() ->
                    chatService.sendMessage(conversationId, "ai-bot",
                        String.join("\n", tokens)).subscribe()
                );
    }
}
```

## SSE vs WebSocket

| 特性 | SSE | WebSocket |
|------|-----|-----------|
| 方向 | 单向（服务器→客户端） | 双向 |
| 协议 | HTTP | WS |
| 自动重连 | 内置 | 需自行实现 |
| 二进制 | 不支持 | 支持 |
| 浏览器兼容 | EventSource API | WebSocket API |
| 适用场景 | 推送通知、AI 流式响应 | 聊天室、实时协作 |

## @Retryable 的重试策略

使用加权退避 + 抖动：

```java
@Retryable(
    maxRetries = 3,
    delay = 200,       // 初始延迟 200ms
    multiplier = 2,    // 每次翻倍
    jitter = 100       // 抖动 100ms 防止惊群
)
```

实际延迟：`200 + random(0,100)` → `400 + random(0,100)` → `800 + random(0,100)`。