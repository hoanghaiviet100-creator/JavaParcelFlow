-- V1: Parcel Flow base schema (original, unmodified).
-- CREATE DATABASE and USE statements removed: Flyway connects to an existing DB.

CREATE TABLE roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(30) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    hub_id BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB;

-- hub_id FK will be added after hubs table is created.

-- =========================================================
-- 2. GEOGRAPHY
-- =========================================================

CREATE TABLE provinces (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE districts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    province_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,

    CONSTRAINT fk_districts_province
        FOREIGN KEY (province_id) REFERENCES provinces(id)
) ENGINE=InnoDB;

CREATE TABLE wards (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    district_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,

    CONSTRAINT fk_wards_district
        FOREIGN KEY (district_id) REFERENCES districts(id)
) ENGINE=InnoDB;

-- =========================================================
-- 3. HUBS
-- =========================================================

CREATE TABLE hubs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    type ENUM('MAIN_HUB', 'TRANSIT_HUB', 'DELIVERY_HUB', 'PICKUP_HUB') NOT NULL,
    phone VARCHAR(30),
    address_line VARCHAR(255) NOT NULL,
    ward_id BIGINT NULL,
    district_id BIGINT NOT NULL,
    province_id BIGINT NOT NULL,
    parent_hub_id BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_hubs_ward
        FOREIGN KEY (ward_id) REFERENCES wards(id),
    CONSTRAINT fk_hubs_district
        FOREIGN KEY (district_id) REFERENCES districts(id),
    CONSTRAINT fk_hubs_province
        FOREIGN KEY (province_id) REFERENCES provinces(id),
    CONSTRAINT fk_hubs_parent
        FOREIGN KEY (parent_hub_id) REFERENCES hubs(id)
) ENGINE=InnoDB;

ALTER TABLE users
    ADD CONSTRAINT fk_users_hub
    FOREIGN KEY (hub_id) REFERENCES hubs(id);

CREATE TABLE hub_service_areas (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    hub_id BIGINT NOT NULL,
    province_id BIGINT NOT NULL,
    district_id BIGINT NULL,
    ward_id BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_hsa_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_hsa_province
        FOREIGN KEY (province_id) REFERENCES provinces(id),
    CONSTRAINT fk_hsa_district
        FOREIGN KEY (district_id) REFERENCES districts(id),
    CONSTRAINT fk_hsa_ward
        FOREIGN KEY (ward_id) REFERENCES wards(id),
    CONSTRAINT uq_hub_service_area
        UNIQUE (hub_id, province_id, district_id, ward_id)
) ENGINE=InnoDB;

-- =========================================================
-- 4. ORDERS / PARCELS / PARTY SNAPSHOTS
-- =========================================================

CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_code VARCHAR(50) NOT NULL UNIQUE,
    status ENUM(
        'CREATED',
        'RECEIVED_AT_ORIGIN_HUB',
        'WAITING_FOR_ROUTE',
        'IN_TRANSIT',
        'ARRIVED_AT_FINAL_HUB',
        'OUT_FOR_DELIVERY',
        'DELIVERED',
        'DELIVERY_FAILED',
        'RETURNING',
        'RETURNED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'CREATED',

    created_hub_id BIGINT NOT NULL,
    current_hub_id BIGINT NULL,
    final_hub_id BIGINT NULL,

    service_type ENUM('STANDARD', 'EXPRESS', 'ECONOMY') NOT NULL DEFAULT 'STANDARD',
    payment_type ENUM('SENDER_PAY', 'RECEIVER_PAY', 'COD') NOT NULL DEFAULT 'SENDER_PAY',

    total_weight DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
    cod_amount DECIMAL(12,2) NOT NULL DEFAULT 0,

    note VARCHAR(500),
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_created_hub
        FOREIGN KEY (created_hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_orders_current_hub
        FOREIGN KEY (current_hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_orders_final_hub
        FOREIGN KEY (final_hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_orders_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE order_parties (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    party_type ENUM('SENDER', 'RECEIVER') NOT NULL,

    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(150) NULL,
    address_line VARCHAR(255) NOT NULL,
    ward_id BIGINT NULL,
    district_id BIGINT NOT NULL,
    province_id BIGINT NOT NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_parties_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_parties_ward
        FOREIGN KEY (ward_id) REFERENCES wards(id),
    CONSTRAINT fk_order_parties_district
        FOREIGN KEY (district_id) REFERENCES districts(id),
    CONSTRAINT fk_order_parties_province
        FOREIGN KEY (province_id) REFERENCES provinces(id),
    CONSTRAINT uq_order_party_type
        UNIQUE (order_id, party_type)
) ENGINE=InnoDB;

CREATE TABLE parcel_categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_fragile BOOLEAN NOT NULL DEFAULT FALSE,
    is_liquid BOOLEAN NOT NULL DEFAULT FALSE,
    is_high_value BOOLEAN NOT NULL DEFAULT FALSE,
    requires_special_handling BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;

CREATE TABLE parcels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    parcel_code VARCHAR(50) NOT NULL UNIQUE,
    category_id BIGINT NULL,

    weight DECIMAL(10,2) NOT NULL,
    length DECIMAL(10,2) NULL,
    width DECIMAL(10,2) NULL,
    height DECIMAL(10,2) NULL,
    declared_value DECIMAL(12,2) NOT NULL DEFAULT 0,
    note VARCHAR(500),

    status ENUM(
        'CREATED',
        'RECEIVED_AT_ORIGIN_HUB',
        'WAITING_FOR_ROUTE',
        'WAITING_FOR_OUTBOUND',
        'IN_TRANSIT',
        'ARRIVED_AT_HUB',
        'READY_FOR_DELIVERY',
        'ASSIGNED_TO_SHIPPER',
        'OUT_FOR_DELIVERY',
        'DELIVERED',
        'DELIVERY_FAILED',
        'RETURNING',
        'RETURNED',
        'LOST',
        'DAMAGED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'CREATED',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_parcels_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_parcels_category
        FOREIGN KEY (category_id) REFERENCES parcel_categories(id)
) ENGINE=InnoDB;

-- =========================================================
-- 5. PARCEL ROUTE PLAN
-- =========================================================

CREATE TABLE parcel_route_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parcel_id BIGINT NOT NULL,
    planned_by BIGINT NOT NULL,
    status ENUM('DRAFT', 'APPROVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at DATETIME NULL,

    CONSTRAINT fk_prp_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id),
    CONSTRAINT fk_prp_planned_by
        FOREIGN KEY (planned_by) REFERENCES users(id),
    CONSTRAINT uq_active_route_plan_per_parcel
        UNIQUE (parcel_id)
) ENGINE=InnoDB;

CREATE TABLE parcel_route_steps (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parcel_route_plan_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    from_hub_id BIGINT NOT NULL,
    to_hub_id BIGINT NOT NULL,

    expected_departure_at DATETIME NULL,
    expected_arrival_at DATETIME NULL,
    actual_departure_at DATETIME NULL,
    actual_arrival_at DATETIME NULL,

    status ENUM('PENDING', 'READY', 'IN_TRANSIT', 'ARRIVED', 'SKIPPED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',

    CONSTRAINT fk_prs_plan
        FOREIGN KEY (parcel_route_plan_id) REFERENCES parcel_route_plans(id),
    CONSTRAINT fk_prs_from_hub
        FOREIGN KEY (from_hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_prs_to_hub
        FOREIGN KEY (to_hub_id) REFERENCES hubs(id),
    CONSTRAINT uq_route_step_sequence
        UNIQUE (parcel_route_plan_id, sequence_no)
) ENGINE=InnoDB;

-- =========================================================
-- 6. HUB SCANS / CURRENT STATE / CUSTODY
-- =========================================================

CREATE TABLE hub_scans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parcel_id BIGINT NOT NULL,
    hub_id BIGINT NOT NULL,
    scan_type ENUM('INBOUND', 'OUTBOUND', 'SORTING', 'EXCEPTION') NOT NULL,
    route_step_id BIGINT NULL,
    scanned_by BIGINT NOT NULL,
    note VARCHAR(500),
    scanned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_hub_scans_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id),
    CONSTRAINT fk_hub_scans_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_hub_scans_route_step
        FOREIGN KEY (route_step_id) REFERENCES parcel_route_steps(id),
    CONSTRAINT fk_hub_scans_scanned_by
        FOREIGN KEY (scanned_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE parcel_current_state (
    parcel_id BIGINT PRIMARY KEY,
    current_status ENUM(
        'CREATED',
        'RECEIVED_AT_ORIGIN_HUB',
        'WAITING_FOR_ROUTE',
        'WAITING_FOR_OUTBOUND',
        'IN_TRANSIT',
        'ARRIVED_AT_HUB',
        'READY_FOR_DELIVERY',
        'ASSIGNED_TO_SHIPPER',
        'OUT_FOR_DELIVERY',
        'DELIVERED',
        'DELIVERY_FAILED',
        'RETURNING',
        'RETURNED',
        'LOST',
        'DAMAGED',
        'CANCELLED'
    ) NOT NULL,
    current_hub_id BIGINT NULL,
    current_user_id BIGINT NULL,
    current_route_step_id BIGINT NULL,

    responsibility_type ENUM('HUB', 'DRIVER', 'SHIPPER', 'CUSTOMER', 'SYSTEM') NOT NULL DEFAULT 'SYSTEM',
    responsible_user_id BIGINT NULL,
    responsible_hub_id BIGINT NULL,

    last_scan_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_pcs_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id),
    CONSTRAINT fk_pcs_current_hub
        FOREIGN KEY (current_hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_pcs_current_user
        FOREIGN KEY (current_user_id) REFERENCES users(id),
    CONSTRAINT fk_pcs_route_step
        FOREIGN KEY (current_route_step_id) REFERENCES parcel_route_steps(id),
    CONSTRAINT fk_pcs_responsible_user
        FOREIGN KEY (responsible_user_id) REFERENCES users(id),
    CONSTRAINT fk_pcs_responsible_hub
        FOREIGN KEY (responsible_hub_id) REFERENCES hubs(id)
) ENGINE=InnoDB;

CREATE TABLE parcel_custody_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parcel_id BIGINT NOT NULL,

    from_responsibility_type ENUM('HUB', 'DRIVER', 'SHIPPER', 'CUSTOMER', 'SYSTEM') NULL,
    from_user_id BIGINT NULL,
    from_hub_id BIGINT NULL,

    to_responsibility_type ENUM('HUB', 'DRIVER', 'SHIPPER', 'CUSTOMER', 'SYSTEM') NOT NULL,
    to_user_id BIGINT NULL,
    to_hub_id BIGINT NULL,

    action_type ENUM(
        'RECEIVED_FROM_CUSTOMER',
        'HANDOVER_TO_HUB',
        'HANDOVER_TO_DRIVER',
        'RECEIVED_BY_DRIVER',
        'RECEIVED_AT_DESTINATION_HUB',
        'HANDOVER_TO_SHIPPER',
        'RECEIVED_BY_SHIPPER',
        'DELIVERED_TO_RECEIVER',
        'DELIVERY_FAILED',
        'RETURNED_TO_HUB',
        'EXCEPTION_REPORTED'
    ) NOT NULL,

    related_route_step_id BIGINT NULL,
    note VARCHAR(500),
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pcl_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id),
    CONSTRAINT fk_pcl_from_user
        FOREIGN KEY (from_user_id) REFERENCES users(id),
    CONSTRAINT fk_pcl_from_hub
        FOREIGN KEY (from_hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_pcl_to_user
        FOREIGN KEY (to_user_id) REFERENCES users(id),
    CONSTRAINT fk_pcl_to_hub
        FOREIGN KEY (to_hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_pcl_route_step
        FOREIGN KEY (related_route_step_id) REFERENCES parcel_route_steps(id),
    CONSTRAINT fk_pcl_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB;

-- =========================================================
-- 7. LAST-MILE DELIVERY
-- =========================================================

CREATE TABLE shipper_profiles (
    user_id BIGINT PRIMARY KEY,
    hub_id BIGINT NOT NULL,
    vehicle_type ENUM('MOTORBIKE', 'VAN', 'TRUCK') NOT NULL DEFAULT 'MOTORBIKE',
    max_orders_per_day INT NOT NULL DEFAULT 30,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    current_lat DECIMAL(10,7) NULL,
    current_lng DECIMAL(10,7) NULL,
    last_location_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_shipper_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_shipper_profiles_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id)
) ENGINE=InnoDB;

CREATE TABLE delivery_zones (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    hub_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    province_id BIGINT NOT NULL,
    district_id BIGINT NULL,
    ward_id BIGINT NULL,
    priority INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_delivery_zones_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id),
    CONSTRAINT fk_delivery_zones_province
        FOREIGN KEY (province_id) REFERENCES provinces(id),
    CONSTRAINT fk_delivery_zones_district
        FOREIGN KEY (district_id) REFERENCES districts(id),
    CONSTRAINT fk_delivery_zones_ward
        FOREIGN KEY (ward_id) REFERENCES wards(id)
) ENGINE=InnoDB;

CREATE TABLE shipper_zones (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shipper_id BIGINT NOT NULL,
    zone_id BIGINT NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_shipper_zones_shipper
        FOREIGN KEY (shipper_id) REFERENCES shipper_profiles(user_id),
    CONSTRAINT fk_shipper_zones_zone
        FOREIGN KEY (zone_id) REFERENCES delivery_zones(id),
    CONSTRAINT uq_shipper_zone
        UNIQUE (shipper_id, zone_id)
) ENGINE=InnoDB;

CREATE TABLE delivery_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parcel_id BIGINT NOT NULL,
    shipper_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    assignment_type ENUM('AUTO_ZONE', 'AUTO_NEAREST', 'AUTO_WORKLOAD', 'MANUAL') NOT NULL DEFAULT 'MANUAL',
    assignment_reason VARCHAR(500),
    status ENUM(
        'ASSIGNED',
        'ACCEPTED',
        'PICKED_UP',
        'OUT_FOR_DELIVERY',
        'DELIVERED',
        'FAILED',
        'RETURNED_TO_HUB',
        'CANCELLED'
    ) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at DATETIME NULL,
    picked_up_at DATETIME NULL,
    completed_at DATETIME NULL,

    CONSTRAINT fk_delivery_assignments_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id),
    CONSTRAINT fk_delivery_assignments_shipper
        FOREIGN KEY (shipper_id) REFERENCES shipper_profiles(user_id),
    CONSTRAINT fk_delivery_assignments_assigned_by
        FOREIGN KEY (assigned_by) REFERENCES users(id)
) ENGINE=InnoDB;

-- =========================================================
-- 8. PUBLIC TRACKING
-- =========================================================

CREATE TABLE tracking_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    parcel_id BIGINT NULL,
    status VARCHAR(80) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    hub_id BIGINT NULL,
    visible_to_customer BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tracking_events_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_tracking_events_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id),
    CONSTRAINT fk_tracking_events_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id)
) ENGINE=InnoDB;

-- =========================================================
-- 9. BASIC INDEXES
-- =========================================================

CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_hub_id ON users(hub_id);
CREATE INDEX idx_hubs_location ON hubs(province_id, district_id, ward_id);
CREATE INDEX idx_hsa_location ON hub_service_areas(province_id, district_id, ward_id);

CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_hub ON orders(created_hub_id);
CREATE INDEX idx_orders_current_hub ON orders(current_hub_id);
CREATE INDEX idx_orders_final_hub ON orders(final_hub_id);

CREATE INDEX idx_order_parties_phone ON order_parties(phone);
CREATE INDEX idx_order_parties_order_type ON order_parties(order_id, party_type);

CREATE INDEX idx_parcels_order ON parcels(order_id);
CREATE INDEX idx_parcels_status ON parcels(status);

CREATE INDEX idx_route_steps_plan_seq ON parcel_route_steps(parcel_route_plan_id, sequence_no);
CREATE INDEX idx_route_steps_status ON parcel_route_steps(status);

CREATE INDEX idx_hub_scans_parcel_time ON hub_scans(parcel_id, scanned_at);
CREATE INDEX idx_hub_scans_hub_time ON hub_scans(hub_id, scanned_at);

CREATE INDEX idx_custody_parcel_time ON parcel_custody_logs(parcel_id, created_at);
CREATE INDEX idx_custody_to_user ON parcel_custody_logs(to_user_id);
CREATE INDEX idx_custody_to_hub ON parcel_custody_logs(to_hub_id);

CREATE INDEX idx_shipper_profiles_hub ON shipper_profiles(hub_id, is_available);
CREATE INDEX idx_delivery_zones_location ON delivery_zones(hub_id, province_id, district_id, ward_id);
CREATE INDEX idx_delivery_assignments_shipper_status ON delivery_assignments(shipper_id, status);
CREATE INDEX idx_delivery_assignments_parcel ON delivery_assignments(parcel_id);

CREATE INDEX idx_tracking_events_order_time ON tracking_events(order_id, created_at);
CREATE INDEX idx_tracking_events_parcel_time ON tracking_events(parcel_id, created_at);

-- =========================================================
-- 10. SEED ROLES
-- =========================================================

INSERT INTO roles (code, name, description) VALUES
('ADMIN', 'Admin', 'Full system access'),
('HUB_MANAGER', 'Hub Manager', 'Manage hub operations'),
('HUB_STAFF', 'Hub Staff', 'Scan and process parcels at hub'),
('DISPATCHER', 'Dispatcher', 'Plan parcel routes and assign delivery'),
('SHIPPER', 'Shipper', 'Last-mile delivery staff');

