# 05. SSE 实时推送

> SSE（Server-Sent Events）是 WebFlux 最典型的应用场景——服务端持续推送事件流，客户端通过 `EventSource` 接收。相比 WebSocket，SSE **单向**（服务端→客户端），**轻量**（基于 HTTP），天然适配**行情推送**、**日志流**、**AI 流式对话**等场景。

---

## 1. 核心概念

### SSE vs WebSocket

| 维度 | SSE | WebSocket |
|------|-----|-----------|
| 方向 | 服务端 → 客户端（单向） | 双向 |
| 传输层 | HTTP（长连接） | TCP |
| 自动重连 | ✅ 内置 | ❌ 需要手动实现 |
| 消息格式 | `text/event-stream`（纯文本） | 二进制 / 文本 |
| 适用场景 | 行情推送、日志流、AI 对话 | 聊天室、游戏 |

### 数据格式

```
data: {"code":"AAPL","price":189.50,"changePercent":0.85,"timestamp":"2026-07-16T11:30:00Z"}

data: {"code":"AAPL","price":190.12,"changePercent":1.18,"timestamp":"2026-07-16T11:30:01Z"}
```

每两条消息之间用两个换行符隔开。

---

## 2. 服务端实现

### Controller

```java
@RestController
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping(value = "/stocks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StockPrice> allStocks() {
        return stockService.allPrices();
    }

    @GetMapping(value = "/stocks/{code}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StockPrice> stockByCode(@PathVariable String code) {
        return stockService.priceByCode(code);
    }
}
```

**关键**：`produces = MediaType.TEXT_EVENT_STREAM_VALUE` 告诉 Spring 这是 SSE 端点，返回值按 SSE 格式序列化。

### 数据源（Sinks.Many）

```java
@Service
public class StockService {

    @PostConstruct
    void init() {
        Flux.interval(Duration.ofSeconds(1)).subscribe(tick -> {
            var stock = new StockPrice("AAPL", "Apple Inc.",
                    189.50 + Math.random() * 2,
                    (Math.random() - 0.5) * 2,
                    Instant.now());
            var result = sink.tryEmitNext(stock);
            if (result.isFailure()) {
                log.warn("Backpressure: stock dropped");
            }
        });
    }

    public Flux<StockPrice> allPrices() {
        return sink.asFlux();
    }
}
```

### Sinks 类型选择

```java
// 多播 — 所有订阅者都收到（默认选择）
Sinks.many().multicast().onBackpressureBuffer();

// 单播 — 只允许一个订阅者
Sinks.many().unicast().onBackpressureBuffer();

// 仅最新 — 新订阅者只收到最新值
Sinks.many().replay().latest();
```

**推荐**：ESB 模式用 `multicast().onBackpressureBuffer()`，确保所有订阅者都能接收到。

---

## 3. 客户端接收

### 浏览器 JavaScript

```javascript
const eventSource = new EventSource('/api/stocks/AAPL');
eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log(`AAPL: $${data.price}`);
};
eventSource.onerror = (err) => {
    console.error('SSE error:', err);
    // EventSource 会自动重连
};
```

### WebClient（Java 服务端调用）

```java
WebClient client = WebClient.create("http://localhost:8080");

client.get().uri("/stocks")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .bodyToFlux(String.class)
        .subscribe(event -> log.info("SSE: {}", event));
```

### Spring 的 ServerSentEvent<T>

```java
@GetMapping(value = "/stocks/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<StockPrice>> stockSse() {
    return stockService.allPrices()
            .map(price -> ServerSentEvent.<StockPrice>builder(price)
                    .event("stock")           // event 类型
                    .id(price.timestamp().toString())  // 事件 ID
                    .retry(Duration.ofSeconds(3))       // 重连间隔
                    .build());
}
```

---

## 4. 测试 SSE

```java
@Test
@DisplayName("GET /stocks — 返回 SSE 流，至少收到 1 条数据")
void stockStream() {
    webClient.get().uri("/stocks")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .take(Duration.ofSeconds(3))
            .as(StepVerifier::create)
            .expectNextCount(1)
            .thenCancel()
            .verify(Duration.ofSeconds(5));
}
```

**要点**：使用 `take(Duration)` 限制接收时间，避免测试无限等待。`thenCancel()` 显式取消。

---

## 5. 完整流程

```
Client                          Server (Netty)
  │                                │
  │── GET /stocks ────────────────→│
  │                                │  Sinks.Many<StockPrice>
  │                                │    ↑
  │                                │  Flux.interval(1s)
  │                                │
  │←── SSE: data: {...}\n\n ──────│  t=1s
  │←── SSE: data: {...}\n\n ──────│  t=2s
  │←── SSE: data: {...}\n\n ──────│  t=3s
  │  ...                          │  ...
  │── [断开连接] ─────────────────│
  │                                │  Disposable 自动取消
```

**自动清理**：客户端断开连接时，对应的 `Subscription` 自动取消，`Sinks.Many` 不会泄漏。

---

## 📝 总结

| 概念 | 一句话 |
|------|--------|
| SSE | 基于 HTTP 的服务端推送，`TEXT_EVENT_STREAM` |
| Sinks.Many | 创建热流 Publisher，用于推送实时数据 |
| multicast() | 多播模式，所有订阅者均能收到 |
| Flux.interval | 定时器，与 SSE 天然配合 |
| 自动重连 | 浏览器 EventSource 内置重连机制 |
| ServerSentEvent<T> | Spring 封装的 SSE 事件对象，支持 event/ID/retry 字段 |