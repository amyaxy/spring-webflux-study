package cloud.imuyi.webflux.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank(message = "name is required")
        String name,

        @Email(message = "invalid email")
        String email,

        @Min(value = 1, message = "age must be >= 1")
        int age
) {}