# 03. 函数式端点（RouterFunction）

> 除了注解式 `@RestController`，WebFlux 还提供**函数式**编程模型——`RouterFunction` + `HandlerFunction`，适合灵活路由组合的场景。

---

## 1. Handler（处理器）

```java
@Component
public class UserHandler {

    private final UserService userService;
    private final Validator validator;

    public UserHandler(UserService userService, Validator validator) {
        this.userService = userService;
        this.validator = validator;
    }

    public Mono<ServerResponse> findAll(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(userService.findAll(), User.class);
    }

    public Mono<ServerResponse> findById(ServerRequest request) {
        var id = Long.parseLong(request.pathVariable("id"));
        return userService.findById(id)
                .flatMap(user -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(user))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreateUserRequest.class)
                .flatMap(body -> {
                    var violations = validator.validate(body);
                    if (!violations.isEmpty()) {
                        var msg = violations.stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .collect(Collectors.joining(", "));
                        return ServerResponse.badRequest().bodyValue(msg);
                    }
                    return userService.create(body)
                            .flatMap(user -> ServerResponse.status(HttpStatus.CREATED)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(user));
                });
    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        var id = Long.parseLong(request.pathVariable("id"));
        return userService.delete(id)
                .then(ServerResponse.noContent().build());
    }
}
```

### Handler 函数签名

```java
Mono<ServerResponse> handle(ServerRequest request)
```

- `ServerRequest` — 请求对象，提供 `pathVariable()`、`bodyToMono()`、`headers()` 等
- `ServerResponse` — 响应对象，通过链式 Builder 创建：`ok()` → `body()` → 返回

| ServerRequest 方法 | 用途 |
|--------------------|------|
| `request.pathVariable("id")` | 取路径参数 |
| `request.bodyToMono(Class)` | 取请求体并反序列化 |
| `request.bodyToFlux(Class)` | 取流式请求体 |
| `request.queryParam("name")` | 取查询参数 |

| ServerResponse 静态方法 | 用途 |
|------------------------|------|
| `ServerResponse.ok()` | 200 |
| `ServerResponse.status(HttpStatus)` | 自定义状态码 |
| `ServerResponse.noContent().build()` | 204 |
| `ServerResponse.notFound().build()` | 404 |
| `ServerResponse.badRequest().bodyValue(msg)` | 400 |

---

## 2. Router（路由定义）

```java
@Configuration
public class UserRouter {

    @Bean
    public RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
        return RouterFunctions.route()
                .GET("/func/users", handler::findAll)
                .GET("/func/users/{id}", handler::findById)
                .POST("/func/users", handler::create)
                .DELETE("/func/users/{id}", handler::delete)
                .build();
    }
}
```

### 路由 DSL 支持的 HTTP 方法

| 方法 | 语法 |
|------|------|
| GET | `.GET("/path", handler::method)` |
| POST | `.POST("/path", handler::method)` |
| PUT | `.PUT("/path", handler::method)` |
| DELETE | `.DELETE("/path", handler::method)` |
| PATCH | `.PATCH("/path", handler::method)` |
| 任意 | `.route(RequestPredicates.GET("/path"), handler)` |

### 复杂路由组合

```java
@Bean
public RouterFunction<ServerResponse> complexRoutes() {
    return RouterFunctions.route()
            // 带 Header 条件
            .GET("/api/v2/users", RequestPredicates.header("X-API-Version", "2"),
                 handler::findAllV2)
            // 嵌套路由
            .path("/api", builder -> builder
                    .GET("/users", handler::findAll)
                    .GET("/users/{id}", handler::findById))
            // 资源嵌套
            .path("/users/{userId}", builder -> builder
                    .GET("/orders", orderHandler::findByUserId)
                    .POST("/orders", orderHandler::create))
            .build();
}
```

---

## 3. 函数式 vs 注解式

| 维度 | 注解式 (`@RestController`) | 函数式 (`RouterFunction`) |
|------|---------------------------|--------------------------|
| **可读性** | 声明式，一目了然 | 链式，集中路由管理 |
| **灵活性** | 固定的路径模板 | 可在 Handler 中灵活取参、条件路由 |
| **路由组合** | 分散在各 Controller 类 | 集中在一个 Router Bean |
| **校验方式** | `@Valid` 自动校验 | 手动调用 `Validator.validate()` |
| **适用场景** | 标准 REST API | 条件路由、动态 API、版本管理 |

### 选型建议

```
标准 CRUD API                     → 注解式（开发效率高）
条件路由（Header 版本、A/B 测试） → 函数式（灵活）
超大规模 API（20+ 端点）          → 函数式（路由集中管理）
SSE / WebSocket / 流式接口        → 注解式即可，函数式更灵活但无显著优势
```

---

## 📝 总结

| 概念 | 一句话 |
|------|--------|
| RouterFunction | 路由 DSL，定义 URL → Handler 的映射 |
| HandlerFunction | 处理器函数 `ServerRequest → Mono<ServerResponse>` |
| ServerResponse | 响应构建器，支持 `ok()` / `notFound()` / `badRequest()` |
| RouterFunctions.route() | 流畅的 DSL Builder |
| RequestPredicates | 请求条件匹配器，支持 Header / 路径 / 内容类型 |