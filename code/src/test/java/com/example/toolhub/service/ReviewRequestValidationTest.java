package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ReviewRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void ratingMustBePresentAndBetweenOneAndFive() {
        assertFalse(validator.validate(new CreateReviewRequest(null, null)).isEmpty());
        assertFalse(validator.validate(new CreateReviewRequest((short) 0, null)).isEmpty());
        assertFalse(validator.validate(new UpdateReviewRequest((short) 6, null)).isEmpty());
        assertTrue(validator.validate(new CreateReviewRequest((short) 1, null)).isEmpty());
        assertTrue(validator.validate(new UpdateReviewRequest((short) 5, null)).isEmpty());
    }

    @Test
    void commentIsOptionalButCannotExceedTwoThousandCharacters() {
        assertTrue(validator.validate(new CreateReviewRequest((short) 4, null)).isEmpty());
        assertTrue(validator.validate(new CreateReviewRequest((short) 4, "x".repeat(2000))).isEmpty());
        assertFalse(validator.validate(new UpdateReviewRequest((short) 4, "x".repeat(2001))).isEmpty());
    }
}
