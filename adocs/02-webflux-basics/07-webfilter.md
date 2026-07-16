# 07. WebFilter 过滤链

> WebFilter 是 WebFlux 的过滤器接口，类似 Servlet 的 Filter，用于在请求处理前后做横切逻辑——日志、限流、认证、Trace ID 注入等。

---

## 1. 核心接口

```java
public interface WebFilter {
    Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain);
}
```

- `ServerWebExchange` — 请求/响应封装，可读写 Request / Response
- `WebFilterChain` — 过滤器链，调用 `chain.filter(exchange)` 转到下一个过滤器

---

## 2. 请求日志过滤器

```java
@Component
@Order(1)
public class LoggingWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        long start = System.currentTimeMillis();

        return chain.filter(exchange).doFinally(signalType -> {
            long elapsed = System.currentTimeMillis() - start;
            var response = exchange.getResponse();
            String status = signalType == SignalType.ON_COMPLETE
                    ? String.valueOf(response.getStatusCode().value())
                    : "onError";
            log.info("[{}] {} -> {} ({}ms, {})",
                    request.getMethod(),
                    request.getURI().getPath(),
                    status,
                    elapsed,
                    signalType.name());
        });
    }
}
```

### 日志输出示例

```
[GET] /users -> 200 (18ms, onComplete)
[GET] /users/999 -> 404 (24ms, onError)
[POST] /users -> 201 (89ms, onComplete)
```

**关键点**：
- `doFinally` 确保无论正常完成还是错误都能记录
- `chain.filter(exchange)` 之前记录开始时间
- 通过 `exchange.getResponse().getStatusCode()` 获取响应状态码

---

## 3. IP 限流过滤器（Sliding Window）

```java
@Component
@Order(2)
public class RateLimitWebFilter implements WebFilter {

    private static final int MAX_REQUESTS = 10;   // 每窗口 10 次
    private static final Duration WINDOW = Duration.ofSeconds(1); // 1s 窗口

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        var window = windows.computeIfAbsent(ip, k -> new Window());
        if (window.isExceeded()) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        window.record();
        return chain.filter(exchange);
    }

    static class Window {
        private final Deque<Instant> timestamps = new ConcurrentLinkedDeque<>();

        boolean isExceeded() {
            purge();
            return timestamps.size() >= MAX_REQUESTS;
        }

        void record() {
            timestamps.addLast(Instant.now());
        }

        private void purge() {
            var cutoff = Instant.now().minus(WINDOW);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
        }
    }
}
```

---

## 4. 过滤器执行顺序

通过 `@Order` 注解控制：

```java
@Component @Order(1)   // 最先执行：日志 → 向下传递 → 响应日志
public class LoggingWebFilter implements WebFilter { ... }

@Component @Order(2)   // 其次执行：限流检查 → 通过 → 向下传递
public class RateLimitWebFilter implements WebFilter { ... }
```

```
Request In
  │
  ▼
LoggingWebFilter (Order=1)   ──记录开始时间──→
  │
  ▼
RateLimitWebFilter (Order=2) ──检查限流──→
  │                                     ❌ 超过 → 429
  ▼                                     ↓
… 其他过滤器 …                        响应
  │
  ▼
Controller / Handler
  │
  ▼
  ←── LoggingWebFilter doFinally 记录耗时
```

---

## 5. 常见 WebFilter 用途

| 用途 | 实现方式 |
|------|----------|
| **请求日志** | `doFinally` 记录耗时 + 状态码 |
| **IP 限流** | 滑动窗口计数器 |
| **Trace ID 注入** | 在 Request Header 中注入 UUID |
| **JWT 校验** | 提取 Token → 校验 → 注入 SecurityContext |
| **CORS** | 设置响应头 `Access-Control-*` |
| **请求体大小限制** | 检查 `Content-Length` Header |

### CORS WebFilter 示例

```java
@Component
@Order(0)
public class CorsWebFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var response = exchange.getResponse();
        response.getHeaders().add("Access-Control-Allow-Origin", "*");
        response.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
        response.getHeaders().add("Access-Control-Allow-Headers", "*");

        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            response.setStatusCode(HttpStatus.NO_CONTENT);
            return response.setComplete();
        }
        return chain.filter(exchange);
    }
}
```

---

## 📝 总结

| 概念 | 一句话 |
|------|--------|
| WebFilter | WebFlux 过滤器接口，`filter(ServerWebExchange, WebFilterChain)` |
| `@Order` | 控制过滤器执行顺序，值越小越优先 |
| `chain.filter(exchange)` | 继续执行后续过滤器和处理器 |
| `exchange.getResponse()` | 可以修改响应状态码和 Header |
| `doFinally` | 在过滤器链完成后执行的回调 |