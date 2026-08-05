package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.ParcelStatus;

import java.util.List;

/**
 * What a parcel may legally become next.
 *
 * <p>Served so the UI can offer exactly the statuses the server will accept. The
 * scan screens used to render the whole {@code ParcelStatus} enum in one flat
 * dropdown, which is how a delivered parcel could be set to CANCELLED by picking
 * the wrong line.
 *
 * @param corrections supervisor-only repairs; empty for roles that may not make them,
 *                    so the UI can simply render what it is given
 */
public record ParcelTransitionsResponse(
        ParcelStatus current,
        List<ParcelStatus> allowed,
        List<ParcelStatus> corrections) {
}
