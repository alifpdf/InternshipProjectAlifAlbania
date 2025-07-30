package finalagent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import com.fazecast.jSerialComm.SerialPort;
import jade.domain.FIPAException;



import java.util.ArrayList;
import java.util.List;

import finalagent.AddDB;

import static java.lang.Math.abs;
import static finalagent.AddDB.generateSingleValidPoint;


import java.util.*;


public class SensorAgent extends Agent {


    private String nextAgent;
    private List<String> listAgent = new ArrayList<>();
    private Set<String> blacklist = new HashSet<>();
    private Map<String, Long> lastPingSent = new HashMap<>();
    private Set<String> pingAwaiting = new HashSet<>();
    private Set<String> knownAgents = new HashSet<>();
    
    private Map<String, Integer> localDevice=new HashMap<>();

    List<double[]> compareList = new ArrayList<>();




    private int idKit;

    private AddDB addDB;




    @Override
    protected void setup() {
        // Ajoutez ce comportement dans votre méthode setup


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







        Object[] args = getArguments();
        idKit = (Integer)args[1];

        //agent with main container
        if((boolean)args[0]){
            
            addDB.addKit(0,0,idKit);
            
                
                localDevice=addDB.getLocalDeviceIdsFromArduino();
                addDB.insertLocalDevicesToMain(idKit);
                addDB.arduino(localDevice,0,0);
                
                addDB.TruncateTable();
                //Thread.sleep(30_000); // To wait 30 seconds
                
                addDB.insertLocalDevicesToMain(idKit);
                
                
                updateAgentList();
                nextagent();
                
                //sendToken();
                


//agent with second container
        }else{
            addDB.insertLocalDevicesToMain(idKit);
            updateAgentList();
            nextagent();

        }





        addBehaviour(new TickerBehaviour(this, 30_000) {
            protected void onTick() {
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, buildSensorSearchTemplate());
                    for (DFAgentDescription desc : result) {
                        String agentName = desc.getName().getLocalName();

                        if (!agentName.equals(getLocalName())) {
                            //To test if the agents respond
                            ACLMessage ping = new ACLMessage(ACLMessage.REQUEST);
                            ping.setConversationId("ping");
                            ping.setReplyWith("ping" + System.currentTimeMillis());
                         
                            ping.addReceiver(new AID(agentName, AID.ISLOCALNAME));
                            send(ping);
                            //To put TTL
                            lastPingSent.put(agentName, System.currentTimeMillis());
                            pingAwaiting.add(agentName);
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });




//if one agent doesn't respond, it is blacklisted
        addBehaviour(new TickerBehaviour(this, 5_000) {
            protected void onTick() {
                long now = System.currentTimeMillis();
                Iterator<String> it = pingAwaiting.iterator();
                while (it.hasNext()) {
                    String agent = it.next();
                    long sentTime = lastPingSent.getOrDefault(agent, 0L);
                    if (now - sentTime > 30_000) { // timeout = 10 secondes
                        blacklist.add(agent);
                        updateAgentList();
                        nextagent();

                        System.err.println(agent + " didn't respond to ping. It is blacklisted.");


                        ACLMessage notif = new ACLMessage(ACLMessage.INFORM);
                        notif.setContent("blacklist:" + agent);


                        for (String peer : listAgent) {
                            if (!getLocalName().equals(peer))   
{
                                notif.addReceiver(new AID(peer, AID.ISLOCALNAME));
                            }
                        }
                        send(notif);
                        //To ensure the blaclisted agent is removed correctly in ping Awaiting list.
                        it.remove();
                        sendToken();
                    }
                }
            }
        });




      addBehaviour(new TickerBehaviour(this, 30_000) { 
    @Override
    protected void onTick() {
        System.out.println("Local position moving from "+getLocalName());

        addDB.updateLocalValidPoint(); // Updating coordinate (xa, ya)
        double[] coordinates = addDB.getLastAdjustedCoordinates();
        
        System.out.println(getLocalName() + " is saving local measure...");
       // addDB.saveMeasurementToDatabase(); 

         addDB.arduino(localDevice,coordinates[0],coordinates[1]);
    }
});


 addBehaviour(new TickerBehaviour(this, 100_000) { 
    @Override
    protected void onTick() {
        System.out.println("Local measurement to global measurement from "+getLocalName());
        addDB.transferLastLocalMeasurementToMain(idKit);
        
    }
});






        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();

                                
                if (msg == null || msg.getContent() == null) {
                    block();
                    return;
                }


                String content = msg.getContent();
                int performative = msg.getPerformative();
                String conversationId= msg.getConversationId();

                try {
                    //if the message is ping
                   

                if ("ping".equals(msg.getConversationId())) {

                    //To retrieve the agent that responded
                    String sender = msg.getSender().getLocalName();

                    //To check if the agent was blaclisted
                    /*Yes: one considers that the agent is become to alive
                     *
                     * No: one condiders that the agent is ever alive
                     *
                     * */
                    boolean wasBlacklisted = blacklist.remove(sender);
                    pingAwaiting.remove(sender);


                    //To check that if agent is new or not


                    boolean isNewAgent = !knownAgents.contains(sender);
                    knownAgents.add(sender);

                    System.out.println(getLocalName() + " confirm " + sender + " is alive.");


                    // To determine if the agent is blacklisted or if they are a new agent

                    if (wasBlacklisted || isNewAgent) {
                        System.out.println(getLocalName() + " relaunches the TOKEN for " + sender +
                                (wasBlacklisted ? " (returned from blacklist)" : " (new agent detected)"));

                        updateAgentList();
                        nextagent();
                     
                        sendToken();
                    }

                }





                    // To receive TOKEN

                   if ("TOKEN".equals(content)) {
    System.out.println(getLocalName() + " received TOKEN, scheduling start in 50 sec...");

    addBehaviour(new WakerBehaviour(myAgent, 50_000) {
        protected void onWake() {
            System.out.println(getLocalName() + " starts execution after 50 sec delay");

            SequentialBehaviour sequential = new SequentialBehaviour();

            sequential.addSubBehaviour(new OneShotBehaviour() {
                public void action() {
                    updateAgentList();
                    nextagent();
                }
            });

          

            sequential.addSubBehaviour(new OneShotBehaviour() {
    @Override
                public void action() {
                    double[] coordinates = addDB.getLastAdjustedCoordinates();
                    String data = addDB.getLastTemperatureWithCoordinates(coordinates[0],coordinates[1]); // "x,y,temp"
                    String data1 = addDB.getLastPHWithCoordinates(coordinates[0],coordinates[1]);          // "x,y,ph"
                    Map<Integer, String> others = addDB.getLastOtherMeasurementsWithCoordinates(coordinates[0],coordinates[1]); // id_device -> "x,y,value"

                    String[] parts = data.split(",");
                    String[] partsPh = data1.split(",");

                    if (parts.length >= 3 && partsPh.length >= 3) {
                        String temperature = parts[2];
                        String ph = partsPh[2];

                        // Construire la partie autres capteurs : id=value|id=value|...
                        StringBuilder othersString = new StringBuilder();
                        for (Map.Entry<Integer, String> entry : others.entrySet()) {
                            String[] valParts = entry.getValue().split(",");
                            if (valParts.length == 3) {
                                String val = valParts[2]; // seulement la valeur
                                othersString.append(entry.getKey()).append("=").append(val).append("|");
                            }
                        }

                        // Retirer le dernier '|'
                        if (othersString.length() > 0) {
                            othersString.setLength(othersString.length() - 1);
                        }

                        for (String peer : listAgent) {
                            if (peer != null && !getLocalName().equals(peer)) {
                                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                                msg.addReceiver(new AID(peer, AID.ISLOCALNAME));
                                msg.setConversationId("Compare_Data");
                                msg.setContent(temperature + "," + ph + "," + getLocalName() + "," + othersString); // "21.5,7.1,z2,3=90.0|4=120.5"
                                send(msg);
                                System.out.println("Data sent to " + peer + " : " + msg.getContent());
                            }
                        }
                    } else {
                        System.err.println(getLocalName() + " - Invalid data format for temperature or pH.");
                    }
                }
            });



            sequential.addSubBehaviour(new OneShotBehaviour() {
                @Override
                public void action() {
                    try {
                        double[] maxDiffEntry = null;
                        double maxDiff = 0;

                        for (double[] entry : compareList) {
                            double diff = entry[2];
                            if (diff * diff >= maxDiff * maxDiff) {
                                maxDiff = diff;
                                maxDiffEntry = entry;
                            }
                        }

                        if (maxDiffEntry != null) {
                            double targetX = maxDiffEntry[0];
                            double targetY = maxDiffEntry[1];
                            int kitId = (int) maxDiffEntry[3];
                            String targetAgent = "Z" + kitId;

                            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                            msg.setConversationId("Moving data");
                            msg.addReceiver(new AID(targetAgent, AID.ISLOCALNAME));
                            msg.setContent(targetX + "," + targetY);
                            send(msg);

                            System.out.printf("Moving data sent to %s at (%.4f, %.4f)%n", targetAgent, targetX, targetY);

                            myAgent.addBehaviour(new WakerBehaviour(myAgent, 10_000) {
                                protected void onWake() {
                                    addDB.updateLocalCoordinates(idKit, targetX, targetY);
                                    System.out.printf("Kit updated to (%.4f, %.4f)%n", targetX, targetY);
                                    updateAgentList();
                                    nextagent();
                                    sendToken();
                                }
                            });
                        } else {
                            System.out.println("No difference found. No move needed.");
                            updateAgentList();
                            nextagent();
                            sendToken();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            addBehaviour(sequential);
        }
    });
}


                   else if ("Compare_Data".equals(conversationId)) {
   

                        String[] parts = content.split(",", 4);
                        if (parts.length < 3) {
                            System.out.println("Invalid format: " + content);
                            return;
                        }

                        try {
                            double temperature = Double.parseDouble(parts[0]);
                            double ph = Double.parseDouble(parts[1]);
                            String senderAgent = parts[2];
                            Map<Integer, Double> otherValues = new HashMap<>();

                            // Parse optional additional sensor values
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

                            // Call comparison logic
                            double[] localResult = AddDB.KitMeasureTemperatureCompare(temperature, ph, otherValues);

                            if (localResult != null && localResult.length == 3) {
                                ACLMessage reply = msg.createReply();
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setConversationId("Difference");
                                reply.setContent(String.format("Difference,%.3f,%.3f,%.2f,%d,%s",
                                        localResult[0], localResult[1], localResult[2], idKit, getLocalName()));
                                send(reply);

                                System.out.printf("Data received from %s ➜ Δ max at (%.3f, %.3f), Δ = %.2f%n",
                                        senderAgent, localResult[0], localResult[1], localResult[2]);
                            } else {
                                System.out.println("⚠ No matching local position found for comparison.");
                            }

                        } catch (NumberFormatException e) {
                            System.out.println("Format error in Compare_Data: " + e.getMessage());
                        }
                }


                    else if ("Difference".equals(conversationId)) {
                        String[] parts = content.split(",");
                        if (parts.length == 6 && parts[0].equals("Difference")) {
                            double x = Double.parseDouble(parts[1]);
                            double y = Double.parseDouble(parts[2]);
                            double diff = Double.parseDouble(parts[3]);
                            int sourceId = Integer.parseInt(parts[4]);
                            String agentSource = parts[5];

                            // Ajouter dans une liste (tu peux stocker aussi agentSource si besoin ailleurs)
                            compareList.add(new double[]{x, y, diff, sourceId});

                            System.out.printf(" Résult received from  %s : Δ = %.2f°C à (%.2f, %.2f) [kit ID = %d]%n",
                                    agentSource, diff, x, y, sourceId);
                        } else {
                            System.out.println(" Bad format for 'Difference' recveived : " + content);
                        }
                    }



                    // Calculating the distance between the sending agent and the receiving agent
                    else if ("Moving data".equals(conversationId)) {


                        String[] parts = content.split(",");
                        if (parts.length == 3) {
                            double targetX = Double.parseDouble(parts[1]);
                            double targetY = Double.parseDouble(parts[2]);

                            // To generate new point on the study area from kit
                            double[] newPoint = generateSingleValidPoint(targetX, targetY);

                            // To update the new position from the sender kit
                            addDB.updateKitCoordinates(idKit, newPoint[0], newPoint[1]);

                            // Sends an acknowledgment response
                            ACLMessage reply = msg.createReply();
                            reply.setPerformative(ACLMessage.INFORM);
                            reply.setContent("Moving done");
                            send(reply);

                        }

                    }


                    //to blaclist the unworked agent

                    else if (content.startsWith("blacklist:")) {
                        String[] parts = content.split(":");
                        if (parts.length == 2) {
                            String agentToBlacklist = parts[1].trim();
                            if (!blacklist.contains(agentToBlacklist)) {
                                blacklist.add(agentToBlacklist);
                                System.out.println(getLocalName() + " add " + agentToBlacklist + " to blacklist.");
                            } else {
                                System.out.println(getLocalName() + " has ever added " + agentToBlacklist + " to its blacklist.");
                            }
                        }
                    }

                    // STOP
                    else if (content.equals("STOP")) {
                        System.out.println(getLocalName() + " received STOP. Terminating.");
                        doDelete();
                    }

                    // General INFORM

                    else if (performative == ACLMessage.INFORM) {
                        System.out.println(getLocalName() + " reads : " + content);
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
        nextagent();
        // Check if the nextAgent is valid
        if (nextAgent == null || nextAgent.trim().isEmpty()) {
            System.err.println(getLocalName() + " - No available agent, TOKEN not sent.");
            return;
        }

        // Create an INFORM message with content "TOKEN"
        ACLMessage token = new ACLMessage(ACLMessage.INFORM);
        token.setContent("TOKEN");

        // Set the receiver of the message to the next agent
        token.addReceiver(new AID(nextAgent, AID.ISLOCALNAME));

        // Send the message to the next agent
        send(token);

        // Log the action to the console
        System.out.println(getLocalName() + " gives token to " + nextAgent);
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
                if (!name.equals(getLocalName()) && !blacklist.contains(name)) {
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





}
