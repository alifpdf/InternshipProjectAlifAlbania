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


-- Synchronisation des séquences avec les ID insérés manuellement

SELECT setval('devicecategory_id_seq', (SELECT MAX(id) FROM devicecategory));
SELECT setval('parameter_id_seq', (SELECT MAX(id) FROM parameter));
SELECT setval('kit_id_seq', (SELECT MAX(id) FROM kit));
SELECT setval('localdevice_id_seq', (SELECT MAX(id) FROM localdevice));
SELECT setval('device_id_seq', (SELECT MAX(id) FROM device));
SELECT setval('measurement_id_seq', (SELECT MAX(id) FROM measurement));
SELECT setval('localmeasurement_id_seq', (SELECT MAX(id) FROM localmeasurement));

