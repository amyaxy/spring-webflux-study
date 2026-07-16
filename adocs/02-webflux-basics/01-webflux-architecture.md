# 01. WebFlux 架构概览

> Spring WebFlux 是 Spring Framework 5.0 引入的响应式 Web 框架，Spring Boot 4.1 中作为一等公民与 Spring MVC 并存。

---

## 1. 核心组件对比

| 维度 | Spring MVC（Servlet） | Spring WebFlux（Reactive） |
|------|----------------------|---------------------------|
| 底层容器 | Tomcat / Jetty（Servlet API） | **Netty** / Undertow（NIO） |
| 请求分发 | `DispatcherServlet` | **`DispatcherHandler`** |
| 接口层 | `@Controller` + `HttpServletRequest` | `@RestController` + `ServerHttpRequest` |
| 返回值 | `ModelAndView` / `ResponseEntity` | **`Mono<T>` / `Flux<T>` / `ServerResponse`** |
| 线程模型 | 每请求一线程（Thread-per-request） | 事件循环（Event Loop） |
| 安全性 | `SecurityContextHolder`（ThreadLocal） | `ReactiveSecurityContextHolder`（Reactive Context） |

### 线程模型差异

```
MVC (Tomcat):
  Thread-1 ──→ UserService.getUser() [阻塞 JDBC] ──→ 返回响应 → Thread-1 释放
  Thread-2 ──→ UserService.getUser() [阻塞 JDBC] ──→ 返回响应 → Thread-2 释放
  ★ 线程数与并发数成正比，线程膨胀 → OOM

WebFlux (Netty):
  EventLoop-1 ──→ UserService.getUser() [非阻塞] → EventLoop-1 空闲处理其他请求
  EventLoop-1 ──→ StockService.stream() [非阻塞] → EventLoop-1 空闲处理其他请求
  ★ 少量 EventLoop 线程处理海量并发，线程数与并发数无关
```

---

## 2. 请求处理流程

```
Netty（端口 8080）
  │
  ▼
HttpHandler（最底层接口，很少直接使用）
  │
  ▼
WebHttpHandlerBuilder
  │  ├── WebFilter 链（日志、限流、认证）
  │  └── ExceptionHandler
  │
  ▼
DispatcherHandler（核心分发器）
  │
  ├── ① HandlerMapping（路由匹配）
  │   ├── RequestMappingHandlerMapping（注解式 @RequestMapping）
  │   └── RouterFunctionMapping（函数式 RouterFunction）
  │
  ├── ② HandlerAdapter（执行处理器）
  │   ├── RequestMappingHandlerAdapter（调用 @Controller 方法）
  │   └── HandlerFunctionAdapter（调用 HandlerFunction）
  │
  └── ③ HandlerResultHandler（写响应）
      ├── ResponseEntityResultHandler（ResponseEntity / ServerResponse）
      ├── ResponseBodyResultHandler（@ResponseBody 返回值）
      └── ServerSentEventResultHandler（SSE）
```

```java
// DispatcherHandler 核心逻辑（简化）
public Mono<Void> handle(ServerWebExchange exchange) {
    return Flux.fromIterable(this.handlerMappings)
        .concatMap(mapping -> mapping.getHandler(exchange))  // ① 找 Handler
        .next()
        .flatMap(handler -> invokeHandler(exchange, handler)) // ② 执行
        .flatMap(result -> handleResult(exchange, result));   // ③ 写响应
}
```

---

## 3. 启动原理

```
SpringApplication.run(ReactiveApiApplication.class)
  │
  ├── 检测到 WebFlux 依赖 → 自动配置 Netty + DispatcherHandler
  │
  ├── WebServerManager.start()
  │   └── NettyReactiveWebServerFactory.create()
  │       └── Reactor Netty → 监听 8080
  │
  └── DispatcherHandler 注册:
      ├── handlerMappings（扫描 @RequestMapping + RouterFunction Bean）
      ├── handlerAdapters（注册各类适配器）
      └── resultHandlers（注册各类结果处理器）
```

**关键 Bean**：当 classpath 存在 `spring-boot-starter-webflux` 时，Spring Boot 自动配置 `ReactiveWebServerFactory`（Netty）和 `DispatcherHandler`。

---

## 4. Netty EventLoop 模型

```
                         Netty Thread Model
┌─────────────────────────────────────────────────┐
│  Boss Group（通常 1 线程）                       │
│  └─ Accept 连接 → 注册到 Worker Group            │
├─────────────────────────────────────────────────┤
│  Worker Group（通常 CPU 核数 × 2 线程）          │
│  ├─ EventLoop-1                                  │
│  ├─ EventLoop-2     ←── I/O 事件分发             │
│  ├─ ...                                          │
│  └─ EventLoop-N                                  │
├─────────────────────────────────────────────────┤
│  Reactor Schedulers（异步处理）                   │
│  ├─ Schedulers.parallel()   ← CPU 密集型         │
│  └─ Schedulers.boundedElastic()  ← 阻塞 I/O      │
└─────────────────────────────────────────────────┘
```

### 重要限制

- ❌ **不要在 EventLoop 线程执行阻塞操作**（JDBC、`Thread.sleep()`）
- ✅ 阻塞操作改用 `subscribeOn(Schedulers.boundedElastic())`
- ✅ 纯 I/O 操作（DB R2DBC、Redis Reactive、远程 API 调用）直接在 EventLoop 执行

```java
@GetMapping("/users")
public Flux<User> findAll() {
    return userService.findAll()
        .subscribeOn(Schedulers.boundedElastic());  // 如果 Service 有阻塞
}
```

---

## 📝 总结

| 概念 | 一句话 |
|------|--------|
| DispatcherHandler | WebFlux 的请求分发核心，类似 MVC 的 DispatcherServlet |
| Netty | WebFlux 默认容器，事件驱动 NIO |
| EventLoop | 少量线程处理海量连接，不能阻塞 |
| Schedulers | `parallel()` CPU 密集型，`boundedElastic()` 阻塞操作 |