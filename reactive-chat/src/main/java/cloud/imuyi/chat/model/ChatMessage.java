package cloud.imuyi.chat.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Table("chat_messages")
public class ChatMessage {
    @Id
    private Long id;
    private String chatRoom;
    private String sender;
    private String content;
    private LocalDateTime createdAt;
}