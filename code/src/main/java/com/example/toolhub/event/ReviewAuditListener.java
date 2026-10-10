package com.example.toolhub.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReviewAuditListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewAuditListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCreated(ReviewCreatedEvent event) {
        try {
            LOGGER.info("review.created reviewId={} toolId={} userId={}",
                    event.reviewId(), event.toolId(), event.userId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not write review-created audit event for reviewId={}", event.reviewId(), exception);
        }
    }
}
