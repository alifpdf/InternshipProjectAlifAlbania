package finalagent;

import java.sql.*;
import java.time.LocalDate;
import java.time.Instant;
import static java.lang.Math.abs;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.Instant;



public class AddDB {

     static String URL = "jdbc:postgresql://192.168.0.101:5432/postgres";
    static String USER = "postgres";
    static String PASSWORD = "1234";

    public static void addKit(double latitude, double longitude) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM public.kit WHERE latitude = ? AND longitude = ?")) {
                check.setDouble(1, latitude);
                check.setDouble(2, longitude);
                ResultSet rs = check.executeQuery();
                if (!rs.next()) {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO public.kit(latitude, longitude) VALUES (?, ?)")) {
                        insert.setDouble(1, latitude);
                        insert.setDouble(2, longitude);
                        insert.executeUpdate();
                        System.out.println(" Kit ajouté.");
                    }
                } else {
                    System.out.println(" Kit déjà existant.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    
     public static void addSensor(String nameCategory, String nameModel, Date date,
                                 String manufacturer, String serialNumber,
                                 String parameterName, String unit, Integer kit_id) {
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
                            "INSERT INTO device(category_id, parameter_id, model, serial_number, install_date, manufacturer,kit_id) " +
                                    "VALUES (?, ?, ?, ?, ?, ?,?)")) {
                        insert.setInt(1, categoryId);
                        insert.setInt(2, parameterId);
                        insert.setString(3, nameModel);
                        insert.setString(4, serialNumber);
                        insert.setDate(5, date);
                        insert.setString(6, manufacturer);
                        insert.setInt(7, kit_id);
                        insert.executeUpdate();
                        System.out.println("Sensor added.");
                    }
                } else {
                    System.out.println("Sensor already exists.");
                }
            }

            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
     public boolean hasSensorInDatabase(Integer idKit) {


        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String sql = """
            SELECT 1 FROM Device d
            WHERE d.kit_id = ?
            LIMIT 1
        """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1,idKit);
                ResultSet rs = ps.executeQuery();
                return rs.next(); // true sil y a au moins une ligne
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean isKitCountEqualToAgents(String[] listagent) {

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM Kit");
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                int kitCount = rs.getInt(1);
                int agentCount = listagent.length;


                return kitCount == agentCount;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
    public String getLastMeasurementsByKit(int idKit) {
        StringBuilder response = new StringBuilder("?? Measures from kit ID = " + idKit + " :\n");
       ;
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (PreparedStatement ps = conn.prepareStatement("""
            SELECT d.serial_number, m.value, m.timestamp
            FROM Measurement m
            JOIN Device d ON m.device_id = d.id
            WHERE d.kit_id = ?
            AND m.timestamp = (
                SELECT MAX(m2.timestamp)
                FROM Measurement m2
                WHERE m2.device_id = d.id
            )
            ORDER BY d.serial_number
        """)) {
                ps.setInt(1, idKit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String serial = rs.getString("serial_number");
                    double value = rs.getDouble("value");
                    Timestamp ts = rs.getTimestamp("timestamp");
                    response.append(serial)
                            .append(" -> ").append(value)
                            .append(" (").append(ts).append(")\n");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.append("Error");
        }

        return response.toString();
    }
public Double[] actualKitLocation() {
    String sql = "SELECT latitude, longitude FROM location LIMIT 1";
String URL = "jdbc:postgresql://localhost:5432/postgres";
    String USER = "postgres";
    String PASSWORD = "1234";
    try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        if (rs.next()) {
            BigDecimal lat = rs.getBigDecimal("latitude");
            BigDecimal lon = rs.getBigDecimal("longitude");
            return new Double[]{lat.doubleValue(), lon.doubleValue()};
        }

    } catch (SQLException e) {
        e.printStackTrace();
    }

    return new Double[]{0.0, 0.0}; // fallback
}


    public Double[] actualLocationStudy(int idLocation) {
        try(Connection conn=DriverManager.getConnection(URL,USER,PASSWORD)){
            try (PreparedStatement ps = conn.prepareStatement("""
 SELECT latitude, longitude FROM area  WHERE id = ?  """)){

                ps.setInt(1, idLocation);
                ResultSet rs = ps.executeQuery();
                rs.next();
                double latitude = rs.getInt("latitude");
                double longitude = rs.getInt("longitude");
                return new Double[]{latitude, longitude};


            }


        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
     public void saveMeasurementToDatabase(int idKit) {


        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {

            try (PreparedStatement ps = conn.prepareStatement("""
            SELECT d.id, d.serial_number, k.currentLatitude, k.currentlongitude FROM Device d join Kit k ON d.kit_id = k.id
            WHERE d.kit_id = ?
        """)) {
                ps.setInt(1, idKit);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    int deviceLocationId = rs.getInt("id");
                    String serialNumber = rs.getString("serial_number");
                    Double latitude = rs.getDouble("currentlatitude");
                    Double longitude = rs.getDouble("currentlongitude");

                    // Valeur alatoire entre 1 et 10
                    double value = 1 + Math.random() * 9;

                    try (PreparedStatement insert = conn.prepareStatement("""
                    INSERT INTO Measurement (timestamp, device_id,latitude,longitude,value)
                    VALUES (?, ?, ?,?,?)
                """)) {
                        insert.setTimestamp(1, Timestamp.from(Instant.now()));
                        insert.setInt(2, deviceLocationId);
                        insert.setDouble(3, latitude);
                        insert.setDouble(4, longitude);
                        insert.setDouble(5, value);
                        insert.executeUpdate();
                        System.out.println("data saved by " + serialNumber + " with value=" + value);
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteKit(int idKit) {
        // to delete kit from database
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement("DELETE FROM Kit WHERE id = ?")) {
            ps.setInt(1, idKit);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                System.out.println("Kit ID " + idKit + " deleted from database.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }
 public String[] addnewAgentOnlist(String[] listagent) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement("SELECT MAX(id) FROM Kit");
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                int lastkit_id = rs.getInt(1);


                // To update the list of agent
                listagent = new String[lastkit_id];
                for (int i = 0; i < lastkit_id; i++) {
                    listagent[i] = "z" + (i + 1);
                }
                return listagent;

            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return listagent;
    }

    public int toHavetheLastkit() throws SQLException {
        int lastkit_id=0;
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);

             PreparedStatement ps = conn.prepareStatement("SELECT MAX(id) FROM Kit");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                lastkit_id = rs.getInt(1);

            }
        }
        return lastkit_id;
    }


 public void updateKitCoordinates(int kitId, double newLat, double newLon) {


        String sql = """
        UPDATE Kit
        SET currentLatitude = ?, currentLongitude = ?
        WHERE id = ?
    """;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // Conversion des coordonnes en BigDecimal avec prcision exacte
            BigDecimal preciseLat = BigDecimal.valueOf(newLat);
            BigDecimal preciseLon = BigDecimal.valueOf(newLon);

            ps.setBigDecimal(1, preciseLat);
            ps.setBigDecimal(2, preciseLon);
            ps.setInt(3, kitId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                
                System.out.println("Kit ID " + kitId + " updated to new position (" + preciseLat + ", " + preciseLon + ")");
                
                 updateLocalCoordinates(newLat,newLon) ;
                
            } else {
                System.out.println("No kit found with ID = " + kitId);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        
        
    }
public void updateLocalCoordinates(double newLat, double newLon) {
    String updateSql = """
        UPDATE location
        SET latitude = ?, longitude = ?
    """;

    String URL = "jdbc:postgresql://localhost:5432/postgres";
    String USER = "postgres";
    String PASSWORD = "1234";

    try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
         PreparedStatement updatePs = conn.prepareStatement(updateSql)) {

        BigDecimal preciseLat = BigDecimal.valueOf(newLat);
        BigDecimal preciseLon = BigDecimal.valueOf(newLon);

        updatePs.setBigDecimal(1, preciseLat);
        updatePs.setBigDecimal(2, preciseLon);

        int affected = updatePs.executeUpdate();
        if (affected > 0) {
            System.out.println("? local coordinates updated");
        } else {
            System.out.println("? No row updated. Table may be empty.");
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
        System.out.println("?? Test de AddDB");

        // 1. Ajouter un Kit
        AddDB.addKit(41.1234, 20.8765);

        // 2. Ajouter un capteur (sensor)
        AddDB.addSensor(
                "Temperature Sensor",
                "TS-200",
                Date.valueOf(LocalDate.of(2024, 5, 10)),
                "ThermoCorp",
                "TS200-XYZ",
                "temprature",
                "C",
                1

        );

        System.out.println("End");
    }

}
