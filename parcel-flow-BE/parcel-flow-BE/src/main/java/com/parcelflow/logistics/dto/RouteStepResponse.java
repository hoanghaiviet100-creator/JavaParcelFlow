package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.RouteStepStatus;
import com.parcelflow.domain.ParcelRouteStep;

import java.time.LocalDateTime;

public record RouteStepResponse(
        Long id,
        Integer sequenceNo,
        Long fromHubId,
        Long toHubId,
        RouteStepStatus status,
        LocalDateTime expectedDepartureAt,
        LocalDateTime expectedArrivalAt,
        LocalDateTime actualDepartureAt,
        LocalDateTime actualArrivalAt) {

    public static RouteStepResponse from(ParcelRouteStep s) {
        return new RouteStepResponse(
                s.getId(),
                s.getSequenceNo(),
                s.getFromHubId(),
                s.getToHubId(),
                s.getStatus(),
                s.getExpectedDepartureAt(),
                s.getExpectedArrivalAt(),
                s.getActualDepartureAt(),
                s.getActualArrivalAt());
    }
}
