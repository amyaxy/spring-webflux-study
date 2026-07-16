# 04. 请求参数与校验

> WebFlux 的参数绑定和校验与 Spring MVC 类似，但返回值差异带来了操作符层面的处理区别。

---

## 1. 参数绑定方式

| 注解 | 来源 | 示例 |
|------|------|------|
| `@PathVariable` | 路径模板 `{id}` | `/users/{id}` |
| `@RequestParam` | 查询参数 `?name=xxx` | `/users?page=0` |
| `@RequestBody` | 请求体 JSON | `POST /users` |
| `@RequestHeader` | 请求头 | `X-Trace-Id` |
| `@CookieValue` | Cookie | `sessionId` |

```java
@GetMapping("/{id}")
public Mono<User> findById(@PathVariable Long id)

@GetMapping
public Flux<User> search(@RequestParam(required = false) String name)

@PostMapping
public Mono<User> create(@Valid @RequestBody CreateUserRequest request)

@GetMapping("/header")
public Mono<String> header(@RequestHeader("X-Trace-Id") String traceId)
```

---

## 2. 请求体验证（`@Valid`）

### 请求 DTO

```java
public record CreateUserRequest(
    @NotBlank(message = "用户名不能为空")
    String name,

    @Email(message = "邮箱格式不正确")
    String email,

    @Min(value = 1, message = "年龄至少为 1")
    int age
) {}
```

### Controller 使用

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Mono<User> create(@Valid @RequestBody CreateUserRequest request) {
    return userService.create(request);
}
```

当校验失败时，WebFlux 会自动抛出 `WebExchangeBindException`，返回 400。

---

## 3. 全局异常处理（RFC 9457 Problem Details）

Spring 6+ / Boot 4+ 支持 **Problem Details** 标准（RFC 9457）：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidation(WebExchangeBindException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Validation Failed");
        detail.setDetail(ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", ")));
        return detail;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        var detail = ProblemDetail.forStatus(ex.getStatusCode());
        detail.setTitle(ex.getReason());
        return detail;
    }
}
```

### Problem Detail 响应示例

```json
// POST /users  {"name": "", "email": "invalid", "age": 0}
{
  "type": "about:blank",
  "title": "Validation Failed",
  "status": 400,
  "detail": "name: 用户名不能为空, email: 邮箱格式不正确, age: 年龄至少为 1"
}
```

```json
// GET /users/999
{
  "type": "about:blank",
  "title": "User not found: 999",
  "status": 404
}
```

---

## 4. 函数式端点的校验

函数式路由中不能使用 `@Valid`，需要手动调用 `Validator`：

```java
@Component
public class UserHandler {

    private final Validator validator;

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
                                    .bodyValue(user));
                });
    }
}
```

---

## 📝 总结

| 概念 | 一句话 |
|------|--------|
| `@Valid` | 注解式 Controller 自动校验，失败抛 400 |
| `@RestControllerAdvice` | 全局异常处理，配合 `ProblemDetail` 输出标准化错误响应 |
| `ProblemDetail` | RFC 9457 标准化错误响应格式 |
| 函数式校验 | 手动注入 `Validator` 调用 `validate()` |
| `ResponseStatusException` | 编程式抛 HTTP 状态码异常，自动转为响应 |