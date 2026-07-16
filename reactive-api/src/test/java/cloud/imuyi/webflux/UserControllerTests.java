package cloud.imuyi.webflux;

import cloud.imuyi.webflux.model.User;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 注解式 Controller 独立测试
 * <p>
 * 通过 unique property 强制独立的 Spring Context，避免与其它测试类共享 Service 实例。
 */
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

    @Test
    @Order(1)
    @DisplayName("GET /users — 返回 3 条初始数据")
    void findAll() {
        webClient.get().uri("/users")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(User.class).hasSize(3);
    }

    @Test
    @Order(2)
    @DisplayName("GET /users/1 — 返回 Alice")
    void findById() {
        webClient.get().uri("/users/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Alice");
    }

    @Test
    @Order(3)
    @DisplayName("GET /users/999 — 返回 404")
    void findByIdNotFound() {
        webClient.get().uri("/users/999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(4)
    @DisplayName("POST /users — 创建 Diana，返回 201")
    void create() {
        var body = """
                {"name": "Diana", "email": "diana@example.com", "age": 26}
                """;
        webClient.post().uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isNotEmpty()
                .jsonPath("$.name").isEqualTo("Diana");
    }

    @Test
    @Order(5)
    @DisplayName("POST /users — 校验失败返回 400")
    void createValidationFails() {
        var body = """
                {"name": "", "email": "invalid", "age": 0}
                """;
        webClient.post().uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Order(6)
    @DisplayName("DELETE /users/1 — 删除 Alice，返回 204")
    void delete() {
        webClient.delete().uri("/users/1")
                .exchange()
                .expectStatus().isNoContent();
    }
}