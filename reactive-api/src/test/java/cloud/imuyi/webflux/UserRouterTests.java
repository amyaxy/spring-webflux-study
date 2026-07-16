package cloud.imuyi.webflux;

import cloud.imuyi.webflux.model.User;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 函数式 Router 独立测试
 * <p>
 * 通过 unique property 强制独立的 Spring Context，与 UserControllerTests 不共享 Service。
 */
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

    @Test
    @Order(1)
    @DisplayName("GET /func/users — 返回 3 条初始数据")
    void findAll() {
        webClient.get().uri("/func/users")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(User.class).hasSize(3);
    }

    @Test
    @Order(2)
    @DisplayName("POST /func/users — 创建 Eve，返回 201")
    void create() {
        var body = """
                {"name": "Eve", "email": "eve@example.com", "age": 30}
                """;
        webClient.post().uri("/func/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.name").isEqualTo("Eve");
    }

    @Test
    @Order(3)
    @DisplayName("POST /func/users — 校验失败返回 400")
    void createValidationFails() {
        var body = """
                {"name": "", "email": "bad", "age": 0}
                """;
        webClient.post().uri("/func/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }
}