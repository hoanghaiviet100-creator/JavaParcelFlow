package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.PartyType;

public record OrderPartyResponse(
        Long id,
        PartyType partyType,
        String fullName,
        String phone,
        String email,
        String addressLine,
        Long wardId,
        Long districtId,
        Long provinceId) {
}
