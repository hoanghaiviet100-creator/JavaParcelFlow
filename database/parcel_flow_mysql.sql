-- =====================================================================
--  PARCEL FLOW - MySQL 8 database (single file)
-- ---------------------------------------------------------------------
--  Requires MySQL 8.0.16 or newer: CHECK constraints are only enforced from
--  8.0.16 onwards; older servers parse and silently ignore them.
--
--  Verified against mysql:8.0 - the file runs clean end to end, and a suite of
--  20 negative tests confirms every constraint actually rejects the data it is
--  meant to reject.
--
--  Run it with a client that understands DELIMITER (mysql CLI, Workbench,
--  DBeaver, HeidiSQL):
--      mysql -u root -p < parcel_flow_mysql.sql
--
--  Contents
--    0.  Database                     8.  Public tracking
--    1.  Auth / internal users        9.  Indexes
--    2.  Geography                    10. Views (operations + reporting)
--    3.  Hubs                         11. Functions and stored procedures
--    4.  Orders / parcels / parties   12. Triggers
--    5.  Parcel route plans           13. Seed: reference data
--    6.  Scans / custody / state      14. Seed: demo data
--    7.  Last-mile delivery
--
--  COMPATIBILITY
--  Every table and column name matches the schema the Spring Boot application
--  already maps, so no JPA entity has to change. What this file adds on top of
--  the original Flyway migrations:
--    * utf8mb4 throughout, for Vietnamese names and addresses;
--    * CHECK constraints carrying validation that today lives only in DTO
--      annotations and service code;
--    * ON DELETE / ON UPDATE rules on every foreign key;
--    * composite foreign keys that make a ward/district/province mismatch
--      impossible to store at all;
--    * indexes matched to the queries the repositories actually run;
--    * triggers keeping derived data correct no matter which code path writes
--      (orders.total_weight, parcel_current_state,
--      parcel_categories.requires_special_handling);
--    * views and stored procedures for reporting and for the scan / handover
--      workflow.
--
--  WHY EVERY FOREIGN KEY USES "ON UPDATE RESTRICT"
--  MySQL refuses a referential action (CASCADE, SET NULL) on any column that
--  also appears in a CHECK constraint (ERROR 3823), or that feeds a STORED
--  generated column (ERROR 1215). Several columns here do both: district_id is
--  both checked and referenced, while hub_id and parcel_id feed the uniqueness
--  keys area_key, active_parcel_id and open_parcel_id. Every id in this schema
--  is a surrogate key that never changes, so ON UPDATE CASCADE bought nothing
--  and is RESTRICT throughout. ON DELETE CASCADE is kept where a child row is
--  genuinely a component of its parent (order parties, route steps, shipper
--  zones, current state).
--
--  RELATIONSHIP TO THE APPLICATION'S OWN MIGRATIONS
--  The backend manages its schema with Flyway
--  (src/main/resources/db/migration/V1..V4) and applies it automatically on
--  startup. This file is the standalone equivalent for running the database on
--  its own - for coursework submission, for inspecting the design, or for a
--  MySQL instance without the application. Do not point Flyway at a database
--  built from this file unless you also set spring.flyway.baseline-on-migrate,
--  or Flyway will refuse to start against a non-empty schema it has no history
--  for. See README.md.
-- =====================================================================

-- =====================================================================
-- 0. DATABASE
-- =====================================================================

CREATE DATABASE IF NOT EXISTS parcel_flow
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE parcel_flow;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;


-- =====================================================================
-- 1. AUTH / INTERNAL USERS
-- =====================================================================

CREATE TABLE roles (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- role code doubles as the Spring Security authority (ROLE_<code>)
    CONSTRAINT ck_roles_code_upper CHECK (code = UPPER(code))
) ENGINE=InnoDB;

CREATE TABLE users (
    id                   BIGINT       PRIMARY KEY AUTO_INCREMENT,
    full_name            VARCHAR(150) NOT NULL,
    email                VARCHAR(150) NOT NULL UNIQUE,
    phone                VARCHAR(30)  UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    role_id              BIGINT       NOT NULL,
    hub_id               BIGINT       NULL,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,

    -- auth columns (were Flyway V2)
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    password_expires_at  DATETIME     NULL,

    -- permanent-lock metadata (were Flyway V3)
    lock_reason          VARCHAR(100) NULL,
    locked_at            DATETIME     NULL,

    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id) REFERENCES roles(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- AuthService lower-cases every email before it reaches the database.
    CONSTRAINT ck_users_email_lower  CHECK (email = LOWER(email)),
    CONSTRAINT ck_users_email_format CHECK (email LIKE '%_@_%._%'),
    CONSTRAINT ck_users_name_filled  CHECK (TRIM(full_name) <> ''),

    -- A temporary password always carries a deadline: createAccount() and
    -- resendTemporaryPassword() set both, changePassword() clears both.
    -- Without this a temp password would never expire and the
    -- AUTH_TEMP_PASSWORD_EXPIRED path would be unreachable.
    CONSTRAINT ck_users_temp_pwd_expiry
        CHECK (must_change_password = FALSE OR password_expires_at IS NOT NULL),

    -- lockPermanently() writes reason + locked_at together;
    -- unlockAccount() clears them together.
    CONSTRAINT ck_users_lock_paired
        CHECK ((lock_reason IS NULL) = (locked_at IS NULL)),
    CONSTRAINT ck_users_locked_is_inactive
        CHECK (locked_at IS NULL OR is_active = FALSE)
) ENGINE=InnoDB;

-- hub_id FK is added after hubs exists (section 3).


-- =====================================================================
-- 2. GEOGRAPHY
-- =====================================================================

CREATE TABLE provinces (
    id   BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30)  NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE districts (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    province_id BIGINT       NOT NULL,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,

    CONSTRAINT fk_districts_province
        FOREIGN KEY (province_id) REFERENCES provinces(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- Referenced key for the composite FKs below. Not redundant with the PK:
    -- it is what lets a child row prove "my district really is in my province".
    CONSTRAINT uq_districts_id_province UNIQUE (id, province_id)
) ENGINE=InnoDB;

CREATE TABLE wards (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    district_id BIGINT       NOT NULL,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,

    CONSTRAINT fk_wards_district
        FOREIGN KEY (district_id) REFERENCES districts(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT uq_wards_id_district UNIQUE (id, district_id)
) ENGINE=InnoDB;


-- =====================================================================
-- 3. HUBS
-- =====================================================================

CREATE TABLE hubs (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code          VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(150) NOT NULL,
    type          ENUM('MAIN_HUB', 'TRANSIT_HUB', 'DELIVERY_HUB', 'PICKUP_HUB') NOT NULL,
    phone         VARCHAR(30),
    address_line  VARCHAR(255) NOT NULL,
    ward_id       BIGINT       NULL,
    district_id   BIGINT       NOT NULL,
    province_id   BIGINT       NOT NULL,
    parent_hub_id BIGINT       NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,

    -- The plain single-column FKs of the original schema let a hub name a ward
    -- in Ha Noi while claiming Ho Chi Minh City as its province: each key was
    -- valid on its own. These composite keys close that hole. ward_id may still
    -- be NULL (district-level address) because MySQL skips a composite FK check
    -- when any part of the key is NULL.
    CONSTRAINT fk_hubs_district_in_province
        FOREIGN KEY (district_id, province_id) REFERENCES districts(id, province_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_hubs_ward_in_district
        FOREIGN KEY (ward_id, district_id) REFERENCES wards(id, district_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_hubs_parent
        FOREIGN KEY (parent_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT ck_hubs_name_filled   CHECK (TRIM(name) <> ''),
    CONSTRAINT ck_hubs_address_filled CHECK (TRIM(address_line) <> '')
    -- parent_hub_id <> id cannot be a CHECK in MySQL (AUTO_INCREMENT is not yet
    -- assigned when the row-level CHECK runs on INSERT); trg_hubs_bi/bu handle it.
) ENGINE=InnoDB;

-- Deferred from section 1.
ALTER TABLE users
    ADD CONSTRAINT fk_users_hub
    FOREIGN KEY (hub_id) REFERENCES hubs(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE TABLE hub_service_areas (
    id          BIGINT   PRIMARY KEY AUTO_INCREMENT,
    hub_id      BIGINT   NOT NULL,
    province_id BIGINT   NOT NULL,
    district_id BIGINT   NULL,
    ward_id     BIGINT   NULL,
    is_active   BOOLEAN  NOT NULL DEFAULT TRUE,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Generated key: NULL is never equal to NULL, so the original
    -- UNIQUE (hub_id, province_id, district_id, ward_id) silently allowed
    -- (1, 5, NULL, NULL) to be inserted any number of times. Folding the NULLs
    -- into 0 makes the duplicate a real duplicate. Ids start at 1, so 0 can
    -- never collide with a genuine key.
    area_key VARCHAR(64) GENERATED ALWAYS AS (CONCAT_WS('-',
        hub_id, IFNULL(province_id, 0), IFNULL(district_id, 0), IFNULL(ward_id, 0)
    )) STORED,

    -- RESTRICT, not CASCADE: hub_id is a base column of the STORED generated
    -- column area_key, and MySQL rejects CASCADE / SET NULL referential actions
    -- on such a column (ERROR 1215). Retiring a hub therefore means retiring its
    -- service areas first, which is the safer order anyway.
    CONSTRAINT fk_hsa_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- ON UPDATE RESTRICT, not CASCADE: MySQL refuses to let a column take part
    -- in a CHECK constraint when it also carries a referential action, and
    -- ck_hsa_ward_needs_district below needs district_id. Geography ids are
    -- surrogate keys that never change, so nothing is lost.
    CONSTRAINT fk_hsa_district_in_province
        FOREIGN KEY (district_id, province_id) REFERENCES districts(id, province_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_hsa_ward_in_district
        FOREIGN KEY (ward_id, district_id) REFERENCES wards(id, district_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT uq_hub_service_area UNIQUE (area_key),

    -- A ward-level area must state its district too, otherwise the composite
    -- FK above is skipped and the ward could belong anywhere.
    CONSTRAINT ck_hsa_ward_needs_district
        CHECK (ward_id IS NULL OR district_id IS NOT NULL)
) ENGINE=InnoDB;


-- =====================================================================
-- 4. ORDERS / PARCELS / PARTY SNAPSHOTS
-- =====================================================================

CREATE TABLE orders (
    id             BIGINT      PRIMARY KEY AUTO_INCREMENT,
    order_code     VARCHAR(50) NOT NULL UNIQUE,
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
    final_hub_id   BIGINT NULL,

    service_type ENUM('STANDARD', 'EXPRESS', 'ECONOMY')       NOT NULL DEFAULT 'STANDARD',
    payment_type ENUM('SENDER_PAY', 'RECEIVER_PAY', 'COD')    NOT NULL DEFAULT 'SENDER_PAY',

    -- Maintained by trg_parcels_ai/au/ad: it is SUM(parcels.weight) and the
    -- application only ever computes it once, at creation.
    total_weight DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_fee    DECIMAL(12,2) NOT NULL DEFAULT 0,
    cod_amount   DECIMAL(12,2) NOT NULL DEFAULT 0,

    note       VARCHAR(500),
    created_by BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_created_hub
        FOREIGN KEY (created_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_orders_current_hub
        FOREIGN KEY (current_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_orders_final_hub
        FOREIGN KEY (final_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_orders_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- Referenced key so a child row can prove it belongs to this order.
    CONSTRAINT uq_orders_id_code UNIQUE (id, order_code),

    -- CodeGenerator.orderCode(): "OD" + yyyyMMdd + 6 characters drawn from an
    -- alphabet with no I, O, 0 or 1.
    CONSTRAINT ck_orders_code_format
        CHECK (REGEXP_LIKE(order_code, '^OD[0-9]{8}[ABCDEFGHJKLMNPQRSTUVWXYZ2-9]{6}$')),

    CONSTRAINT ck_orders_amounts_non_negative
        CHECK (total_weight >= 0 AND total_fee >= 0 AND cod_amount >= 0),

    -- Money is only collected on delivery for a COD order. Stated in this
    -- direction on purpose: OrderService defaults cod_amount to 0 for every
    -- payment type, so requiring COD orders to be non-zero would reject orders
    -- the application legitimately creates today.
    CONSTRAINT ck_orders_cod_requires_cod_payment
        CHECK (cod_amount = 0 OR payment_type = 'COD')
) ENGINE=InnoDB;

CREATE TABLE order_parties (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    party_type   ENUM('SENDER', 'RECEIVER') NOT NULL,

    full_name    VARCHAR(150) NOT NULL,
    phone        VARCHAR(30)  NOT NULL,
    email        VARCHAR(150) NULL,
    address_line VARCHAR(255) NOT NULL,
    ward_id      BIGINT       NULL,
    district_id  BIGINT       NOT NULL,
    province_id  BIGINT       NOT NULL,
    latitude     DECIMAL(10,7) NULL,
    longitude    DECIMAL(10,7) NULL,

    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Digits only, so it matches PublicTrackingService.normalize() exactly.
    -- Indexed below: the public tracking page compares on this value.
    phone_digits VARCHAR(30) GENERATED ALWAYS AS (
        REGEXP_REPLACE(phone, '[^0-9]', '')
    ) STORED,

    CONSTRAINT fk_order_parties_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_order_parties_district_in_province
        FOREIGN KEY (district_id, province_id) REFERENCES districts(id, province_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_order_parties_ward_in_district
        FOREIGN KEY (ward_id, district_id) REFERENCES wards(id, district_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- Exactly one sender and one receiver per order. The "at most one" half is
    -- structural; "at least one" is checked by trg_orders_parties_* below.
    CONSTRAINT uq_order_party_type UNIQUE (order_id, party_type),

    CONSTRAINT ck_order_parties_name_filled    CHECK (TRIM(full_name) <> ''),
    CONSTRAINT ck_order_parties_phone_filled   CHECK (TRIM(phone) <> ''),
    CONSTRAINT ck_order_parties_address_filled CHECK (TRIM(address_line) <> ''),
    CONSTRAINT ck_order_parties_email_format
        CHECK (email IS NULL OR email LIKE '%_@_%._%'),
    CONSTRAINT ck_order_parties_coords_paired
        CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_order_parties_lat_range
        CHECK (latitude  IS NULL OR latitude  BETWEEN -90  AND 90),
    CONSTRAINT ck_order_parties_lng_range
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB;

CREATE TABLE parcel_categories (
    id                        BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code                      VARCHAR(50)  NOT NULL UNIQUE,
    name                      VARCHAR(100) NOT NULL,
    description               VARCHAR(255),
    is_fragile                BOOLEAN      NOT NULL DEFAULT FALSE,
    is_liquid                 BOOLEAN      NOT NULL DEFAULT FALSE,
    is_high_value             BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Summarises the three flags above. Kept as a plain column (the JPA entity
    -- writes it) but recomputed by trg_parcel_categories_bi/bu so it can never
    -- disagree with them.
    requires_special_handling BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;

CREATE TABLE parcels (
    id             BIGINT        PRIMARY KEY AUTO_INCREMENT,
    order_id       BIGINT        NOT NULL,
    parcel_code    VARCHAR(50)   NOT NULL UNIQUE,
    category_id    BIGINT        NULL,

    weight         DECIMAL(10,2) NOT NULL,
    length         DECIMAL(10,2) NULL,
    width          DECIMAL(10,2) NULL,
    height         DECIMAL(10,2) NULL,
    declared_value DECIMAL(12,2) NOT NULL DEFAULT 0,
    note           VARCHAR(500),

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
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,

    -- Volumetric weight at the industry-standard divisor of 6000; NULL until
    -- all three dimensions are known. Reporting only — no code path uses it.
    volumetric_weight DECIMAL(10,2) GENERATED ALWAYS AS (
        ROUND(length * width * height / 6000, 2)
    ) STORED,

    CONSTRAINT fk_parcels_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_parcels_category
        FOREIGN KEY (category_id) REFERENCES parcel_categories(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- Referenced key: lets tracking_events prove its parcel is on its order.
    CONSTRAINT uq_parcels_id_order UNIQUE (id, order_id),

    -- CodeGenerator.parcelCode(): "PC" + yyyyMMdd + 8 characters.
    CONSTRAINT ck_parcels_code_format
        CHECK (REGEXP_LIKE(parcel_code, '^PC[0-9]{8}[ABCDEFGHJKLMNPQRSTUVWXYZ2-9]{8}$')),

    -- ParcelRequest.weight is @Positive.
    CONSTRAINT ck_parcels_weight_positive   CHECK (weight > 0),
    CONSTRAINT ck_parcels_value_non_negative CHECK (declared_value >= 0),
    CONSTRAINT ck_parcels_dimensions_positive CHECK (
        (length IS NULL OR length > 0)
        AND (width  IS NULL OR width  > 0)
        AND (height IS NULL OR height > 0)),
    -- A box is measured on all three axes or on none.
    CONSTRAINT ck_parcels_dimensions_complete CHECK (
        (length IS NULL AND width IS NULL AND height IS NULL)
        OR (length IS NOT NULL AND width IS NOT NULL AND height IS NOT NULL))
) ENGINE=InnoDB;


-- =====================================================================
-- 5. PARCEL ROUTE PLAN
-- =====================================================================

CREATE TABLE parcel_route_plans (
    id          BIGINT   PRIMARY KEY AUTO_INCREMENT,
    parcel_id   BIGINT   NOT NULL,
    planned_by  BIGINT   NOT NULL,
    status      ENUM('DRAFT', 'APPROVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
                NOT NULL DEFAULT 'DRAFT',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at DATETIME NULL,

    -- The original constraint was named uq_active_route_plan_per_parcel but was
    -- a plain UNIQUE (parcel_id): it allowed one plan per parcel *ever*, so a
    -- cancelled plan could never be replaced. This generated column carries the
    -- parcel id only while the plan is still open, so the unique index enforces
    -- what the name always claimed.
    active_parcel_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('DRAFT', 'APPROVED', 'IN_PROGRESS') THEN parcel_id END
    ) STORED,

    -- ON UPDATE RESTRICT: parcel_id feeds the STORED generated column
    -- active_parcel_id, and MySQL rejects CASCADE on such a base column.
    CONSTRAINT fk_prp_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_prp_planned_by
        FOREIGN KEY (planned_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT uq_active_route_plan_per_parcel UNIQUE (active_parcel_id),
    CONSTRAINT uq_route_plans_id_parcel        UNIQUE (id, parcel_id),

    CONSTRAINT ck_prp_approved_after_created
        CHECK (approved_at IS NULL OR approved_at >= created_at),
    CONSTRAINT ck_prp_draft_not_approved
        CHECK (status <> 'DRAFT' OR approved_at IS NULL)
) ENGINE=InnoDB;

CREATE TABLE parcel_route_steps (
    id                   BIGINT   PRIMARY KEY AUTO_INCREMENT,
    parcel_route_plan_id BIGINT   NOT NULL,
    sequence_no          INT      NOT NULL,
    from_hub_id          BIGINT   NOT NULL,
    to_hub_id            BIGINT   NOT NULL,

    expected_departure_at DATETIME NULL,
    expected_arrival_at   DATETIME NULL,
    actual_departure_at   DATETIME NULL,
    actual_arrival_at     DATETIME NULL,

    status ENUM('PENDING', 'READY', 'IN_TRANSIT', 'ARRIVED', 'SKIPPED', 'CANCELLED')
           NOT NULL DEFAULT 'PENDING',

    CONSTRAINT fk_prs_plan
        FOREIGN KEY (parcel_route_plan_id) REFERENCES parcel_route_plans(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_prs_from_hub
        FOREIGN KEY (from_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_prs_to_hub
        FOREIGN KEY (to_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT uq_route_step_sequence UNIQUE (parcel_route_plan_id, sequence_no),

    CONSTRAINT ck_prs_sequence_positive CHECK (sequence_no >= 1),
    -- A leg that starts and ends at the same hub is not a leg.
    CONSTRAINT ck_prs_distinct_hubs      CHECK (from_hub_id <> to_hub_id),
    CONSTRAINT ck_prs_expected_order
        CHECK (expected_arrival_at IS NULL OR expected_departure_at IS NULL
               OR expected_arrival_at >= expected_departure_at),
    CONSTRAINT ck_prs_actual_order
        CHECK (actual_arrival_at IS NULL OR actual_departure_at IS NULL
               OR actual_arrival_at >= actual_departure_at),
    -- Cannot arrive without having departed.
    CONSTRAINT ck_prs_arrival_needs_departure
        CHECK (actual_arrival_at IS NULL OR actual_departure_at IS NOT NULL),
    -- Written as implications (NOT p OR q) rather than CASE: MySQL rejects a
    -- CASE expression in a CHECK with "non-boolean type specified" (ERROR 3812).
    CONSTRAINT ck_prs_status_timestamps CHECK (
        (status <> 'IN_TRANSIT' OR actual_departure_at IS NOT NULL)
        AND (status <> 'ARRIVED' OR actual_arrival_at IS NOT NULL)
        AND (status <> 'PENDING'
             OR (actual_departure_at IS NULL AND actual_arrival_at IS NULL)))
) ENGINE=InnoDB;


-- =====================================================================
-- 6. HUB SCANS / CURRENT STATE / CUSTODY
-- =====================================================================

CREATE TABLE hub_scans (
    id            BIGINT   PRIMARY KEY AUTO_INCREMENT,
    parcel_id     BIGINT   NOT NULL,
    hub_id        BIGINT   NOT NULL,
    scan_type     ENUM('INBOUND', 'OUTBOUND', 'SORTING', 'EXCEPTION') NOT NULL,
    route_step_id BIGINT   NULL,
    scanned_by    BIGINT   NOT NULL,
    note          VARCHAR(500),
    scanned_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_hub_scans_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_hub_scans_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_hub_scans_route_step
        FOREIGN KEY (route_step_id) REFERENCES parcel_route_steps(id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_hub_scans_scanned_by
        FOREIGN KEY (scanned_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- An EXCEPTION scan with no explanation is useless to support staff.
    CONSTRAINT ck_hub_scans_exception_has_note
        CHECK (scan_type <> 'EXCEPTION' OR TRIM(IFNULL(note, '')) <> '')
) ENGINE=InnoDB;

CREATE TABLE parcel_current_state (
    parcel_id      BIGINT PRIMARY KEY,
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
    current_hub_id        BIGINT NULL,
    current_user_id       BIGINT NULL,
    current_route_step_id BIGINT NULL,

    responsibility_type ENUM('HUB', 'DRIVER', 'SHIPPER', 'CUSTOMER', 'SYSTEM')
                        NOT NULL DEFAULT 'SYSTEM',
    responsible_user_id BIGINT NULL,
    responsible_hub_id  BIGINT NULL,

    last_scan_at DATETIME NULL,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_pcs_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_pcs_current_hub
        FOREIGN KEY (current_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pcs_current_user
        FOREIGN KEY (current_user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pcs_route_step
        FOREIGN KEY (current_route_step_id) REFERENCES parcel_route_steps(id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_pcs_responsible_user
        FOREIGN KEY (responsible_user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pcs_responsible_hub
        FOREIGN KEY (responsible_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- Custody must name whoever actually holds the parcel: a driver or shipper
    -- is a person, a hub is a place. Without this, "IN_TRANSIT, responsible:
    -- DRIVER, driver: NULL" is storable and the parcel has no owner.
    CONSTRAINT ck_pcs_responsibility_target CHECK (
        responsibility_type NOT IN ('DRIVER', 'SHIPPER')
        OR responsible_user_id IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE parcel_custody_logs (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    parcel_id BIGINT NOT NULL,

    from_responsibility_type ENUM('HUB', 'DRIVER', 'SHIPPER', 'CUSTOMER', 'SYSTEM') NULL,
    from_user_id BIGINT NULL,
    from_hub_id  BIGINT NULL,

    to_responsibility_type ENUM('HUB', 'DRIVER', 'SHIPPER', 'CUSTOMER', 'SYSTEM') NOT NULL,
    to_user_id BIGINT NULL,
    to_hub_id  BIGINT NULL,

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
    note       VARCHAR(500),
    created_by BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pcl_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pcl_from_user
        FOREIGN KEY (from_user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pcl_from_hub
        FOREIGN KEY (from_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pcl_to_user
        FOREIGN KEY (to_user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pcl_to_hub
        FOREIGN KEY (to_hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pcl_route_step
        FOREIGN KEY (related_route_step_id) REFERENCES parcel_route_steps(id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_pcl_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT ck_pcl_to_target CHECK (
        to_responsibility_type NOT IN ('DRIVER', 'SHIPPER')
        OR to_user_id IS NOT NULL)
) ENGINE=InnoDB;


-- =====================================================================
-- 7. LAST-MILE DELIVERY
-- =====================================================================

CREATE TABLE shipper_profiles (
    user_id            BIGINT PRIMARY KEY,
    hub_id             BIGINT NOT NULL,
    vehicle_type       ENUM('MOTORBIKE', 'VAN', 'TRUCK') NOT NULL DEFAULT 'MOTORBIKE',
    max_orders_per_day INT     NOT NULL DEFAULT 30,
    is_available       BOOLEAN NOT NULL DEFAULT TRUE,
    current_lat        DECIMAL(10,7) NULL,
    current_lng        DECIMAL(10,7) NULL,
    last_location_at   DATETIME NULL,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_shipper_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_shipper_profiles_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT ck_shipper_capacity
        CHECK (max_orders_per_day BETWEEN 1 AND 500),
    CONSTRAINT ck_shipper_coords_paired
        CHECK ((current_lat IS NULL) = (current_lng IS NULL)),
    -- A position with no timestamp can never be aged out of a "nearest
    -- available shipper" search.
    CONSTRAINT ck_shipper_location_timestamped
        CHECK (current_lat IS NULL OR last_location_at IS NOT NULL),
    CONSTRAINT ck_shipper_lat_range
        CHECK (current_lat IS NULL OR current_lat BETWEEN -90 AND 90),
    CONSTRAINT ck_shipper_lng_range
        CHECK (current_lng IS NULL OR current_lng BETWEEN -180 AND 180)
) ENGINE=InnoDB;

CREATE TABLE delivery_zones (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    hub_id      BIGINT       NOT NULL,
    name        VARCHAR(150) NOT NULL,
    province_id BIGINT       NOT NULL,
    district_id BIGINT       NULL,
    ward_id     BIGINT       NULL,
    priority    INT          NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Same NULL-folding trick as hub_service_areas: without it the same
    -- geography could be registered as a zone for one hub many times over.
    area_key VARCHAR(64) GENERATED ALWAYS AS (CONCAT_WS('-',
        hub_id, province_id, IFNULL(district_id, 0), IFNULL(ward_id, 0)
    )) STORED,

    -- RESTRICT for the same reason as hub_service_areas: hub_id feeds area_key.
    CONSTRAINT fk_delivery_zones_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- ON UPDATE RESTRICT for the same reason as hub_service_areas: district_id
    -- appears in ck_delivery_zone_ward_needs_district.
    CONSTRAINT fk_delivery_zones_district_in_province
        FOREIGN KEY (district_id, province_id) REFERENCES districts(id, province_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_delivery_zones_ward_in_district
        FOREIGN KEY (ward_id, district_id) REFERENCES wards(id, district_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT uq_delivery_zone_area UNIQUE (area_key),
    CONSTRAINT uq_delivery_zone_name UNIQUE (hub_id, name),

    CONSTRAINT ck_delivery_zone_priority CHECK (priority >= 0),
    CONSTRAINT ck_delivery_zone_ward_needs_district
        CHECK (ward_id IS NULL OR district_id IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE shipper_zones (
    id         BIGINT  PRIMARY KEY AUTO_INCREMENT,
    shipper_id BIGINT  NOT NULL,
    zone_id    BIGINT  NOT NULL,
    priority   INT     NOT NULL DEFAULT 0,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_shipper_zones_shipper
        FOREIGN KEY (shipper_id) REFERENCES shipper_profiles(user_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_shipper_zones_zone
        FOREIGN KEY (zone_id) REFERENCES delivery_zones(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,

    CONSTRAINT uq_shipper_zone UNIQUE (shipper_id, zone_id),
    CONSTRAINT ck_shipper_zone_priority CHECK (priority >= 0)
) ENGINE=InnoDB;

CREATE TABLE delivery_assignments (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    parcel_id       BIGINT NOT NULL,
    shipper_id      BIGINT NOT NULL,
    assigned_by     BIGINT NOT NULL,
    assignment_type ENUM('AUTO_ZONE', 'AUTO_NEAREST', 'AUTO_WORKLOAD', 'MANUAL')
                    NOT NULL DEFAULT 'MANUAL',
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
    assigned_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at  DATETIME NULL,
    picked_up_at DATETIME NULL,
    completed_at DATETIME NULL,

    -- Nothing in the Java layer stopped a second open assignment on the same
    -- parcel, which would put two shippers on the same doorstep. This carries
    -- the parcel id only while the assignment is still in flight, so the unique
    -- index below permits any number of closed assignments but only one open.
    open_parcel_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('ASSIGNED', 'ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY')
             THEN parcel_id END
    ) STORED,

    -- ON UPDATE RESTRICT: parcel_id feeds the STORED generated column
    -- open_parcel_id (see the note on uq_open_assignment_per_parcel).
    CONSTRAINT fk_delivery_assignments_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_delivery_assignments_shipper
        FOREIGN KEY (shipper_id) REFERENCES shipper_profiles(user_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_delivery_assignments_assigned_by
        FOREIGN KEY (assigned_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT uq_open_assignment_per_parcel UNIQUE (open_parcel_id),

    -- The lifecycle timestamps run forward.
    CONSTRAINT ck_da_time_order CHECK (
        (accepted_at  IS NULL OR accepted_at  >= assigned_at)
        AND (picked_up_at IS NULL OR picked_up_at >= IFNULL(accepted_at, assigned_at))
        AND (completed_at IS NULL OR completed_at >=
             IFNULL(picked_up_at, IFNULL(accepted_at, assigned_at)))),

    -- The status/timestamp mapping from
    -- DeliveryAssignmentService.updateStatusForShipper(). Reaching a state
    -- implies its own stamp; earlier stamps are not required, because a shipper
    -- may go straight from ASSIGNED to FAILED.
    CONSTRAINT ck_da_status_timestamps CHECK (
        (status <> 'ASSIGNED'
         OR (accepted_at IS NULL AND picked_up_at IS NULL AND completed_at IS NULL))
        AND (status <> 'ACCEPTED'
             OR (accepted_at IS NOT NULL AND completed_at IS NULL))
        AND (status <> 'PICKED_UP'
             OR (picked_up_at IS NOT NULL AND completed_at IS NULL))
        AND (status <> 'OUT_FOR_DELIVERY' OR completed_at IS NULL)
        -- DELIVERED / FAILED / RETURNED_TO_HUB / CANCELLED are the closed states.
        AND (status NOT IN ('DELIVERED', 'FAILED', 'RETURNED_TO_HUB', 'CANCELLED')
             OR completed_at IS NOT NULL))
) ENGINE=InnoDB;


-- =====================================================================
-- 8. PUBLIC TRACKING
-- =====================================================================

CREATE TABLE tracking_events (
    id        BIGINT      PRIMARY KEY AUTO_INCREMENT,
    order_id  BIGINT      NOT NULL,
    parcel_id BIGINT      NULL,
    status    VARCHAR(80) NOT NULL,
    title     VARCHAR(150) NOT NULL,
    message   VARCHAR(500) NOT NULL,
    hub_id    BIGINT      NULL,
    visible_to_customer BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tracking_events_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    -- Composite, not a plain parcel_id FK: previously the two keys were
    -- independent, so an event could attach parcel 900 (which belongs to order
    -- 12) to order 34 and leak it to the wrong customer on the public page.
    CONSTRAINT fk_tracking_events_parcel_in_order
        FOREIGN KEY (parcel_id, order_id) REFERENCES parcels(id, order_id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_tracking_events_hub
        FOREIGN KEY (hub_id) REFERENCES hubs(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT ck_tracking_status_filled  CHECK (TRIM(status)  <> ''),
    CONSTRAINT ck_tracking_title_filled   CHECK (TRIM(title)   <> ''),
    CONSTRAINT ck_tracking_message_filled CHECK (TRIM(message) <> '')
) ENGINE=InnoDB;


-- =====================================================================
-- 9. INDEXES
--    Every index below backs a query the repositories or services actually
--    run; the comment names it.
-- =====================================================================

-- users -------------------------------------------------------------
CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_hub_id  ON users(hub_id);
-- UserRepository.findByEmail / existsByEmail (the login hot path)
CREATE INDEX idx_users_email   ON users(email);
-- admin "locked accounts" screen
CREATE INDEX idx_users_locked  ON users(is_active, locked_at);
-- sweep for expired temporary passwords
CREATE INDEX idx_users_temp_pwd ON users(must_change_password, password_expires_at);

-- geography ---------------------------------------------------------
CREATE INDEX idx_districts_province ON districts(province_id);
CREATE INDEX idx_wards_district     ON wards(district_id);

-- hubs --------------------------------------------------------------
CREATE INDEX idx_hubs_location ON hubs(province_id, district_id, ward_id);
CREATE INDEX idx_hubs_parent   ON hubs(parent_hub_id);
CREATE INDEX idx_hubs_type     ON hubs(type, is_active);
-- HubService.list() sorts by name and the UI only shows live hubs
CREATE INDEX idx_hubs_active_name ON hubs(is_active, name);
CREATE INDEX idx_hsa_location  ON hub_service_areas(province_id, district_id, ward_id);
CREATE INDEX idx_hsa_hub       ON hub_service_areas(hub_id, is_active);

-- orders ------------------------------------------------------------
CREATE INDEX idx_orders_status       ON orders(status);
CREATE INDEX idx_orders_created_hub  ON orders(created_hub_id);
CREATE INDEX idx_orders_current_hub  ON orders(current_hub_id);
CREATE INDEX idx_orders_final_hub    ON orders(final_hub_id);
CREATE INDEX idx_orders_created_by   ON orders(created_by);
-- OrderService.list() pages newest first; also serves "open orders by status"
CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);
CREATE INDEX idx_orders_created_at     ON orders(created_at DESC);

-- order parties -----------------------------------------------------
CREATE INDEX idx_order_parties_phone      ON order_parties(phone);
-- PublicTrackingService compares digits only, so the index must match
CREATE INDEX idx_order_parties_phone_digits ON order_parties(phone_digits);
CREATE INDEX idx_order_parties_order_type ON order_parties(order_id, party_type);
CREATE INDEX idx_order_parties_location   ON order_parties(province_id, district_id, ward_id);

-- parcels -----------------------------------------------------------
CREATE INDEX idx_parcels_order    ON parcels(order_id);
CREATE INDEX idx_parcels_status   ON parcels(status);
CREATE INDEX idx_parcels_category ON parcels(category_id);
CREATE INDEX idx_parcels_created  ON parcels(created_at DESC);

-- route plans -------------------------------------------------------
CREATE INDEX idx_route_plans_parcel   ON parcel_route_plans(parcel_id);
CREATE INDEX idx_route_plans_planner  ON parcel_route_plans(planned_by);
CREATE INDEX idx_route_plans_status   ON parcel_route_plans(status);
CREATE INDEX idx_route_steps_plan_seq ON parcel_route_steps(parcel_route_plan_id, sequence_no);
CREATE INDEX idx_route_steps_status   ON parcel_route_steps(status);
CREATE INDEX idx_route_steps_from_hub ON parcel_route_steps(from_hub_id, status);
CREATE INDEX idx_route_steps_to_hub   ON parcel_route_steps(to_hub_id, status);

-- scans / custody ---------------------------------------------------
CREATE INDEX idx_hub_scans_parcel_time ON hub_scans(parcel_id, scanned_at);
CREATE INDEX idx_hub_scans_hub_time    ON hub_scans(hub_id, scanned_at);
CREATE INDEX idx_hub_scans_scanner     ON hub_scans(scanned_by, scanned_at);
CREATE INDEX idx_hub_scans_type_time   ON hub_scans(scan_type, scanned_at);

CREATE INDEX idx_custody_parcel_time ON parcel_custody_logs(parcel_id, created_at);
CREATE INDEX idx_custody_to_user     ON parcel_custody_logs(to_user_id);
CREATE INDEX idx_custody_to_hub      ON parcel_custody_logs(to_hub_id);
CREATE INDEX idx_custody_action      ON parcel_custody_logs(action_type, created_at);

CREATE INDEX idx_pcs_status_hub ON parcel_current_state(current_status, current_hub_id);
CREATE INDEX idx_pcs_responsible_user ON parcel_current_state(responsible_user_id);

-- last mile ---------------------------------------------------------
CREATE INDEX idx_shipper_profiles_hub ON shipper_profiles(hub_id, is_available);
CREATE INDEX idx_delivery_zones_location
    ON delivery_zones(hub_id, province_id, district_id, ward_id);
CREATE INDEX idx_shipper_zones_zone ON shipper_zones(zone_id, is_active, priority);
CREATE INDEX idx_delivery_assignments_shipper_status
    ON delivery_assignments(shipper_id, status);
-- DeliveryAssignmentService.listForShipper(): the shipper's queue, newest first
CREATE INDEX idx_delivery_assignments_shipper_time
    ON delivery_assignments(shipper_id, assigned_at DESC);
CREATE INDEX idx_delivery_assignments_parcel ON delivery_assignments(parcel_id);
CREATE INDEX idx_delivery_assignments_status_time
    ON delivery_assignments(status, assigned_at DESC);

-- tracking ----------------------------------------------------------
CREATE INDEX idx_tracking_events_order_time  ON tracking_events(order_id, created_at);
CREATE INDEX idx_tracking_events_parcel_time ON tracking_events(parcel_id, created_at);
-- PublicTrackingService filters to customer-visible rows before ordering
CREATE INDEX idx_tracking_events_public
    ON tracking_events(order_id, visible_to_customer, created_at);


-- =====================================================================
-- 10. VIEWS
-- =====================================================================

-- Flattened geography, used by most of the views below.
CREATE OR REPLACE VIEW v_geography AS
SELECT w.id        AS ward_id,
       w.name      AS ward_name,
       d.id        AS district_id,
       d.name      AS district_name,
       p.id        AS province_id,
       p.name      AS province_name
FROM wards w
JOIN districts d ON d.id = w.district_id
JOIN provinces p ON p.id = d.province_id;

-- Hub directory with readable location and its parent's name.
CREATE OR REPLACE VIEW v_hub_directory AS
SELECT h.id, h.code, h.name, h.type, h.phone, h.is_active,
       h.address_line,
       w.name  AS ward_name,
       d.name  AS district_name,
       p.name  AS province_name,
       parent.code AS parent_hub_code,
       parent.name AS parent_hub_name
FROM hubs h
JOIN districts d      ON d.id = h.district_id
JOIN provinces p      ON p.id = d.province_id
LEFT JOIN wards w     ON w.id = h.ward_id
LEFT JOIN hubs parent ON parent.id = h.parent_hub_id;

-- One row per order: parties, totals and where it is.
CREATE OR REPLACE VIEW v_order_detail AS
SELECT o.id,
       o.order_code,
       o.status,
       o.service_type,
       o.payment_type,
       o.total_weight,
       o.total_fee,
       o.cod_amount,
       o.created_at,
       ch.code AS created_hub_code,
       ch.name AS created_hub_name,
       cur.name AS current_hub_name,
       fh.name  AS final_hub_name,
       u.full_name AS created_by_name,
       s.full_name    AS sender_name,
       s.phone        AS sender_phone,
       CONCAT_WS(', ', s.address_line, sw.name, sd.name, sp.name) AS sender_address,
       r.full_name    AS receiver_name,
       r.phone        AS receiver_phone,
       CONCAT_WS(', ', r.address_line, rw.name, rd.name, rp.name) AS receiver_address,
       (SELECT COUNT(*) FROM parcels px WHERE px.order_id = o.id) AS parcel_count
FROM orders o
JOIN hubs ch      ON ch.id  = o.created_hub_id
LEFT JOIN hubs cur ON cur.id = o.current_hub_id
LEFT JOIN hubs fh  ON fh.id  = o.final_hub_id
JOIN users u      ON u.id   = o.created_by
LEFT JOIN order_parties s ON s.order_id = o.id AND s.party_type = 'SENDER'
LEFT JOIN districts sd ON sd.id = s.district_id
LEFT JOIN provinces sp ON sp.id = sd.province_id
LEFT JOIN wards     sw ON sw.id = s.ward_id
LEFT JOIN order_parties r ON r.order_id = o.id AND r.party_type = 'RECEIVER'
LEFT JOIN districts rd ON rd.id = r.district_id
LEFT JOIN provinces rp ON rp.id = rd.province_id
LEFT JOIN wards     rw ON rw.id = r.ward_id;

-- Operations board: every parcel with its live location and holder.
CREATE OR REPLACE VIEW v_parcel_tracking AS
SELECT pc.id            AS parcel_id,
       pc.parcel_code,
       pc.weight,
       pc.status        AS parcel_status,
       o.id             AS order_id,
       o.order_code,
       o.status         AS order_status,
       o.service_type,
       cat.name         AS category_name,
       cat.requires_special_handling,
       cs.current_status,
       cs.responsibility_type,
       ch.code          AS current_hub_code,
       ch.name          AS current_hub_name,
       ru.full_name     AS responsible_person,
       rh.name          AS responsible_hub,
       cs.last_scan_at,
       TIMESTAMPDIFF(HOUR, o.created_at, NOW()) AS age_hours
FROM parcels pc
JOIN orders o                    ON o.id  = pc.order_id
LEFT JOIN parcel_categories cat  ON cat.id = pc.category_id
LEFT JOIN parcel_current_state cs ON cs.parcel_id = pc.id
LEFT JOIN hubs  ch ON ch.id = cs.current_hub_id
LEFT JOIN users ru ON ru.id = cs.responsible_user_id
LEFT JOIN hubs  rh ON rh.id = cs.responsible_hub_id;

-- The custody chain in reading order, with who handed over to whom.
CREATE OR REPLACE VIEW v_parcel_custody_chain AS
SELECT l.id,
       l.parcel_id,
       pc.parcel_code,
       l.created_at,
       l.action_type,
       l.from_responsibility_type,
       COALESCE(fu.full_name, fh.name) AS handed_over_by,
       l.to_responsibility_type,
       COALESCE(tu.full_name, th.name) AS received_by,
       cu.full_name AS recorded_by,
       l.note
FROM parcel_custody_logs l
JOIN parcels pc     ON pc.id = l.parcel_id
LEFT JOIN users fu  ON fu.id = l.from_user_id
LEFT JOIN hubs  fh  ON fh.id = l.from_hub_id
LEFT JOIN users tu  ON tu.id = l.to_user_id
LEFT JOIN hubs  th  ON th.id = l.to_hub_id
JOIN users cu       ON cu.id = l.created_by;

-- Route plan progress: how far along each plan is.
CREATE OR REPLACE VIEW v_route_plan_progress AS
SELECT rp.id AS plan_id,
       rp.status AS plan_status,
       pc.parcel_code,
       u.full_name AS planned_by,
       COUNT(st.id) AS total_steps,
       SUM(st.status = 'ARRIVED') AS completed_steps,
       ROUND(100.0 * SUM(st.status = 'ARRIVED') / NULLIF(COUNT(st.id), 0), 1)
           AS percent_complete,
       MIN(CASE WHEN st.status IN ('PENDING', 'READY', 'IN_TRANSIT')
                THEN st.sequence_no END) AS next_step_no,
       rp.created_at,
       rp.approved_at
FROM parcel_route_plans rp
JOIN parcels pc ON pc.id = rp.parcel_id
JOIN users   u  ON u.id  = rp.planned_by
LEFT JOIN parcel_route_steps st ON st.parcel_route_plan_id = rp.id
GROUP BY rp.id, rp.status, pc.parcel_code, u.full_name, rp.created_at, rp.approved_at;

-- The public tracking timeline: customer-visible rows only, oldest first.
CREATE OR REPLACE VIEW v_public_tracking_timeline AS
SELECT o.order_code,
       te.id AS event_id,
       te.status,
       te.title,
       te.message,
       h.name AS location,
       te.created_at
FROM tracking_events te
JOIN orders o     ON o.id = te.order_id
LEFT JOIN hubs h  ON h.id = te.hub_id
WHERE te.visible_to_customer = TRUE
ORDER BY te.created_at;

-- --------------------------------------------------------------
-- Reporting
-- --------------------------------------------------------------

-- Daily volume and revenue per creating hub.
CREATE OR REPLACE VIEW v_report_daily_volume AS
SELECT DATE(o.created_at) AS report_date,
       h.code AS hub_code,
       h.name AS hub_name,
       COUNT(DISTINCT o.id)  AS orders_created,
       COUNT(pc.id)          AS parcels_created,
       ROUND(SUM(pc.weight), 2) AS total_weight_kg,
       ROUND(SUM(o.total_fee), 2)   AS total_fee,
       ROUND(SUM(o.cod_amount), 2)  AS total_cod
FROM orders o
JOIN hubs h      ON h.id = o.created_hub_id
LEFT JOIN parcels pc ON pc.order_id = o.id
GROUP BY DATE(o.created_at), h.code, h.name;

-- Scan throughput per hub per day, split by scan type.
CREATE OR REPLACE VIEW v_report_hub_throughput AS
SELECT DATE(hs.scanned_at) AS report_date,
       h.code AS hub_code,
       h.name AS hub_name,
       COUNT(*)                             AS total_scans,
       SUM(hs.scan_type = 'INBOUND')        AS inbound_scans,
       SUM(hs.scan_type = 'OUTBOUND')       AS outbound_scans,
       SUM(hs.scan_type = 'SORTING')        AS sorting_scans,
       SUM(hs.scan_type = 'EXCEPTION')      AS exception_scans,
       COUNT(DISTINCT hs.parcel_id)         AS distinct_parcels,
       COUNT(DISTINCT hs.scanned_by)        AS staff_on_duty
FROM hub_scans hs
JOIN hubs h ON h.id = hs.hub_id
GROUP BY DATE(hs.scanned_at), h.code, h.name;

-- Live workload: what is sitting at each hub right now.
CREATE OR REPLACE VIEW v_report_hub_backlog AS
SELECT h.code AS hub_code,
       h.name AS hub_name,
       cs.current_status,
       COUNT(*) AS parcel_count,
       ROUND(AVG(TIMESTAMPDIFF(HOUR, cs.updated_at, NOW())), 1) AS avg_hours_in_status
FROM parcel_current_state cs
JOIN hubs h ON h.id = cs.current_hub_id
GROUP BY h.code, h.name, cs.current_status;

-- Shipper scoreboard.
CREATE OR REPLACE VIEW v_report_shipper_performance AS
SELECT u.id   AS shipper_id,
       u.full_name AS shipper_name,
       h.name AS hub_name,
       sp.vehicle_type,
       sp.max_orders_per_day,
       COUNT(da.id) AS total_assignments,
       SUM(da.status = 'DELIVERED')       AS delivered,
       SUM(da.status = 'FAILED')          AS failed,
       SUM(da.status = 'RETURNED_TO_HUB') AS returned,
       SUM(da.status IN ('ASSIGNED', 'ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY'))
           AS still_open,
       ROUND(100.0 * SUM(da.status = 'DELIVERED')
             / NULLIF(SUM(da.status IN ('DELIVERED', 'FAILED', 'RETURNED_TO_HUB')), 0), 1)
           AS success_rate_pct,
       ROUND(AVG(CASE WHEN da.status = 'DELIVERED'
                      THEN TIMESTAMPDIFF(MINUTE, da.assigned_at, da.completed_at) END), 0)
           AS avg_minutes_to_deliver
FROM shipper_profiles sp
JOIN users u ON u.id = sp.user_id
JOIN hubs  h ON h.id = sp.hub_id
LEFT JOIN delivery_assignments da ON da.shipper_id = sp.user_id
GROUP BY u.id, u.full_name, h.name, sp.vehicle_type, sp.max_orders_per_day;

-- Everything that went wrong, for the exceptions desk.
CREATE OR REPLACE VIEW v_report_parcel_exceptions AS
SELECT pc.parcel_code,
       o.order_code,
       pc.status AS parcel_status,
       h.name    AS last_known_hub,
       cs.updated_at AS since,
       TIMESTAMPDIFF(HOUR, cs.updated_at, NOW()) AS hours_stuck,
       (SELECT l.note FROM parcel_custody_logs l
         WHERE l.parcel_id = pc.id ORDER BY l.created_at DESC LIMIT 1) AS last_note
FROM parcels pc
JOIN orders o ON o.id = pc.order_id
LEFT JOIN parcel_current_state cs ON cs.parcel_id = pc.id
LEFT JOIN hubs h ON h.id = cs.current_hub_id
WHERE pc.status IN ('DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'LOST', 'DAMAGED');

-- Delivery lead time per service level, against the promised window.
CREATE OR REPLACE VIEW v_report_service_level AS
SELECT o.service_type,
       COUNT(*) AS delivered_orders,
       ROUND(AVG(TIMESTAMPDIFF(HOUR, o.created_at, te.created_at)), 1) AS avg_hours,
       MIN(TIMESTAMPDIFF(HOUR, o.created_at, te.created_at)) AS fastest_hours,
       MAX(TIMESTAMPDIFF(HOUR, o.created_at, te.created_at)) AS slowest_hours,
       SUM(TIMESTAMPDIFF(HOUR, o.created_at, te.created_at) >
           CASE o.service_type WHEN 'EXPRESS' THEN 24
                               WHEN 'STANDARD' THEN 72
                               ELSE 120 END) AS breached_sla
FROM orders o
JOIN tracking_events te
  ON te.order_id = o.id AND te.status = 'DELIVERED'
WHERE o.status = 'DELIVERED'
GROUP BY o.service_type;


-- =====================================================================
-- 11. FUNCTIONS AND STORED PROCEDURES
-- =====================================================================

DELIMITER $$

-- CustodyMapping.responsibilityFor() — the same switch, in SQL.
DROP FUNCTION IF EXISTS fn_responsibility_for $$
CREATE FUNCTION fn_responsibility_for(p_status VARCHAR(40))
RETURNS VARCHAR(20)
DETERMINISTIC NO SQL
BEGIN
    RETURN CASE p_status
        WHEN 'IN_TRANSIT'          THEN 'DRIVER'
        WHEN 'ASSIGNED_TO_SHIPPER' THEN 'SHIPPER'
        WHEN 'OUT_FOR_DELIVERY'    THEN 'SHIPPER'
        WHEN 'DELIVERED'           THEN 'CUSTOMER'
        WHEN 'LOST'                THEN 'SYSTEM'
        WHEN 'DAMAGED'             THEN 'SYSTEM'
        WHEN 'CANCELLED'           THEN 'SYSTEM'
        ELSE 'HUB'
    END;
END $$

-- CustodyMapping.actionFor().
DROP FUNCTION IF EXISTS fn_custody_action_for $$
CREATE FUNCTION fn_custody_action_for(p_status VARCHAR(40))
RETURNS VARCHAR(40)
DETERMINISTIC NO SQL
BEGIN
    RETURN CASE p_status
        WHEN 'RECEIVED_AT_ORIGIN_HUB' THEN 'RECEIVED_FROM_CUSTOMER'
        WHEN 'IN_TRANSIT'             THEN 'HANDOVER_TO_DRIVER'
        WHEN 'ARRIVED_AT_HUB'         THEN 'RECEIVED_AT_DESTINATION_HUB'
        WHEN 'OUT_FOR_DELIVERY'       THEN 'HANDOVER_TO_SHIPPER'
        WHEN 'DELIVERED'              THEN 'DELIVERED_TO_RECEIVER'
        WHEN 'DELIVERY_FAILED'        THEN 'DELIVERY_FAILED'
        WHEN 'RETURNED'               THEN 'RETURNED_TO_HUB'
        ELSE 'EXCEPTION_REPORTED'
    END;
END $$

-- PublicTrackingService.maskPhone(): reveal only the last three digits.
DROP FUNCTION IF EXISTS fn_mask_phone $$
CREATE FUNCTION fn_mask_phone(p_phone VARCHAR(30))
RETURNS VARCHAR(40)
DETERMINISTIC NO SQL
BEGIN
    DECLARE v_digits VARCHAR(30);
    SET v_digits = REGEXP_REPLACE(IFNULL(p_phone, ''), '[^0-9]', '');
    IF CHAR_LENGTH(v_digits) <= 3 THEN
        RETURN '***';
    END IF;
    RETURN CONCAT(REPEAT('*', CHAR_LENGTH(v_digits) - 3), RIGHT(v_digits, 3));
END $$


-- ---------------------------------------------------------------------
-- Parcel status change = four writes (parcel row, current state, custody log,
-- customer timeline) that must succeed or fail together.
--
-- TRANSACTION STRUCTURE — this matters and is easy to get wrong.
-- MySQL has no nested transactions: a START TRANSACTION inside an already-open
-- transaction *implicitly commits* the outer one. So a procedure that opens its
-- own transaction can never be called from another procedure that has one —
-- the caller's earlier work silently becomes permanent and its ROLLBACK does
-- nothing.
--
-- The split below is what keeps that from happening:
--   sp_apply_parcel_status   the worker. No transaction control, no error
--                            handler; errors propagate to whoever called it.
--                            This is what the other procedures call.
--   sp_update_parcel_status  the public entry point. Owns the transaction and
--                            the rollback handler. Never called internally.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_apply_parcel_status $$
CREATE PROCEDURE sp_apply_parcel_status(
    IN p_parcel_id BIGINT,
    IN p_status    VARCHAR(40),
    IN p_hub_id    BIGINT,
    IN p_user_id   BIGINT,
    IN p_note      VARCHAR(500))
MODIFIES SQL DATA
BEGIN
    DECLARE v_order_id    BIGINT;
    DECLARE v_code        VARCHAR(50);
    DECLARE v_prev_resp   VARCHAR(20);
    DECLARE v_prev_user   BIGINT;
    DECLARE v_prev_hub    BIGINT;
    DECLARE v_new_resp    VARCHAR(20);

    SELECT order_id, parcel_code INTO v_order_id, v_code
      FROM parcels WHERE id = p_parcel_id FOR UPDATE;

    IF v_order_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Parcel not found';
    END IF;

    SELECT responsibility_type, responsible_user_id, responsible_hub_id
      INTO v_prev_resp, v_prev_user, v_prev_hub
      FROM parcel_current_state WHERE parcel_id = p_parcel_id;

    SET v_new_resp = fn_responsibility_for(p_status);

    UPDATE parcels SET status = p_status WHERE id = p_parcel_id;

    INSERT INTO parcel_current_state (
        parcel_id, current_status, current_hub_id,
        responsibility_type, responsible_user_id, responsible_hub_id, last_scan_at)
    VALUES (p_parcel_id, p_status, p_hub_id, v_new_resp, p_user_id, p_hub_id, NOW())
    ON DUPLICATE KEY UPDATE
        current_status      = VALUES(current_status),
        current_hub_id      = COALESCE(VALUES(current_hub_id), current_hub_id),
        responsibility_type = VALUES(responsibility_type),
        responsible_user_id = VALUES(responsible_user_id),
        responsible_hub_id  = VALUES(responsible_hub_id),
        last_scan_at        = VALUES(last_scan_at);

    INSERT INTO parcel_custody_logs (
        parcel_id, from_responsibility_type, from_user_id, from_hub_id,
        to_responsibility_type, to_user_id, to_hub_id,
        action_type, note, created_by)
    VALUES (p_parcel_id, v_prev_resp, v_prev_user, v_prev_hub,
            v_new_resp, p_user_id, p_hub_id,
            fn_custody_action_for(p_status), p_note, p_user_id);

    INSERT INTO tracking_events (
        order_id, parcel_id, status, title, message, hub_id, visible_to_customer)
    VALUES (v_order_id, p_parcel_id, p_status,
            CONCAT('Parcel ', p_status),
            CONCAT('Parcel ', v_code, ' status changed to ', p_status),
            p_hub_id, TRUE);
END $$

-- Public entry point: wraps the worker in its own transaction.
DROP PROCEDURE IF EXISTS sp_update_parcel_status $$
CREATE PROCEDURE sp_update_parcel_status(
    IN p_parcel_id BIGINT,
    IN p_status    VARCHAR(40),
    IN p_hub_id    BIGINT,
    IN p_user_id   BIGINT,
    IN p_note      VARCHAR(500))
MODIFIES SQL DATA
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    CALL sp_apply_parcel_status(p_parcel_id, p_status, p_hub_id, p_user_id, p_note);
    COMMIT;
END $$


-- ---------------------------------------------------------------------
-- sp_scan_parcel — record a barcode scan and let it drive the status.
-- INBOUND at a hub means the parcel arrived; OUTBOUND means it left.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_scan_parcel $$
CREATE PROCEDURE sp_scan_parcel(
    IN p_parcel_id BIGINT,
    IN p_hub_id    BIGINT,
    IN p_scan_type VARCHAR(20),
    IN p_user_id   BIGINT,
    IN p_note      VARCHAR(500))
MODIFIES SQL DATA
BEGIN
    DECLARE v_is_origin  BOOLEAN DEFAULT FALSE;
    DECLARE v_new_status VARCHAR(40) DEFAULT NULL;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    INSERT INTO hub_scans (parcel_id, hub_id, scan_type, scanned_by, note)
    VALUES (p_parcel_id, p_hub_id, p_scan_type, p_user_id, p_note);

    IF p_scan_type = 'INBOUND' THEN
        SELECT o.created_hub_id = p_hub_id INTO v_is_origin
          FROM parcels pc JOIN orders o ON o.id = pc.order_id
         WHERE pc.id = p_parcel_id;
        SET v_new_status = IF(v_is_origin, 'RECEIVED_AT_ORIGIN_HUB', 'ARRIVED_AT_HUB');
    ELSEIF p_scan_type = 'OUTBOUND' THEN
        SET v_new_status = 'IN_TRANSIT';
    ELSEIF p_scan_type = 'SORTING' THEN
        SET v_new_status = 'WAITING_FOR_OUTBOUND';
    END IF;

    IF v_new_status IS NOT NULL THEN
        CALL sp_apply_parcel_status(p_parcel_id, v_new_status, p_hub_id, p_user_id, p_note);
    END IF;

    COMMIT;
END $$


-- ---------------------------------------------------------------------
-- sp_assign_shipper — hand a parcel to a shipper and move it to
-- ASSIGNED_TO_SHIPPER. The unique index on open_parcel_id already prevents a
-- second open assignment; this reports it as a readable error first.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_assign_shipper $$
CREATE PROCEDURE sp_assign_shipper(
    IN p_parcel_id  BIGINT,
    IN p_shipper_id BIGINT,
    IN p_assigned_by BIGINT,
    IN p_type       VARCHAR(20),
    IN p_reason     VARCHAR(500))
MODIFIES SQL DATA
BEGIN
    DECLARE v_open INT DEFAULT 0;
    DECLARE v_hub  BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_open
      FROM delivery_assignments
     WHERE parcel_id = p_parcel_id
       AND status IN ('ASSIGNED', 'ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY');

    IF v_open > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Parcel already has an open delivery assignment';
    END IF;

    SELECT hub_id INTO v_hub FROM shipper_profiles WHERE user_id = p_shipper_id;
    IF v_hub IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No shipper profile for that user';
    END IF;

    INSERT INTO delivery_assignments (
        parcel_id, shipper_id, assigned_by, assignment_type, assignment_reason, status)
    VALUES (p_parcel_id, p_shipper_id, p_assigned_by,
            IFNULL(p_type, 'MANUAL'), p_reason, 'ASSIGNED');

    CALL sp_apply_parcel_status(
        p_parcel_id, 'ASSIGNED_TO_SHIPPER', v_hub, p_shipper_id, p_reason);

    COMMIT;
END $$


-- ---------------------------------------------------------------------
-- sp_advance_assignment — the shipper-side transition, stamping the right
-- timestamp for the target status and moving the parcel with it. Mirrors
-- DeliveryAssignmentService.updateStatusForShipper(), including the ownership
-- check that a shipper may only touch their own work.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_advance_assignment $$
CREATE PROCEDURE sp_advance_assignment(
    IN p_assignment_id BIGINT,
    IN p_shipper_id    BIGINT,
    IN p_status        VARCHAR(20),
    IN p_note          VARCHAR(500))
MODIFIES SQL DATA
BEGIN
    DECLARE v_owner     BIGINT;
    DECLARE v_parcel_id BIGINT;
    DECLARE v_hub       BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT shipper_id, parcel_id INTO v_owner, v_parcel_id
      FROM delivery_assignments WHERE id = p_assignment_id FOR UPDATE;

    IF v_owner IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Assignment not found';
    END IF;
    IF v_owner <> p_shipper_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'This delivery assignment does not belong to you';
    END IF;

    SELECT hub_id INTO v_hub FROM shipper_profiles WHERE user_id = p_shipper_id;

    UPDATE delivery_assignments
       SET status       = p_status,
           accepted_at  = IF(p_status = 'ACCEPTED',  NOW(), accepted_at),
           picked_up_at = IF(p_status = 'PICKED_UP', NOW(), picked_up_at),
           completed_at = IF(p_status IN ('DELIVERED', 'FAILED', 'RETURNED_TO_HUB', 'CANCELLED'),
                             NOW(), completed_at)
     WHERE id = p_assignment_id;

    -- Carry the parcel along with the assignment.
    IF p_status = 'OUT_FOR_DELIVERY' THEN
        CALL sp_apply_parcel_status(v_parcel_id, 'OUT_FOR_DELIVERY', v_hub, p_shipper_id, p_note);
    ELSEIF p_status = 'DELIVERED' THEN
        CALL sp_apply_parcel_status(v_parcel_id, 'DELIVERED', v_hub, p_shipper_id, p_note);
    ELSEIF p_status = 'FAILED' THEN
        CALL sp_apply_parcel_status(v_parcel_id, 'DELIVERY_FAILED', v_hub, p_shipper_id, p_note);
    ELSEIF p_status = 'RETURNED_TO_HUB' THEN
        CALL sp_apply_parcel_status(v_parcel_id, 'RETURNING', v_hub, p_shipper_id, p_note);
    END IF;

    COMMIT;
END $$


-- ---------------------------------------------------------------------
-- sp_public_tracking — PublicTrackingService.track() as one call.
-- PII is revealed only when the caller supplies the receiver's phone number
-- and it matches; that is what stops someone harvesting names and addresses by
-- guessing order codes. The phone is masked even for a verified caller.
-- Returns two result sets: the header, then the timeline.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_public_tracking $$
CREATE PROCEDURE sp_public_tracking(
    IN p_order_code VARCHAR(50),
    IN p_phone      VARCHAR(30))
READS SQL DATA
BEGIN
    DECLARE v_order_id BIGINT;
    DECLARE v_verified BOOLEAN DEFAULT FALSE;

    SELECT id INTO v_order_id FROM orders
     WHERE order_code = UPPER(TRIM(p_order_code));

    IF v_order_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No shipment found for that code.';
    END IF;

    SELECT COUNT(*) > 0 INTO v_verified
      FROM order_parties
     WHERE order_id = v_order_id
       AND party_type = 'RECEIVER'
       AND phone_digits = REGEXP_REPLACE(IFNULL(p_phone, ''), '[^0-9]', '')
       AND REGEXP_REPLACE(IFNULL(p_phone, ''), '[^0-9]', '') <> '';

    SELECT o.order_code,
           o.status,
           IF(v_verified, s.full_name,    NULL) AS sender_name,
           IF(v_verified, s.address_line, NULL) AS sender_address,
           IF(v_verified, r.full_name,    NULL) AS receiver_name,
           IF(v_verified, r.address_line, NULL) AS receiver_address,
           IF(v_verified, fn_mask_phone(r.phone), NULL) AS receiver_phone,
           (SELECT COUNT(*) FROM parcels WHERE order_id = o.id) AS parcel_count
    FROM orders o
    LEFT JOIN order_parties s ON s.order_id = o.id AND s.party_type = 'SENDER'
    LEFT JOIN order_parties r ON r.order_id = o.id AND r.party_type = 'RECEIVER'
    WHERE o.id = v_order_id;

    SELECT te.status, te.title, te.message, h.name AS location, te.created_at
      FROM tracking_events te
      LEFT JOIN hubs h ON h.id = te.hub_id
     WHERE te.order_id = v_order_id
       AND te.visible_to_customer = TRUE
     ORDER BY te.created_at;
END $$

DELIMITER ;


-- =====================================================================
-- 12. TRIGGERS
--     These keep derived data correct and enforce the rules that a CHECK
--     constraint cannot see (another table, or an AUTO_INCREMENT id).
--     They do NOT write tracking_events: TrackingService owns the customer
--     timeline and a trigger doing the same job would duplicate every entry.
-- =====================================================================

DELIMITER $$

-- parcel_categories: requires_special_handling always summarises the flags.
DROP TRIGGER IF EXISTS trg_parcel_categories_bi $$
CREATE TRIGGER trg_parcel_categories_bi BEFORE INSERT ON parcel_categories
FOR EACH ROW
BEGIN
    SET NEW.requires_special_handling =
        (NEW.is_fragile OR NEW.is_liquid OR NEW.is_high_value);
END $$

DROP TRIGGER IF EXISTS trg_parcel_categories_bu $$
CREATE TRIGGER trg_parcel_categories_bu BEFORE UPDATE ON parcel_categories
FOR EACH ROW
BEGIN
    SET NEW.requires_special_handling =
        (NEW.is_fragile OR NEW.is_liquid OR NEW.is_high_value);
END $$


-- users: AuthService requires a hub for SHIPPER accounts.
DROP TRIGGER IF EXISTS trg_users_bi $$
CREATE TRIGGER trg_users_bi BEFORE INSERT ON users
FOR EACH ROW
BEGIN
    DECLARE v_role VARCHAR(50);
    SELECT code INTO v_role FROM roles WHERE id = NEW.role_id;
    IF v_role = 'SHIPPER' AND NEW.hub_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'hub_id is required when role is SHIPPER';
    END IF;
    SET NEW.email = LOWER(TRIM(NEW.email));
END $$

DROP TRIGGER IF EXISTS trg_users_bu $$
CREATE TRIGGER trg_users_bu BEFORE UPDATE ON users
FOR EACH ROW
BEGIN
    DECLARE v_role VARCHAR(50);
    SELECT code INTO v_role FROM roles WHERE id = NEW.role_id;
    IF v_role = 'SHIPPER' AND NEW.hub_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'hub_id is required when role is SHIPPER';
    END IF;
    SET NEW.email = LOWER(TRIM(NEW.email));
END $$


-- hubs: a hub cannot be its own parent (the CHECK cannot see AUTO_INCREMENT).
DROP TRIGGER IF EXISTS trg_hubs_bu $$
CREATE TRIGGER trg_hubs_bu BEFORE UPDATE ON hubs
FOR EACH ROW
BEGIN
    IF NEW.parent_hub_id IS NOT NULL AND NEW.parent_hub_id = NEW.id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A hub cannot be its own parent';
    END IF;
END $$


-- shipper_profiles: the profile must belong to a user holding SHIPPER, and the
-- hub on the profile must be the user's own hub (the two were free to disagree).
DROP TRIGGER IF EXISTS trg_shipper_profiles_bi $$
CREATE TRIGGER trg_shipper_profiles_bi BEFORE INSERT ON shipper_profiles
FOR EACH ROW
BEGIN
    DECLARE v_role VARCHAR(50);
    DECLARE v_hub  BIGINT;
    SELECT r.code, u.hub_id INTO v_role, v_hub
      FROM users u JOIN roles r ON r.id = u.role_id
     WHERE u.id = NEW.user_id;
    IF v_role IS NULL OR v_role <> 'SHIPPER' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Shipper profile requires a user with the SHIPPER role';
    END IF;
    IF v_hub IS NOT NULL AND NEW.hub_id <> v_hub THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Shipper profile hub must match the user hub';
    END IF;
END $$


-- shipper_zones: a shipper only works zones belonging to their own hub.
DROP TRIGGER IF EXISTS trg_shipper_zones_bi $$
CREATE TRIGGER trg_shipper_zones_bi BEFORE INSERT ON shipper_zones
FOR EACH ROW
BEGIN
    DECLARE v_shipper_hub BIGINT;
    DECLARE v_zone_hub    BIGINT;
    SELECT hub_id INTO v_shipper_hub FROM shipper_profiles WHERE user_id = NEW.shipper_id;
    SELECT hub_id INTO v_zone_hub    FROM delivery_zones   WHERE id = NEW.zone_id;
    IF v_shipper_hub <> v_zone_hub THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Delivery zone belongs to a different hub than the shipper';
    END IF;
END $$


-- parcels: open the current-state row and keep orders.total_weight honest.
-- INSERT IGNORE, because OrderService.createParcel() inserts the same row a
-- moment later; whichever runs first wins and the other is a no-op.
DROP TRIGGER IF EXISTS trg_parcels_ai $$
CREATE TRIGGER trg_parcels_ai AFTER INSERT ON parcels
FOR EACH ROW
BEGIN
    INSERT IGNORE INTO parcel_current_state
        (parcel_id, current_status, responsibility_type)
    VALUES (NEW.id, NEW.status, 'SYSTEM');

    UPDATE orders o
       SET o.total_weight = (SELECT IFNULL(SUM(p.weight), 0)
                               FROM parcels p WHERE p.order_id = NEW.order_id)
     WHERE o.id = NEW.order_id;
END $$

DROP TRIGGER IF EXISTS trg_parcels_au $$
CREATE TRIGGER trg_parcels_au AFTER UPDATE ON parcels
FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status THEN
        UPDATE parcel_current_state
           SET current_status = NEW.status
         WHERE parcel_id = NEW.id AND current_status <> NEW.status;
    END IF;

    IF NEW.weight <> OLD.weight OR NEW.order_id <> OLD.order_id THEN
        UPDATE orders o
           SET o.total_weight = (SELECT IFNULL(SUM(p.weight), 0)
                                   FROM parcels p WHERE p.order_id = NEW.order_id)
         WHERE o.id = NEW.order_id;
    END IF;
END $$

DROP TRIGGER IF EXISTS trg_parcels_ad $$
CREATE TRIGGER trg_parcels_ad AFTER DELETE ON parcels
FOR EACH ROW
BEGIN
    UPDATE orders o
       SET o.total_weight = (SELECT IFNULL(SUM(p.weight), 0)
                               FROM parcels p WHERE p.order_id = OLD.order_id)
     WHERE o.id = OLD.order_id;
END $$


-- delivery_assignments: stamp the lifecycle timestamp for the target status so
-- ck_da_status_timestamps is satisfied no matter which client does the update.
DROP TRIGGER IF EXISTS trg_delivery_assignments_bu $$
CREATE TRIGGER trg_delivery_assignments_bu BEFORE UPDATE ON delivery_assignments
FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status THEN
        IF NEW.status = 'ACCEPTED' AND NEW.accepted_at IS NULL THEN
            SET NEW.accepted_at = NOW();
        END IF;
        IF NEW.status = 'PICKED_UP' AND NEW.picked_up_at IS NULL THEN
            SET NEW.picked_up_at = NOW();
        END IF;
        IF NEW.status IN ('DELIVERED', 'FAILED', 'RETURNED_TO_HUB', 'CANCELLED')
           AND NEW.completed_at IS NULL THEN
            SET NEW.completed_at = NOW();
        END IF;
    END IF;
END $$

DELIMITER ;


-- =====================================================================
-- 13. SEED: REFERENCE DATA
-- =====================================================================

INSERT INTO roles (code, name, description) VALUES
('ADMIN',       'Admin',       'Full system access'),
('HUB_MANAGER', 'Hub Manager', 'Manage hub operations'),
('HUB_STAFF',   'Hub Staff',   'Scan and process parcels at hub'),
('DISPATCHER',  'Dispatcher',  'Plan parcel routes and assign delivery'),
('SHIPPER',     'Shipper',     'Last-mile delivery staff');

INSERT INTO parcel_categories
    (code, name, description, is_fragile, is_liquid, is_high_value) VALUES
('GENERAL',     'Hàng thường',      'Hàng hoá thông thường',            FALSE, FALSE, FALSE),
('DOCUMENT',    'Tài liệu',         'Thư từ, hồ sơ, giấy tờ',           FALSE, FALSE, FALSE),
('FRAGILE',     'Hàng dễ vỡ',       'Thuỷ tinh, gốm sứ, màn hình',      TRUE,  FALSE, FALSE),
('ELECTRONICS', 'Hàng điện tử',     'Điện thoại, laptop, phụ kiện',     TRUE,  FALSE, TRUE),
('LIQUID',      'Chất lỏng',        'Mỹ phẩm, đồ uống đóng chai',       FALSE, TRUE,  FALSE),
('VALUABLE',    'Hàng giá trị cao', 'Trang sức, đồng hồ',               FALSE, FALSE, TRUE),
('FOOD',        'Thực phẩm khô',    'Bánh kẹo, đồ khô đóng gói',        FALSE, FALSE, FALSE);


-- =====================================================================
-- 14. SEED: DEMO DATA
--     Enough to log in, browse hubs, open an order and follow it through the
--     custody chain. Drop this whole section for a production install.
--
--     Passwords are BCrypt hashes generated with the same Spring Security
--     encoder the application uses:
--         admin@parcelflow.local      Admin@12345
--         manager.hcm@parcelflow.local  Manager@12345
--         staff.hcm@parcelflow.local    Staff@12345
--         dispatcher@parcelflow.local   Dispatch@12345
--         shipper1@parcelflow.local     Shipper@12345
--         shipper2@parcelflow.local     Shipper@12345
--     The admin account matches app.bootstrap-admin in application.yml, so
--     AdminBootstrap sees it already exists and does not create a duplicate.
-- =====================================================================

-- Geography ---------------------------------------------------------
INSERT INTO provinces (id, code, name) VALUES
(1, 'VN-SG', 'TP. Hồ Chí Minh'),
(2, 'VN-HN', 'Hà Nội'),
(3, 'VN-DN', 'Đà Nẵng');

INSERT INTO districts (id, province_id, code, name) VALUES
(1, 1, 'SG-Q1',  'Quận 1'),
(2, 1, 'SG-Q3',  'Quận 3'),
(3, 1, 'SG-Q7',  'Quận 7'),
(4, 1, 'SG-TD',  'TP. Thủ Đức'),
(5, 2, 'HN-BD',  'Ba Đình'),
(6, 2, 'HN-CG',  'Cầu Giấy'),
(7, 3, 'DN-HC',  'Hải Châu');

INSERT INTO wards (id, district_id, code, name) VALUES
(1,  1, 'SG-Q1-BN',  'Phường Bến Nghé'),
(2,  1, 'SG-Q1-BT',  'Phường Bến Thành'),
(3,  2, 'SG-Q3-VTS', 'Phường Võ Thị Sáu'),
(4,  3, 'SG-Q7-TP',  'Phường Tân Phú'),
(5,  4, 'SG-TD-LT',  'Phường Linh Trung'),
(6,  5, 'HN-BD-NTS', 'Phường Ngọc Hà'),
(7,  6, 'HN-CG-DN',  'Phường Dịch Vọng'),
(8,  7, 'DN-HC-TB',  'Phường Thanh Bình');

-- Hubs --------------------------------------------------------------
INSERT INTO hubs (id, code, name, type, phone, address_line,
                  ward_id, district_id, province_id, parent_hub_id) VALUES
(1, 'HUB-SG-MAIN', 'Kho tổng TP.HCM',      'MAIN_HUB',     '02838001100',
    '123 Nguyễn Huệ',            1, 1, 1, NULL),
(2, 'HUB-SG-Q7',   'Bưu cục Quận 7',       'DELIVERY_HUB', '02838001101',
    '45 Nguyễn Thị Thập',        4, 3, 1, 1),
(3, 'HUB-SG-TD',   'Bưu cục Thủ Đức',      'DELIVERY_HUB', '02838001102',
    '12 Võ Văn Ngân',            5, 4, 1, 1),
(4, 'HUB-HN-MAIN', 'Kho tổng Hà Nội',      'MAIN_HUB',     '02438001100',
    '88 Kim Mã',                 6, 5, 2, NULL),
(5, 'HUB-HN-CG',   'Bưu cục Cầu Giấy',     'DELIVERY_HUB', '02438001101',
    '210 Xuân Thủy',             7, 6, 2, 4),
(6, 'HUB-DN-TRAN', 'Trung chuyển Đà Nẵng', 'TRANSIT_HUB',  '02368001100',
    '5 Nguyễn Văn Linh',         8, 7, 3, NULL);

INSERT INTO hub_service_areas (hub_id, province_id, district_id, ward_id) VALUES
(1, 1, 1,    NULL),
(1, 1, 2,    NULL),
(2, 1, 3,    NULL),
(3, 1, 4,    NULL),
(4, 2, 5,    NULL),
(5, 2, 6,    NULL),
(6, 3, NULL, NULL);

-- Users -------------------------------------------------------------
INSERT INTO users (id, full_name, email, phone, password_hash, role_id, hub_id,
                   is_active, must_change_password) VALUES
(1, 'System Administrator', 'admin@parcelflow.local',        '0900000001',
    '$2a$10$n/8qmpZ9RrInJxVFoV6C/OBM19mf7uqk3ogw4OvQuaPaf8CbljRQ.',
    (SELECT id FROM roles WHERE code = 'ADMIN'),       NULL, TRUE, FALSE),
(2, 'Trần Hữu Quản',        'manager.hcm@parcelflow.local',  '0900000002',
    '$2a$10$cmhpZ3YX3/VEWwxqbFPpme4op1EwcBfKYs6uIvZnrSMTY/VGWt0FO',
    (SELECT id FROM roles WHERE code = 'HUB_MANAGER'), 1,    TRUE, FALSE),
(3, 'Nguyễn Văn Kho',       'staff.hcm@parcelflow.local',    '0900000003',
    '$2a$10$ug/l5UYjgJuIFIp2lrgZO.MqzLNgAYXJ84UdMu4q86A6UrmHriAFG',
    (SELECT id FROM roles WHERE code = 'HUB_STAFF'),   1,    TRUE, FALSE),
(4, 'Lê Thị Điều Phối',     'dispatcher@parcelflow.local',   '0900000004',
    '$2a$10$miWAVauW0.IKBc0zz8/TmuwFFHgk5pbLjr5kkEPNJUB7/WRGKx0gu',
    (SELECT id FROM roles WHERE code = 'DISPATCHER'),  1,    TRUE, FALSE),
(5, 'Phạm Minh Giao',       'shipper1@parcelflow.local',     '0900000005',
    '$2a$10$Uib.7J4x6N/XIil3.9mFxuRYoidoFr8cL/FMtSZa6ecIF7XEkTTCy',
    (SELECT id FROM roles WHERE code = 'SHIPPER'),     2,    TRUE, FALSE),
(6, 'Võ Thanh Vận',         'shipper2@parcelflow.local',     '0900000006',
    '$2a$10$Uib.7J4x6N/XIil3.9mFxuRYoidoFr8cL/FMtSZa6ecIF7XEkTTCy',
    (SELECT id FROM roles WHERE code = 'SHIPPER'),     3,    TRUE, FALSE);

INSERT INTO shipper_profiles (user_id, hub_id, vehicle_type, max_orders_per_day) VALUES
(5, 2, 'MOTORBIKE', 30),
(6, 3, 'VAN',       50);

INSERT INTO delivery_zones (id, hub_id, name, province_id, district_id, ward_id, priority) VALUES
(1, 2, 'Quận 7 - Tân Phú',    1, 3, 4,    10),
(2, 2, 'Quận 7 - toàn quận',  1, 3, NULL,  5),
(3, 3, 'Thủ Đức - Linh Trung', 1, 4, 5,   10);

INSERT INTO shipper_zones (shipper_id, zone_id, priority) VALUES
(5, 1, 10),
(5, 2,  5),
(6, 3, 10);

-- One order end to end ----------------------------------------------
INSERT INTO orders (id, order_code, status, created_hub_id, current_hub_id, final_hub_id,
                    service_type, payment_type, total_fee, cod_amount, note, created_by)
VALUES (1, 'OD20260722ABC234', 'CREATED', 1, 1, 2,
        'STANDARD', 'COD', 35000.00, 450000.00, 'Giao giờ hành chính', 3);

INSERT INTO order_parties (order_id, party_type, full_name, phone, email,
                           address_line, ward_id, district_id, province_id) VALUES
(1, 'SENDER',   'Cửa hàng ABC',   '0912345678', 'shop.abc@example.com',
    '120 Lê Lợi',           2, 1, 1),
(1, 'RECEIVER', 'Nguyễn Thị Hoa', '0987654321', NULL,
    '55 Nguyễn Thị Thập',   4, 3, 1);

INSERT INTO parcels (id, order_id, parcel_code, category_id, weight,
                     length, width, height, declared_value, note, status)
VALUES (1, 1, 'PC20260722XY345678',
        (SELECT id FROM parcel_categories WHERE code = 'ELECTRONICS'),
        1.50, 30.00, 20.00, 10.00, 450000.00, 'Tai nghe không dây', 'CREATED');

-- The first timeline entry, exactly as OrderService.createOrder() writes it.
INSERT INTO tracking_events (order_id, parcel_id, status, title, message, hub_id)
VALUES (1, NULL, 'CREATED', 'Order created',
        'Order OD20260722ABC234 created', 1);

-- Walk the parcel through the flow using the procedures above, so the custody
-- log, current state and timeline are all populated consistently.
CALL sp_scan_parcel(1, 1, 'INBOUND',  3, 'Nhận hàng tại kho tổng');
CALL sp_scan_parcel(1, 1, 'SORTING',  3, 'Phân loại tuyến Quận 7');
CALL sp_scan_parcel(1, 1, 'OUTBOUND', 3, 'Xuất kho đi bưu cục Quận 7');
CALL sp_scan_parcel(1, 2, 'INBOUND',  3, 'Đến bưu cục Quận 7');
CALL sp_update_parcel_status(1, 'READY_FOR_DELIVERY', 2, 3, 'Sẵn sàng giao');
CALL sp_assign_shipper(1, 5, 4, 'AUTO_ZONE', 'Trùng zone Quận 7 - Tân Phú');


-- =====================================================================
-- SMOKE TEST — uncomment to verify the install.
-- =====================================================================
-- SELECT * FROM v_order_detail;
-- SELECT * FROM v_parcel_tracking;
-- SELECT * FROM v_parcel_custody_chain ORDER BY created_at;
-- CALL sp_public_tracking('OD20260722ABC234', '0987654321');
-- CALL sp_public_tracking('OD20260722ABC234', NULL);   -- PII hidden
