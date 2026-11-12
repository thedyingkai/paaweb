package com.cwnupaa.backend.service;

import org.springframework.context.ApplicationEventPublisher;

public record RatingRefreshRequested() {
    public static void publish(ApplicationEventPublisher events) {
        if (events != null) events.publishEvent(new RatingRefreshRequested());
    }
}
