# InternshipProjectAlifAlbania
# FinalAgent
## Description
**FinalAgent** is a Java application (based on **JADE**) that orchestrates “sensor” agents on a local network and ingests measurements coming from an **Arduino** via the serial port.
Two levels of databases are used:
- **Local database**: temporarily stores the devices and measurements of the kit.
- **Global database**: centralizes all the data collected by every kit.
The project includes:
- `Main.java` : entry point that creates a JADE container (main or secondary) and launches a `SensorAgent` named `Z<id>`.
- `SensorAgent.java` : the agent that registers with the DF, discovers/coordinates peers, and communicates with the database and the serial port.
- `AddDB.java` : utilities for managing the local and global databases, reading Arduino serial data, registering devices, and inserting measurements.
- `script.sh` : Bash script that compiles, detects a peer on port `60000/tcp` with `nmap`, and automatically restarts the app if it stops.
- `arduino.txt` : Arduino sketch (communication protocol described below).
- `script.sql` : PostgreSQL script that creates the schema of the **global database**.

---
## Requirements
- **JDK 11+**
- **Bash** (Linux/macOS — or Git Bash / WSL on Windows)
- **nmap** (used by `script.sh` for network scanning)
- **PostgreSQL** (local and global databases)
- Java dependencies (place JARs in `lib/`):
  - **JADE** (agent platform)
  - **jSerialComm** (serial communication)
  - **PostgreSQL JDBC driver**

---
```
├── src/main/java/finalagent/
│ ├── Main.java
│ ├── SensorAgent.java
│ └── AddDB.java
├── src/main/resources/
│ └── script.sql # global database schema
├── lib/ # dependencies (jade.jar, jSerialComm.jar, postgresql.jar, …)
├── out/ # compiled classes
├── script.sh # auto build/run + network scan
├── arduino.txt # Arduino sketch (serial communication format)
└── README.md
```

---
## Databases
### Global database
Defined in `src/main/resources/script.sql`.
It contains:
- **DeviceCategory** : categories of devices
- **Parameter** : measured parameters (name, unit)
- **Kit** : kits with coordinates `(x, y)`
- **Device** : devices with metadata (model, serial number, dates, manufacturer, links to Kit/Category/Parameter)
- **Measurement** : timestamped measurements linked to a device
Before creation, all tables are dropped (`DROP TABLE ... CASCADE`).
Sequences (`SERIAL`) are synchronized with `setval`.

### Local database
Created/reset by the Java code (`AddDB`).
It contains:
- **LocalDevice** : devices detected by the kit (category, parameter, unit, model, serial, manufacturer, dates)
- **LocalMeasurement** : timestamped local measurements with coordinates `(x, y)` linked to `LocalDevice`
Tables are automatically recreated at startup.
Sequences `localdevice_id_seq` and `localmeasurement_id_seq` are also reset.
> The local database acts as a buffer: measurements are stored locally before being synchronized to the global database.

---
## Compilation
```bash
javac -cp "lib/*" -d out src/main/java/finalagent/*.java
```

---
## Execution
To run the program directly, you can launch it without a remote peer (main JADE container on localhost:60000 with GUI) using:
```bash
java -cp "out\:lib/*" finalagent.Main <kit_id>
```

If you want to connect to an existing main container (secondary container), use:
```bash
java -cp "out\:lib/*" finalagent.Main <kit_id> <main_container_ip>
```

You can also use the provided script, which requires two mandatory arguments `<X>` and `<Y>` (for example, kit coordinates or specific parameters). First make it executable, then run it:
```bash
chmod +x script.sh
./script.sh <X> <Y>
```

For example:
```bash
./script.sh 0 10
```

The script will recompile the project, retrieve the local IP and use its last octet as the kit ID, scan 192.168.0.100-150 on wlan0 to find a host with port 60000/tcp open, run finalagent.Main with `<id> <X> <Y>` and optionally the discovered IP, and automatically restart after the program exits (infinite loop).

---
## Arduino — serial protocol
The Arduino communicates via the serial port `/dev/ttyUSB0` at 9600 baud. The protocol works as follows: sensors first announce themselves, for example:
```
{Sensor Temperature, Sensor pH}
```

Then they send metadata:
```
[Sensor Temperature, DFRobot, Waterproof DS18B20, TEMP-SN-001, 2024-04-05, 0, °C, temperature] /
[Sensor pH, Atlas Scientific, EZO, PH-SN-002, 2024-04-05, 0, pH, ph]
```

Finally, they send measurements (with the value in field 6):
```
[Sensor Temperature, -, -, -, -, 21.73, °C, temperature] /
[Sensor pH, -, -, -, -, 7.02, pH, ph]
```
