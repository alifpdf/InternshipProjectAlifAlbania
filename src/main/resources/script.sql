-- ========================================
-- 🧹 Cleanup
-- ========================================
DROP VIEW IF EXISTS MeasurementView;
DROP TABLE IF EXISTS Measurement;
DROP TABLE IF EXISTS Device;
DROP TABLE IF EXISTS DeviceCategory;
DROP TABLE IF EXISTS Parameter;
DROP TABLE IF EXISTS Kit;
DROP TABLE IF EXISTS Area;
DROP TABLE IF EXISTS LocationDataReport;

-- ========================================
--  Table creation
-- ========================================

-- Device categories
CREATE TABLE DeviceCategory (
                                id SERIAL PRIMARY KEY,
                                name TEXT NOT NULL UNIQUE
);

-- Measured parameters
CREATE TABLE Parameter (
                           id SERIAL PRIMARY KEY,
                           name TEXT NOT NULL UNIQUE,
                           unit TEXT
);

-- Monitoring areas
CREATE TABLE Area (
                      id SERIAL PRIMARY KEY,
                      name TEXT NOT NULL UNIQUE,
                      latitude DOUBLE PRECISION,
                      longitude DOUBLE PRECISION,
                      region TEXT
);

-- Sensor kits
CREATE TABLE Kit (
                     id SERIAL PRIMARY KEY,
                     currentLatitude NUMERIC(18, 15),
                     currentLongitude NUMERIC(18, 15)
);

-- Devices
CREATE TABLE Device (
                        id SERIAL PRIMARY KEY,
                        category_id INTEGER REFERENCES DeviceCategory(id),
                        parameter_id INTEGER REFERENCES Parameter(id),
                        model TEXT NOT NULL,
                        serial_number TEXT UNIQUE NOT NULL,
                        install_date DATE,
                        manufacturer TEXT,
                        kit_id INTEGER REFERENCES Kit(id) ON DELETE CASCADE
);

-- Measurements
CREATE TABLE Measurement (
                             id SERIAL PRIMARY KEY,
                             timestamp TIMESTAMPTZ NOT NULL,
                             device_id INTEGER REFERENCES Device(id),
                             area_id INTEGER REFERENCES Area(id),
                             Latitude DOUBLE PRECISION,
                             Longitude DOUBLE PRECISION,
                             value DOUBLE PRECISION
);

-- Area-based reports
CREATE TABLE LocationDataReport (
                                    id SERIAL PRIMARY KEY,
                                    location_name TEXT NOT NULL,
                                    generated_at TIMESTAMPTZ DEFAULT NOW(),
                                    content TEXT,
                                    raw_data JSONB
);

-- ========================================
-- 👁️ Enriched Measurement View
-- ========================================
CREATE VIEW MeasurementView AS
SELECT
    m.id AS measurement_id,
    m.timestamp,
    m.value,

    -- Device info
    d.serial_number AS device_serial,
    d.model AS device_model,
    d.manufacturer,

    -- Parameter info
    p.name AS parameter_name,
    p.unit AS parameter_unit,

    -- Area info
    a.name AS area_name,
    a.latitude,
    a.longitude,
    a.region,

    -- Kit coordinates
    k.currentLatitude AS kit_latitude,
    k.currentLongitude AS kit_longitude

FROM Measurement m
         JOIN Device d ON m.device_id = d.id
         JOIN Parameter p ON d.parameter_id = p.id
         JOIN Area a ON m.area_id = a.id
         JOIN Kit k ON d.kit_id = k.id;

-- ========================================
-- 📥 Initial Data Insertion
-- ========================================

-- Parameters
INSERT INTO Parameter (name, unit) VALUES
                                       ('temperature', '°C'),
                                       ('pH', ''),
                                       ('turbidity', 'NTU');

-- Categories
INSERT INTO DeviceCategory (name) VALUES
                                      ('Temperature Sensor'),
                                      ('pH Sensor'),
                                      ('Multi-parameter Sensor');

-- Kits
INSERT INTO Kit (currentLatitude, currentLongitude) VALUES
                                                        (41.125, 20.800),
                                                        (41.098, 20.780);

-- Devices
INSERT INTO Device (category_id, parameter_id, model, serial_number, install_date, manufacturer, kit_id) VALUES
                                                                                                             (1, 1, 'TS-100', 'TS100-A1', '2024-04-01', 'SensTech', 1),
                                                                                                             (2, 2, 'PH-200', 'PH200-B1', '2024-04-02', 'AquaProbe', 2);

-- Areas
INSERT INTO Area (name, latitude, longitude, region) VALUES
                                                         ('Ohrid North', 41.125, 20.800, 'Ohrid'),
                                                         ('Ohrid South', 41.098, 20.780, 'Ohrid');

-- Measurements
INSERT INTO Measurement (timestamp, device_id, area_id,Latitude,Longitude, value) VALUES
                                                                   (NOW(), 1, 1, 41.125, 20.800, 21.7),
                                                                   (NOW(), 2, 2,41.098, 20.780, 7.2);

-- Reports
INSERT INTO LocationDataReport (location_name, content, raw_data) VALUES
                                                                      (
                                                                          'Ohrid North',
                                                                          'Summary: average temperature = 21.7°C. No anomaly detected.',
                                                                          '[
                                                                            {
                                                                              "timestamp": "2024-04-12T10:00:00Z",
                                                                              "parameter": "temperature",
                                                                              "value": 21.7,
                                                                              "device": "TS100-A1",
                                                                              "kit_id": 1
                                                                            }
                                                                          ]'::jsonb
                                                                      ),
                                                                      (
                                                                          'Ohrid South',
                                                                          'Summary: pH measured at 7.2. Value is within the acceptable range.',
                                                                          '[
                                                                            {
                                                                              "timestamp": "2024-04-12T10:00:00Z",
                                                                              "parameter": "pH",
                                                                              "value": 7.2,
                                                                              "device": "PH200-B1",
                                                                              "kit_id": 2
                                                                            }
                                                                          ]'::jsonb
                                                                      );
