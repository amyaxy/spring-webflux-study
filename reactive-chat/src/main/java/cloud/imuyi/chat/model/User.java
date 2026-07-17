package cloud.imuyi.chat.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Table("users")
public class User {
    @Id
    private Long id;
    private String username;
    private String displayName;
    private LocalDateTime createdAt;
}