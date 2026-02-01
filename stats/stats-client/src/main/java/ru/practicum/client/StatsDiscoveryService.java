package ru.practicum.client;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import ru.practicum.exception.StatsClientException;

@Component
@RequiredArgsConstructor
public class StatsDiscoveryService {

    private final DiscoveryClient discoveryClient;
    private static final String STATS_SERVER_ID = "stats-server".toUpperCase();

    public ServiceInstance getInstance (){
        try {
            return discoveryClient
                    .getInstances(STATS_SERVER_ID)
                    .getFirst();
        }catch (Exception e){
            throw new StatsClientException("Ошибка обнаружения stats-server через Service Discovery");
        }
    }
}
