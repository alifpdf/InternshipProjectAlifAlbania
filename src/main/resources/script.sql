-- =======================
-- Full Cleanup
-- =======================

DROP TABLE IF EXISTS Measurement CASCADE;
DROP TABLE IF EXISTS Device CASCADE;
DROP TABLE IF EXISTS Kit CASCADE;
DROP TABLE IF EXISTS Parameter CASCADE;
DROP TABLE IF EXISTS DeviceCategory CASCADE;

-- =======================
-- Table Creation
-- =======================

-- 1. Device Categories
CREATE TABLE DeviceCategory (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

-- 2. Parameters
CREATE TABLE Parameter (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    unit TEXT
);

-- 3. Kits
CREATE TABLE Kit (
    id SERIAL PRIMARY KEY,
    x DOUBLE PRECISION,
    y DOUBLE PRECISION
);

-- 4. Devices
CREATE TABLE Device (
    id SERIAL PRIMARY KEY,
    category_id INTEGER REFERENCES DeviceCategory(id),
    parameter_id INTEGER REFERENCES Parameter(id),
    model TEXT NOT NULL,
    serial_number TEXT NOT NULL,
    install_date DATE,
    manufacturer TEXT,
    deployment_date DATE,
    local_id_device INTEGER,
    kit_id INTEGER REFERENCES Kit(id) ON DELETE CASCADE
);

-- 5. Measurements
CREATE TABLE Measurement (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    device_id INTEGER REFERENCES Device(id) ON DELETE CASCADE,
    value DOUBLE PRECISION
);

-- =======================
-- Sequence Synchronization
-- =======================

SELECT setval('devicecategory_id_seq', COALESCE((SELECT MAX(id) FROM devicecategory), 1), false);
SELECT setval('parameter_id_seq', COALESCE((SELECT MAX(id) FROM parameter), 1), false);
SELECT setval('kit_id_seq', COALESCE((SELECT MAX(id) FROM kit), 1), false);
SELECT setval('device_id_seq', COALESCE((SELECT MAX(id) FROM device), 1), false);
SELECT setval('measurement_id_seq', COALESCE((SELECT MAX(id) FROM measurement), 1), false);
