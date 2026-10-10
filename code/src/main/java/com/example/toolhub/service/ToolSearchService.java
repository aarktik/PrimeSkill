package com.example.toolhub.service;

import com.example.toolhub.dto.response.ToolResponse;
import java.util.List;
import org.springframework.data.domain.Page;

public interface ToolSearchService {
    Page<ToolResponse> search(String keyword, Long categoryId, List<String> tagSlugs,
                              String sort, int page, int size);
}
