package cloud.imuyi.webflux.model;

public record User(
        Long id,
        String name,
        String email,
        int age
) {}