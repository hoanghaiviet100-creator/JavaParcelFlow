package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.HubType;
import com.parcelflow.domain.Hub;

public record HubResponse(
        Long id,
        String code,
        String name,
        HubType type,
        String phone,
        String addressLine,
        Long wardId,
        Long districtId,
        Long provinceId,
        Long parentHubId,
        boolean isActive) {

    public static HubResponse from(Hub h) {
        return new HubResponse(
                h.getId(),
                h.getCode(),
                h.getName(),
                h.getType(),
                h.getPhone(),
                h.getAddressLine(),
                h.getWardId(),
                h.getDistrictId(),
                h.getProvinceId(),
                h.getParentHubId(),
                Boolean.TRUE.equals(h.getIsActive()));
    }
}
