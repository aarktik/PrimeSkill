package com.example.toolhub.exception;

public class StaleReviewRevisionException extends RuntimeException {
    public StaleReviewRevisionException() { super("Submission changed; review the current candidate before deciding"); }
}
