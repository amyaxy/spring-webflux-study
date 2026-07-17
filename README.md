# spring-webflux-study

针对 流式响应 / SSE / WebSocket 等场景，WebFlux 背压优势不可替代。

---

## 项目模块

| 模块 | 阶段 | 说明 |
|------|------|------|
| `reactor-playground` | 阶段一 | Reactor Core 基础语法实验：Flux/Mono、操作符、错误处理、背压、调度器 |
| `reactive-api` | 阶段二 | 响应式 CRUD、SSE 行情推送、Virtual Threads 混合架构 |
| `resilient-api` | 阶段三 | Spring 7.x 弹性特性（@Retryable, @ConcurrencyLimit）+ API Versioning |

---

## 学习笔记

- [学习计划](./adocs/PLAN.md)
- 阶段一：Reactor Core — [`adocs/01-reactor-core/`](./adocs/01-reactor-core/)
- 阶段二：WebFlux 基础 — [`adocs/02-webflux-basics/`](./adocs/02-webflux-basics/)
- 阶段三：弹性与 API 版本化 — [`adocs/03-resilience/`](./adocs/03-resilience/)