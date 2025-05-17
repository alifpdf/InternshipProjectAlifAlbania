package finalagent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

import java.sql.*;
import java.time.Instant;


public class SensorAgent extends Agent {
    private Integer idKit;
    private String nextAgent;
    private String[] listagent;
    private Boolean lastegent;


    @Override
    protected void setup() {
        Object[] args = getArguments();

        nextAgent = (String) args[0];
        listagent = (String[]) args[1];
        String localName = getLocalName().toLowerCase(); // ex: z1
        idKit = Integer.parseInt(localName.
                replaceAll("[^0-9]", ""));


        lastegent = (Boolean) args[2];
        nextagent();


        if (getLocalName().equals(listagent[0])) {
            addBehaviour(new WakerBehaviour(this, 2000) {
                protected void onWake() {
                    sendToken();
                }
            });
        }

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();

                if (msg != null && msg.getContent().equals("TOKEN")) {
                    System.out.println(getLocalName() + " start");
                    nextagent();

                    if(lastegent){
                        if(!isKitCountEqualToAgents(listagent)){
                            addAnotherAgent();
                            notifynewPeerrAgent();
                        }

                    }

                    // Checking sensor
                    if (!hasSensorInDatabase(idKit)) {
                        System.out.println("Agent " + getLocalName() + " has no sensor. It will pass the token and delete itself.");

                        // Supprimer le kit de la base de données
                        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "1234");
                             PreparedStatement ps = conn.prepareStatement("DELETE FROM Kit WHERE id = ?")) {
                            ps.setInt(1, idKit);
                            int deleted = ps.executeUpdate();
                            if (deleted > 0) {
                                System.out.println("Kit ID " + idKit + " deleted from database.");
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }

                        notifyPeersAgentDead();  // Notify other agents that this one is dead
                        sendToken();             // Pass the token to the next agent
                        doDelete();              // Terminate this agent
                        return;
                    }





                    SequentialBehaviour sequential = new SequentialBehaviour();

                    // Step 1 - Local measure and save it to database
                    sequential.addSubBehaviour(new OneShotBehaviour() {
                        public void action() {
                            System.out.println(getLocalName() + " local sensor...");
                            saveMeasurementToDatabase();
                        }
                    });

                    // Step 2 - To request to others agent
                    ParallelBehaviour askPeers = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
                    for (String peer : listagent) {
                        if (!peer.equals(getLocalName())) {
                            askPeers.addSubBehaviour(new OneShotBehaviour() {
                                public void action() {
                                    ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                                    req.addReceiver(new AID(peer, AID.ISLOCALNAME));
                                    req.setContent("DONNEE?");
                                    send(req);
                                    System.out.println(getLocalName() + " request to " + peer);
                                }
                            });
                        }
                    }
                    sequential.addSubBehaviour(askPeers);

                    // Step 3 - To give the token to the next agent
                    sequential.addSubBehaviour(new WakerBehaviour(myAgent, 3000) {
                        protected void onWake() {
                            sendToken();
                        }
                    });

                    addBehaviour(sequential);
                }

                //Each agent is linked to a kit that sends sensor data to the sending agent
                else if (msg != null && msg.getPerformative() == ACLMessage.REQUEST) {
                    String result = getLastMeasurementsByKit();
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent(result);
                    send(reply);
                }

                //To update list of agent if one agent died
                else if (msg != null && msg.getPerformative() == ACLMessage.INFORM &&
                        msg.getContent().startsWith("DEAD_AGENT:")) {

                    String deadAgent = msg.getContent().split(":")[1];
                    System.out.println(deadAgent + " is dead. Updating peers (order preserved).");

                    // To delete in saving the order
                    java.util.List<String> updatedPeers = new java.util.ArrayList<>();
                    for (String p : listagent) {
                        if (!p.equals(deadAgent)) {
                            updatedPeers.add(p);
                        }
                    }

                    listagent = updatedPeers.toArray(new String[0]);


                }
                // Update the agent list by adding a new agent if a kit is added to the database
                else if (msg != null && msg.getPerformative() == ACLMessage.INFORM &&
                        msg.getContent().startsWith("NEW_AGENT")) {
                    String URL = "jdbc:postgresql://localhost:5432/postgres";
                    String USER = "postgres";
                    String PASSWORD = "1234";

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

                        }

                } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                // Read the message sent by other agents from the sending agent
                else if (msg != null && msg.getPerformative() == ACLMessage.INFORM) {
                    System.out.println(getLocalName() + " reads : " + msg.getContent());
                }

                else if (msg != null && msg.getContent().equals("STOP")) {
                    System.out.println(getLocalName() + " receive the order to delete itself.");
                    doDelete();
                }

                else {
                    block();
                }
            }
        });

    }

    private void sendToken() {
        ACLMessage token = new ACLMessage(ACLMessage.INFORM);
        token.setContent("TOKEN");
        token.addReceiver(new AID(nextAgent, AID.ISLOCALNAME));
        send(token);
        System.out.println(getLocalName() + " gives token to " + nextAgent);
    }
    private String getLastMeasurementsByKit() {
        StringBuilder response = new StringBuilder("📊 Measures from kit ID = " + idKit + " :\n");
        String URL = "jdbc:postgresql://localhost:5432/postgres";
        String USER = "postgres";
        String PASSWORD = "1234";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (PreparedStatement ps = conn.prepareStatement("""
            SELECT d.serial_number, m.value, m.timestamp
            FROM Measurement m
            JOIN DeviceLocation dl ON m.device_location_id = dl.id
            JOIN Device d ON dl.device_id = d.id
            WHERE dl.kit_id = ?
            AND m.timestamp = (
                SELECT MAX(m2.timestamp)
                FROM Measurement m2
                WHERE m2.device_location_id = dl.id
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



    private void saveMeasurementToDatabase() {
        String URL = "jdbc:postgresql://localhost:5432/postgres";
        String USER = "postgres";
        String PASSWORD = "1234";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {

            try (PreparedStatement ps = conn.prepareStatement("""
            SELECT dl.id, d.serial_number FROM DeviceLocation dl
            JOIN Device d ON dl.device_id = d.id
            WHERE dl.kit_id = ?
        """)) {
                ps.setInt(1, idKit);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    int deviceLocationId = rs.getInt("id");
                    String serialNumber = rs.getString("serial_number");

                    // Valeur aléatoire entre 1 et 10
                    double value = 1 + Math.random() * 9;

                    try (PreparedStatement insert = conn.prepareStatement("""
                    INSERT INTO Measurement (timestamp, device_location_id, value)
                    VALUES (?, ?, ?)
                """)) {
                        insert.setTimestamp(1, Timestamp.from(Instant.now()));
                        insert.setInt(2, deviceLocationId);
                        insert.setDouble(3, value);
                        insert.executeUpdate();
                        System.out.println("data saved by " + serialNumber + " with value=" + value);
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    private boolean hasSensorInDatabase(Integer idKit) {
        String URL = "jdbc:postgresql://localhost:5432/postgres";
        String USER = "postgres";
        String PASSWORD = "1234";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String sql = """
            SELECT 1 FROM DeviceLocation dl
            JOIN Device d ON dl.device_id = d.id
            WHERE dl.kit_id = ?
            LIMIT 1
        """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1,idKit);
                ResultSet rs = ps.executeQuery();
                return rs.next(); // true s’il y a au moins une ligne
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    private void notifyPeersAgentDead() {
        ACLMessage deathNotice = new ACLMessage(ACLMessage.INFORM);
        deathNotice.setContent("DEAD_AGENT:" + getLocalName());
        for (String peer : listagent) {
            if (!peer.equals(getLocalName())) {
                deathNotice.addReceiver(new AID(peer, AID.ISLOCALNAME));
            }
        }
        send(deathNotice);// Send the notification message to peers
        System.out.println(getLocalName() + " has notified others of its termination.");

    }

    private void notifynewPeerrAgent() {
        ACLMessage Notice = new ACLMessage(ACLMessage.INFORM);
        Notice.setContent("NEW_AGENT");
        for (String peer : listagent) {
            if (!peer.equals(getLocalName())) {
                Notice.addReceiver(new AID(peer, AID.ISLOCALNAME));
            }
        }
        send(Notice);// Send the notification message to peers
        System.out.println(getLocalName() + " has notified others of its termination.");

    }

    private void nextagent(){
        String current = getLocalName();
        int index=0;
        for (int i = 0; i < listagent.length; i++) {
            if (listagent[i].equals(current)) {
                index = i;
                break;
            }
        }
        String expectedNext = listagent[(index + 1) % listagent.length];
        if (!nextAgent.equals(expectedNext)) {
            System.out.println("new nextAgent: " + nextAgent + " -> " + expectedNext);
            nextAgent = expectedNext;
        }
    }


    public boolean isKitCountEqualToAgents(String[] listagent) {
        String URL = "jdbc:postgresql://localhost:5432/postgres";
        String USER = "postgres";
        String PASSWORD = "1234";

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

    public void addAnotherAgent() {
        String URL = "jdbc:postgresql://localhost:5432/postgres";
        String USER = "postgres";
        String PASSWORD = "1234";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement("SELECT MAX(id) FROM Kit");
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                int lastkit_id = rs.getInt(1);
                String newAgentName = "z" + lastkit_id;

                // To update the list of agent
                listagent = new String[lastkit_id];
                for (int i = 0; i < lastkit_id; i++) {
                    listagent[i] = "z" + (i + 1);
                }
                nextAgent = newAgentName;
                lastegent = false;

                // To check if the agent exist
                if (getContainerController().getAgent(newAgentName) != null) {
                    System.out.println("Agent " + newAgentName + " already exists.");
                    return;
                }

                //To create new agent and to start it
                Object[] agentArgs = {listagent[0], listagent, true};
                AgentController newAgent = getContainerController().createNewAgent(
                        newAgentName, "finalagent.SensorAgent", agentArgs);
                newAgent.start();

                System.out.println("Agent " + newAgentName + " added.");
            }

        } catch (SQLException | StaleProxyException e) {
            e.printStackTrace();
        } catch (ControllerException e) {
            throw new RuntimeException(e);
        }
    }






}
