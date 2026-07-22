package com.parcelflow.logistics.service;

import com.parcelflow.common.error.ApiException;
import com.parcelflow.domain.ParcelRoutePlan;
import com.parcelflow.logistics.dto.RoutePlanResponse;
import com.parcelflow.logistics.dto.RouteStepResponse;
import com.parcelflow.repository.ParcelRoutePlanRepository;
import com.parcelflow.repository.ParcelRouteStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoutePlanService {

    private final ParcelRoutePlanRepository routePlanRepository;
    private final ParcelRouteStepRepository routeStepRepository;

    /** Paged list of route plans (without steps, to keep the payload light). */
    @Transactional(readOnly = true)
    public Page<RoutePlanResponse> list(Pageable pageable) {
        return routePlanRepository.findAll(pageable)
                .map(plan -> toResponse(plan, List.of()));
    }

    /** A single route plan with its ordered steps. */
    @Transactional(readOnly = true)
    public RoutePlanResponse getById(Long id) {
        ParcelRoutePlan plan = routePlanRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Route plan not found: " + id));
        List<RouteStepResponse> steps =
                routeStepRepository.findByParcelRoutePlanIdOrderBySequenceNoAsc(id).stream()
                        .map(RouteStepResponse::from)
                        .toList();
        return toResponse(plan, steps);
    }

    private RoutePlanResponse toResponse(ParcelRoutePlan plan, List<RouteStepResponse> steps) {
        return new RoutePlanResponse(
                plan.getId(),
                plan.getParcelId(),
                plan.getPlannedBy(),
                plan.getStatus(),
                plan.getCreatedAt(),
                plan.getApprovedAt(),
                steps);
    }
}
