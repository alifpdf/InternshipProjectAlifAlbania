package finalagent;


import java.sql.*;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;



import com.fazecast.jSerialComm.*;
import java.io.InputStream;
import java.util.*;


public class AddDB {



public static Connection getLocalConnection() throws SQLException {
        String localURL = "jdbc:postgresql://localhost:5432/postgres";
        String localUSER = "postgres";
        String localPASSWORD = "1234";
        return DriverManager.getConnection(localURL, localUSER, localPASSWORD);
    }

    // Get connection to the MAIN (remote) database
    public static Connection getMainConnection() throws SQLException {
        String mainURL = "jdbc:postgresql://192.168.224.130:5432/postgres";
        String mainUSER = "postgres";
        String mainPASSWORD = "1234";
        return DriverManager.getConnection(mainURL, mainUSER, mainPASSWORD);
    }



/**
 * Adds a new kit with specific coordinates to the database, or updates its location if the kit already exists.
 *
 * @param x      X-coordinate of the kit location
 * @param y      Y-coordinate of the kit location
 * @param kitId  ID of the kit (used for both checking and insertion)
 */
public static void addKit(double x, double y, int kitId) {
    String checkSQL = "SELECT id FROM kit WHERE id = ?";
    String insertWithIdSQL = "INSERT INTO kit (id, x, y) VALUES (?, ?, ?)";
    String updateSQL = "UPDATE kit SET x = ?, y = ? WHERE id = ?";

    try (Connection conn = getMainConnection()) {

        // Step 1: Check if the kit exists
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSQL)) {
            checkStmt.setInt(1, kitId);
            try (ResultSet rs = checkStmt.executeQuery()) {

                if (rs.next()) {
                    // Update existing kit
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {
                        updateStmt.setDouble(1, x);
                        updateStmt.setDouble(2, y);
                        updateStmt.setInt(3, kitId);
                        updateStmt.executeUpdate();
                        System.out.printf("Kit ID %d updated to coordinates (%.2f, %.2f)%n", kitId, x, y);
                    }
                } else {
                    // Insert new kit
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertWithIdSQL)) {
                        insertStmt.setInt(1, kitId);
                        insertStmt.setDouble(2, x);
                        insertStmt.setDouble(3, y);
                        insertStmt.executeUpdate();
                        System.out.printf("Kit ID %d not found. Inserted new kit at (%.2f, %.2f)%n", kitId, x, y);
                    }
                }

            }
        }

    } catch (SQLException e) {
        System.err.println("Error while adding or updating kit:");
        e.printStackTrace();
    }
}





/**
 * Updates the columns xa and ya in the Location table
 * by generating random valid points around the original (x, y) position.
 * This assumes there is only one row in the Location table.
 */
public void updateLocalValidPoint() {
    Random rand = new Random();
   

    // SQL queries to select and update the Location row
    String selectSql = "SELECT x, y, xa, ya FROM Location";
    String updateSql = "UPDATE Location SET xa = ?, ya = ? WHERE x = ? AND y = ?";

    try (
        Connection conn = getLocalConnection();
        PreparedStatement selectStmt = conn.prepareStatement(selectSql);
        ResultSet rs = selectStmt.executeQuery()
    ) {
        while (rs.next()) {
            // Original coordinates
            double x = rs.getDouble("x");
            double y = rs.getDouble("y");
            double xaa = rs.getDouble("xa");
            double yaa = rs.getDouble("ya");

            Set<Double> usedAngles = new HashSet<>();
            boolean updated = false;

            // Try up to 200 random directions
            for (int i = 0; i < 200; i++) {
                double theta = 2 * Math.PI * rand.nextDouble(); // Random angle
                double roundedTheta = Math.round(theta * 100.0) / 100.0;

                // Skip already used directions
                if (!usedAngles.add(roundedTheta)) continue;

                // Generate random radius up to 60 units
                double radius = rand.nextDouble() * 60;

                // Calculate new coordinates
                double xa = x + radius * Math.cos(roundedTheta);
                double ya = y + radius * Math.sin(roundedTheta);

                // Round to 2 decimal places
                xa = Math.round(xa * 100.0) / 100.0;
                ya = Math.round(ya * 100.0) / 100.0;

                // Try to update the row with new xa, ya
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setDouble(1, xa);
                    updateStmt.setDouble(2, ya);
                    updateStmt.setDouble(3, x);  // WHERE x = ?
                    updateStmt.setDouble(4, y);  // AND y = ?

                    int affected = updateStmt.executeUpdate();
                    if (affected > 0) {
                        System.out.printf("Update: (x=%.3f, y=%.3f) → (xa=%.3f, ya=%.3f)%n", xaa, yaa, xa, ya);
                        updated = true;
                        break;
                    }
                }
            }

            // If no valid point was found
            if (!updated) {
                System.out.printf("No valid position found around (%.3f, %.3f)%n", x, y);
            }
        }

    } catch (SQLException e) {
        System.err.println("SQL error occurred:");
        e.printStackTrace();
    }
}




 public static void TruncateTable() {
  
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = getLocalConnection();
            stmt = conn.createStatement();
            stmt.execute("TRUNCATE TABLE LocalMeasurement;");
            System.out.println("Table LocalMeasurement successfully emptied.");
        } catch (SQLException e) {
            System.err.println("Erreur during the TRUNCATE : " + e.getMessage());
        } 
    }


/**
 * Retrieves the last adjusted coordinates (xa, ya) from the Location table.
 * Assumes either one row exists or retrieves the most recent entry if multiple exist.
 *
 * @return a double array {xa, ya}, or null if not found
 */
public static double[] getLastAdjustedCoordinates() {


    String sql = "SELECT xa, ya FROM Location LIMIT 1";  // You can add ORDER BY if needed

    try (
        Connection conn = getLocalConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()
    ) {
        if (rs.next()) {
            double xa = rs.getDouble("xa");
            double ya = rs.getDouble("ya");
            return new double[] { xa, ya };
        }
    } catch (SQLException e) {
        System.err.println("SQL error while retrieving last adjusted coordinates:");
        e.printStackTrace();
    }

    return null; // No coordinates found
}




/**
 * Gets the latest temperature value at a specific coordinate (xa, ya)
 * from the LocalMeasurement table, for id_device = 1 (temperature sensor).
 *
 * @param xa X coordinate to match
 * @param ya Y coordinate to match
 * @return String in the format "x,y,value" or empty string if no match found
 */
public String getLastTemperatureWithCoordinates(double xa, double ya) {
    String result = "";


    String sql = """
        SELECT value, x, y
        FROM LocalMeasurement
        WHERE id_device = 1 AND x = ? AND y = ?
        ORDER BY timestamp DESC
        LIMIT 1
    """;

    try (
        Connection conn = getLocalConnection();
        PreparedStatement ps = conn.prepareStatement(sql)
    ) {
        ps.setDouble(1, xa);
        ps.setDouble(2, ya);

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double value = rs.getDouble("value");

                // Round values to 2 decimals
                x = Math.round(x * 100.0) / 100.0;
                y = Math.round(y * 100.0) / 100.0;
                value = Math.round(value * 100.0) / 100.0;

                result = x + "," + y + "," + value;
            }
        }

    } catch (SQLException e) {
        System.err.println(" SQL error while retrieving temperature:");
        e.printStackTrace();
    }

    return result;
}

    
    /**
 * Retrieves the latest pH value from LocalMeasurement where x and y match
 * the specified coordinates, and the sensor used is pH sensor (id_device = 2).
 *
 * @param xa X coordinate to match
 * @param ya Y coordinate to match
 * @return A string formatted as "x,y,value" or an empty string if not found
 */
public String getLastPHWithCoordinates(double xa, double ya) {
    String result = "";

  

    String sql = """
        SELECT value, x, y
        FROM LocalMeasurement
        WHERE id_device = 2 AND x = ? AND y = ?
        ORDER BY timestamp DESC
        LIMIT 1
    """;

    try (
        Connection conn = getLocalConnection();
        PreparedStatement ps = conn.prepareStatement(sql)
    ) {
        ps.setDouble(1, xa);
        ps.setDouble(2, ya);

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double value = rs.getDouble("value");

                // Round values to 2 decimal places
                x = Math.round(x * 100.0) / 100.0;
                y = Math.round(y * 100.0) / 100.0;
                value = Math.round(value * 100.0) / 100.0;

                result = x + "," + y + "," + value;
            }
        }

    } catch (SQLException e) {
        System.err.println(" SQL error while retrieving pH:");
        e.printStackTrace();
    }

    return result;
}





/**
 * Retrieves the latest measurement values (excluding temperature and pH)
 * for all other device IDs, filtered by given coordinates (xa, ya).
 *
 * @param xa X coordinate to match
 * @param ya Y coordinate to match
 * @return Map of device ID to a string "x,y,value" of the latest measurement at the location
 */
public Map<Integer, String> getLastOtherMeasurementsWithCoordinates(double xa, double ya) {
    Map<Integer, String> resultMap = new HashMap<>();

    String sql = """
        SELECT DISTINCT ON (id_device) id_device, value, x, y
        FROM LocalMeasurement
        WHERE id_device NOT IN (1, 2) AND x = ? AND y = ?
        ORDER BY id_device, timestamp DESC
    """;

    try (
        Connection conn = getLocalConnection();
        PreparedStatement ps = conn.prepareStatement(sql)
    ) {
        ps.setDouble(1, xa);
        ps.setDouble(2, ya);

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idDevice = rs.getInt("id_device");
                double x = Math.round(rs.getDouble("x") * 100.0) / 100.0;
                double y = Math.round(rs.getDouble("y") * 100.0) / 100.0;
                double value = Math.round(rs.getDouble("value") * 100.0) / 100.0;

                resultMap.put(idDevice, x + "," + y + "," + value);
            }
        }

    } catch (SQLException e) {
        System.err.println("SQL error while retrieving other measurements:");
        e.printStackTrace();
    }

    return resultMap;
}


   /**
 * Compares latest sensor values (Temperature, pH, and others) against reference values,
 * and returns the (x, y) location with the largest weighted difference.
 *
 * @param referenceTemperature  expected temperature
 * @param phReference           expected pH
 * @param referenceOthers       map of other expected sensor values by device ID
 * @return double[] {x, y, diff} with the maximum deviation
 */
public static double[] KitMeasureTemperatureCompare(Double referenceTemperature, Double phReference, Map<Integer, Double> referenceOthers) {
   
    Map<Integer, Map<String, Double>> deviceValues = new HashMap<>();

    try (
        Connection conn = getLocalConnection();
        PreparedStatement psDevices = conn.prepareStatement("SELECT DISTINCT id_device FROM LocalMeasurement");
        ResultSet rsDevices = psDevices.executeQuery()
    ) {
        // Step 1: Gather device IDs
        List<Integer> deviceIds = new ArrayList<>();
        while (rsDevices.next()) {
            deviceIds.add(rsDevices.getInt("id_device"));
        }

        // Step 2: Retrieve latest 10 values per device with coordinates
        String query = """
            SELECT x, y, value FROM LocalMeasurement
            WHERE id_device = ? ORDER BY timestamp DESC LIMIT 10
        """;

        for (int id : deviceIds) {
            Map<String, Double> values = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // Coordinates rounded for grouping
                        String key = String.format("%.2f,%.2f",
                                Math.round(rs.getDouble("x") * 100.0) / 100.0,
                                Math.round(rs.getDouble("y") * 100.0) / 100.0);
                        double val = Math.round(rs.getDouble("value") * 100.0) / 100.0;
                        values.put(key, val);
                    }
                }
            }
            deviceValues.put(id, values);
        }

        // Step 3: Find coordinates common to all devices
        Set<String> commonKeys = new HashSet<>(deviceValues.getOrDefault(1, Map.of()).keySet()); // Temperature
        commonKeys.retainAll(deviceValues.getOrDefault(2, Map.of()).keySet()); // pH
        for (int id : deviceIds) {
            if (id > 2) {
                commonKeys.retainAll(deviceValues.get(id).keySet());
            }
        }

        // Step 4: Apply weighted difference comparison
        double maxDiff = 0;
        double[] bestResult = null;

        double tempWeight = 0.7 + (referenceOthers.isEmpty() ? 0.1 : 0.0);  // redistribute 10% if no others
        double phWeight = 0.2;
        double otherWeight = referenceOthers.isEmpty() ? 0 : 0.1 / referenceOthers.size();

        for (String key : commonKeys) {
            double tempVal = deviceValues.getOrDefault(1, Map.of()).getOrDefault(key, 0.0);
            double phVal = deviceValues.getOrDefault(2, Map.of()).getOrDefault(key, 0.0);

            double diff = tempWeight * Math.abs(tempVal - referenceTemperature)
                        + phWeight * Math.abs(phVal - phReference);

            for (Map.Entry<Integer, Double> entry : referenceOthers.entrySet()) {
                int devId = entry.getKey();
                double expected = entry.getValue();
                double actual = deviceValues.getOrDefault(devId, Map.of()).getOrDefault(key, 0.0);
                diff += otherWeight * Math.abs(actual - expected);
            }

            diff = Math.round(diff * 100.0) / 100.0;

            if (diff * diff > maxDiff * maxDiff) { // use squared for consistency
                String[] xy = key.split(",");
                bestResult = new double[]{
                    Double.parseDouble(xy[0]),
                    Double.parseDouble(xy[1]),
                    diff
                };
                maxDiff = diff;
            }
        }

        return bestResult;

    } catch (SQLException e) {
        System.err.println("SQL Error in KitMeasureTemperatureCompare:");
        e.printStackTrace();
        return null;
    }
}







  /**
 * Generate a valid new location for a kit, based on a reference point (xr, yr).
 * The new location must be approximately 150 units away and not too close to existing kits.
 *
 * @param xr Reference X coordinate
 * @param yr Reference Y coordinate
 * @return An array [x, y] representing a valid position, or null if none found
 */
public static double[] generateSingleValidPoint(double xr, double yr) {
    List<double[]> existingKitCenters = new ArrayList<>();
    Random rand = new Random();
    Set<Integer> triedAngles = new HashSet<>();

    String sql = "SELECT x, y FROM Kit";

    // Step 1: Load all existing kit positions
    try (Connection conn = getLocalConnection();
         PreparedStatement stmt = conn.prepareStatement(sql);
         ResultSet rs = stmt.executeQuery()) {

        while (rs.next()) {
            double x = Math.round(rs.getDouble("x") * 100.0) / 100.0;
            double y = Math.round(rs.getDouble("y") * 100.0) / 100.0;
            existingKitCenters.add(new double[]{x, y});
        }

    } catch (SQLException e) {
        e.printStackTrace();
        return null;
    }

    if (existingKitCenters.isEmpty()) {
        System.err.println("No existing kit centers found.");
        return null;
    }

    // Step 2: Try 360 random directions to find a valid location
    while (triedAngles.size() < 360) {
        int angleDeg = rand.nextInt(360);
        if (!triedAngles.add(angleDeg)) continue;

        double angleRad = Math.toRadians(angleDeg);
        double x = xr + 150 * Math.cos(angleRad);
        double y = yr + 150 * Math.sin(angleRad);

        // Round to 2 decimal places
        x = Math.round(x * 100.0) / 100.0;
        y = Math.round(y * 100.0) / 100.0;

        // Step 3: Check that the point is not too close to any existing kit
        boolean isValid = true;
        for (double[] center : existingKitCenters) {
            double dx = x - center[0];
            double dy = y - center[1];
            double distanceSquared = dx * dx + dy * dy;

            if (distanceSquared <= 120) { // threshold: radius squared
                isValid = false;
                break;
            }
        }

        // Step 4: Return if valid
        if (isValid) {
            return new double[]{x, y};
        }
    }

    // Step 5: If no valid point found after 360 tries
    System.err.println("No valid new kit location found after 360 attempts.");
    return null;
}







/**
 * Updates the only row in the 'location' table with new coordinates.
 * Also updates the main 'Kit' table accordingly.
 *
 * @param kitID The kit ID whose coordinates will also be updated in the main database
 * @param newX  New X coordinate
 * @param newY  New Y coordinate
 */
public void updateLocalCoordinates(int kitID, double newX, double newY) {
    String updateSql = """
        UPDATE location
        SET x = ?, y = ?, xa = ?, ya = ?
    """;

  

    try (Connection conn = getLocalConnection();
         PreparedStatement updatePs = conn.prepareStatement(updateSql)) {

        updatePs.setDouble(1, newX);  // x
        updatePs.setDouble(2, newY);  // y
        updatePs.setDouble(3, newX);  // xa
        updatePs.setDouble(4, newY);  // ya

        int affected = updatePs.executeUpdate();
        if (affected > 0) {
            System.out.println("Local coordinates updated.");
            updateKitCoordinates(kitID, newX, newY); // Also update main Kit table
        } else {
            System.out.println("No update performed. Is the table empty?");
        }

    } catch (SQLException e) {
        System.err.println("Error updating local coordinates:");
        e.printStackTrace();
    }
}


/**
 * Updates the coordinates of a kit (in the main database).
 *
 * @param kitId ID of the kit
 * @param newx  New X coordinate
 * @param newy  New Y coordinate
 */
public void updateKitCoordinates(int kitId, double newx, double newy) {
    String sql = """
        UPDATE Kit
        SET x = ?, y = ?
        WHERE id = ?
    """;

    try (Connection conn = getMainConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setDouble(1, newx);
        ps.setDouble(2, newy);
        ps.setInt(3, kitId);

        int affected = ps.executeUpdate();
        if (affected > 0) {
            System.out.println("Kit ID " + kitId + " updated to new position (" + newx + ", " + newy + ")");
        } else {
            System.out.println("No kit found with ID = " + kitId);
        }

    } catch (SQLException e) {
        System.err.println("Error while updating Kit coordinates:");
        e.printStackTrace();
    }
}








/**
 * Generates and inserts a simulated measurement for each device (temperature, pH, and others)
 * into the LocalMeasurement table. Coordinates are retrieved from the local Location table.
 * This is used by agents in secondary containers (e.g., for simulation purposes).
 */
public void saveMeasurementToDatabase() {
   
    try (Connection conn = getLocalConnection()) {

        // Step 1: Retrieve current coordinates from Location table
        double xa = 0;
        double ya = 0;

        String locationSql = "SELECT xa, ya FROM Location";
        try (PreparedStatement psLoc = conn.prepareStatement(locationSql);
             ResultSet rs = psLoc.executeQuery()) {

            if (rs.next()) {
                xa = rs.getDouble("xa");
                ya = rs.getDouble("ya");
            } else {
                System.err.println("No coordinates found in Location table.");
                return;
            }
        }

        Timestamp now = Timestamp.from(Instant.now());

        // Step 2: Insert temperature and pH values
        String insertSql = """
            INSERT INTO LocalMeasurement (timestamp, x, y, id_device, value)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
            // Simulated temperature for device ID = 1
            double temp = Math.round((15 + Math.random() * 9) * 100.0) / 100.0;
            insert.setTimestamp(1, now);
            insert.setDouble(2, xa);
            insert.setDouble(3, ya);
            insert.setInt(4, 1);
            insert.setDouble(5, temp);
            insert.executeUpdate();
            System.out.printf("Temperature inserted: %.2f°C at (%.4f, %.4f)%n", temp, xa, ya);

            // Simulated pH for device ID = 2
            double ph = Math.round((Math.random() * 14) * 100.0) / 100.0;
            insert.setTimestamp(1, now);
            insert.setDouble(2, xa);
            insert.setDouble(3, ya);
            insert.setInt(4, 2);
            insert.setDouble(5, ph);
            insert.executeUpdate();
            System.out.printf(" pH inserted: %.2f at (%.4f, %.4f)%n", ph, xa, ya);
        }

        // Step 3: Insert values for all other devices (except ID 1 and 2)
        String otherDevicesSql = """
            SELECT DISTINCT id FROM LocalDevice WHERE id NOT IN (1, 2)
        """;

        try (
            PreparedStatement psOther = conn.prepareStatement(otherDevicesSql);
            ResultSet rsOther = psOther.executeQuery();
            PreparedStatement insertOther = conn.prepareStatement(insertSql)
        ) {
            while (rsOther.next()) {
                int deviceId = rsOther.getInt("id");
                double randomValue = Math.round((Math.random() * 100) * 100.0) / 100.0;

                insertOther.setTimestamp(1, now);
                insertOther.setDouble(2, xa);
                insertOther.setDouble(3, ya);
                insertOther.setInt(4, deviceId);
                insertOther.setDouble(5, randomValue);
                insertOther.executeUpdate();

                System.out.printf(" Random measurement inserted: %.2f (Device ID = %d) at (%.4f, %.4f)%n",
                        randomValue, deviceId, xa, ya);
            }
        }

    } catch (SQLException e) {
        System.err.println("SQL error during LocalMeasurement insertion:");
        e.printStackTrace();
    }
}




   
     
/**
 * Reads sensor metadata sent by the Arduino via serial port,
 * and inserts or retrieves corresponding local device IDs from the database.
 *
 * @return A map linking each sensor name to its corresponding local device ID.
 */
public Map<String, Integer> getLocalDeviceIdsFromArduino() {
    Map<String, Integer> sensorIdMap = new HashMap<>(); // Map: sensor name → local device ID
    Map<String, Boolean> receivedMap = new HashMap<>(); // Tracks whether metadata for each sensor has been received

    // Attempt to access the serial port
    SerialPort serialPort = SerialPort.getCommPort("/dev/ttyUSB0");
    if (serialPort == null) {
        System.out.println("Port /dev/ttyUSB0 not found.");
        return sensorIdMap;
    }

    // Configure the port (baud rate and timeout)
    serialPort.setBaudRate(9600);
    serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);

    // Try to open the serial port
    if (!serialPort.openPort()) {
        System.err.println("Failed to open serial port.");
        return sensorIdMap;
    }

   

    try (
        InputStream in = serialPort.getInputStream();
        Scanner scanner = new Scanner(in); // Reads data from serial
        Connection connLocal =getLocalConnection();
    ) {
        boolean allSensorsAnnounced = false;

        // Loop: process each line received from Arduino
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            // First step: extract list of announced sensor names
            if (!allSensorsAnnounced && line.startsWith("[") && line.endsWith("]") && line.contains("Sensor")) {
                String[] sensors = line.substring(1, line.length() - 1).split(",");
                for (String sensor : sensors) {
                    String sensorName = sensor.trim();
                    receivedMap.put(sensorName, false); // Mark each sensor as not yet received
                    System.out.println("Available sensor: " + sensorName);
                }
                allSensorsAnnounced = true;
                continue;
            }

            // Process each measurement block
            String[] measurements = line.split("/");
            for (String measurement : measurements) {
                measurement = measurement.trim();
                if (!measurement.startsWith("[") || !measurement.endsWith("]")) continue;

                String[] parts = measurement.substring(1, measurement.length() - 1).split(",");
                if (parts.length != 8) {
                    System.out.println("Badly formatted line: " + measurement);
                    continue;
                }

                // Extract sensor metadata
                String name = parts[0].trim();              // Sensor display name
                String brand = parts[1].trim();             // Manufacturer
                String model = parts[2].trim();             // Model name/code
                String ref = parts[3].trim();               // Serial number or internal reference
                Date deploymentDate = java.sql.Date.valueOf(parts[4].trim()); // Installation date
                String unit = parts[6].trim();              // Measurement unit (e.g., °C, mV)
                String parameter = parts[7].trim();         // What is being measured (e.g., temperature)

                try {
                    // Find or insert the device and retrieve its ID
                    int localDeviceId = getOrCreateLocalDeviceId(
                        connLocal, name, parameter, unit, model, ref, deploymentDate, brand
                    );

                    // Save the mapping and mark this sensor as received
                    sensorIdMap.put(name, localDeviceId);
                    receivedMap.put(name, true);

                    System.out.printf("Sensor '%s' saved with ID %d%n", name, localDeviceId);

                } catch (SQLException e) {
                    System.err.println("Error saving sensor from Arduino: " + name);
                    e.printStackTrace();
                }
            }

            // Stop reading once all sensors have been processed
            boolean allReceived = receivedMap.values().stream().allMatch(received -> received);
            if (!receivedMap.isEmpty() && allReceived) {
                System.out.println("All sensors from Arduino saved successfully.");
                break;
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        // Close the serial port when done
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            System.out.println("Serial port /dev/ttyUSB0 closed.");
        }
    }

    return sensorIdMap;
}


/**
 * Reads measurements from Arduino via serial port and saves them to the LocalMeasurement table.
 *
 * @param sensorIdMap A map linking sensor names to their local device IDs
 * @param x The X coordinate of the measurement location
 * @param y The Y coordinate of the measurement location
 */
public void arduino(Map<String, Integer> sensorIdMap, double x, double y) {
    SerialPort serialPort = SerialPort.getCommPort("/dev/ttyUSB0");

    if (serialPort == null) {
        System.out.println("Port /dev/ttyUSB0 not found.");
        return;
    }

    // Configure serial port
    serialPort.setBaudRate(9600);
    serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);

    // Ensure the port is closed before reopening
    if (serialPort.isOpen()) {
        serialPort.closePort();
        try {
            Thread.sleep(500); // Small pause to ensure the port is released
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt flag
            System.err.println("Sleep interrupted: " + e.getMessage());
        }
    }

    serialPort.openPort(); // Open the port


    try (
        InputStream in = serialPort.getInputStream();
        Scanner scanner = new Scanner(in);
        Connection connLocal = getLocalConnection()
    ) {
        System.out.println("Reading measurements for insertion into LocalMeasurement...");

        // Track which sensors have sent data
        Map<String, Boolean> receivedMap = new HashMap<>();
        for (String name : sensorIdMap.keySet()) {
            receivedMap.put(name, false);
        }
        System.out.println("Expected sensors: " + receivedMap.keySet());

        // Read data line-by-line from serial
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] measurements = line.split("/");
            for (String measurement : measurements) {
                measurement = measurement.trim();

                // Validate format: should be inside brackets
                if (!measurement.startsWith("[") || !measurement.endsWith("]")) continue;

                String[] parts = measurement.substring(1, measurement.length() - 1).split(",");

                // Skip improperly formatted lines
                if (parts.length != 8) {
                    if(parts.length !=sensorIdMap.size()){
                        
                    System.out.println("Incorrectly formatted line (8 fields expected): " + measurement);}
                    continue;
                }

                String name = parts[0].trim();        // Sensor name
                String valueStr = parts[5].trim();    // Measured value

                // Skip unknown sensors
                if (!sensorIdMap.containsKey(name)) {
                    System.err.println("Unknown sensor: " + name);
                    continue;
                }

                int localDeviceId = sensorIdMap.get(name);

                try {
                    double value = Double.parseDouble(valueStr);
                    insertLocalMeasurement(connLocal, localDeviceId, x, y, value);
                    System.out.printf("Measurement saved for %s (ID %d): %.2f%n", name, localDeviceId, value);
                    receivedMap.put(name, true);
                } catch (NumberFormatException e) {
                    System.err.printf("Number format error for sensor %s: %s%n", name, valueStr);
                }
            }

            // If all expected sensors have been received, stop reading
            boolean allReceived = receivedMap.values().stream().allMatch(received -> received);
            if (allReceived) {
                System.out.println("All measurements have been saved.");
                break;
            }
        }

    } catch (Exception e) {
        System.err.println("Exception while reading Arduino data:");
        e.printStackTrace();
    } finally {
        // Ensure port is closed after use
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            System.out.println("Serial port /dev/ttyUSB0 closed.");
        }
    }
}




private void insertLocalMeasurement(Connection conn, int localDeviceId, double x, double y, double value) throws SQLException {
    String sql = """
        INSERT INTO LocalMeasurement (timestamp, x, y, id_device, value)
        VALUES (?, ?, ?, ?, ?)
    """;

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        stmt.setTimestamp(1, timestamp);
        stmt.setDouble(2, x);
        stmt.setDouble(3, y);
        stmt.setInt(4, localDeviceId);
        stmt.setDouble(5, value);

        stmt.executeUpdate();
    }
}




public static void insertLocalDevicesToMain(int id_kit) {

    String fetchSql = """
        SELECT id, name_category, name_parameter, name_unit, model, serial_number, manufacturer, deployment_date
        FROM LocalDevice
    """;

    try (
        Connection localConn = getLocalConnection();
        Connection mainConn = getMainConnection();
        PreparedStatement fetchStmt = localConn.prepareStatement(fetchSql);
        ResultSet rs = fetchStmt.executeQuery()
    ) {
        while (rs.next()) {
            int localDeviceId = rs.getInt("id");
            String category = rs.getString("name_category");
            String parameter = rs.getString("name_parameter");
            String unit = rs.getString("name_unit");
            String model = rs.getString("model");
            String serial = rs.getString("serial_number");
            String manufacturer = rs.getString("manufacturer");
            Date deploymentDate = rs.getDate("deployment_date");

           // Step 1: Insert or retrieve the category
            int categoryId = checkAndInsertCategory(mainConn, category);

            // Step 2: Insert or retrieve the parameter
            int parameterId = checkAndInsertParameter(mainConn, parameter, unit);

            // Step 3: Insert or retrieve the device
            int deviceId = checkAndInsertDevice(
                mainConn,
                categoryId,
                parameterId,
                manufacturer,
                model,
                serial,
                id_kit,
                localDeviceId,
                deploymentDate
            );

            System.out.printf(" LocalDevice saved to Device : ID = %d (%s, %s)%n", deviceId, model, parameter);
        }

    } catch (SQLException e) {
        System.err.println(" Error in saving data from LocalDevice to Device :");
        e.printStackTrace();
    }
}



/**
 * Inserts or retrieves a local sensor (device) based on its unique attributes from Arduino data.
 * If the device already exists in the LocalDevice table, its ID is returned.
 * If not, a new row is inserted and the new ID is returned.
 *
 * @param conn             Database connection to the local DB
 * @param nameCategory     Sensor type/category name
 * @param parameter        Measured parameter (e.g., temperature, pH)
 * @param unit             Unit of measurement (e.g., °C, pH)
 * @param model            Model name or version
 * @param serialNumber     Unique serial number of the device
 * @param deploymentDate   Date when the device was deployed
 * @param manufacturer     Manufacturer name
 * @return The ID of the existing or newly inserted LocalDevice
 * @throws SQLException If any DB error occurs
 */
private static int getOrCreateLocalDeviceId(Connection conn,
                                            String nameCategory,
                                            String parameter,
                                            String unit,
                                            String model,
                                            String serialNumber,
                                            Date deploymentDate,
                                            String manufacturer) throws SQLException {
    // Step 1: Try to find an existing device with the same attributes
    String selectSql = """
        SELECT id FROM LocalDevice
        WHERE name_category = ? AND name_parameter = ? AND name_unit = ?
          AND model = ? AND serial_number = ? AND deployment_date = ? AND manufacturer = ?
    """;

    try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
        selectStmt.setString(1, nameCategory);
        selectStmt.setString(2, parameter);
        selectStmt.setString(3, unit);
        selectStmt.setString(4, model);
        selectStmt.setString(5, serialNumber);
        selectStmt.setDate(6, deploymentDate);
        selectStmt.setString(7, manufacturer);

        ResultSet rs = selectStmt.executeQuery();
        if (rs.next()) {
            // Device already exists, return its ID
            return rs.getInt("id");
        }
    }

    // Step 2: If not found, insert a new device into LocalDevice
    String insertSql = """
        INSERT INTO LocalDevice (
            name_category, name_parameter, name_unit, model,
            serial_number, deployment_date, manufacturer, install_date
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING id
    """;

    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
        Date installDate = new Date(System.currentTimeMillis()); // current date for install_date

        insertStmt.setString(1, nameCategory);
        insertStmt.setString(2, parameter);
        insertStmt.setString(3, unit);
        insertStmt.setString(4, model);
        insertStmt.setString(5, serialNumber);
        insertStmt.setDate(6, deploymentDate);
        insertStmt.setString(7, manufacturer);
        insertStmt.setDate(8, installDate);

        ResultSet rs = insertStmt.executeQuery();
        if (rs.next()) {
            // Return the ID of the newly inserted device
            return rs.getInt("id");
        }
    }

    // Step 3: Something went wrong — fail
    throw new SQLException("Failed to retrieve or insert into LocalDevice");
}





    /**
     * To insert or not insert category
     *
     * */

   private static int checkAndInsertCategory(Connection conn, String categoryName) throws SQLException {
    String query = "INSERT INTO DeviceCategory (name) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM DeviceCategory WHERE name = ?) RETURNING id";
    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
        pstmt.setString(1, categoryName);
        pstmt.setString(2, categoryName);
        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) return rs.getInt("id");
    }

    String selectQuery = "SELECT id FROM DeviceCategory WHERE name = ?";
    try (PreparedStatement ps = conn.prepareStatement(selectQuery)) {
        ps.setString(1, categoryName);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) return rs.getInt("id");
    }

    throw new SQLException("Error ID from DeviceCategory .");
}


    /**
     *To insert or not insert parameter
     * */

    private static int checkAndInsertParameter(Connection conn, String parameterName, String unit) throws SQLException {
    String query = "INSERT INTO Parameter (name, unit) SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM Parameter WHERE name = ? AND unit = ?) RETURNING id";
    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
        pstmt.setString(1, parameterName);
        pstmt.setString(2, unit);
        pstmt.setString(3, parameterName);
        pstmt.setString(4, unit);
        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) return rs.getInt("id");
    }

    String selectQuery = "SELECT id FROM Parameter WHERE name = ? AND unit = ?";
    try (PreparedStatement ps = conn.prepareStatement(selectQuery)) {
        ps.setString(1, parameterName);
        ps.setString(2, unit);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) return rs.getInt("id");
    }

    throw new SQLException("Error id from parameter");
}


    /**
     * To insert or not insert device
     */
private static int checkAndInsertDevice(Connection conn, int categoryId, int parameterId, String manufacturer,
                                        String model, String serialNumber, int kitId, int localDeviceId,
                                        Date deploymentDate) throws SQLException {
    String query = """
        INSERT INTO Device (
            category_id, parameter_id, model, serial_number, install_date,
            manufacturer, kit_id, local_id_device, deployment_date
        )
        SELECT ?, ?, ?, ?, CURRENT_DATE, ?, ?, ?, ?
        WHERE NOT EXISTS (
            SELECT 1 FROM Device
            WHERE model = ? AND serial_number = ? AND manufacturer = ? AND local_id_device = ? AND kit_id = ?
        )
        RETURNING id
    """;

    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
        // Valeurs pour INSERT
        pstmt.setInt(1, categoryId);
        pstmt.setInt(2, parameterId);
        pstmt.setString(3, model);
        pstmt.setString(4, serialNumber);
        pstmt.setString(5, manufacturer);
        pstmt.setInt(6, kitId);
        pstmt.setInt(7, localDeviceId);
        pstmt.setDate(8, deploymentDate);

        // Valeurs pour WHERE NOT EXISTS
        pstmt.setString(9, model);
        pstmt.setString(10, serialNumber);
        pstmt.setString(11, manufacturer);
        pstmt.setInt(12, localDeviceId);
        pstmt.setInt(13, kitId);

        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) return rs.getInt("id");
    }

    // Fallback si déjà existant
    String selectQuery = """
        SELECT id FROM Device
        WHERE model = ? AND serial_number = ? AND manufacturer = ? AND local_id_device = ? AND kit_id = ?
    """;

    try (PreparedStatement ps = conn.prepareStatement(selectQuery)) {
        ps.setString(1, model);
        ps.setString(2, serialNumber);
        ps.setString(3, manufacturer);
        ps.setInt(4, localDeviceId);
        ps.setInt(5, kitId);

        ResultSet rs = ps.executeQuery();
        if (rs.next()) return rs.getInt("id");
    }

    throw new SQLException("Erreur lors de l'insertion ou de la récupération du device.");
}



  



public void transferLastLocalMeasurementToMain(int kitId) {
   
    // SQL query to get all distinct local device IDs from LocalMeasurement
    String sqlDistinctDevices = "SELECT DISTINCT id_device FROM LocalMeasurement";

    // SQL query to get the most recent measurement for a given device
    String sqlLastMeasurement = """
        SELECT timestamp, value
        FROM LocalMeasurement
        WHERE id_device = ?
        ORDER BY timestamp DESC
        LIMIT 1
    """;

    // SQL query to find the corresponding device ID in the main database
    String sqlFindMainDevice = """
        SELECT id
        FROM Device
        WHERE local_id_device = ? AND kit_id = ?
    """;

    // SQL query to insert the measurement into the main Measurement table
    String sqlInsert = """
        INSERT INTO Measurement (timestamp, device_id, value)
        VALUES (?, ?, ?)
    """;

    try (
        // Open connections to both local and main databases
        Connection connLocal = getLocalConnection();
        Connection connMain = getMainConnection();
        // Prepare and execute the query to fetch distinct device IDs from LocalMeasurement
        PreparedStatement psDevices = connLocal.prepareStatement(sqlDistinctDevices);
        ResultSet rsDevices = psDevices.executeQuery()
    ) {
        boolean hasTransferred = false; // Flag to check if at least one transfer was successful

        // Iterate over each local device ID
        while (rsDevices.next()) {
            int localDeviceId = rsDevices.getInt("id_device");

            try (
                // Prepare all statements needed for this device
                PreparedStatement psLast = connLocal.prepareStatement(sqlLastMeasurement);
                PreparedStatement psFind = connMain.prepareStatement(sqlFindMainDevice);
                PreparedStatement psInsert = connMain.prepareStatement(sqlInsert)
            ) {
                // Step 1: Get the latest sensor measurement from the local database
                psLast.setInt(1, localDeviceId);
                try (ResultSet rsLast = psLast.executeQuery()) {
                    if (!rsLast.next()) continue; // Skip if no measurement is found

                    Timestamp timestamp = rsLast.getTimestamp("timestamp");
                    double value = rsLast.getDouble("value");

                    // Step 2: Find the corresponding device ID in the main database
                    psFind.setInt(1, localDeviceId);
                    psFind.setInt(2, kitId);
                    try (ResultSet rsFind = psFind.executeQuery()) {
                        if (!rsFind.next()) {
                            // Skip if no matching device is found in the main DB
                            System.out.printf(
                                "No matching main device found (local_id_device=%d, kit_id=%d)%n",
                                localDeviceId, kitId
                            );
                            continue;
                        }

                        int mainDeviceId = rsFind.getInt("id");

                        // Step 3: Insert the measurement into the main database
                        psInsert.setTimestamp(1, timestamp);
                        psInsert.setInt(2, mainDeviceId);
                        psInsert.setDouble(3, value);

                        int affected = psInsert.executeUpdate();
                        if (affected > 0) {
                            // Log success
                            System.out.printf(
                                "Measurement transferred: %.2f (Local ID %d → Main ID %d)%n",
                                value, localDeviceId, mainDeviceId
                            );
                            hasTransferred = true;
                        }
                    }
                }
            }
        }

        // If no measurements were transferred, notify
        if (!hasTransferred) {
            System.out.println("No measurements were transferred.");
        }

    } catch (SQLException e) {
        // Catch and print any SQL errors during the process
        System.err.println("SQL error during measurement transfer:");
        e.printStackTrace();
    }
}







}

