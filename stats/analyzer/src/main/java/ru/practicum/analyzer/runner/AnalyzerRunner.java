package ru.practicum.analyzer.runner;


import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.practicum.analyzer.service.EventSimilarityService;
import ru.practicum.analyzer.service.UserActionService;


@Component
@RequiredArgsConstructor
public class AnalyzerRunner implements CommandLineRunner {
    private final EventSimilarityService eventSimilarityService;
    private final UserActionService userActionService;

    @Override
    public void run(String... args) {
        Thread userActionServiceThread = new Thread(userActionService);
        userActionServiceThread.setName("UserActionServiceThread");
        userActionServiceThread.start();

        eventSimilarityService.start();
    }
}