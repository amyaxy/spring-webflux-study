# 07 · 响应式测试

## 概述

响应式应用的测试主要使用 `StepVerifier` 测试 Publisher 行为和 `WebTestClient` 测试 REST 端点。

## WebTestClient（Spring Boot 4.x 注意）

Spring Boot 4.x 移除了 `@AutoConfigureWebTestClient`，需要手动创建：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void shouldCreateAndRetrieveUser() {
        var client = webTestClient();

        client.post().uri(uriBuilder -> uriBuilder
                        .path("/api/users")
                        .queryParam("username", "alice")
                        .queryParam("displayName", "Alice Wang")
                        .build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo("alice");
    }
}
```

## StepVerifier 测试

测试响应式流的核心工具：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Test
    void shouldSendAndReceiveMessage() {
        // 验证发送成功
        chatService.sendMessage("room1", "alice", "Hello!")
                .as(StepVerifier::create)
                .assertNext(msg -> {
                    assertEquals("room1", msg.getChatRoom());
                    assertEquals("alice", msg.getSender());
                    assertNotNull(msg.getId());
                })
                .verifyComplete();

        // 验证历史查询
        chatService.historyByRoom("room1")
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldStreamMessagesLive() {
        var stream = chatService.streamByRoom("room2");
        chatService.sendMessage("room2", "bob", "Live!").subscribe();
        stream.as(StepVerifier::create)
                .assertNext(msg -> assertEquals("Live!", msg.getContent()))
                .thenCancel()
                .verify();
    }
}
```

## StepVerifier 常用方法

| 方法 | 用途 |
|------|------|
| `expectNext(T)` | 期望下一个元素为 T |
| `expectNextCount(n)` | 期望后续有 n 个元素 |
| `assertNext(Consumer)` | 断言下一个元素属性 |
| `thenCancel()` | 取消流（无限流测试） |
| `verifyComplete()` | 验证流正常结束 |
| `verifyError()` | 验证流抛出异常 |
| `verifyTimeout(Duration)` | 验证超时 |

## 踩坑记录

### 1. WebTestClient 不能 @Autowired

Spring Boot 4.x 移除了 `@AutoConfigureWebTestClient` webflux 支持，必须手动 `WebTestClient.bindToServer()` + `@LocalServerPort`。

### 2. StepVerifier 的时序问题

`shouldStreamMessagesLive` 测试中，先订阅流，再发送消息：

```java
var stream = chatService.streamByRoom("room2");
chatService.sendMessage("room2", "bob", "Live!").subscribe(); // 先订阅再发
```

如果交换顺序，消息可能在订阅前已经发出，导致断言失败。

### 3. assert 关键字不可用

Java 21 默认禁用 `assert` 关键字（需 `-ea` JVM 参数），应使用 JUnit 的 `Assertions` 静态方法：

```java
import static org.junit.jupiter.api.Assertions.*;
assertEquals(expected, actual);     // ✅
assert expected.equals(actual);    // ❌ 需要 -ea 参数
```