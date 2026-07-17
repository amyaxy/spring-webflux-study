# spring-webflux-study

针对 流式响应 / SSE / WebSocket 等场景，WebFlux 背压优势不可替代。

---

## 项目模块

| 模块 | 阶段 | 说明 |
|------|------|------|
| `reactive-api` | 阶段二 | 响应式 CRUD、SSE 行情推送、Virtual Threads 混合架构 |
| `resilient-api` | 阶段三 | Spring 7.x 弹性特性（@Retryable, @ConcurrencyLimit）+ API Versioning |