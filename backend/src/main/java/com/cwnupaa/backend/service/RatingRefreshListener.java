package com.cwnupaa.backend.service;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RatingRefreshListener {
    private final RatingService ratings;
    public RatingRefreshListener(RatingService ratings) { this.ratings=ratings; }
    @Async
    @TransactionalEventListener(fallbackExecution=true)
    public void refresh(RatingRefreshRequested event) {
        try { ratings.refreshAllRatings(); }
        catch(Exception e) { LoggerFactory.getLogger(getClass()).error("Rating refresh after record change failed ({})",e.getClass().getSimpleName()); }
    }
}
