package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.example.toolhub.domain.entity.Review;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.entity.User;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.dto.response.ReviewResponse;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.DuplicateResourceException;
import com.example.toolhub.mapper.ReviewMapper;
import com.example.toolhub.repository.ReviewRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.repository.UserRepository;
import com.example.toolhub.service.impl.ReviewServiceImpl;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ToolRepository toolRepository;
    @Mock private UserRepository userRepository;
    @Mock private ToolService toolService;
    @Mock private ReviewMapper reviewMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EntityManager entityManager;

    private ReviewServiceImpl service;
    private ToolResponse publishedTool;

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(reviewRepository, toolRepository, userRepository,
                toolService, reviewMapper, eventPublisher, entityManager);
        lenient().when(toolRepository.findForUpdateById(3L))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(com.example.toolhub.domain.entity.Tool.class)));
        publishedTool = ToolResponse.builder().id(3L).ownerId(9L).status(ToolStatus.PUBLISHED).build();
        lenient().when(toolService.getById(3L, 7L, false)).thenReturn(publishedTool);
    }

    @Test
    void create_trimsCommentAndPublishesCreatedEvent() {
        User user = org.mockito.Mockito.mock(User.class);
        Tool tool = org.mockito.Mockito.mock(Tool.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(toolRepository.getReferenceById(3L)).thenReturn(tool);
        when(reviewRepository.existsByTool_IdAndUser_Id(3L, 7L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewMapper.toResponse(any(Review.class))).thenReturn(
                new ReviewResponse(11L, 3L, 7L, "Reviewer", (short) 5, "Good tool", null, null));

        ReviewResponse response = service.create(3L, new CreateReviewRequest((short) 5, "  Good tool  "), 7L, false);

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertEquals("Good tool", reviewCaptor.getValue().getComment());
        assertEquals((short) 5, response.rating());
        verify(toolRepository).findForUpdateById(3L);
        verify(entityManager).refresh(any(com.example.toolhub.domain.entity.Tool.class));
        verify(eventPublisher).publishEvent(any(com.example.toolhub.event.ReviewCreatedEvent.class));
    }

    @Test
    void create_blankCommentStoresNull() {
        User user = org.mockito.Mockito.mock(User.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(toolRepository.getReferenceById(3L)).thenReturn(org.mockito.Mockito.mock(Tool.class));
        when(reviewRepository.existsByTool_IdAndUser_Id(3L, 7L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewMapper.toResponse(any(Review.class))).thenReturn(
                new ReviewResponse(11L, 3L, 7L, "Reviewer", (short) 4, null, null, null));

        service.create(3L, new CreateReviewRequest((short) 4, "  \t "), 7L, false);

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertNull(reviewCaptor.getValue().getComment());
    }

    @Test
    void create_duplicateReviewReturnsConflict() {
        when(reviewRepository.existsByTool_IdAndUser_Id(3L, 7L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.create(3L, new CreateReviewRequest((short) 5, null), 7L, false));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void create_rejectsOwnerAndNonPublishedTool() {
        when(toolService.getById(3L, 9L, false)).thenReturn(publishedTool);
        assertThrows(AccessDeniedException.class,
                () -> service.create(3L, new CreateReviewRequest((short) 5, null), 9L, false));

        ToolResponse draft = ToolResponse.builder().id(3L).ownerId(9L).status(ToolStatus.DRAFT).build();
        when(toolService.getById(3L, 7L, false)).thenReturn(draft);
        assertThrows(CatalogConflictException.class,
                () -> service.create(3L, new CreateReviewRequest((short) 5, null), 7L, false));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void update_onlyAuthorCanEdit_evenAdminCannotEditAnotherUsersReview() {
        when(toolService.getById(3L, 7L, true)).thenReturn(publishedTool);
        Review review = org.mockito.Mockito.mock(Review.class);
        User author = org.mockito.Mockito.mock(User.class);
        when(review.getUser()).thenReturn(author);
        when(author.getId()).thenReturn(8L);
        when(reviewRepository.findByTool_IdAndId(3L, 12L)).thenReturn(Optional.of(review));

        assertThrows(AccessDeniedException.class,
                () -> service.update(3L, 12L, new UpdateReviewRequest((short) 2, null), 7L, true));
        verify(review, never()).update(any(), any());
    }

    @Test
    void update_rejectsNonPublishedToolBeforeLoadingReview() {
        ToolResponse draft = ToolResponse.builder().id(3L).ownerId(9L).status(ToolStatus.DRAFT).build();
        when(toolService.getById(3L, 7L, false)).thenReturn(draft);

        assertThrows(CatalogConflictException.class,
                () -> service.update(3L, 12L, new UpdateReviewRequest((short) 4, "updated"), 7L, false));

        verify(reviewRepository, never()).findByTool_IdAndId(any(), any());
    }

    @Test
    void delete_authorCanDeleteReviewWithoutLookingUpHiddenTool() {
        Review review = org.mockito.Mockito.mock(Review.class);
        User author = org.mockito.Mockito.mock(User.class);
        when(review.getUser()).thenReturn(author);
        when(author.getId()).thenReturn(7L);
        when(reviewRepository.findByTool_IdAndId(99L, 12L)).thenReturn(Optional.of(review));

        service.delete(99L, 12L, 7L, false);

        verify(reviewRepository).delete(review);
        verify(toolService, never()).getById(any(), any(), anyBoolean());
    }
}
