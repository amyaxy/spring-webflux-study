package cloud.imuyi.webflux.handler;

import cloud.imuyi.webflux.model.CreateUserRequest;
import cloud.imuyi.webflux.model.User;
import cloud.imuyi.webflux.service.UserService;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * 函数式端点 Handler — 与 UserController 等价的路由逻辑
 */
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