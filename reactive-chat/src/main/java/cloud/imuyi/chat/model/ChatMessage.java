package cloud.imuyi.chat.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Table("chat_messages")
public class ChatMessage {
    @Id
    @Column("id")
    private Long id;
    @Column("chat_room")
    private String chatRoom;
    @Column("sender")
    private String sender;
    @Column("content")
    private String content;
    @Column("created_at")
    private LocalDateTime createdAt;
}