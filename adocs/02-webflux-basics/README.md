# 阶段二：Spring WebFlux 核心框架

> **项目模块**：`reactive-api`
> **包路径**：`cloud.imuyi.webflux`
> **依赖**：spring-boot-starter-webflux + validation + reactor-test
> **端口**：8080

---

## 📖 知识点目录

| # | 知识点 | 代码 | 核心概念 |
|---|--------|------|----------|
| 01 | [WebFlux 架构概览](01-webflux-architecture.md) | — | DispatcherHandler, Netty, HttpHandler |
| 02 | [注解式 Controller](02-annotated-controller.md) | `UserController.java` | `@RestController` + Mono/Flux |
| 03 | [函数式端点](03-functional-endpoints.md) | `UserRouter.java`+`UserHandler.java` | RouterFunction vs 注解式 |
| 04 | [请求参数与校验](04-request-validation.md) | `GlobalExceptionHandler.java` | `@Valid`, Problem Detail |
| 05 | [SSE 实时推送](05-server-sent-events.md) | `StockController.java`+`StockService.java` | Sinks.Many, `TEXT_EVENT_STREAM` |
| 06 | [WebClient 客户端](06-webclient.md) | `WebClientDemo.java` | retrieve() vs exchangeToMono() |
| 07 | [WebFilter 过滤链](07-webfilter.md) | `LoggingWebFilter.java`+`RateLimitWebFilter.java` | 过滤器链, `@Order` |
| 08 | [WebTestClient 测试](08-webtestclient.md) | `UserControllerTests.java`+`UserRouterTests.java`+`StockControllerTests.java` | 测试隔离, SSE 测试 |
| 09 | [速查表 & 常见模式](09-common-patterns.md) | — | 一站式参考 |

---

## 🧪 测试覆盖

```
UserControllerTests   → 6 个用例：findAll / findById / 404 / create / 校验 / delete
UserRouterTests       → 3 个用例：findAll / create / 校验
StockControllerTests  → 1 个用例：SSE stream (StepVerifier)
                     ──────────────────────────────
                      10 个用例，全部通过 ✅
```

**测试隔离策略**：每个测试类通过 `@SpringBootTest(properties = {"test.group=…"})` 使用独立的 Spring Context，`UserService` 实例互不共享。

---

## 🏗 项目结构

```
reactive-api/src/
├── main/java/cloud/imuyi/webflux/
│   ├── ReactiveApiApplication.java        # 启动类
│   ├── model/                             # 数据模型
│   │   ├── User.java                      # Record 用户实体
│   │   ├── StockPrice.java                # 股票行情 Record
│   │   └── CreateUserRequest.java         # 创建请求 DTO
│   ├── controller/                        # 注解式 Controller
│   │   ├── UserController.java            # 用户 CRUD（含 404）
│   │   └── StockController.java           # SSE 行情
│   ├── service/                           # 业务层
│   │   ├── UserService.java               # ConcurrentHashMap 模拟存储
│   │   └── StockService.java              # Sinks.Many 热流推送
│   ├── router/                            # 函数式路由
│   │   ├── UserRouter.java                # /func/users 路由定义
│   │   └── StockRouter.java               # /func/stocks SSE 路由
│   ├── handler/                           # 函数式 Handler
│   │   └── UserHandler.java               # 含校验逻辑
│   ├── filter/                            # WebFilter
│   │   ├── LoggingWebFilter.java          # 请求日志
│   │   └── RateLimitWebFilter.java        # IP 限流
│   ├── exception/                         # 异常处理
│   │   └── GlobalExceptionHandler.java    # 全局异常
│   └── client/                            # WebClient 演示
│       └── WebClientDemo.java
├── test/java/cloud/imuyi/webflux/
│   ├── UserControllerTests.java           # 注解式 API 测试
│   ├── UserRouterTests.java               # 函数式 API 测试
│   └── StockControllerTests.java          # SSE 测试
└── pom.xml
```