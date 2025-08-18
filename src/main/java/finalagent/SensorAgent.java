package finalagent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import jade.domain.FIPAException;



import java.util.ArrayList;
import java.util.List;




import java.util.*;


public class SensorAgent extends Agent {

    private boolean isTokenProcessing = false;



    // The next agent in the ring to send the token to
    private String nextAgent;

    // List of all known agents excluding blacklisted ones
    private List<String> listAgent = new ArrayList<>();

    // Agents that have been marked as unresponsive
    private Set<String> blacklist = new HashSet<>();


    // All agents this agent has ever seen
    private Set<String> knownAgents = new HashSet<>();

    // Local sensor device IDs associated with this agent
    private Map<String, Integer> localDevice = new HashMap<>();

    // List storing comparison results with other agents
    List<double[]> compareList = new ArrayList<>();

    // Set to store which agents have already sent their Difference
    private Set<String> respondedAgents = new HashSet<>();


    private long lastTokenSentTime = 0;

    // Static coordinates shared among agents
    public static double xFromKit;
    public static double yFromKit;
    public static double xaFromKit;
    public static double yaFromKit;

    // Unique identifier for this kit/agent
    private int idKit;

    // Database access object
    private AddDB addDB;






    @Override
    protected void setup() {

        // Initialize database handler
        addDB = new AddDB();



        try {

            // Create a new description for this agent
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());  // Set the agent's AID (unique ID)

            // Define the service this agent provides
            ServiceDescription sd = new ServiceDescription();
            sd.setType("sensor");  // General type of the service
            sd.setName("SensorAgent-" + getLocalName());  // Specific name for the service

            dfd.addServices(sd);  // Attach the service to the agent description

            // Register the agent and its services with the DF (Directory Facilitator)
            DFService.register(this, dfd);

            // Confirmation message
            System.out.println(getLocalName() + " registered with DF.");

        } catch (FIPAException e) {
            // If registration fails, show the error
            System.err.println("Failed to register agent with DF: " + e.getMessage());
            e.printStackTrace();
        }


// Retrieve arguments passed to the agent (mainContainerFlag, kitId)
        Object[] args = getArguments();
        idKit = (Integer) args[1];
        xFromKit=  addDB.round2( (Double)args[2]);
        yFromKit=addDB.round2((Double)args[3]);
        xaFromKit=addDB.round2((Double)args[2]);
        yaFromKit=addDB.round2((Double)args[3]);


// Main container logic: truncate DB, setup kit and devices, delay start
        if((boolean)args[0]){
            

            addDB.resetLocalTables();
            addDB.addKit(xFromKit, yFromKit, idKit);
             localDevice=addDB.getLocalDeviceIdsFromArduino();
            addDB.insertLocalDevicesToMain(idKit);
            addDB.arduino(localDevice,xaFromKit,yaFromKit);
    

// Delayed behavior to start after setup
            addBehaviour(new WakerBehaviour(this, 30_000) {
                @Override
                protected void onWake() {
                    System.out.println(getLocalName() + " starts after 30s delay");
                    
                    /*addDB.insertSecondContainer();
                    addDB.insertLocalDevicesToMain(idKit);
                    addDB.saveMeasurementToDatabase(xaFromKit,yaFromKit);*/
                    
           
            
                    
                    updateAgentList();
                    nextagent();
                    sendToken();
                }
            });





        }else{
             addDB.resetLocalTables();
            // For other containers, only setup kit and update agent list
            addDB.addKit(xFromKit, yFromKit, idKit);
            addDB.insertSecondContainer();
            addDB.insertLocalDevicesToMain(idKit);
            addDB.saveMeasurementToDatabase(xaFromKit,yaFromKit);
            
            updateAgentList();
            nextagent();

        }





        addBehaviour(new TickerBehaviour(this, 10_000) { // Check every 10s
            @Override
            protected void onTick() {
                // If more than 2 minutes passed since last token
                if (System.currentTimeMillis() - lastTokenSentTime > 120_000 && (boolean)args[0]) {
                    System.out.println(getLocalName() + " - No TOKEN sent for 2 minutes. Triggering resend...");
                    sendToken();
                }
            }
        });




// Periodically ping all known agents to check if they are alive
        addBehaviour(new TickerBehaviour(this, 30_000) {
            protected void onTick() {
                if ((boolean) args[0]) {
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, buildSensorSearchTemplate());
                        for (DFAgentDescription desc : result) {
                            String agentName = desc.getName().getLocalName();

                            // Exclude blacklisted agents and self
                            if (!agentName.equals(getLocalName())) {

                                // Try to ping the agent to check if it's alive
                                ACLMessage ping = new ACLMessage(ACLMessage.REQUEST);
                                ping.setConversationId("ping");

                                ping.addReceiver(new AID(agentName, AID.ISLOCALNAME));
                                try {
                                    send(ping);  // Send the ping message
                                    System.out.println(getLocalName() + " sent PING to " + agentName);
                                } catch (Exception e) {
                                    // If the send fails, blacklist the agent immediately
                                    System.err.println(getLocalName() + " - Failed to send ping to " + agentName + ": " + e.getMessage());
                                    blacklistAgentAndNotify(agentName);  // Important: avoid retrying unreachable agents
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();  // Handle DFService or runtime errors
                    }
                }
            }
        });










// Every 30 seconds: update local position and simulate sensor readings
        addBehaviour(new TickerBehaviour(this, 30_000) {
            @Override
            protected void onTick() {
                System.out.println("Local position moving from "+getLocalName());

                addDB.updateLocalValidPoint(); // Updating coordinate (xa, ya)


                System.out.println(getLocalName() + " is saving local measure...");
               // addDB.saveMeasurementToDatabase(xaFromKit,yaFromKit);

                addDB.arduino(localDevice,xaFromKit,yaFromKit);
            }
        });

        // Every 100 seconds: transfer local measurements to main database
        addBehaviour(new TickerBehaviour(this, 100_000) {
            @Override
            protected void onTick() {
                System.out.println("Local measurement to global measurement from "+getLocalName());
                addDB.transferLastLocalMeasurementToMain(idKit);

            }
        });





        // Handles all incoming messages and reacts based on their conversation ID
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();


                if (msg == null) {
                    block();
                    return;
                }



                String content = msg.getContent();
                int performative = msg.getPerformative();
                String conversationId= msg.getConversationId();

                try {
                    //if the message is ping

                    if (content != null && content.contains("MTS-error") && content.contains("Agent not found")) {
                        String failedAgent = extractAgentFromMtsError(content);  // implement this helper
                        System.err.println(getLocalName() + " - Detected unreachable agent: " + failedAgent + " at blacklisting.");
                        blacklistAgentAndNotify(failedAgent);
                    }




                    if ("ping".equals(conversationId)) {
                        String sender = msg.getSender().getLocalName();
                        //Ignore messages from AMS
                        if ("ams".equalsIgnoreCase(sender)) {
                            System.out.println(getLocalName() + " - Ignoring ping from AMS.");
                            return;
                        }



                        // Add to known agents if it's the first time we see them
                        boolean isNewAgent = knownAgents.add(sender);
                        if (isNewAgent) {
                            System.out.println(getLocalName() + " discovered new agent via ping: " + sender);
                        }


                        System.out.println(getLocalName() + " confirms " + sender + " is alive.");


                        // Refresh agent list and next agent logic
                        updateAgentList();
                        nextagent();

                        // Reply with PONG
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setConversationId("pong");
                        send(reply);
                        System.out.println(getLocalName() + " sent PONG to " + sender);
                    }


                    else if ("pong".equals(conversationId)) {
                        String sender = msg.getSender().getLocalName();


                        // Check if agent was previously blacklisted
                        boolean wasBlacklisted = blacklist.remove(sender);

                        // Add to known agents if new
                        boolean isNewAgent = knownAgents.add(sender);  // returns true if newly added

                        if (wasBlacklisted) {
                            System.out.println(getLocalName() + " removed " + sender + " from blacklist (via PONG).");

                            // Notify other agents of whitelist update
                            ACLMessage notif = new ACLMessage(ACLMessage.INFORM);
                            notif.setConversationId("whitelist");
                            notif.setContent(sender);

                            for (String peer : listAgent) {
                                if (!peer.equals(getLocalName()) && !"ams".equalsIgnoreCase(peer)) {
                                    notif.addReceiver(new AID(peer, AID.ISLOCALNAME));
                                }
                            }

                            send(notif);
                        }

                        if (isNewAgent) {
                            System.out.println(getLocalName() + " discovered new agent via pong: " + sender);
                        }

                        System.out.println(getLocalName() + " received PONG from " + sender);


                        updateAgentList();
                        nextagent();
                      


                    }



                    //to blacklist the unworked agent

                    else if ("blacklist".equals(conversationId)) {
                        String agentToBlacklist = content.trim();
                        String senderAgent = msg.getSender().getLocalName();
                        System.out.println(getLocalName() + " received BLACKLIST notification from " + senderAgent);

                        if (!blacklist.contains(agentToBlacklist)) {
                            blacklist.add(agentToBlacklist);
                            updateAgentList();
                            nextagent();

                            System.out.println(getLocalName() + " added " + agentToBlacklist + " to its blacklist (via message).");
                        } else {
                            System.out.println(getLocalName() + " already had " + agentToBlacklist + " in its blacklist.");
                        }
                    }

                    else if ("whitelist".equals(conversationId)) {
                        String agentToUnblock = content.trim();
                        String senderAgent = msg.getSender().getLocalName();

                        System.out.println(getLocalName() + " received WHITELIST notification from " + senderAgent + " for " + agentToUnblock);

                        if (blacklist.contains(agentToUnblock)) {
                            blacklist.remove(agentToUnblock);
                            updateAgentList();   // Refresh after change
                            nextagent();
                            System.out.println(getLocalName() + " removed " + agentToUnblock + " from blacklist (via WHITELIST).");
                        } else {
                            System.out.println(getLocalName() + " already had " + agentToUnblock + " unblocked.");
                        }

                    }






                    // -- TOKEN Reception Block --
                    else if ("TOKEN".equals(conversationId)) {
                        if (isTokenProcessing) {
                            System.out.println(getLocalName() + " - Already processing a TOKEN, ignoring.");
                            return;
                        }
                        isTokenProcessing = true;
                       
                        System.out.println(getLocalName() + " received TOKEN, scheduling start in 20 sec...");


// Wait 50 seconds before executing the sequence
                        addBehaviour(new WakerBehaviour(myAgent, 20_000) {
                            protected void onWake() {
                                System.out.println(getLocalName() + " starts execution after 20 sec delay");
                                 // Clear previous comparison results
                        addDB.TruncateLocalMeasurementOnly();
                        compareList.clear();
                        respondedAgents.clear();

                                addDB.arduino(localDevice,xaFromKit,yaFromKit);
                                //addDB.saveMeasurementToDatabase(xaFromKit,yaFromKit);

                                SequentialBehaviour sequential = new SequentialBehaviour();
                                // Step 1: Update agent list and determine next agent
                                sequential.addSubBehaviour(new OneShotBehaviour() {
                                    public void action() {
                                        updateAgentList();
                                        nextagent();
                                    }
                                });


// Step 2: Send local measurements to peers
                                sequential.addSubBehaviour(new OneShotBehaviour() {
                                    @Override
                                    public void action() {
                                        try {
                                            // Retrieve latest measurements
                                            String data = addDB.getLastTemperatureWithCoordinates(xaFromKit, yaFromKit); // "x,y,temp"
                                            String data1 = addDB.getLastPHWithCoordinates(xaFromKit, yaFromKit);         // "x,y,ph"
                                            Map<Integer, String> others = addDB.getLastOtherMeasurementsWithCoordinates(xaFromKit, yaFromKit);

                                            String[] parts = data != null ? data.split(",") : new String[0];
                                            String[] partsPh = data1 != null ? data1.split(",") : new String[0];

                                            if (parts.length >= 3 && partsPh.length >= 3) {
                                                String temperature = parts[2];
                                                String ph = partsPh[2];

                                                // Build other sensor values as id=value|...
                                                StringBuilder othersString = new StringBuilder();
                                                for (Map.Entry<Integer, String> entry : others.entrySet()) {
                                                    String[] valParts = entry.getValue().split(",");
                                                    if (valParts.length >= 3) {
                                                        String val = valParts[2];
                                                        othersString.append(entry.getKey()).append("=").append(val).append("|");
                                                    }
                                                }
                                                if (!othersString.isEmpty()) {
                                                    othersString.setLength(othersString.length() - 1); // remove last '|'
                                                }

                                                // Send measurement to all other agents
                                                for (String peer : listAgent) {
                                                    if (peer != null && !peer.equals(getLocalName()) && !blacklist.contains(peer)) {
                                                        try {
                                                            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                                                            msg.addReceiver(new AID(peer, AID.ISLOCALNAME));
                                                            msg.setConversationId("Compare_Data");

                                                            String payload = String.format(Locale.US, "%s,%s,%s,%s",
                                                                    temperature, ph, getLocalName(), othersString.toString());
                                                            msg.setContent(payload);

                                                            send(msg);
                                                            System.out.println("Data sent to " + peer + " : " + msg.getContent());
                                                        } catch (Exception e) {
                                                            System.err.println(getLocalName() + " - Error sending Compare_Data to " + peer + ": " + e.getMessage());
                                                            blacklistAgentAndNotify(peer);
                                                        }
                                                    }
                                                }
                                            } else {
                                                System.err.println(getLocalName() + " - Invalid sensor data format (temperature or pH missing).");
                                            }

                                        } catch (Exception e) {
                                            System.err.println("Error in Compare_Data sending: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    }
                                });


                                // Step 3: After collecting responses, compute max difference and send move command
                                sequential.addSubBehaviour(new Behaviour() {

                                    private long startTime = System.currentTimeMillis();
                                    private final long TIMEOUT_MS = 20_000; // 20 seconds

                                    @Override
                                    public void action() {
                                        boolean allResponses = respondedAgents.size() >= listAgent.size();

                                        boolean timeoutReached = (System.currentTimeMillis() - startTime) >= TIMEOUT_MS;

                                        if (allResponses || timeoutReached) {
                                            if (allResponses) {
                                                System.out.println(getLocalName() + " - All responses received ("
                                                        + compareList.size() + "/" + listAgent.size() + ")");
                                            } else {
                                                System.out.println(getLocalName() + " - Timeout reached after "
                                                        + (TIMEOUT_MS / 1000) + "s ("
                                                        + compareList.size() + "/" + listAgent.size() + " responses)");
                                            }

                                            // Find the entry with the maximum absolute difference
                                            double[] maxDiffEntry = null;
                                            double maxDiff = 0;
                                            for (double[] entry : compareList) {
                                                double diff = entry[2];
                                                if (Math.abs(diff) >= Math.abs(maxDiff)) {
                                                    maxDiff = diff;
                                                    maxDiffEntry = entry;
                                                }
                                            }

                                            // Build a set of unique coordinates in the format "x:y"
                                            Set<String> coordinatesSet = new LinkedHashSet<>();
                                            for (double[] entry : compareList) {
                                                coordinatesSet.add(addDB.round2(entry[4]) + ":" + addDB.round2(entry[5]));
                                            }
                                            coordinatesSet.add(xFromKit + ":" + yFromKit); 
                                            String coordBuilder = String.join("|", coordinatesSet);

                                            if (maxDiffEntry != null) {
                                                double targetX = addDB.round2(maxDiffEntry[0]);
                                                double targetY = addDB.round2(maxDiffEntry[1]);
                                                int kitId = (int) maxDiffEntry[3];
                                                String targetAgent = "Z" + kitId;

                                                // Prepare MOVE message content
                                                String content = String.format(Locale.US, "%2f,%2f,%s",
                                                        targetX, targetY, coordBuilder);

                                                // Send the MOVE request to the target agent
                                                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                                                msg.setConversationId("Moving data");
                                                msg.addReceiver(new AID(targetAgent, AID.ISLOCALNAME));
                                                msg.setContent(content);
                                                send(msg);
                                                
                                                System.out.println(String.format(
                                                        "%s intends to move to x=%.2f, y=%.2f - a point within the area of %s. " +
                                                        "It requests %s to select a location far from previously visited positions: [%s], including its own usual zone.",
                                                        getLocalName(), targetX, targetY, targetAgent, targetAgent, coordBuilder
                                                    ));
                                                
                                                // After 20 seconds, update local coordinates and send the token
                                                myAgent.addBehaviour(new WakerBehaviour(myAgent, 20_000) {
                                                    protected void onWake() {
                                                        xFromKit=targetX;
                                                        yFromKit=targetY;
                                                        xaFromKit=targetX;
                                                        yaFromKit=targetY;
                                                        addDB.updateKitCoordinates(idKit,xFromKit,yFromKit);
                                                        System.out.printf("Kit updated to (%.2f, %.2f)%n", targetX, targetY);
                                                        addDB.TruncateLocalMeasurementOnly();
                                                        // addDB.saveMeasurementToDatabase(xaFromKit,yaFromKit);
                                                        addDB.arduino(localDevice,xaFromKit,yaFromKit);
                                                        updateAgentList();
                                                        nextagent();
                                                        sendToken();
                                                        isTokenProcessing = false;
                                                    }
                                                });
                                            } else {
                                                System.out.println("No difference found. No move needed.");
                                                updateAgentList();
                                                nextagent();
                                                sendToken();
                                                isTokenProcessing = false;
                                            }

                                            stop(); // End this Behaviour
                                        }
                                    }

                                    @Override
                                    public boolean done() {
                                        // Finish when all responses are received OR when the timeout is reached
                                        return respondedAgents.size() >= listAgent.size()
                                                || (System.currentTimeMillis() - startTime) >= TIMEOUT_MS;

                                    }
                                });




                                // Start the sequential behavior
                                addBehaviour(sequential);
                            }
                        });
                    }




                    else if ("Compare_Data".equals(conversationId)) {

                        // Split the message content into up to 4 parts: temperature, pH, senderAgent, and other sensor values
                        String[] parts = content.split(",", 4);
                        if (parts.length < 3) {
                            System.out.println("Invalid format: " + content);
                            return; // Message doesn't have the expected minimum fields
                        }
                        addDB.arduino(localDevice,xaFromKit,yaFromKit);
                        //addDB.saveMeasurementToDatabase(xaFromKit,yaFromKit);

                        try {
                            // Parse temperature, pH, and the sender agent's name
                            double temperature = Double.parseDouble(parts[0]);
                            double ph = Double.parseDouble(parts[1]);
                            String senderAgent = parts[2];

                            Map<Integer, Double> otherValues = new HashMap<>();

                            // If there are additional sensor values (e.g., "3=5.2|4=6.1"), parse them into a map
                            if (parts.length == 4 && !parts[3].isEmpty()) {
                                for (String item : parts[3].split("\\|")) {
                                    String[] keyVal = item.split("=");
                                    if (keyVal.length == 2) {
                                        otherValues.put(
                                                Integer.parseInt(keyVal[0]),
                                                Double.parseDouble(keyVal[1])
                                        );
                                    }
                                }
                            }

                            // Call the comparison method to determine if this agent's local values show a stronger difference
                            double[] localResult = AddDB.KitMeasureTemperatureCompare(temperature, ph, otherValues);

                            if (localResult != null && localResult.length == 3) {
                                // Prepare and send a response message with the result
                                ACLMessage reply = msg.createReply();
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setConversationId("Difference");

                                // Format: Difference,xa,ya,diff,idKit,xFromKit,yFromKit
                                reply.setContent(String.format(Locale.US, "Difference,%.2f,%.2f,%.2f,%d,%.1f,%.2f",
                                        localResult[0], localResult[1], localResult[2], idKit, xFromKit, yFromKit));
                                send(reply);

                                System.out.printf(
                                        "Data received from %s with Δ max at (%.2f, %.2f), Δ = %.2f%n",
                                        senderAgent, localResult[0], localResult[1], localResult[2]
                                );

                            } else {
                                System.out.println("No matching local position found for comparison.");
                            }

                        } catch (NumberFormatException e) {
                            System.out.println("Format error in Compare_Data: " + e.getMessage());
                        }
                    }




                    else if ("Difference".equals(conversationId)) {
                        String[] parts = content.split(",");
                        if (parts.length == 7 && parts[0].equals("Difference")) {

                            double xa = addDB.round2(Double.parseDouble(parts[1].trim()));
                            double ya = addDB.round2(Double.parseDouble(parts[2].trim()));
                            double diff = Double.parseDouble(parts[3].trim());
                            int kitId = Integer.parseInt(parts[4].trim());
                            double x = addDB.round2(Double.parseDouble(parts[5].trim()));
                            double y = addDB.round2(Double.parseDouble(parts[6].trim()));

                            String senderAgent = "Z" + kitId;

                            // Ignore duplicate responses from the same agent
                            if (!respondedAgents.contains(senderAgent)) {
                                respondedAgents.add(senderAgent);
                                compareList.add(new double[]{xa, ya, diff, kitId, x, y});
                                System.out.printf(
                                        "Result received from kit ID %d : Δ = %.2f°C at (%.2f, %.2f)%n",
                                        kitId, diff, x, y
                                );
                            } else {
                                System.out.println(getLocalName() + " - Duplicate Difference from " + senderAgent + " ignored.");
                            }

                        } else {
                            System.out.println("Bad format for 'Difference' received : " + content);
                        }
                    }




                    // Calculating the distance between the sending agent and the receiving agent
                    else if ("Moving data".equals(conversationId) && performative == ACLMessage.REQUEST) {


                        String senderAgent = msg.getSender().getLocalName();
                        System.out.println(getLocalName() + " received MOVE request from " + senderAgent + " with content: " + content);

                        // Expecting format: targetX, targetY, x1:y1|x2:y2|...
                        String[] parts = content.split(",", 3);  // Only 3 expected parts

                        if (parts.length == 3) {
                            try {
                                double targetX = addDB.round2(Double.parseDouble(parts[0].trim()));
                                double targetY = addDB.round2(Double.parseDouble(parts[1].trim()));
                                String coordsStr = parts[2].trim();

                                List<double[]> receivedCoordinates = new ArrayList<>();

                                // Parse each coordinate pair from the path string
                                for (String pair : coordsStr.split("\\|")) {
                                    String[] xy = pair.split(":");
                                    if (xy.length == 2) {
                                        try {
                                            double x = addDB.round2(Double.parseDouble(xy[0].trim()));
                                            double y = addDB.round2(Double.parseDouble(xy[1].trim()));
                                            receivedCoordinates.add(new double[]{x, y});
                                        } catch (NumberFormatException e) {
                                            System.err.println(getLocalName() + " - Invalid coordinate in path: " + pair);
                                        }
                                    }
                                }

                                // Generate a new valid point near the target, avoiding previous positions
                                double[] newPoint = AddDB.generateSingleValidPoint(targetX, targetY, receivedCoordinates);

                                // Update this agent's coordinates in the database
                                addDB.updateLocalCoordinates(idKit, addDB.round2(newPoint[0]), addDB.round2(newPoint[1]));

                                System.out.printf("Kit %d moved to (%.2f, %.2f)%n", idKit, newPoint[0], newPoint[1]);
                                addDB.TruncateLocalMeasurementOnly();
                                // addDB.saveMeasurementToDatabase(xaFromKit,yaFromKit);
                                addDB.arduino(localDevice,xaFromKit,yaFromKit);

                            } catch (NumberFormatException e) {
                                System.err.println(getLocalName() + " - Invalid numeric value in Moving data: " + content);
                            }
                        } else {
                            System.err.println(getLocalName() + " - Invalid format for Moving data: " + content);
                        }

                        // Send acknowledgment to the sender
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setConversationId("Moving data");

                        reply.setContent("Moving done");
                        send(reply);
                    }
                    else if ("Moving data".equals(conversationId) && performative == ACLMessage.INFORM) {
                        String senderAgent = msg.getSender().getLocalName();
                        if ("Moving done".equalsIgnoreCase(content.trim())) {
                            System.out.println(getLocalName() + " received confirmation from " + senderAgent + ": Moving done.");
                        } else {
                            System.out.println(getLocalName() + " received unknown INFORM in Moving data from " + senderAgent + ": " + content);
                        }

                    }

                    else {
                        System.out.println(getLocalName() + " - received null content.");
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });



    }






    /**
     * Builds a DF search template to find agents that offer a "sensor" service.
     * @return A template description for searching sensor-type services.
     */
    private DFAgentDescription buildSensorSearchTemplate() {
        DFAgentDescription template = new DFAgentDescription(); // Agent search template

        ServiceDescription sd = new ServiceDescription();
        sd.setType("sensor"); // We are looking for agents offering "sensor" type service

        template.addServices(sd); // Attach the service type to the search template
        return template;
    }



    /**
     * Sends a "TOKEN" message to the next agent in the list.
     * Used for coordinating actions in a token-passing system among agents.
     */
    private void sendToken() {
        // Update the next agent dynamically before sending the token
        updateAgentList();
        nextagent();

        // Check if the nextAgent is valid
        if (nextAgent == null || nextAgent.trim().isEmpty()) {

            System.err.println(getLocalName() + " - No available agent, TOKEN not sent.");
            return;
        }
        if (blacklist.contains(nextAgent)) {
            System.err.println(getLocalName() + " - Next agent " + nextAgent + " is blacklisted. Skipping token send.");
            return;
        }

        // Create an INFORM message with content "TOKEN"
        ACLMessage token = new ACLMessage(ACLMessage.INFORM);
        token.setConversationId("TOKEN");

        // Set the receiver of the message to the next agent
        token.addReceiver(new AID(nextAgent, AID.ISLOCALNAME));

        // Send the message to the next agent
        try {
            lastTokenSentTime = System.currentTimeMillis();
            send(token);
            // Log the action to the console
            System.out.println(getLocalName() + " gives token to " + nextAgent);
        } catch (Exception e) {
            System.err.println(getLocalName() + " - Error sending TOKEN to " + nextAgent + ": " + e.getMessage());
            // Blacklist the agent if sending the token fails
            blacklistAgentAndNotify(nextAgent);
        }
    }








    /**
     * Updates the list of active agents by querying the Directory Facilitator (DF).
     * Excludes the current agent and any agents in the blacklist.
     */
    private void updateAgentList() {
        try {
            // Search the DF for agents providing a "sensor" service
            DFAgentDescription[] result = DFService.search(this, buildSensorSearchTemplate());

            // Clear the current list before repopulating it
            listAgent.clear();

            // Iterate over the search results
            for (DFAgentDescription agentDesc : result) {
                String name = agentDesc.getName().getLocalName();

                // Add agent to list if it's not self and not in blacklist
                boolean isBlacklisted = blacklist.contains(name);


                if (!name.equals(getLocalName()) && !isBlacklisted && !"ams".equals(name)) {
                    System.out.println(getLocalName() + " sees DF agent: " + name + " (blacklisted=" + isBlacklisted + ")");
                    listAgent.add(name);
                }
            }

            // Display the updated list of agents
            System.out.println(getLocalName() + " - List of agent after update : " + listAgent);

        } catch (Exception e) {
            System.err.println("Error updating listagent from DF: " + e.getMessage());
            e.printStackTrace();
        }
    }



    /**
     * Determines the next agent in the logical ring based on alphabetical order.
     */
    public void nextagent() {
        try {
            List<String> agentNames = new ArrayList<>();

            // Filter out blacklisted agents from the list
            for (String name : listAgent) {

//to check if the agent is not blacklisted

                if (!blacklist.contains(name)) {
                    agentNames.add(name);
                }
            }




            // Ensure the current agent is included in the list
            if (!agentNames.contains(getLocalName())) {
                agentNames.add(getLocalName());
            }

            // Sort the list alphabetically
            Collections.sort(agentNames);

            // Find this agent's position in the list
            int index = agentNames.indexOf(getLocalName());

            // Set the next agent in the ring, or null if alone
            if (agentNames.size() > 1) {
                this.nextAgent = agentNames.get((index + 1) % agentNames.size());
            } else {
                this.nextAgent = null;
            }

            System.out.println(getLocalName() + " nextAgent = " + this.nextAgent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds an agent to the local blacklist and notifies all other agents about it.
     *
     * @param agentName The local name of the agent to blacklist.
     */
    private void blacklistAgentAndNotify(String agentName) {
        // If the agent is already blacklisted, do nothing
        if (blacklist.contains(agentName)) {
            System.out.println(getLocalName() + " - Agent " + agentName + " is already blacklisted.");
            return;
        }

        // Add the agent to the local blacklist
        blacklist.add(agentName);

        // Remove the agent from the current active agents list
        listAgent.remove(agentName);

        // Update the list of agents from the DF and determine the next agent
        updateAgentList();
        nextagent();

        System.err.println(getLocalName() + " - Agent " + agentName + " has been blacklisted.");

        // Create a notification message for other agents about the blacklist update
        ACLMessage notif = new ACLMessage(ACLMessage.INFORM);
        notif.setConversationId("blacklist"); // Identifies the purpose of the message
        notif.setContent(agentName); // The name of the blacklisted agent

        // Send the notification to all active agents except this one
        for (String peer : listAgent) {
            if (!getLocalName().equals(peer)) {
                notif.addReceiver(new AID(peer, AID.ISLOCALNAME));
            }
        }

        // Broadcast the blacklist message
        send(notif);
        System.out.println(getLocalName() + " broadcasted BLACKLIST for " + agentName);
    }



    private String extractAgentFromMtsError(String msg) {
        try {
            // Focus on the part inside (MTS-error ( agent-identifier :name XYZ ...
            int errorBlockStart = msg.indexOf("(MTS-error");
            if (errorBlockStart == -1) return "unknown";

            // Look for :name after MTS-error block start
            int nameStart = msg.indexOf(":name ", errorBlockStart);
            if (nameStart == -1) return "unknown";

            nameStart += ":name ".length();
            int nameEnd = msg.indexOf("@", nameStart);
            if (nameEnd == -1) return "unknown";

            return msg.substring(nameStart, nameEnd);  // Extract only the local name (before @)
        } catch (Exception e) {
            return "unknown";
        }
    }




}
