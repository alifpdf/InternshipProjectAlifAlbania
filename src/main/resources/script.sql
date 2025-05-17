-- ========================================
-- 🚫 Nettoyage
-- ========================================
DROP VIEW IF EXISTS MeasurementView;
DROP TABLE IF EXISTS Measurement;
DROP TABLE IF EXISTS DeviceLocation;
DROP TABLE IF EXISTS Device;
DROP TABLE IF EXISTS DeviceCategory;
DROP TABLE IF EXISTS Parameter;
DROP TABLE IF EXISTS Location;
DROP TABLE IF EXISTS LocationDataReport;

-- ========================================
-- 🚀 Création des tables
-- ========================================

-- Catégories de capteurs
CREATE TABLE DeviceCategory (
                                id SERIAL PRIMARY KEY,
                                name TEXT NOT NULL UNIQUE
);

-- Paramètres mesurés
CREATE TABLE Parameter (
                           id SERIAL PRIMARY KEY,
                           name TEXT NOT NULL UNIQUE,       -- ex : "température", "pH"
                           unit TEXT                        -- ex : "°C", "mg/L", "NTU"
);

-- Capteurs
CREATE TABLE Device (
                        id SERIAL PRIMARY KEY,
                        category_id INTEGER REFERENCES DeviceCategory(id),
                        parameter_id INTEGER REFERENCES Parameter(id),
                        model TEXT NOT NULL,
                        serial_number TEXT UNIQUE NOT NULL,
                        install_date DATE,
                        manufacturer TEXT
);

-- Lieux
CREATE TABLE Location (
                          id SERIAL PRIMARY KEY,
                          name TEXT NOT NULL UNIQUE,
                          latitude DOUBLE PRECISION,
                          longitude DOUBLE PRECISION,
                          region TEXT
);
CREATE TABLE Kit (
                     id SERIAL PRIMARY KEY,
                     latitude DOUBLE PRECISION,
                     longitude DOUBLE PRECISION
);

-- Déploiement capteurs
CREATE TABLE DeviceLocation (
                                id SERIAL PRIMARY KEY,
                                device_id INTEGER REFERENCES Device(id) ON DELETE CASCADE,
                                location_id INTEGER REFERENCES Location(id) ON DELETE CASCADE,
                                deployment_date DATE,
                                kit_id INTEGER REFERENCES Kit(id) ON DELETE CASCADE ,
                                CONSTRAINT unique_device_location UNIQUE (device_id, location_id, kit_id)
);

-- Mesures
CREATE TABLE Measurement (
                             id SERIAL PRIMARY KEY,
                             timestamp TIMESTAMPTZ NOT NULL,
                             device_location_id INTEGER REFERENCES DeviceLocation(id),
                             value DOUBLE PRECISION
);



-- Vue enrichie Measurement
CREATE VIEW MeasurementView AS
SELECT
    m.id AS measurement_id,
    m.timestamp,
    m.value,

    -- Infos capteur
    d.serial_number AS device_serial,
    d.model AS device_model,
    d.manufacturer,

    -- Paramètre mesuré
    p.name AS parameter_name,
    p.unit AS parameter_unit,

    -- Infos localisation
    l.name AS location_name,
    l.latitude,
    l.longitude,
    l.region,


   -- kit localisation
    k.latitude AS kit_latitude,
    k.longitude AS kit_longitude




FROM Measurement m
         JOIN DeviceLocation dl ON m.device_location_id = dl.id
         JOIN Device d ON dl.device_id = d.id
         JOIN Parameter p ON d.parameter_id = p.id
         JOIN Location l ON dl.location_id = l.id
         JOIN Kit k ON dl.kit_id = k.id;

-- Rapport enrichi par lieu
CREATE TABLE LocationDataReport (
                                    id SERIAL PRIMARY KEY,
                                    location_name TEXT NOT NULL,
                                    generated_at TIMESTAMPTZ DEFAULT NOW(),
                                    content TEXT,
                                    raw_data JSONB
);

-- ========================================
-- 📥 Insertion de données initiales
-- ========================================

-- Paramètres
INSERT INTO Parameter (name, unit) VALUES
                                       ('température', '°C'),
                                       ('pH', ''),
                                       ('turbidité', 'NTU');

-- Catégories
INSERT INTO DeviceCategory (name) VALUES
                                      ('Capteur température'),
                                      ('Capteur pH'),
                                      ('Capteur multi-paramètre');

-- Capteurs
INSERT INTO Device (category_id, parameter_id, model, serial_number, install_date, manufacturer) VALUES
                                                                                                     (1, 1, 'TS-100', 'TS100-A1', '2024-04-01', 'SensTech'),     -- température
                                                                                                     (2, 2, 'PH-200', 'PH200-B1', '2024-04-02', 'AquaProbe');    -- pH

-- Localisations
INSERT INTO Location (name, latitude, longitude, region) VALUES
                                                             ('Ohrid Nord', 41.125, 20.800, 'Ohrid'),
                                                             ('Ohrid Sud', 41.098, 20.780, 'Ohrid');

INSERT INTO Kit(latitude, longitude) VALUES
                                         (41.125, 20.800),
                                         (41.098, 20.780);

-- Déploiements
INSERT INTO DeviceLocation (device_id, location_id, deployment_date,kit_id) VALUES
                                                                         (1, 1, '2024-04-05',1),  -- TS100-A1 → Ohrid Nord
                                                                         (2, 2, '2024-04-06',2);  -- PH200-B1 → Ohrid Sud

-- Mesures
INSERT INTO Measurement (timestamp, device_location_id, value) VALUES
                                                                   (NOW(), 1, 21.7),     -- Température
                                                                   (NOW(), 2, 7.2);      -- pH

-- Rapport Ohrid Nord
INSERT INTO LocationDataReport (location_name, content, raw_data) VALUES (
                                                                             'Ohrid Nord',
                                                                             'Résumé : température moyenne = 21.7°C. Aucune anomalie détectée.',
                                                                             '[
                                                                               {
                                                                                 "timestamp": "2024-04-12T10:00:00Z",
                                                                                 "parameter": "température",
                                                                                 "value": 21.7,
                                                                                 "device": "TS100-A1",
                                                                                 "kit_id": 1
                                                                               }
                                                                             ]'::jsonb
                                                                         );

-- Rapport Ohrid Sud
INSERT INTO LocationDataReport (location_name, content, raw_data) VALUES (
                                                                             'Ohrid Sud',
                                                                             'Résumé : pH mesuré à 7.2. Valeur conforme à la norme.',
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



