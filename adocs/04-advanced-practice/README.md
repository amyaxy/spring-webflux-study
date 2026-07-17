# 阶段四：数据访问 & 高阶实战

> R2DBC 响应式数据库 · WebSocket · AI Chat SSE · Virtual Threads 混合 · 可观测性

## 知识点清单

| # | 主题 | 笔记 |
|---|------|------|
| 01 | R2DBC 基础 | 响应式 Repository CRUD + H2 内存数据库 |
| 02 | 响应式事务 | @Transactional 在响应式环境的边界与陷阱 |
| 03 | WebSocket 实时通信 | WebSocketHandler + Sinks.Many 发布订阅 |
| 04 | AI Chat SSE | 事件分派器模式 + @Retryable 重试 |
| 05 | Virtual Threads 混合 | 阻塞 I/O 走 VT + 流式走 WebFlux |
| 06 | 可观测性 | Micrometer + Actuator + 链路追踪 |
| 07 | 响应式测试 | WebTestClient + StepVerifier |

## 对应模块

`reactive-chat/` — 完整响应式全栈项目（端口 8084）