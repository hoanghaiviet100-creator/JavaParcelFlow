package com.parcelflow.logistics.web;

import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.logistics.dto.StatsResponse;
import com.parcelflow.logistics.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard counts. Any authenticated user may read the overview; the frontend
 * shows each role the subset relevant to it.
 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<StatsResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.success(statsService.overview(), "OK"));
    }
}
