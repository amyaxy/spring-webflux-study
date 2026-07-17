package cloud.imuyi.chat.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Table("users")
public class User {
    @Id
    @Column("id")
    private Long id;
    @Column("username")
    private String username;
    @Column("display_name")
    private String displayName;
    @Column("created_at")
    private LocalDateTime createdAt;
}