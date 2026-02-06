package ru.practicum.analyzer.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.grpc.stats.analyzer.InteractionsCountRequestProto;
import ru.practicum.grpc.stats.analyzer.RecommendedEventProto;
import ru.practicum.grpc.stats.analyzer.SimilarEventsRequestProto;
import ru.practicum.grpc.stats.analyzer.UserPredictionsRequestProto;
import ru.practicum.grpc.stats.dashboard.RecommendationsControllerGrpc;

import java.util.List;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class RecommendationsController extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {
    private final AnalyzerService analyzerService;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        try {
            log.info("Получен запрос по gRPC на получение потока рекомендованных мероприятий для указанного " +
                    "пользователя userId = {}, max_results = {},", request.getUserId(), request.getMaxResults());

            List<RecommendedEventProto> recommendationsForUser = analyzerService.getRecommendationsForUser(request);

            for (RecommendedEventProto event : recommendationsForUser) {
                responseObserver.onNext(event);
                log.info("Ответ. Рекомендация = {}", event);
            }
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

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> responseObserver) {
        try {
            log.info("Получен запрос по gRPC на получение потока мероприятий, с которыми не взаимодействовал " +
                            "этот пользователь, но которые максимально похожи на указанное мероприятие" +
                            " userId = {}, eventId = {}, max_results = {},", request.getUserId(), request.getEventId(),
                    request.getMaxResults());
            List<RecommendedEventProto> similarEvents = analyzerService.getSimilarEvents(request);

            for (RecommendedEventProto event : similarEvents) {
                responseObserver.onNext(event);
                log.info("Ответ. Рекомендация = {}", event);
            }

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

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        try {
            log.info("Получен запрос по gRPC на получение потока с суммой максимальных весов действий " +
                            "каждого пользователя с этими мероприятиями. Список идентификаторов eventIds = {},",
                    request.getEventIdList());
            List<RecommendedEventProto> listOfMaximumWeights = analyzerService.getInteractionsCount(request);

            for (RecommendedEventProto maximumWeight : listOfMaximumWeights) {
                responseObserver.onNext(maximumWeight);
                log.info("Ответ. Максимальный вес = {}", maximumWeight);
            }

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
}