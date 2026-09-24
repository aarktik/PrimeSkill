package com.example.toolhub.service.impl;

import com.example.toolhub.domain.entity.Tag;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.entity.ToolTag;
import com.example.toolhub.dto.request.TagRequest;
import com.example.toolhub.dto.response.TagResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.mapper.TagMapper;
import com.example.toolhub.repository.TagRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.repository.ToolTagRepository;
import com.example.toolhub.service.TagService;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagServiceImpl implements TagService {
    private final TagRepository tagRepository;
    private final ToolRepository toolRepository;
    private final ToolTagRepository toolTagRepository;
    private final TagMapper tagMapper;

    public TagServiceImpl(TagRepository tagRepository,
                          ToolRepository toolRepository,
                          ToolTagRepository toolTagRepository,
                          TagMapper tagMapper) {
        this.tagRepository = tagRepository;
        this.toolRepository = toolRepository;
        this.toolTagRepository = toolTagRepository;
        this.tagMapper = tagMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> findAll() {
        return tagRepository.findAll().stream().map(tagMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TagResponse findById(Long id) {
        return tagMapper.toResponse(findTag(id));
    }

    @Override
    @Transactional
    public TagResponse create(TagRequest request, boolean actorIsAdmin) {
        assertAdmin(actorIsAdmin);
        ensureUnique(request, null);
        return tagMapper.toResponse(tagRepository.save(tagMapper.toEntity(request)));
    }

    @Override
    @Transactional
    public TagResponse update(Long id, TagRequest request, boolean actorIsAdmin) {
        assertAdmin(actorIsAdmin);
        Tag tag = findTag(id);
        ensureUnique(request, id);
        tagMapper.updateEntity(tag, request);
        return tagMapper.toResponse(tag);
    }

    @Override
    @Transactional
    public void delete(Long id, boolean actorIsAdmin) {
        assertAdmin(actorIsAdmin);
        Tag tag = findTag(id);
        if (toolTagRepository.countByIdTagId(id) > 0) {
            throw new CatalogConflictException("Tag is still referenced by a tool");
        }
        tagRepository.delete(tag);
    }

    @Override
    @Transactional
    public void assignTag(Long toolId, Long tagId, Long actorUserId, boolean actorIsAdmin) {
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + toolId));
        Tag tag = findTag(tagId);
        assertCanMutate(tool, actorUserId, actorIsAdmin);
        if (toolTagRepository.existsByIdToolIdAndIdTagId(toolId, tagId)) {
            throw new CatalogConflictException("Tag is already assigned to this tool");
        }
        toolTagRepository.save(new ToolTag(tool, tag));
    }

    @Override
    @Transactional
    public void unassignTag(Long toolId, Long tagId, Long actorUserId, boolean actorIsAdmin) {
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + toolId));
        findTag(tagId);
        assertCanMutate(tool, actorUserId, actorIsAdmin);
        if (!toolTagRepository.existsByIdToolIdAndIdTagId(toolId, tagId)) {
            throw new ResourceNotFoundException("Tag is not assigned to this tool");
        }
        toolTagRepository.deleteByIdToolIdAndIdTagId(toolId, tagId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> findTagsOfTool(Long toolId) {
        if (!toolRepository.existsById(toolId)) {
            throw new ResourceNotFoundException("Tool not found: " + toolId);
        }
        return toolTagRepository.findByIdToolId(toolId).stream()
                .map(toolTag -> tagMapper.toResponse(toolTag.getTag()))
                .toList();
    }

    private Tag findTag(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + id));
    }

    private void ensureUnique(TagRequest request, Long currentId) {
        boolean duplicateName = currentId == null
                ? tagRepository.existsByNameIgnoreCase(request.name())
                : tagRepository.existsByNameIgnoreCaseAndIdNot(request.name(), currentId);
        boolean duplicateSlug = currentId == null
                ? tagRepository.existsBySlug(request.slug())
                : tagRepository.existsBySlugAndIdNot(request.slug(), currentId);
        if (duplicateName || duplicateSlug) {
            throw new CatalogConflictException("Tag name or slug already exists");
        }
    }

    private void assertAdmin(boolean actorIsAdmin) {
        if (!actorIsAdmin) {
            throw new AccessDeniedException("Administrator role is required");
        }
    }

    private void assertCanMutate(Tool tool, Long actorUserId, boolean actorIsAdmin) {
        if (!actorIsAdmin && (actorUserId == null || !actorUserId.equals(tool.getOwnerId()))) {
            throw new AccessDeniedException("You do not own this tool");
        }
    }
}
