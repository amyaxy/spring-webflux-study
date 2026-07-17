# spring-webflux-study

针对 流式响应 / SSE / WebSocket 等场景，WebFlux 背压优势不可替代。

---

## 项目模块

| 模块 | 阶段 | 说明 |
|------|------|------|
| `reactor-playground` | 阶段一 | Reactor Core 基础语法实验：Flux/Mono、操作符、错误处理、背压、调度器 |
| `reactive-api` | 阶段二 | 响应式 CRUD、SSE 行情推送、Virtual Threads 混合架构 |
| `reactive-chat` | 阶段四 | R2DBC + WebSocket + SSE AI Chat + Virtual Threads 混合 + 可观测性 |

---

## 学习笔记

- [`📄 学习计划`](./adocs/PLAN.md)
- **阶段一：Reactor Core** — [`adocs/01-reactor-core/`](./adocs/01-reactor-core/)
  - [`01-flux-interval`](./adocs/01-reactor-core/01-flux-interval.md) — Flux.interval 定时序列
  - [`02-operators`](./adocs/01-reactor-core/02-operators.md) — 常用操作符
  - [`03-error-handling`](./adocs/01-reactor-core/03-error-handling.md) — 错误处理策略
  - [`04-backpressure`](./adocs/01-reactor-core/04-backpressure.md) — 背压机制
  - [`05-schedulers`](./adocs/01-reactor-core/05-schedulers.md) — 调度器与线程模型
  - [`06-common-methods`](./adocs/01-reactor-core/06-common-methods.md) — 常见 API 速查
- **阶段二：WebFlux 基础** — [`adocs/02-webflux-basics/`](./adocs/02-webflux-basics/)
  - [`01-webflux-architecture`](./adocs/02-webflux-basics/01-webflux-architecture.md) — WebFlux 架构
  - [`02-annotated-controller`](./adocs/02-webflux-basics/02-annotated-controller.md) — 注解式控制器
  - [`03-functional-endpoints`](./adocs/02-webflux-basics/03-functional-endpoints.md) — 函数式端点
  - [`04-request-validation`](./adocs/02-webflux-basics/04-request-validation.md) — 请求校验
  - [`05-server-sent-events`](./adocs/02-webflux-basics/05-server-sent-events.md) — SSE 服务端推送
  - [`06-webclient`](./adocs/02-webflux-basics/06-webclient.md) — WebClient 客户端
  - [`07-webfilter`](./adocs/02-webflux-basics/07-webfilter.md) — WebFilter 过滤器
  - [`08-webtestclient`](./adocs/02-webflux-basics/08-webtestclient.md) — WebTestClient 测试
  - [`09-common-patterns`](./adocs/02-webflux-basics/09-common-patterns.md) — 常见模式速查
- **阶段三：弹性与 API 版本化** — [`adocs/03-resilience/`](./adocs/03-resilience/)
  - [`01-retryable`](./adocs/03-resilience/01-retryable.md) — @Retryable 声明式重试
  - [`02-concurrency-limit`](./adocs/03-resilience/02-concurrency-limit.md) — @ConcurrencyLimit 并发限流
  - [`03-retry-template`](./adocs/03-resilience/03-retry-template.md) — RetryTemplate 编程式重试
  - [`04-api-versioning`](./adocs/03-resilience/04-api-versioning.md) — API Versioning（无 @ApiVersion）
  - [`05-reactive-retry`](./adocs/03-resilience/05-reactive-retry.md) — 响应式重试
  - [`06-http-message-converters`](./adocs/03-resilience/06-http-message-converters.md) — HttpMessageConverters
  - [`07-common-patterns`](./adocs/03-resilience/07-common-patterns.md) — 速查表与常见模式
- **阶段四：数据访问 & 高阶实战** — [`adocs/04-advanced-practice/`](./adocs/04-advanced-practice/)
  - [`01-r2dbc-basics`](./adocs/04-advanced-practice/01-r2dbc-basics.md) — R2DBC 基础
  - [`02-reactive-transactions`](./adocs/04-advanced-practice/02-reactive-transactions.md) — 响应式事务
  - [`03-reactive-websocket`](./adocs/04-advanced-practice/03-reactive-websocket.md) — WebSocket 实时通信
  - [`04-reactive-ai-chat-sse`](./adocs/04-advanced-practice/04-reactive-ai-chat-sse.md) — AI Chat SSE
  - [`05-virtual-threads-hybrid`](./adocs/04-advanced-practice/05-virtual-threads-hybrid.md) — Virtual Threads 混合
  - [`06-observability`](./adocs/04-advanced-practice/06-observability.md) — 可观测性
  - [`07-reactive-testing`](./adocs/04-advanced-practice/07-reactive-testing.md) — 响应式测试