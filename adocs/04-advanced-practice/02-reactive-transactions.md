# 02 · 响应式事务

## 概述

Spring 的 `@Transactional` 同样适用于 R2DBC，但在响应式环境中需要理解其边界和限制。

## 基本用法

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public Mono<User> create(String username, String displayName) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }
}
```

## 关键要点

### 1. 事务绑定到 Reactor 上下文

`@Transactional` 在 R2DBC 中通过 Reactor 的 Context 传播，而非线程局部变量。这意味着：

- 必须在同一 Reactor 链中调用
- `.block()` 调用会打破事务上下文
- 跨线程的 `subscribeOn` 可能丢失事务

### 2. 正确

```java
@Transactional
public Mono<Void> transfer(String from, String to, int amount) {
    return accountRepo.deduct(from, amount)
        .then(accountRepo.add(to, amount))
        .then(); // 整个链在同一个事务中
}
```

### 3. 错误

```java
@Transactional
public void transferBlocking(String from, String to, int amount) {
    accountRepo.deduct(from, amount).block();  // ❌ block 中断事务上下文
    accountRepo.add(to, amount).block();
}
```

### 4. 回滚规则

- 响应式事务默认回滚 `RuntimeException` 及其子类
- 检查型异常不会触发回滚
- 可通过 `@Transactional(rollbackFor = Exception.class)` 自定义

## 与 Virtual Threads 的对比

| 特性 | R2DBC + @Transactional | Virtual Threads + JDBC |
|------|------------------------|----------------------|
| 线程模型 | 事件循环（少量线程） | 大量虚拟线程 |
| 事务传播 | Reactor Context | ThreadLocal |
| 适用场景 | 短事务、低延迟 | 长事务、复杂业务逻辑 |
| 阻塞兼容 | 不兼容 `.block()` | 天然支持阻塞 |

## 踩坑记录

**不在响应式链中使用 `.block()`**。即使 `@Transactional` 声明了，`.block()` 会启动一个新的事务上下文，导致 `@Transactional` 失效。