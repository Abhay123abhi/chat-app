package com.substring.chat.repositories;

import com.substring.chat.entities.Room;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoomRepository extends MongoRepository<Room, String> {
    Optional<Room> findByRoomId(String roomId);
}
