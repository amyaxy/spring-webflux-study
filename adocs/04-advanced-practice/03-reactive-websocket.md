# 03 · WebSocket 实时通信

## 概述

Spring WebFlux 使用 `WebSocketHandler` 实现响应式 WebSocket，配合 `Sinks.Many` 实现房间级消息广播。

## 配置 WebSocket 路由

```java
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(ChatWebSocketHandler handler) {
        return new SimpleUrlHandlerMapping(
            Map.of("/ws/chat/{room}", handler), 1);
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
```

## 实现 WebSocketHandler

```java
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String room = extractRoom(session);

        // 输入：接收客户端消息 → 保存到数据库
        Mono<Void> input = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .flatMap(text -> {
                    Map<String, String> map = objectMapper.readValue(text, Map.class);
                    return chatService.sendMessage(room,
                            map.get("sender"), map.get("content"));
                })
                .then();

        // 输出：历史消息 + 实时流 → 推送客户端
        Flux<String> output = chatService.historyByRoom(room)
                .concatWith(chatService.streamByRoom(room))
                .map(this::toJson);

        return Mono.when(input, session.send(output.map(session::textMessage)));
    }
}
```

## Sinks.Many 实时广播

`Sinks.Many` 是 Reactor 3.5+ 引入的发布者，支持多播（多个订阅者）：

```java
private final Map<String, Sinks.Many<ChatMessage>> roomSinks = new ConcurrentHashMap<>();

private Sinks.Many<ChatMessage> sinkForRoom(String room) {
    return roomSinks.computeIfAbsent(room, k ->
            Sinks.many().multicast().onBackpressureBuffer(256));
}

public Flux<ChatMessage> streamByRoom(String room) {
    return sinkForRoom(room).asFlux();
}
```

### Sinks 类型选择

| 类型 | 行为 | 适用场景 |
|------|------|---------|
| `unicast()` | 单订阅者 | 点对点消息 |
| `multicast()` | 多订阅者，晚加入者不接收历史 | 聊天室广播 |
| `replay()` | 多订阅者，新订阅者接收指定历史 | 重放场景 |

## 踩坑记录

### `tryEmitNext` 与 `emitNext`

- `tryEmitNext`：非阻塞尝试，失败返回 `EmitResult.FAIL_NON_SERIALIZED`
- `emitNext`：阻塞直到成功，适合错误不敏感场景

聊天室场景推荐 `tryEmitNext`，配合 `doOnNext` 确保消息已经持久化。