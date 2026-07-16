# 06. WebClient 客户端

> WebClient 是 WebFlux 的响应式 HTTP 客户端，替代传统的 `RestTemplate`。支持 `Mono<T>` / `Flux<T>` 响应式调用，天然适配 SSE 流式接收。

---

## 1. 创建 WebClient

```java
// 简单创建
WebClient client = WebClient.create("http://localhost:8080");

// Builder 模式 — 自定义配置
WebClient client = WebClient.builder()
        .baseUrl("http://localhost:8080")
        .defaultHeader("X-Trace-Id", UUID.randomUUID().toString())
        .defaultCookie("session", "abc123")
        .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .filter(ExchangeFilterFunction.ofRequestProcessor(
            request -> Mono.just(ExchangeRequest.from(request)
                .header("X-Requested-With", "WebClient"))))
        .build();
```

---

## 2. retrieve() — 简单取值

```java
// GET 列表 → Flux<T>
Flux<User> users = client.get()
        .uri("/users")
        .retrieve()
        .bodyToFlux(User.class);

// GET 单个 → Mono<T>
Mono<User> user = client.get()
        .uri("/users/{id}", 1)
        .retrieve()
        .bodyToMono(User.class);

// POST → Mono<T>
Mono<User> created = client.post()
        .uri("/users")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new CreateUserRequest("Diana", "diana@example.com", 26))
        .retrieve()
        .bodyToMono(User.class);

// DELETE → Mono<Void>
Mono<Void> deleted = client.delete()
        .uri("/users/{id}", 1)
        .retrieve()
        .bodyToMono(Void.class);
```

### 处理 4xx/5xx

```java
Mono<User> result = client.get()
        .uri("/users/999")
        .retrieve()
        .onStatus(HttpStatus.NOT_FOUND::equals,
            response -> Mono.error(new RuntimeException("User not found")))
        .bodyToMono(User.class);
```

---

## 3. exchangeToMono() — 精细控制

当需要读取响应状态码、Header 时用 `exchangeToMono()`：

```java
client.get()
        .uri("/users/1")
        .exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(User.class);
            } else if (response.statusCode() == HttpStatus.NOT_FOUND) {
                return Mono.empty();
            } else {
                return response.createException()
                        .flatMap(Mono::error);
            }
        });
```

### retrieve() vs exchangeToMono()

| 维度 | retrieve() | exchangeToMono() |
|------|-----------|-----------------|
| 简洁性 | ✅ 一行搞定 | ❌ 需要自己处理 |
| 状态码处理 | `onStatus()` 回调 | 完全自控 |
| Header 读取 | ❌ | ✅ |
| 推荐度 | ✅ 首选 | 特殊场景使用 |

> ⚠️ `exchangeToMono()` 需要手动管理响应的消费，忘记消费可能导致连接泄漏。如无特殊需求，优先用 `retrieve()`。

---

## 4. SSE 流式接收

```java
// 接收 SSE 流
Flux<String> events = client.get()
        .uri("/stocks")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .bodyToFlux(String.class);

// 订阅处理
events.subscribe(
    event -> log.info("SSE: {}", event),
    error -> log.error("SSE error", error),
    () -> log.info("SSE stream ended")
);
```

---

## 5. 超时与重试

```java
// 超时
Mono<User> result = client.get()
        .uri("/users/1")
        .retrieve()
        .bodyToMono(User.class)
        .timeout(Duration.ofSeconds(5))
        .onErrorResume(TimeoutException.class,
            e -> Mono.just(fallbackUser));

// 重试
client.get()
        .uri("/external/api")
        .retrieve()
        .bodyToMono(String.class)
        .retry(3)  // 最多重试 3 次
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
            .filter(throwable -> throwable instanceof WebClientResponseException.ServiceUnavailable));
```

---

## 6. Filters（过滤器）

```java
// 日志过滤器
WebClient client = WebClient.builder()
        .filter(ExchangeFilterFunction.ofRequestProcessor(
            request -> {
                log.info("Request: {} {}", request.method(), request.url());
                return Mono.just(request);
            }))
        .filter(ExchangeFilterFunction.ofResponseProcessor(
            response -> {
                log.info("Response: {}", response.statusCode());
                return Mono.just(response);
            }))
        .build();
```

---

## 7. 并发聚合

WebClient 最强大的场景——并发调多个外部 API 并聚合：

```java
// 并发查两个外部 API
Mono<UserDetail> result = Mono.zip(
    client.get().uri("/user/1").retrieve().bodyToMono(User.class),
    client.get().uri("/user/1/orders").retrieve().bodyToFlux(Order.class).collectList()
).map(tuple -> {
    var user = tuple.getT1();
    var orders = tuple.getT2();
    return new UserDetail(user, orders);
});
```

---

## 📝 总结

| 概念 | 一句话 |
|------|--------|
| `retrieve()` | 简单取值，首选 |
| `exchangeToMono()` | 需要读取 Header/状态码时使用 |
| `.bodyToMono(Class)` | 响应体转为 `Mono<T>` |
| `.bodyToFlux(Class)` | 响应体转为 `Flux<T>`，SSE 流式接收 |
| `onStatus()` | 自定义 HTTP 错误处理 |
| `timeout()` | 超时控制，配合 `onErrorResume` 降级 |
| `retry()` / `retryWhen()` | 重试策略 |