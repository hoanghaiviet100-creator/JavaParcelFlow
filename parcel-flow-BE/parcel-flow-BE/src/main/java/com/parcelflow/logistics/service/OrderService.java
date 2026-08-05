package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.*;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.common.util.CodeGenerator;
import com.parcelflow.domain.Order;
import com.parcelflow.domain.OrderParty;
import com.parcelflow.domain.Parcel;
import com.parcelflow.domain.ParcelCurrentState;
import com.parcelflow.logistics.dto.*;
import com.parcelflow.repository.OrderPartyRepository;
import com.parcelflow.repository.OrderRepository;
import com.parcelflow.repository.ParcelCurrentStateRepository;
import com.parcelflow.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderPartyRepository orderPartyRepository;
    private final ParcelRepository parcelRepository;
    private final ParcelCurrentStateRepository currentStateRepository;
    private final TrackingService trackingService;
    private final CodeGenerator codeGenerator;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req, Long createdByUserId) {
        BigDecimal totalWeight = req.getParcels().stream()
                .map(ParcelRequest::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = orderRepository.save(Order.builder()
                .orderCode(codeGenerator.orderCode())
                .status(OrderStatus.CREATED)
                .createdHubId(req.getCreatedHubId())
                .currentHubId(req.getCreatedHubId())
                .finalHubId(req.getFinalHubId())
                .serviceType(req.getServiceType() != null ? req.getServiceType() : ServiceType.STANDARD)
                .paymentType(req.getPaymentType() != null ? req.getPaymentType() : PaymentType.SENDER_PAY)
                .totalWeight(totalWeight)
                .totalFee(BigDecimal.ZERO)
                .codAmount(req.getCodAmount() != null ? req.getCodAmount() : BigDecimal.ZERO)
                .note(req.getNote())
                .createdBy(createdByUserId)
                .build());
        Long orderId = order.getId();

        OrderParty sender = saveParty(req.getSender(), orderId, PartyType.SENDER);
        OrderParty receiver = saveParty(req.getReceiver(), orderId, PartyType.RECEIVER);

        List<Parcel> parcels = req.getParcels().stream()
                .map(pr -> createParcel(pr, orderId))
                .toList();

        trackingService.record(orderId, null, OrderStatus.CREATED.name(),
                "Order created",
                "Order " + order.getOrderCode() + " created", req.getCreatedHubId(), true);

        return buildResponse(order, sender, receiver, parcels);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        OrderParty sender = orderPartyRepository.findByOrderIdAndPartyType(id, PartyType.SENDER).orElse(null);
        OrderParty receiver = orderPartyRepository.findByOrderIdAndPartyType(id, PartyType.RECEIVER).orElse(null);
        List<Parcel> parcels = parcelRepository.findByOrderId(id);
        return buildResponse(order, sender, receiver, parcels);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> list(Pageable pageable) {
        return orderRepository.findAll(pageable).map(o -> new OrderSummaryResponse(
                o.getId(), o.getOrderCode(), o.getStatus(),
                o.getTotalWeight(), o.getCodAmount(), o.getCreatedAt()));
    }

    @Transactional
    public OrderResponse update(Long id, UpdateOrderRequest req) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        if (req.getNote() != null) {
            order.setNote(req.getNote());
        }
        if (req.getServiceType() != null) {
            order.setServiceType(req.getServiceType());
        }
        if (req.getPaymentType() != null) {
            order.setPaymentType(req.getPaymentType());
        }
        if (req.getFinalHubId() != null) {
            order.setFinalHubId(req.getFinalHubId());
        }
        if (req.getCodAmount() != null) {
            order.setCodAmount(req.getCodAmount());
        }
        orderRepository.save(order);
        return getById(id);
    }

    /** DELETE is a logical cancel: hard-deleting would violate parcel/tracking foreign keys. */
    @Transactional
    public void cancel(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        trackingService.record(order.getId(), null, OrderStatus.CANCELLED.name(),
                "Order cancelled",
                "Order " + order.getOrderCode() + " cancelled", order.getCurrentHubId(), true);
    }

    /**
     * Undo a cancellation.
     *
     * <p>Cancelling is the one order transition with no way back, and
     * {@code ParcelService.syncOrderStatus} deliberately refuses to let parcel
     * movement revive a cancelled order. Together that made a cancellation — whether
     * deliberate or a mis-click on the wrong row — final: the order never changed
     * status again, no matter what its parcels did, and the only repair was an UPDATE
     * against the database.
     *
     * <p>The restored status is derived from the parcels rather than remembered, so a
     * shipment reinstated after its parcels have moved on comes back at the point they
     * actually reached, not the point where it was cancelled.
     */
    @Transactional
    public OrderResponse reinstate(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw ApiException.conflict("Order " + order.getOrderCode()
                    + " is not cancelled (currently " + order.getStatus().name() + ")");
        }

        List<ParcelStatus> parcelStatuses = parcelRepository.findByOrderId(id).stream()
                .map(Parcel::getStatus)
                .toList();
        OrderStatus restored = OrderStatusDeriver.derive(parcelStatuses);
        if (restored == OrderStatus.CANCELLED) {
            throw ApiException.conflict("Every parcel on " + order.getOrderCode()
                    + " is cancelled, so there is nothing to reinstate. Correct a parcel"
                    + " out of CANCELLED first and the order follows automatically.");
        }

        order.setStatus(restored);
        orderRepository.save(order);

        trackingService.record(order.getId(), null, restored.name(),
                "Order reinstated",
                "Order " + order.getOrderCode() + " reinstated as " + restored.name(),
                order.getCurrentHubId(), true);

        return getById(id);
    }

    private OrderParty saveParty(PartyRequest p, Long orderId, PartyType type) {
        OrderParty party = OrderParty.builder()
                .orderId(orderId)
                .partyType(type)
                .fullName(p.getFullName())
                .phone(p.getPhone())
                .email(p.getEmail())
                .addressLine(p.getAddressLine())
                .wardId(p.getWardId())
                .districtId(p.getDistrictId())
                .provinceId(p.getProvinceId())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .build();
        return orderPartyRepository.save(party);
    }

    private Parcel createParcel(ParcelRequest pr, Long orderId) {
        Parcel parcel = Parcel.builder()
                .orderId(orderId)
                .parcelCode(codeGenerator.parcelCode())
                .categoryId(pr.getCategoryId())
                .weight(pr.getWeight())
                .length(pr.getLength())
                .width(pr.getWidth())
                .height(pr.getHeight())
                .declaredValue(pr.getDeclaredValue() != null ? pr.getDeclaredValue() : BigDecimal.ZERO)
                .note(pr.getNote())
                .status(ParcelStatus.CREATED)
                .build();
        parcel = parcelRepository.save(parcel);

        ParcelCurrentState state = new ParcelCurrentState();
        state.setParcelId(parcel.getId());
        state.setCurrentStatus(ParcelStatus.CREATED);
        state.setResponsibilityType(ResponsibilityType.SYSTEM);
        currentStateRepository.save(state);

        return parcel;
    }

    private OrderResponse buildResponse(Order order, OrderParty sender, OrderParty receiver,
                                        List<Parcel> parcels) {
        List<ParcelResponse> parcelResponses = parcels.stream()
                .map(p -> new ParcelResponse(p.getId(), p.getParcelCode(),
                        p.getCategoryId(), p.getWeight(), p.getStatus()))
                .toList();
        return new OrderResponse(
                order.getId(), order.getOrderCode(), order.getStatus(),
                order.getServiceType(), order.getPaymentType(),
                order.getTotalWeight(), order.getTotalFee(), order.getCodAmount(),
                order.getNote(), order.getCreatedHubId(), order.getCurrentHubId(),
                order.getFinalHubId(), order.getCreatedBy(), order.getCreatedAt(),
                toParty(sender), toParty(receiver), parcelResponses);
    }

    private OrderPartyResponse toParty(OrderParty p) {
        if (p == null) {
            return null;
        }
        return new OrderPartyResponse(p.getId(), p.getPartyType(), p.getFullName(),
                p.getPhone(), p.getEmail(), p.getAddressLine(),
                p.getWardId(), p.getDistrictId(), p.getProvinceId());
    }
}