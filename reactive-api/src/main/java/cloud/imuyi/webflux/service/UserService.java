package cloud.imuyi.webflux.service;

import cloud.imuyi.webflux.model.CreateUserRequest;
import cloud.imuyi.webflux.model.User;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模拟用户 Service — 用 ConcurrentHashMap 代替数据库
 */
@Service
public class UserService {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @PostConstruct
    void init() {
        save("Alice", "alice@example.com", 28);
        save("Bob", "bob@example.com", 32);
        save("Charlie", "charlie@example.com", 24);
    }

    public Flux<User> findAll() {
        return Flux.fromIterable(store.values());
    }

    public Mono<User> findById(Long id) {
        return Mono.justOrEmpty(store.get(id));
    }

    public Mono<User> create(CreateUserRequest request) {
        var user = new User(idGen.getAndIncrement(), request.name(), request.email(), request.age());
        store.put(user.id(), user);
        return Mono.just(user);
    }

    public Mono<User> update(Long id, CreateUserRequest request) {
        var existing = store.get(id);
        if (existing == null) {
            return Mono.empty();
        }
        var updated = new User(id, request.name(), request.email(), request.age());
        store.put(id, updated);
        return Mono.just(updated);
    }

    public Mono<Void> delete(Long id) {
        store.remove(id);
        return Mono.empty();
    }

    private User save(String name, String email, int age) {
        var user = new User(idGen.getAndIncrement(), name, email, age);
        store.put(user.id(), user);
        return user;
    }
}