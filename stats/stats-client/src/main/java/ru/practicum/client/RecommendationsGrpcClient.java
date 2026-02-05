package ru.practicum.client;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import ru.practicum.grpc.stats.analyzer.InteractionsCountRequestProto;
import ru.practicum.grpc.stats.analyzer.RecommendedEventProto;
import ru.practicum.grpc.stats.analyzer.SimilarEventsRequestProto;
import ru.practicum.grpc.stats.analyzer.UserPredictionsRequestProto;
import ru.practicum.grpc.stats.dashboard.RecommendationsControllerGrpc;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class RecommendationsGrpcClient {

    @GrpcClient("analyzer")
    RecommendationsControllerGrpc.RecommendationsControllerBlockingStub client;

    public Stream<RecommendedEventProto> getSimilarEvents(long eventId, long userId, int maxResults) {
        Iterator<RecommendedEventProto> iterator = null;
        try {
            SimilarEventsRequestProto request = SimilarEventsRequestProto.newBuilder()
                    .setEventId(eventId)
                    .setUserId(userId)
                    .setMaxResults(maxResults)
                    .build();

            iterator = client.getSimilarEvents(request); // gRPC-метод getSimilarEvents возвращает Iterator, потому что в его схеме мы указали, что он должен вернуть поток сообщений (stream stats.message.RecommendedEventProto)
            log.info("Запрос {} успешно отправлено", request);


        } catch (StatusRuntimeException e) {
            log.error("Ошибка gRPC вызова: код={}, описание={}",
                    e.getStatus().getCode(), e.getStatus().getDescription(), e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при отправке", e);
        }
        return asStream(iterator); // преобразую Iterator в Stream
    }


    public Stream<RecommendedEventProto> getRecommendationsForUser(long userId, int maxResults) {
        Iterator<RecommendedEventProto> iterator = null;
        try {
            UserPredictionsRequestProto request = UserPredictionsRequestProto.newBuilder()
                    .setUserId(userId)
                    .setMaxResults(maxResults)
                    .build();

            iterator = client.getRecommendationsForUser(request);
            log.info("Запрос {} успешно отправлено", request);
            return asStream(iterator);
        } catch (StatusRuntimeException e) {
            log.error("Ошибка gRPC вызова: код={}, описание={}",
                    e.getStatus().getCode(), e.getStatus().getDescription(), e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при отправке", e);
        }
        return asStream(iterator);
    }

    public Stream<RecommendedEventProto> getInteractionsCount(List<Long> eventIds) {
        Iterator<RecommendedEventProto> iterator = null;
        try {
            InteractionsCountRequestProto request = InteractionsCountRequestProto.newBuilder()
                    .addAllEventId(eventIds)
                    .build();
            iterator = client.getInteractionsCount(request);
            log.info("Запрос {} успешно отправлено", request);
            return asStream(iterator);
        } catch (StatusRuntimeException e) {
            log.error("Ошибка gRPC вызова: код={}, описание={}",
                    e.getStatus().getCode(), e.getStatus().getDescription(), e);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при отправке", e);
        }
        return asStream(iterator);
    }

    private Stream<RecommendedEventProto> asStream(Iterator<RecommendedEventProto> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false
        );
    }
}