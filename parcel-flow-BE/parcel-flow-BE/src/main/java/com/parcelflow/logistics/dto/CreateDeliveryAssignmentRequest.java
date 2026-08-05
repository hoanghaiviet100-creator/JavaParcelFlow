package com.parcelflow.logistics.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Hand one parcel to one courier.
 *
 * <p>Until this existed nothing created a delivery assignment: the only rows in
 * the table came from seed data. Marking a parcel ASSIGNED_TO_SHIPPER on the scan
 * screen set a label that named no courier, so the parcel never reached anyone's
 * queue and the status was, in effect, a lie.
 */
@Data
public class CreateDeliveryAssignmentRequest {

    @NotNull
    private Long parcelId;

    /** The courier's user id, which is also the primary key of their shipper profile. */
    @NotNull
    private Long shipperId;

    @Size(max = 500)
    private String assignmentReason;
}
