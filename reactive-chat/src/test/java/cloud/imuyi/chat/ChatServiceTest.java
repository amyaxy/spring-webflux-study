package cloud.imuyi.chat;

import cloud.imuyi.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Test
    void shouldSendAndReceiveMessage() {
        chatService.sendMessage("room1", "alice", "Hello!")
                .as(StepVerifier::create)
                .assertNext(msg -> {
                    assertEquals("room1", msg.getChatRoom());
                    assertEquals("alice", msg.getSender());
                    assertEquals("Hello!", msg.getContent());
                    assertNotNull(msg.getId());
                })
                .verifyComplete();

        chatService.historyByRoom("room1")
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldStreamMessagesLive() {
        var stream = chatService.streamByRoom("room2");
        chatService.sendMessage("room2", "bob", "Live!").subscribe();
        stream.as(StepVerifier::create)
                .assertNext(msg -> assertEquals("Live!", msg.getContent()))
                .thenCancel()
                .verify();
    }
}