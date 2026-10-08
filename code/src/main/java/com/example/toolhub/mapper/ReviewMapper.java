package com.example.toolhub.mapper;

import com.example.toolhub.domain.entity.Review;
import com.example.toolhub.domain.entity.UserProfile;
import com.example.toolhub.dto.response.ReviewResponse;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review review) {
        UserProfile profile = review.getUser().getProfile();
        return new ReviewResponse(
                review.getId(),
                review.getTool().getId(),
                review.getUser().getId(),
                profile == null ? null : profile.getDisplayName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}
