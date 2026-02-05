package ru.practicum.collector.service;


import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import ru.practicum.collector.mapper.CollectorMapper;
import ru.practicum.grpc.stats.action.UserActionProto;
import ru.practicum.grpc.stats.service.UserActionControllerGrpc;


import java.time.Instant;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class UserActionController extends UserActionControllerGrpc.UserActionControllerImplBase {
    private final Producer<Long, SpecificRecordBase> producer;
    @Value("${collector.kafka.topic-user-actions}")
    private String topic;

    @Override
    public void collectUserAction(UserActionProto userAction, StreamObserver<Empty> responseObserver) {
        try {
            log.info("Получены данные по gRPC о действиях пользователя userId = {}, eventId = {}, actionType = {}, " +
                            "timestamp = {}", userAction.getUserId(), userAction.getEventId(), userAction.getActionType(),
                    userAction.getTimestamp());

            sendRecord(userAction);

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.warn(e.getLocalizedMessage());
            responseObserver.onError(new StatusRuntimeException(
                    Status.INTERNAL
                            .withDescription(e.getLocalizedMessage())
                            .withCause(e)
            ));
        }
    }

    private void sendRecord(UserActionProto userAction) {
        if (userAction == null) {
            log.warn("Попытка отправить null UserAction");
            return;
        }
        ProducerRecord<Long, SpecificRecordBase> record = new ProducerRecord<>(
                topic,
                null,
                Instant.now().toEpochMilli(),
                userAction.getUserId(),
                CollectorMapper.mapToUserActionAvro(userAction)
        );
        producer.send(record);
        log.info("Отправлены данные по Avro о действиях пользователя value = {}", record.value());
    }
}