package finalagent;

import java.sql.*;
import java.time.LocalDate;

public class AddDB {

    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASSWORD = "1234";

    public static void addKit(double latitude, double longitude) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM kit WHERE latitude = ? AND longitude = ?")) {
                check.setDouble(1, latitude);
                check.setDouble(2, longitude);
                ResultSet rs = check.executeQuery();
                if (!rs.next()) {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO kit(latitude, longitude) VALUES (?, ?)")) {
                        insert.setDouble(1, latitude);
                        insert.setDouble(2, longitude);
                        insert.executeUpdate();
                        System.out.println("✅ Kit ajouté.");
                    }
                } else {
                    System.out.println("🔁 Kit déjà existant.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void addSensor(String nameCategory, String nameModel, Date date,
                                 String manufacturer, String serialNumber,
                                 String parameterName, String unit) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            conn.setAutoCommit(false);

            int parameterId = getOrInsert(conn,
                    "INSERT INTO parameter(name, unit) VALUES (?, ?) " +
                            "ON CONFLICT(name) DO UPDATE SET unit = EXCLUDED.unit RETURNING id",
                    parameterName, unit);

            int categoryId = getOrInsert(conn,
                    "INSERT INTO deviceCategory(name) VALUES (?) " +
                            "ON CONFLICT(name) DO NOTHING RETURNING id",
                    nameCategory);

            if (categoryId == -1) {
                categoryId = getOrSelect(conn,
                        "SELECT id FROM deviceCategory WHERE name = ?", nameCategory);
            }

            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM device WHERE serial_number = ?")) {
                check.setString(1, serialNumber);
                ResultSet rs = check.executeQuery();
                if (!rs.next()) {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO device(category_id, parameter_id, model, serial_number, install_date, manufacturer) " +
                                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                        insert.setInt(1, categoryId);
                        insert.setInt(2, parameterId);
                        insert.setString(3, nameModel);
                        insert.setString(4, serialNumber);
                        insert.setDate(5, date);
                        insert.setString(6, manufacturer);
                        insert.executeUpdate();
                        System.out.println("✅ Capteur ajouté.");
                    }
                } else {
                    System.out.println("🔁 Capteur déjà existant.");
                }
            }

            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void addSensorOnKit(int idKit, int deviceId, int idLocation) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            boolean kitExists = exists(conn, "SELECT 1 FROM kit WHERE id = ?", idKit);
            boolean deviceExists = exists(conn, "SELECT 1 FROM device WHERE id = ?", deviceId);
            boolean locationExists = exists(conn, "SELECT 1 FROM location WHERE id = ?", idLocation);

            if (!kitExists || !deviceExists || !locationExists) {
                System.out.println("❌ Une des entités (kit, device, location) n'existe pas.");
                return;
            }

            boolean alreadyLinked = exists(conn,
                    "SELECT 1 FROM DeviceLocation WHERE device_id = ? AND location_id = ? AND kit_id = ?",
                    deviceId, idLocation, idKit);

            if (!alreadyLinked) {
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO DeviceLocation(device_id, location_id, kit_id, deployment_date) " +
                                "VALUES (?, ?, ?, CURRENT_DATE)")) {
                    insert.setInt(1, deviceId);
                    insert.setInt(2, idLocation);
                    insert.setInt(3, idKit);
                    insert.executeUpdate();
                    System.out.println("Lien capteur/kit/lieu ajouté.");
                }
            } else {
                System.out.println("🔁 Lien déjà existant.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Utilitaires internes
    private static int getOrInsert(Connection conn, String sql, Object... values) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }

    private static int getOrSelect(Connection conn, String sql, Object... values) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }

    private static boolean exists(Connection conn, String sql, Object... values) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }



    public static void main(String[] args) {
        System.out.println("🚀 Test de AddDB");

        // 1. Ajouter un Kit
        AddDB.addKit(41.1234, 20.8765);

        // 2. Ajouter un capteur (sensor)
        AddDB.addSensor(
                "Capteur température",
                "TS-200",
                Date.valueOf(LocalDate.of(2024, 5, 10)),
                "ThermoCorp",
                "TS200-XYZ",
                "température",
                "°C"
        );

        // 3. Lier capteur et kit à une localisation (assure-toi que l'ID 1 et 1 existent)
        AddDB.addSensorOnKit(4, 1, 1);

        System.out.println("✅ Fin du test");
    }


}
