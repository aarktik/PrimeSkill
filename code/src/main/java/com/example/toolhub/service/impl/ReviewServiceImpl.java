package com.example.toolhub.service.impl;

import com.example.toolhub.domain.entity.Review;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.entity.User;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.dto.response.ReviewResponse;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.event.ReviewCreatedEvent;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.DuplicateResourceException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.mapper.ReviewMapper;
import com.example.toolhub.repository.ReviewRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.repository.UserRepository;
import com.example.toolhub.service.ReviewService;
import com.example.toolhub.service.ToolService;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ToolRepository toolRepository;
    private final UserRepository userRepository;
    private final ToolService toolService;
    private final ReviewMapper reviewMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewServiceImpl(ReviewRepository reviewRepository, ToolRepository toolRepository,
                             UserRepository userRepository, ToolService toolService,
                             ReviewMapper reviewMapper, ApplicationEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
        this.toolService = toolService;
        this.reviewMapper = reviewMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> listForTool(Long toolId, Long actorUserId,
                                            boolean actorIsAdmin, Pageable pageable) {
        visibleTool(toolId, actorUserId, actorIsAdmin);
        return reviewRepository.findByTool_IdOrderByCreatedAtDescIdAsc(toolId, pageable)
                .map(reviewMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> listAuthoredBy(Long actorUserId, Pageable pageable) {
        requireActor(actorUserId);
        return reviewRepository.findByUser_IdOrderByCreatedAtDescIdAsc(actorUserId, pageable)
                .map(reviewMapper::toResponse);
    }

    @Override
    @Transactional
    public ReviewResponse create(Long toolId, CreateReviewRequest request,
                                 Long actorUserId, boolean actorIsAdmin) {
        requireActor(actorUserId);
        ToolResponse visibleTool = visibleTool(toolId, actorUserId, actorIsAdmin);
        requirePublished(visibleTool);
        if (Objects.equals(visibleTool.getOwnerId(), actorUserId)) {
            throw new AccessDeniedException("Tool owners cannot review their own tools");
        }
        if (reviewRepository.existsByTool_IdAndUser_Id(toolId, actorUserId)) {
            throw new DuplicateResourceException("You have already reviewed this tool");
        }

        Tool tool = toolRepository.getReferenceById(toolId);
        User user = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));
        Review review = reviewRepository.save(new Review(user, tool, request.rating(), normalize(request.comment())));
        eventPublisher.publishEvent(new ReviewCreatedEvent(review.getId(), toolId, actorUserId));
        return reviewMapper.toResponse(review);
    }

    @Override
    @Transactional
    public ReviewResponse update(Long toolId, Long reviewId, UpdateReviewRequest request,
                                 Long actorUserId, boolean actorIsAdmin) {
        requireActor(actorUserId);
        ToolResponse visibleTool = visibleTool(toolId, actorUserId, actorIsAdmin);
        requirePublished(visibleTool);
        Review review = findReview(toolId, reviewId);
        if (!Objects.equals(review.getUser().getId(), actorUserId)) {
            throw new AccessDeniedException("Only the review author can edit this review");
        }
        review.update(request.rating(), normalize(request.comment()));
        return reviewMapper.toResponse(review);
    }

    @Override
    @Transactional
    public void delete(Long toolId, Long reviewId, Long actorUserId, boolean actorIsAdmin) {
        requireActor(actorUserId);
        Review review = findReview(toolId, reviewId);
        if (!actorIsAdmin && !Objects.equals(review.getUser().getId(), actorUserId)) {
            throw new AccessDeniedException("Only the review author or an admin can delete this review");
        }
        reviewRepository.delete(review);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse findMineForTool(Long toolId, Long actorUserId) {
        if (actorUserId == null) {
            return null;
        }
        return reviewRepository.findByTool_IdAndUser_Id(toolId, actorUserId)
                .map(reviewMapper::toResponse)
                .orElse(null);
    }

    private ToolResponse visibleTool(Long toolId, Long actorUserId, boolean actorIsAdmin) {
        return toolService.getByIdOrSlug(String.valueOf(toolId), actorUserId, actorIsAdmin);
    }

    private Review findReview(Long toolId, Long reviewId) {
        return reviewRepository.findByTool_IdAndId(toolId, reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
    }

    private void requirePublished(ToolResponse tool) {
        if (tool.getStatus() != ToolStatus.PUBLISHED) {
            throw new CatalogConflictException("Reviews can only be created or edited for published tools");
        }
    }

    private String normalize(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireActor(Long actorUserId) {
        if (actorUserId == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
    }
}
