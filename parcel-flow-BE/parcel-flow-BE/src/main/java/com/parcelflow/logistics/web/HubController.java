package com.parcelflow.logistics.web;

import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.logistics.dto.HubResponse;
import com.parcelflow.logistics.service.HubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Logistics hub registry. Read-only for now; any authenticated staff member may browse hubs.
 */
@RestController
@RequestMapping("/api/v1/hubs")
@RequiredArgsConstructor
public class HubController {

    private final HubService hubService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<HubResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(hubService.list(), "OK"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HubResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(hubService.getById(id), "OK"));
    }
}
