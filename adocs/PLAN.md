# Spring Framework 7.x 响应式编程 — 学习计划

> 仓库：https://github.com/amyaxy/spring-webflux-study
>
> Spring Framework 7.0（GA 2025-11）· Spring Boot 4.1 · Java 21+

---

## 🎯 学习目标

系统掌握 Spring Framework 7.x 的响应式编程能力，包括：

- **Reactor 核心**：Mono/Flux 操作符、背压、调度器
- **Spring WebFlux**：注解式/函数式端点、WebClient、SSE
- **Spring 7.x 新特性**：`@Retryable`、`@ConcurrencyLimit`、API Versioning
- **响应式数据访问**：R2DBC、响应式事务
- **Virtual Threads + WebFlux 混合架构**：Spring 7.x 推荐的最佳实践

---

## 📚 学习阶段

### 阶段一：响应式编程基础 & Reactor 核心（Week 1-2）

**目标**：扎实理解 Reactive Streams 规范、Project Reactor 的 `Mono`/`Flux`

| 主题 | 知识点 | 实践项目 |
|------|--------|----------|
| Reactive Streams 规范 | Publisher / Subscriber / Subscription / Processor | 手写自定义 Publisher |
| Reactor 核心 | Mono\<T\>, Flux\<T\>, 冷/热流 | 数据转换练习 |
| 操作符实战 | map/flatMap/filter/zip/merge/concat/switchIfEmpty | 链式处理 JSON 数据流 |
| 错误处理 | onErrorReturn/onErrorResume/retry/timeout | 指数退避重试 |
| 背压机制 | request(n), onBackpressureBuffer/Drop/Latest | 慢消费者背压观察 |
| 调度器 | Schedulers.parallel()/boundedElastic()/immediate() | 线程模型对比 |

**产出**：`reactor-playground/` — Reactor 操作符练习项目

---

### 阶段二：Spring WebFlux 核心框架（Week 3-4）

**目标**：掌握 WebFlux 的 Controller 层、函数式端点、WebClient

| 主题 | 知识点 | 实践项目 |
|------|--------|----------|
| WebFlux 架构 | DispatcherHandler、HttpHandler、Netty | 对比 Spring MVC |
| 注解式 Controller | @RestController + Mono/Flux | CRUD API |
| 函数式端点 | RouterFunction / HandlerFunction | 路由 DSL 重写 API |
| 请求处理 | @RequestBody、@RequestParam、@PathVariable | 参数校验 + 异常处理 |
| 响应处理 | ServerResponse、ResponseEntity、SSE | 股票行情 SSE 推送 |
| WebClient | retrieve() vs exchange()、Filters | 外部 API 流式聚合 |
| 过滤与拦截 | WebFilter、HandlerFilterFunction | 请求日志 + 限流 |

**产出**：`reactive-api/` — 响应式 REST API 项目

---

### 阶段三：Spring 7.x 新特性 — 弹性与 API 版本化（Week 5）

**目标**：掌握 Spring 7.x 内置的 Resilience 特性 + API Versioning

| 主题 | 知识点 | 实践项目 |
|------|--------|----------|
| `@Retryable` | includes/excludes、maxRetries、delay、multiplier、jitter、maxDelay | 指数退避重试 |
| `RetryTemplate` | 编程式 RetryPolicy、自定义 BackOff | 复杂场景重试 |
| `@ConcurrencyLimit` | 并发限流（Virtual Threads 场景） | 保护下游资源 |
| `@EnableResilientMethods` | 开启 Resilience 注解 | 统一配置 |
| 响应式重试 | @Retryable 作用于 Mono/Flux 方法 | Reactive retry 验证 |
| API Versioning | 媒体类型版本解析、废弃标记、版本集校验 | 多版本 API |
| `HttpMessageConverters` | 集中式 Message Converter 配置 | 自定义序列化器 |

**产出**：`resilient-api/` — 弹性增强版 API 项目

---

### 阶段四：数据访问 & 高阶实战（Week 6-8）

**目标**：R2DBC 响应式数据库 + 端到端实战案例

| 主题 | 知识点 | 实践项目 |
|------|--------|----------|
| R2DBC 基础 | ReactiveCrudRepository、R2dbcEntityTemplate | PostgreSQL CRUD |
| 响应式事务 | @Transactional 传播边界 | 理解响应式事务 |
| 全链路响应式 | WebFlux + R2DBC + WebClient | 三层架构 API |
| WebSocket | WebSocketHandler、WebSocketSession | 聊天室 |
| 测试 | WebTestClient、StepVerifier | 端到端测试 |
| 可观测性 | Micrometer Observation、OpenTelemetry | 链路追踪接入 |

**产出**：`reactive-chat/` — 完整响应式全栈项目

---

## 🧪 实战案例

### 案例 1：SSE 实时行情推送

```
GET /api/stocks/{code}/stream
→ text/event-stream
→ 每 1s 推送模拟价格
→ 客户端断开自动取消订阅
```

**技术点**：`Flux.interval()`、`Sinks.Many`、`MediaType.TEXT_EVENT_STREAM_VALUE`、`Disposable`

**所在阶段**：阶段二产出 `reactive-api/`

---

### 案例 2：弹性 API 网关

```
WebClient ──→ WebFlux 网关层 ──→ 下游服务（5% 超时）
                │ @Retryable(4)
                │ @ConcurrencyLimit(20)
                │ API Version 1 vs 2
```

**技术点**：Spring 7.x `@Retryable`（指数退避 + jitter）、`@ConcurrencyLimit`、API Versioning

**所在阶段**：阶段三产出 `resilient-api/`

---

### 案例 3：响应式 AI 聊天 SSE

基于事件分派器模式（AGENT_START / TEXT_BLOCK_DELTA / THINKING_BLOCK_DELTA / AGENT_END）：

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ChatEvent>> streamChat(@RequestBody ChatRequest request) {
    return aiService.chatStream(request.getConversationId(), request.getMessage())
        .map(event -> ServerSentEvent.<ChatEvent>builder(event)
            .event(event.getType().name())
            .build());
}

@Retryable(includes = {ServiceUnavailableException.class, TimeoutException.class},
           maxRetries = 3, delay = 100, multiplier = 2, jitter = 10)
public Flux<ChatEvent> chatStream(String conversationId, String message) {
    return modelClient.stream(conversationId, message);
}
```

**所在阶段**：阶段四产出 `reactive-chat/`

---

### 案例 4：Virtual Threads + WebFlux 混合架构

Spring 7.x 推荐的"最佳实践"——各取所长：

```java
@RestController
public class HybridController {

    // ① 纯 I/O → Virtual Threads（简单）
    @GetMapping("/api/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    // ② 流式 → WebFlux（不可替代）
    @GetMapping(value = "/api/events/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Event> streamEvents() {
        return eventService.subscribe();
    }

    // ③ 混合：Virtual Threads 处理业务 + WebFlux 流式输出
    @PostMapping(value = "/api/reports/stream",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ReportChunk> streamReport(@RequestBody ReportRequest request) {
        var report = reportService.generateReport(request);  // VT 阻塞
        return Flux.fromIterable(report.chunks());           // Flux 流式
    }
}
```

**所在阶段**：贯穿阶段四（配置 `spring.threads.virtual.enabled=true`）

---

## 📂 项目结构

```
spring-webflux-study/
├── adocs/
│   ├── PLAN.md                              # 学习计划（本文件）
│   │
│   ├── 01-reactor-core/                     # 阶段一：响应式编程基础
│   │   ├── README.md                        # 阶段概要笔记
│   │   ├── 01-flux-interval.md              # 知识点：Flux.interval
│   │   └── ...                              # 后续知识点
│   │
│   ├── 02-webflux-basics/                   # 阶段二：WebFlux 核心框架
│   │   ├── README.md                        # 阶段概要笔记 + 目录索引
│   │   ├── 01-webflux-architecture.md        # WebFlux 架构概览
│   │   ├── 02-annotated-controller.md        # 注解式 Controller
│   │   ├── 03-functional-endpoints.md        # 函数式端点（RouterFunction）
│   │   ├── 04-request-validation.md           # 请求参数与校验
│   │   ├── 05-server-sent-events.md           # SSE 实时推送
│   │   ├── 06-webclient.md                    # WebClient 客户端
│   │   ├── 07-webfilter.md                    # WebFilter 过滤链
│   │   ├── 08-webtestclient.md                # WebTestClient 测试
│   │   └── 09-common-patterns.md              # 速查表 & 常见模式
│   │
│   ├── 03-resilience/                       # 阶段三：弹性与 API 版本化
│   │   ├── README.md                        # 阶段概要笔记
│   │   ├── 01-xxx.md                        # 知识点
│   │   └── ...
│   │
│   └── 04-advanced-practice/                # 阶段四：数据访问 & 高阶实战
│       ├── README.md                        # 阶段概要笔记
│       ├── 01-xxx.md                        # 知识点
│       └── ...
│
├── pom.xml                         # 父 POM（Spring Boot 4.1）
├── reactor-playground/             # 阶段一：Reactor 操作符练习
├── reactive-api/                   # 阶段二：WebFlux REST API
├── resilient-api/                  # 阶段三：弹性特性演示
└── reactive-chat/                  # 阶段四：完整全栈案例
```

### 文档组织约定

```
adocs/{阶段目录}/
├── README.md          # 阶段概要笔记（该阶段整体知识点梳理）
├── 01-{topic}.md      # 知识点详解 ①
├── 02-{topic}.md      # 知识点详解 ②
└── ...
```

---

## 🔧 技术栈

| 组件 | 版本     |
|------|--------|
| Spring Boot | 4.1.x  |
| Spring Framework | 7.0.x  |
| Java | 21+    |
| Project Reactor | 2025.x |
| R2DBC | 里程碑版本  |
| Netty | 最新     |
| Maven | 3.9+   |

---

## 🚀 开始

请从阶段一 Reactor 操作符练习开始，逐步推进。