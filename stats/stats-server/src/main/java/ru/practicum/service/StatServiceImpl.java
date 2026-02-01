package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.mapper.StatisticsMapper;
import ru.practicum.model.Stat;
import ru.practicum.model.ViewStats;
import ru.practicum.statistics.dto.EndpointHitDto;
import ru.practicum.statistics.dto.ViewStatsDto;
import ru.practicum.storage.StatRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatServiceImpl implements StatService {
    private final StatRepository statRepository;

    @Transactional
    @Override
    public EndpointHitDto hit(EndpointHitDto request) {
        Stat stat = new Stat();
        stat.setApp(request.getApp());

        stat.setUri(request.getUri());
        stat.setIp(request.getIp());
        stat.setCreated(request.getTimestamp());

        log.info(
                "SAVE HIT → app={}, uri={}, ip={}, time={}",
                stat.getApp(),
                stat.getUri(),
                stat.getIp(),
                stat.getCreated()
        );

        stat = statRepository.save(stat);

        request.setId(stat.getId());
        return request;
    }

    @Override
    public Collection<ViewStatsDto> getStats(
            LocalDateTime start,
            LocalDateTime end,
            List<String> uris,
            boolean unique
    ) {

        // ⬇️ ВАЖНО: нормализация uris
        List<String> normalizedUris = normalizeUris(uris);

        log.info(
                "GET /stats params: start={}, end={}, rawUris={}, normalizedUris={}, unique={}",
                start, end, uris, normalizedUris, unique
        );

        List<ViewStats> list;

        if (normalizedUris.isEmpty()) {
            list = unique
                    ? statRepository.getStatsAllUnique(start, end)
                    : statRepository.getStatsAll(start, end);
        } else {
            list = unique
                    ? statRepository.getStatsUnique(start, end, normalizedUris)
                    : statRepository.getStats(start, end, normalizedUris);
        }

        log.info("Stats repository returned {} rows", list.size());

        return list.stream()
                .map(StatisticsMapper::toViewStatsDto)
                .toList();
    }


    private List<String> normalizeUris(List<String> uris) {
        if (uris == null || uris.isEmpty()) {
            return List.of();
        }

        return uris.stream()
                .flatMap(u -> Arrays.stream(u.split("&uris=")))
                .toList();
    }


}
