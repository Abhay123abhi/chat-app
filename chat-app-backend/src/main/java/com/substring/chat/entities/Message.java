package com.substring.chat.entities;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document("messages")
@CompoundIndexes({
    @CompoundIndex(name = "room_sequence", def = "{'roomId': 1, 'sequence': 1}", unique = true),
    @CompoundIndex(name = "room_request", def = "{'roomId': 1, 'clientMessageId': 1}", unique = true)
})
public class Message {
    @Id private String id;
    private String roomId;
    private String clientMessageId;
    private long sequence;
    private String sender;
    private String content;
    private Instant timeStamp;
}
