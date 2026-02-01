package ru.practicum.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ViewStats {

    private final String app;
    private final String uri;
    private final Long hits;

    public ViewStats(String app, String uri, Long hits) {
        this.app = app;
        this.uri = uri;
        this.hits = hits;
    }

    public String getApp() {
        return app;
    }

    public String getUri() {
        return uri;
    }

    public Long getHits() {
        return hits;
    }
}
