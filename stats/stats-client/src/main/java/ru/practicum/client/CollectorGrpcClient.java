package ru.practicum.client;


import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import ru.practicum.grpc.stats.action.ActionTypeProto;
import ru.practicum.grpc.stats.action.UserActionProto;
import ru.practicum.grpc.stats.service.UserActionControllerGrpc;

import java.time.Instant;

@Service
@Slf4j
public class CollectorGrpcClient {
    @GrpcClient("collector")
    public UserActionControllerGrpc.UserActionControllerBlockingStub client;

    public void collectUserAction(long userId, long eventId, ActionTypeProto action, Instant instant) {
        try {
            UserActionProto userAction = UserActionProto.newBuilder()
                    .setUserId(userId)
                    .setEventId(eventId)
                    .setActionType(action)
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(instant.getEpochSecond())
                            .setNanos(instant.getNano()).build())
                    .build();
            Empty response = client.collectUserAction(userAction);
            log.info("Действие {} успешно отправлено", userAction);
        } catch (StatusRuntimeException e) {
            log.error("Ошибка gRPC вызова: код={}, описание={}",
                    e.getStatus().getCode(), e.getStatus().getDescription(), e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при отправке действия", e);
        }
    }
}