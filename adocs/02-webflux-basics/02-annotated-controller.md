# 02. 注解式 Controller

> 最常用的 WebFlux 开发方式——用 `@RestController` 声明式定义 API，返回 `Mono<T>` / `Flux<T>`。

---

## 1. 基础 CRUD

```java
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Flux<User> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<User> findById(@PathVariable Long id) {
        return userService.findById(id)
                .switchIfEmpty(Mono.error(
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return userService.delete(id);
    }
}
```

### 关键点

| 点 | 说明 |
|----|------|
| **返回值** | `Mono<T>` / `Flux<T>`，WebFlux 自动订阅并序列化 |
| **`@Valid`** | 配合 `spring-boot-starter-validation` 自动校验请求体 |
| **`@ResponseStatus`** | POST → `201 CREATED`，DELETE → `204 NO_CONTENT` |
| **`switchIfEmpty`** | `findById` 返回空时抛 `404 NOT_FOUND` |
| **`ResponseStatusException`** | 标准响应状态码异常，自动转为 HTTP 响应 |

---

## 2. 参数绑定

```java
// PathVariable — 路径参数
@GetMapping("/{id}")
public Mono<User> findById(@PathVariable Long id)

// RequestParam — 查询参数（含默认值/可选）
@GetMapping
public Flux<User> search(@RequestParam(required = false) String name,
                          @RequestParam(defaultValue = "0") int page)

// RequestBody — 请求体（含校验）
@PostMapping
public Mono<User> create(@Valid @RequestBody CreateUserRequest request)

// RequestHeader — 请求头
@GetMapping("/header")
public Mono<String> header(@RequestHeader("X-Trace-Id") String traceId)
```

**注意**：`@RequestParam` 在 WebFlux 中默认 `required = true`，可选参数需显式设置 `required = false` 或使用 `java.util.Optional<T>`。

---

## 3. 响应状态码

| 注解 | 场景 |
|------|------|
| `@ResponseStatus(HttpStatus.CREATED)` | POST 创建成功 |
| `@ResponseStatus(HttpStatus.NO_CONTENT)` | DELETE 成功 |
| `new ResponseStatusException(HttpStatus.NOT_FOUND)` | 资源不存在 |
| `new ResponseStatusException(HttpStatus.BAD_REQUEST)` | 参数错误 |

---

## 4. 响应体序列化

WebFlux 默认使用 Jackson 序列化返回值：

```java
// 自动序列化为 JSON
@GetMapping
public Flux<User> findAll()  →  [{...}, {...}, ...]

// 空 Mono → 404
@GetMapping("/{id}")
public Mono<User> findById(@PathVariable Long id)  →  200 {user} / 404

// empty Flux → 200 []（空数组）
@GetMapping
public Flux<User> search(...)  →  200 []
```

---

## 5. 完整文件参考

**文件位置**：`reactive-api/src/main/java/cloud/imuyi/webflux/controller/UserController.java`

```java
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Flux<User> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<User> findById(@PathVariable Long id) {
        return userService.findById(id)
                .switchIfEmpty(Mono.error(
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public Mono<User> update(@PathVariable Long id, @Valid @RequestBody CreateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return userService.delete(id);
    }
}
```

---

## 📝 总结

| 概念 | 一句话 |
|------|--------|
| `@RestController` | 声明响应式 Controller，自动序列化返回值为 JSON |
| `Mono<T>` | 0 或 1 个元素，类比 `ResponseEntity<T>` |
| `Flux<T>` | 0 到 N 个元素，类比列表返回 |
| `switchIfEmpty` | 实现 404 的关键操作符 |
| `@ResponseStatus` | 声明式设置 HTTP 状态码 |