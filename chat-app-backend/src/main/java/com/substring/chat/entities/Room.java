package com.substring.chat.entities;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document("rooms")
public class Room {
    @Id private String id;
    @Indexed(unique = true) private String roomId;
    private long nextSequence;
}
