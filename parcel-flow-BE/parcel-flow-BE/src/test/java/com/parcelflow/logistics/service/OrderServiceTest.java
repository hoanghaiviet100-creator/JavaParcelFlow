package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.OrderStatus;
import com.parcelflow.common.enums.ParcelStatus;
import com.parcelflow.common.util.CodeGenerator;
import com.parcelflow.domain.Order;
import com.parcelflow.domain.OrderParty;
import com.parcelflow.domain.Parcel;
import com.parcelflow.logistics.dto.CreateOrderRequest;
import com.parcelflow.logistics.dto.OrderResponse;
import com.parcelflow.logistics.dto.ParcelRequest;
import com.parcelflow.logistics.dto.PartyRequest;
import com.parcelflow.repository.OrderPartyRepository;
import com.parcelflow.repository.OrderRepository;
import com.parcelflow.repository.ParcelCurrentStateRepository;
import com.parcelflow.repository.ParcelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private OrderPartyRepository orderPartyRepository;
    private ParcelRepository parcelRepository;
    private ParcelCurrentStateRepository currentStateRepository;
    private TrackingService trackingService;
    private CodeGenerator codeGenerator;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderPartyRepository = mock(OrderPartyRepository.class);
        parcelRepository = mock(ParcelRepository.class);
        currentStateRepository = mock(ParcelCurrentStateRepository.class);
        trackingService = mock(TrackingService.class);
        codeGenerator = mock(CodeGenerator.class);
        service = new OrderService(orderRepository, orderPartyRepository, parcelRepository,
                currentStateRepository, trackingService, codeGenerator);

        when(codeGenerator.orderCode()).thenReturn("OD-TEST");
        when(codeGenerator.parcelCode()).thenReturn("PC-TEST");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(orderPartyRepository.save(any(OrderParty.class))).thenAnswer(inv -> {
            OrderParty p = inv.getArgument(0);
            p.setId(7L);
            return p;
        });
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(inv -> {
            Parcel p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });
    }

    @Test
    void createOrder_persistsOrderPartiesParcelsAndTracking() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCreatedHubId(1L);
        req.setSender(party("Alice"));
        req.setReceiver(party("Bob"));
        ParcelRequest parcel = new ParcelRequest();
        parcel.setWeight(new BigDecimal("2.50"));
        req.setParcels(List.of(parcel));

        OrderResponse response = service.createOrder(req, 99L);

        assertThat(response.orderCode()).isEqualTo("OD-TEST");
        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.totalWeight()).isEqualByComparingTo("2.50");
        assertThat(response.parcels()).hasSize(1);
        assertThat(response.parcels().get(0).status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(response.createdBy()).isEqualTo(99L);

        verify(orderPartyRepository, times(2)).save(any(OrderParty.class));
        verify(currentStateRepository, times(1)).save(any());
        verify(trackingService, times(1))
                .record(eq(1L), isNull(), eq("CREATED"), any(), any(), eq(1L), eq(true));
    }

    private PartyRequest party(String name) {
        PartyRequest p = new PartyRequest();
        p.setFullName(name);
        p.setPhone("0900000000");
        p.setAddressLine("1 Street");
        p.setDistrictId(1L);
        p.setProvinceId(1L);
        return p;
    }
}
