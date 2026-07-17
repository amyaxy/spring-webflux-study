# spring-webflux-study

针对 流式响应 / SSE / WebSocket 等场景，WebFlux 背压优势不可替代。

---

## 项目模块

| 模块 | 阶段 | 说明 |
|------|------|------|
| `reactive-api` | 阶段二 | 响应式 CRUD、SSE 行情推送、Virtual Threads 混合架构 |
| `resilient-api` | 阶段三 | Spring 7.x 弹性特性（@Retryable, @ConcurrencyLimit）+ API Versioning |

## 学习笔记

- [阶段三：弹性特性与 API Versioning](docs/notes/phase-3-resilient-and-api-versioning.md)

---

## Spring Boot 4.x / Spring 7.x 关键变化

- `@Retryable` / `@ConcurrencyLimit` → `org.springframework.resilience.annotation`
- 启用弹性方法 → `@EnableResilientMethods`
- API 版本化 → `RequestPredicates.version()` 谓词 + `ApiVersionConfigurer`
- 事件监听 → `ListenerContainer`（代替 `SmartApplicationListener`）