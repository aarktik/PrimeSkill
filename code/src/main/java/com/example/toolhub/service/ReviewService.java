package com.example.toolhub.service;

import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.dto.response.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    Page<ReviewResponse> listForTool(Long toolId, Long actorUserId, boolean actorIsAdmin, Pageable pageable);

    Page<ReviewResponse> listAuthoredBy(Long actorUserId, Pageable pageable);

    ReviewResponse create(Long toolId, CreateReviewRequest request, Long actorUserId, boolean actorIsAdmin);

    ReviewResponse update(Long toolId, Long reviewId, UpdateReviewRequest request,
                          Long actorUserId, boolean actorIsAdmin);

    void delete(Long toolId, Long reviewId, Long actorUserId, boolean actorIsAdmin);

    ReviewResponse findMineForTool(Long toolId, Long actorUserId);
}
