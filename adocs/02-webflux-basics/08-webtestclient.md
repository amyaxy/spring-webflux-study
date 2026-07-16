# 08. WebTestClient 测试

> WebTestClient 是 WebFlux 的响应式测试利器，链式断言、流式测试，替代 MVC 的 `MockMvc`。

---

## 1. 基础用法

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerTests {

    @LocalServerPort
    private int port;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void findAll() {
        webClient.get().uri("/users")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(User.class).hasSize(3);
    }

    @Test
    void create() {
        webClient.post().uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Diana", "email": "diana@example.com", "age": 26}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Diana");
    }
}
```

### 链式断言流程

```
webClient.method().uri("/path")
        .headers() / .cookies() / .contentType()   ← ① 配置请求
        .bodyValue(body)                             ← ② 设置请求体
        .exchange()                                  ← ③ 发送请求
        .expectStatus().isOk()                       ← ④ 断言状态码
        .expectHeader().contentType(JSON)            ← ⑤ 断言响应头
        .expectBody()                                ← ⑥ 断言响应体
        .jsonPath("$.name").isEqualTo("Alice");
```

---

## 2. 请求体断言

```java
// List 断言
.expectBodyList(User.class)
    .hasSize(3)                                    // 精确数量
    .contains(user1, user2)                         // 包含元素

// JSON Path 断言
.expectBody()
    .jsonPath("$.name").isEqualTo("Alice")          // 单个字段
    .jsonPath("$.age").isEqualTo(28)
    .jsonPath("$[0].name").isEqualTo("Alice")       // 数组索引

// 完整 JSON 断言
.expectBody()
    .json("""                                       // 精确匹配
        {"id": 1, "name": "Alice"}
        """)

// 自定义断言
.expectBody(User.class)
    .value(user -> {
        assertThat(user.name()).isEqualTo("Alice");
        assertThat(user.age()).isGreaterThan(18);
    });
```

---

## 3. SSE 流测试

```java
@Test
void stockStream() {
    webClient.get().uri("/stocks")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .take(Duration.ofSeconds(3))      // 最多等 3 秒
            .as(StepVerifier::create)
            .expectNextCount(1)                // 至少收到 1 条
            .thenCancel()                      // 取消（不关流）
            .verify(Duration.ofSeconds(5));   // 总超时 5 秒
}
```

**要点**：
- `.take(Duration.ofSeconds(3))` — 限制接收时间，避免测试无限等待
- `.thenCancel()` — 显式取消，`verify()` 会超时等待流结束，但 SSE 不会结束
- 用 `returnResult(String.class)` 拿到原始的响应体 Flux

---

## 4. 错误场景测试

```java
@Test
void findByIdNotFound() {
    webClient.get().uri("/users/999")
            .exchange()
            .expectStatus().isNotFound();
}

@Test
void createValidationFails() {
    webClient.post().uri("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                    {"name": "", "email": "invalid", "age": 0}
                    """)
            .exchange()
            .expectStatus().isBadRequest();
}
```

---

## 5. 测试隔离策略

当测试类共享 Spring Context 时，不同测试类的 `@PostConstruct` 数据会互相干扰。

### 方案：独立 Spring Context

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"test.group=user-controller"})  // ← unique property
class UserControllerTests { ... }

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"test.group=user-router"})      // ← 不同 value
class UserRouterTests { ... }
```

**原理**：Spring Boot Test 会根据 `@SpringBootTest` 的配置（包括 `properties`）缓 ApplicationContext。不同的 `properties` 值产生不同的缓存 key，每个类获得**独立的 Bean 容器**，`UserService` 的 `@PostConstruct` 各初始化一次。

---

## 6. 完整测试类模板

### Controller 测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"test.group=user-controller"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserControllerTests {

    @LocalServerPort
    private int port;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test @Order(1)
    void findAll() {
        webClient.get().uri("/users")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(User.class).hasSize(3);
    }

    @Test @Order(2)
    void findById() {
        webClient.get().uri("/users/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Alice");
    }

    @Test @Order(3)
    void findByIdNotFound() {
        webClient.get().uri("/users/999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test @Order(4)
    void create() {
        webClient.post().uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name": "Diana", "email": "diana@example.com", "age": 26}""")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.name").isEqualTo("Diana");
    }

    @Test @Order(5)
    void createValidationFails() {
        webClient.post().uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name": "", "email": "invalid", "age": 0}""")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test @Order(6)
    void delete() {
        webClient.delete().uri("/users/1")
                .exchange()
                .expectStatus().isNoContent();
    }
}
```

### Router 测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"test.group=user-router"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserRouterTests {

    @LocalServerPort
    private int port;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test @Order(1)
    void findAll() {
        webClient.get().uri("/func/users")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(User.class).hasSize(3);
    }

    @Test @Order(2)
    void create() {
        webClient.post().uri("/func/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name": "Eve", "email": "eve@example.com", "age": 30}""")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.name").isEqualTo("Eve");
    }

    @Test @Order(3)
    void createValidationFails() {
        webClient.post().uri("/func/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name": "", "email": "bad", "age": 0}""")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
```

---

## 📝 总结

| 概念 | 一句话 |
|------|--------|
| WebTestClient | 响应式测试客户端，链式断言 |
| `exchange()` | 发送请求并获取响应用于断言 |
| `expectBodyList()` | 断言列表响应体（含大小检查） |
| `.jsonPath()` | JSON Path 表达式断言 |
| StepVerifier | SSE / Flux 流式响应测试 |
| 独立 properties | 通过不同 `test.group` 隔离 Spring Context |