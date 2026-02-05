package ru.practicum.collector.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.grpc.stats.action.UserActionProto;

import java.time.Instant;

@Component
public class CollectorMapper {

    public static UserActionAvro mapToUserActionAvro(UserActionProto userActionProto) {
        return UserActionAvro.newBuilder()
                .setUserId(userActionProto.getUserId())
                .setEventId(userActionProto.getEventId())
                .setActionType(Converter.mapToActionTypeAvro(userActionProto.getActionType()))
                .setTimestamp(Instant.ofEpochSecond(userActionProto.getTimestamp().getSeconds(),
                        userActionProto.getTimestamp().getNanos()))
                .build();
    }
}
