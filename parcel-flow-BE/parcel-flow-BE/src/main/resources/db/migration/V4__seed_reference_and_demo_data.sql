-- V4: reference geography + demo network, hubs, staff and delivery zones.
--
-- Why this exists: V1 seeds only `roles`. On a fresh `docker compose up` the
-- system starts, an admin is bootstrapped, and then nothing can be done —
-- creating an order needs a hub id plus province/district ids, and the only
-- hub endpoint is a read (HubController exposes GET only). There is no API
-- path that can bootstrap this data, so it belongs in a migration.
--
-- Passwords below are BCrypt hashes produced by the same Spring Security
-- encoder the application uses:
--     manager.hcm@parcelflow.local   Manager@12345
--     staff.hcm@parcelflow.local     Staff@12345
--     dispatcher@parcelflow.local    Dispatch@12345
--     shipper1@parcelflow.local      Shipper@12345
--     shipper2@parcelflow.local      Shipper@12345
-- The ADMIN account is deliberately not seeded here: AdminBootstrap creates it
-- from app.bootstrap-admin.* after migrations run.

-- =========================================================
-- Geography
-- =========================================================

INSERT INTO provinces (id, code, name) VALUES
(1, 'VN-SG', 'TP. Ho Chi Minh'),
(2, 'VN-HN', 'Ha Noi'),
(3, 'VN-DN', 'Da Nang');

INSERT INTO districts (id, province_id, code, name) VALUES
(1, 1, 'SG-Q1', 'Quan 1'),
(2, 1, 'SG-Q3', 'Quan 3'),
(3, 1, 'SG-Q7', 'Quan 7'),
(4, 1, 'SG-TD', 'TP. Thu Duc'),
(5, 2, 'HN-BD', 'Ba Dinh'),
(6, 2, 'HN-CG', 'Cau Giay'),
(7, 3, 'DN-HC', 'Hai Chau');

INSERT INTO wards (id, district_id, code, name) VALUES
(1, 1, 'SG-Q1-BN',  'Phuong Ben Nghe'),
(2, 1, 'SG-Q1-BT',  'Phuong Ben Thanh'),
(3, 2, 'SG-Q3-VTS', 'Phuong Vo Thi Sau'),
(4, 3, 'SG-Q7-TP',  'Phuong Tan Phu'),
(5, 4, 'SG-TD-LT',  'Phuong Linh Trung'),
(6, 5, 'HN-BD-NH',  'Phuong Ngoc Ha'),
(7, 6, 'HN-CG-DV',  'Phuong Dich Vong'),
(8, 7, 'DN-HC-TB',  'Phuong Thanh Binh');

-- =========================================================
-- Parcel categories
-- =========================================================

INSERT INTO parcel_categories
    (id, code, name, description, is_fragile, is_liquid, is_high_value, requires_special_handling) VALUES
(1, 'GENERAL',     'General goods',    'Ordinary parcels',              FALSE, FALSE, FALSE, FALSE),
(2, 'DOCUMENT',    'Documents',        'Letters, files, paperwork',     FALSE, FALSE, FALSE, FALSE),
(3, 'FRAGILE',     'Fragile',          'Glass, ceramics, displays',     TRUE,  FALSE, FALSE, TRUE),
(4, 'ELECTRONICS', 'Electronics',      'Phones, laptops, accessories',  TRUE,  FALSE, TRUE,  TRUE),
(5, 'LIQUID',      'Liquids',          'Cosmetics, bottled drinks',     FALSE, TRUE,  FALSE, TRUE),
(6, 'VALUABLE',    'High value',       'Jewellery, watches',            FALSE, FALSE, TRUE,  TRUE),
(7, 'FOOD',        'Dry food',         'Packaged snacks and dry goods', FALSE, FALSE, FALSE, FALSE);

-- =========================================================
-- Hub network
-- =========================================================

INSERT INTO hubs (id, code, name, type, phone, address_line,
                  ward_id, district_id, province_id, parent_hub_id) VALUES
(1, 'HUB-SG-MAIN', 'HCMC Main Hub',        'MAIN_HUB',     '02838001100',
    '123 Nguyen Hue',        1, 1, 1, NULL),
(2, 'HUB-SG-Q7',   'District 7 Branch',    'DELIVERY_HUB', '02838001101',
    '45 Nguyen Thi Thap',    4, 3, 1, 1),
(3, 'HUB-SG-TD',   'Thu Duc Branch',       'DELIVERY_HUB', '02838001102',
    '12 Vo Van Ngan',        5, 4, 1, 1),
(4, 'HUB-HN-MAIN', 'Ha Noi Main Hub',      'MAIN_HUB',     '02438001100',
    '88 Kim Ma',             6, 5, 2, NULL),
(5, 'HUB-HN-CG',   'Cau Giay Branch',      'DELIVERY_HUB', '02438001101',
    '210 Xuan Thuy',         7, 6, 2, 4),
(6, 'HUB-DN-TRAN', 'Da Nang Transit Hub',  'TRANSIT_HUB',  '02368001100',
    '5 Nguyen Van Linh',     8, 7, 3, NULL);

INSERT INTO hub_service_areas (hub_id, province_id, district_id, ward_id) VALUES
(1, 1, 1,    NULL),
(1, 1, 2,    NULL),
(2, 1, 3,    NULL),
(3, 1, 4,    NULL),
(4, 2, 5,    NULL),
(5, 2, 6,    NULL),
(6, 3, NULL, NULL);

-- =========================================================
-- Demo staff
-- =========================================================

INSERT INTO users (id, full_name, email, phone, password_hash, role_id, hub_id,
                   is_active, must_change_password) VALUES
(101, 'Hub Manager HCM', 'manager.hcm@parcelflow.local', '0900000002',
    '$2a$10$cmhpZ3YX3/VEWwxqbFPpme4op1EwcBfKYs6uIvZnrSMTY/VGWt0FO',
    (SELECT id FROM roles WHERE code = 'HUB_MANAGER'), 1, TRUE, FALSE),
(102, 'Hub Staff HCM', 'staff.hcm@parcelflow.local', '0900000003',
    '$2a$10$ug/l5UYjgJuIFIp2lrgZO.MqzLNgAYXJ84UdMu4q86A6UrmHriAFG',
    (SELECT id FROM roles WHERE code = 'HUB_STAFF'), 1, TRUE, FALSE),
(103, 'Dispatcher', 'dispatcher@parcelflow.local', '0900000004',
    '$2a$10$miWAVauW0.IKBc0zz8/TmuwFFHgk5pbLjr5kkEPNJUB7/WRGKx0gu',
    (SELECT id FROM roles WHERE code = 'DISPATCHER'), 1, TRUE, FALSE),
(104, 'Shipper One', 'shipper1@parcelflow.local', '0900000005',
    '$2a$10$Uib.7J4x6N/XIil3.9mFxuRYoidoFr8cL/FMtSZa6ecIF7XEkTTCy',
    (SELECT id FROM roles WHERE code = 'SHIPPER'), 2, TRUE, FALSE),
(105, 'Shipper Two', 'shipper2@parcelflow.local', '0900000006',
    '$2a$10$Uib.7J4x6N/XIil3.9mFxuRYoidoFr8cL/FMtSZa6ecIF7XEkTTCy',
    (SELECT id FROM roles WHERE code = 'SHIPPER'), 3, TRUE, FALSE);

INSERT INTO shipper_profiles (user_id, hub_id, vehicle_type, max_orders_per_day, is_available) VALUES
(104, 2, 'MOTORBIKE', 30, TRUE),
(105, 3, 'VAN',       50, TRUE);

-- =========================================================
-- Delivery zones
-- =========================================================

INSERT INTO delivery_zones (id, hub_id, name, province_id, district_id, ward_id, priority) VALUES
(1, 2, 'D7 - Tan Phu ward', 1, 3, 4,    10),
(2, 2, 'D7 - whole district', 1, 3, NULL, 5),
(3, 3, 'Thu Duc - Linh Trung', 1, 4, 5,  10);

INSERT INTO shipper_zones (shipper_id, zone_id, priority) VALUES
(104, 1, 10),
(104, 2,  5),
(105, 3, 10);
