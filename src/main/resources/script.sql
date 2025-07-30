-- =======================
-- Nettoyage complet
-- =======================
DROP VIEW IF EXISTS MeasurementView;
DROP TABLE IF EXISTS Measurement CASCADE;
DROP TABLE IF EXISTS LocalMeasurement CASCADE;
DROP TABLE IF EXISTS Device CASCADE;
DROP TABLE IF EXISTS LocalDevice CASCADE;
DROP TABLE IF EXISTS DeviceCategory CASCADE;
DROP TABLE IF EXISTS Parameter CASCADE;
DROP TABLE IF EXISTS Kit CASCADE;
DROP TABLE IF EXISTS Location CASCADE;
DROP TABLE IF EXISTS LocationDataReport CASCADE;

-- =======================
-- Création des tables
-- =======================

-- 1. Catégories
CREATE TABLE DeviceCategory (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

-- 2. Paramètres
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

-- 4. Localisation
CREATE TABLE Location (
    x DOUBLE PRECISION,
    y DOUBLE PRECISION,
    xa DOUBLE PRECISION,
    ya DOUBLE PRECISION
);

-- 5. LocalDevice
CREATE TABLE LocalDevice (
    id SERIAL PRIMARY KEY,
    name_category TEXT NOT NULL,
    name_parameter TEXT NOT NULL,
    name_unit TEXT NOT NULL,
    model TEXT NOT NULL,
    serial_number TEXT, -- Nullable
    install_date DATE,
    manufacturer TEXT,
    deployment_date DATE
);

-- 6. Device (vide, mais structure créée)
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

-- 7. Measurement (lié à Device)
CREATE TABLE Measurement (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    device_id INTEGER REFERENCES Device(id) ON DELETE CASCADE,
    value DOUBLE PRECISION
);

-- 8. LocalMeasurement (lié à LocalDevice)
CREATE TABLE LocalMeasurement (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    x DOUBLE PRECISION,
    y DOUBLE PRECISION,
    id_device INTEGER REFERENCES LocalDevice(id) ON DELETE CASCADE,
    value DOUBLE PRECISION
);

-- ================================
-- Trigger : max 10 mesures / capteur
-- ================================
CREATE OR REPLACE FUNCTION limit_10_local_measurements()
RETURNS TRIGGER AS
$$
BEGIN
    DELETE FROM LocalMeasurement
    WHERE id IN (
        SELECT id FROM LocalMeasurement
        WHERE id_device = NEW.id_device
        ORDER BY timestamp ASC
        OFFSET 9
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_limit_10_local_measurements
BEFORE INSERT ON LocalMeasurement
FOR EACH ROW
EXECUTE FUNCTION limit_10_local_measurements();

-- ===========================
-- Données initiales
-- ===========================

-- Catégories
INSERT INTO DeviceCategory (name) VALUES
    ('Sensor Temperature'),
    ('Sensor pH'),
    ('Sensor Multi-parameter');

-- Paramètres
INSERT INTO Parameter (name, unit) VALUES
    ('Temperature', '°C'),
    ('pH', ''),
    ('Turbidity', 'NTU');

-- Kits
INSERT INTO Kit (x, y) VALUES
    (0, 0),
    (60, 60);

-- LocalDevice
INSERT INTO LocalDevice (
    name_category, name_parameter, name_unit, model, serial_number,
    install_date, manufacturer, deployment_date
) VALUES
    ('Sensor Temperature', 'Temperature', '°C', 'Waterproof DS18B20', 'TEMP-SN-001', '2025-07-25', 'DF Robot', '2024-04-05'),
    ('Sensor pH', 'pH', '', 'pH Meter V2.0', 'PH-SN-002', '2025-07-25', 'DF Robot', '2024-04-06');

-- Localisation
INSERT INTO Location (x, y, xa, ya) VALUES (60, 60, 60, 60);

-- LocalMeasurement
INSERT INTO LocalMeasurement (timestamp, x, y, id_device, value) VALUES
    (NOW(), 60, 60, 1, 21.8),
    (NOW(), 60, 60, 2, 6.9);
