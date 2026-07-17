package cloud.imuyi.resilient;

import cloud.imuyi.resilient.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResilientApiTests {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    // ========== API Versioning ==========

    @Test
    void shouldReturnV1UserByName() {
        webTestClient.get()
            .uri("/api/users/1")
            .header("X-API-Version", "1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(UserProfile.class)
            .value(user -> {
                assertThat(user.getId()).isEqualTo(1L);
                assertThat(user.getEmail()).isBlank();
                // v1 不返回 email
            });
    }

    @Test
    void shouldReturnV2UserWithEmail() {
        webTestClient.get()
            .uri("/api/users/1")
            .header("X-API-Version", "2")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(UserProfile.class)
            .value(user -> {
                assertThat(user.getId()).isEqualTo(1L);
                assertThat(user.getEmail()).isNotNull();
            });
    }

    @Test
    void shouldListV2UsersWithEmails() {
        webTestClient.get()
            .uri("/api/users")
            .header("X-API-Version", "2")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(UserProfile.class)
            .hasSize(3)
            .value(users -> {
                assertThat(users.get(0).getEmail()).isNotNull();
                assertThat(users.get(1).getEmail()).isNotNull();
                assertThat(users.get(2).getEmail()).isNotNull();
            });
    }

    // ========== 弹性网关 ==========

    @Test
    void shouldAccessGatewayEndpoint() {
        webTestClient.get()
            .uri("/api/gateway/users/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody(UserProfile.class)
            .value(user -> assertThat(user.getId()).isEqualTo(1L));
    }

    @Test
    void shouldStreamUsersViaSSE() {
        webTestClient.get()
            .uri("/api/gateway/users/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(UserProfile.class)
            .hasSize(3);
    }

    // ========== 响应式重试 ==========

    @Test
    void shouldAccessReactiveRetryEndpoint() {
        webTestClient.get()
            .uri("/api/gateway/users/1/reactive")
            .exchange()
            .expectStatus().isOk()
            .expectBody(UserProfile.class)
            .value(user -> assertThat(user.getId()).isEqualTo(1L));
    }
}