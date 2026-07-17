package cloud.imuyi.chat.service;

import cloud.imuyi.chat.model.User;
import cloud.imuyi.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public Flux<User> findAll() {
        return userRepository.findAll();
    }

    public Mono<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Mono<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public Mono<User> create(String username, String displayName) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }

    @Transactional
    public Mono<Void> deleteById(Long id) {
        return userRepository.deleteById(id);
    }

    public User findByIdBlocking(Long id) {
        return userRepository.findById(id).block();
    }
}