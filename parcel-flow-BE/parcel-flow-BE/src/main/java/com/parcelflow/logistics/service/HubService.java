package com.parcelflow.logistics.service;

import com.parcelflow.common.error.ApiException;
import com.parcelflow.logistics.dto.HubResponse;
import com.parcelflow.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HubService {

    private final HubRepository hubRepository;

    @Transactional(readOnly = true)
    public List<HubResponse> list() {
        return hubRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(HubResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public HubResponse getById(Long id) {
        return hubRepository.findById(id)
                .map(HubResponse::from)
                .orElseThrow(() -> ApiException.notFound("Hub not found: " + id));
    }
}
