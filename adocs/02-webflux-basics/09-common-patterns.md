# 09. 速查表 & 常见模式

> 阶段二一站式参考——开发现场随手查。

---

## 1. 注解式 vs 函数式速查

| 场景 | 注解式 | 函数式 |
|------|--------|--------|
| GET 列表 | `Flux<T> findAll()` | `ServerResponse.ok().body(service.findAll(), T.class)` |
| GET 单个 | `Mono<T> findById(@PathVariable Long id)` | `service.findById(id).flatMap(user → ok().bodyValue(user)).switchIfEmpty(notFound())` |
| POST 创建 | `Mono<T> create(@Valid @RequestBody T body)` / `@ResponseStatus(201)` | `request.bodyToMono(T.class).flatMap(body → ok().bodyValue(service.create(body)))` |
| DELETE | `Mono<Void> delete(@PathVariable Long id)` / `@ResponseStatus(204)` | `service.delete(id).then(noContent().build())` |
| 校验失败 | 自动 `@Valid` → 400 | 手动 `validator.validate()` → `badRequest()` |
| 404 处理 | `switchIfEmpty(Mono.error(ResponseStatusException(404)))` | `switchIfEmpty(notFound().build())` |

---

## 2. 请求方法速查

```java
// GET
webClient.get().uri("/users")...

// POST
webClient.post().uri("/users")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)...

// PUT
webClient.put().uri("/users/{id}", id)...

// PATCH
webClient.patch().uri("/users/{id}", id)...

// DELETE
webClient.delete().uri("/users/{id}", id)...
```

---

## 3. 响应状态码速查

| 状态码 | 注解式 | 函数式 |
|--------|--------|--------|
| 200 OK | 默认 | `ServerResponse.ok()` |
| 201 Created | `@ResponseStatus(CREATED)` | `ServerResponse.status(201)` |
| 204 No Content | `@ResponseStatus(NO_CONTENT)` | `ServerResponse.noContent().build()` |
| 400 Bad Request | `@Valid` 失败自动 | `ServerResponse.badRequest()` |
| 404 Not Found | `ResponseStatusException(404)` | `ServerResponse.notFound().build()` |
| 429 Too Many | `ResponseStatusException(429)` | `ServerResponse.status(429)` |
| 500 Internal | 未捕获异常自动 | 未捕获异常自动 |

---

## 4. MediaType 常用值

```java
MediaType.APPLICATION_JSON                // application/json
MediaType.TEXT_EVENT_STREAM_VALUE         // text/event-stream
MediaType.APPLICATION_NDJSON              // application/x-ndjson
MediaType.TEXT_PLAIN                      // text/plain
```

---

## 5. Sinks 模式速查

```java
// 多播（所有订阅者）
Sinks.many().multicast().onBackpressureBuffer();

// 单播（仅一个订阅者）
Sinks.many().unicast().onBackpressureBuffer();

// 缓存最新 N 条
Sinks.many().replay().limit(10);
Sinks.many().replay().latest();

// 单值（Mono 语义）
Sinks.one();

// 发射结果
sink.tryEmitNext(value);   // 非阻塞，返回 EmissionResult
sink.tryEmitComplete();
sink.tryEmitError(ex);
```

---

## 6. WebFilter 执行顺序

```java
@Component @Order(1)   // 最早执行
@Component @Order(2)
@Component @Order(Integer.MAX_VALUE)  // 最晚执行
```

---

## 7. 常见 JSON Path 断言

```java
// 对象
$.name           → "Alice"
$.address.city   → "Beijing"

// 数组
$[0].name        → 第一个元素的 name
$[0]             → 第一个元素
$.length()       → 数组长度

// 通配
$..price         → 递归查找所有 price 字段
```

---

## 8. 配置项

```yaml
# application.yml
server:
  port: 8080

spring:
  webflux:
    base-path: /api              # 所有路由前缀
  jackson:
    default-property-inclusion: non_null  # JSON 不输出 null

logging:
  level:
    cloud.imuyi.webflux: DEBUG
    org.springframework.web.reactive: INFO
```

---

## 9. 常见问题

### Q: `@Valid` 为什么不生效？

**答**：确认 pom 中有 `spring-boot-starter-validation` 依赖。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### Q: WebTestClient 自动装配 `@Autowired` 不生效？

**答**：Spring Boot 4.x 中 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 不再自动注入 `WebTestClient`。改用 `@LocalServerPort` + `WebTestClient.bindToServer()` 手动创建。

### Q: 函数式路由无法与 base-path 配合？

**答**：`spring.webflux.base-path` 对注解式 `@RequestMapping` 和函数式 `RouterFunction` 都起作用。如果发现路径叠加（如 `/api/api/users`），检查是否在 Controller 的 `@RequestMapping` 中已经写了 `/api`。

### Q: SSE 测试一直等待不结束？

**答**：SSE 是无限流，用 `.take(Duration)` + `.thenCancel()` 控制接收时间。不要用 `verifyComplete()`，用 `thenCancel()`。

```java
.take(Duration.ofSeconds(3))
.as(StepVerifier::create)
.expectNextCount(1)
.thenCancel()
.verify(Duration.ofSeconds(5));
```

### Q: 修改代码后 WebTestClient 版本使用？

**答**：用 `@SpringBootTest(properties = {"test.group=…"})` 隔离不同测试类的 Spring Context，避免数据污染。详见 [08-webtestclient.md](08-webtestclient.md#5-测试隔离策略)。