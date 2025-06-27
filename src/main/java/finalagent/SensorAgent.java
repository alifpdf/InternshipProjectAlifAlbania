package finalagent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;



import static java.lang.Math.abs;


import java.util.*;


public class SensorAgent extends Agent {
    
   
    private String nextAgent;
    private List<String> listagent = new ArrayList<>();
    private Set<String> blacklist = new HashSet<>();
    private Map<String, Long> lastPingSent = new HashMap<>();
    private Set<String> pingAwaiting = new HashSet<>();
    private Set<String> knownAgents = new HashSet<>();


    


    private int idKit=1;

    private AddDB addDB;


    private Boolean[] agreeMoving;


    @Override
    protected void setup() {
        // Ajoutez ce comportement dans votre méthode setup


        addDB = new AddDB();


        try {
                        // Create a new agent description object used to register with the Directory Facilitator (DF)
            DFAgentDescription dfd = new DFAgentDescription();

            // Set the unique identifier (AID) of the agent being registered
            dfd.setName(getAID());

            // Create a service description that defines what this agent offers
            ServiceDescription sd2 = new ServiceDescription();
            sd2.setType("sensor"); // Define the service type

            // Set a unique name for this service, useful for identifying specific agents 
            sd2.setName("SensorAgent-" + getLocalName());

            // Add the service description to the agent description
            dfd.addServices(sd2);

            // Register the agent and its service with the Directory Facilitator (DF)
            DFService.register(this, dfd);

            // Confirmation message printed to the console
            System.out.println(getLocalName() + " registered with DF.");

        } catch (Exception e) {
            System.err.println("Erreur enregistrement DF: " + e.getMessage());
            e.printStackTrace();
        }

        Object[] args = getArguments();
        if((boolean)args[0]){
            try {
                Thread.sleep(30_000); // To wait 30 seconds
                updateAgentList();
                nextagent();
                agreeMoving = new Boolean[listagent.size()];
                sendToken();
                for (int i = 0; i < listagent.size(); i++) {
                    agreeMoving[i] = false;
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }else{

            updateAgentList();
            nextagent(); // 
            agreeMoving = new Boolean[listagent.size()];
            
        }
        

       
        
        
/*
        String localName = getLocalName().toLowerCase();
        int idKit = Integer.parseInt(localName.replaceAll("[^0-9]", ""));


        int minId = Integer.MAX_VALUE;
        for (String agentName : listagent) {
            int currentId = Integer.parseInt(agentName.replaceAll("[^0-9]", ""));
            if (currentId < minId) {
                minId = currentId;
            }
        }

       */


/*
        addBehaviour(new TickerBehaviour(this, 30_000) {
            protected void onTick() {
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, buildSensorSearchTemplate());
                    for (DFAgentDescription desc : result) {
                        String agentName = desc.getName().getLocalName();

                        if (!agentName.equals(getLocalName())) {
                            // Teste si l'agent répond à un ping
                            ACLMessage ping = new ACLMessage(ACLMessage.REQUEST);
                            ping.setConversationId("ping");
                            ping.setReplyWith("ping" + System.currentTimeMillis());
                            ping.setContent("PING?");
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


        addBehaviour(new TickerBehaviour(this, 5_000) {
            protected void onTick() {
                long now = System.currentTimeMillis();
                Iterator<String> it = pingAwaiting.iterator();
                while (it.hasNext()) {
                    String agent = it.next();
                    long sentTime = lastPingSent.getOrDefault(agent, 0L);
                    if (now - sentTime > 10_000) { // timeout = 10 secondes
                        blacklist.add(agent);
                        updateAgentList();
                        nextagent();

                        System.err.println(agent + " didn't respond to ping. It is blacklisted.");
                        
                        
                         ACLMessage notif = new ACLMessage(ACLMessage.INFORM);
                        notif.setContent("blacklist:" + agent);
                       

                        for (String peer : listagent) {
                            if (!peer.equals(getLocalName())) {
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


*/

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();

                if (msg == null) {
                    block(); // Attendre un message
                    return;
                }




                String content = msg.getContent();
                int performative = msg.getPerformative();
                
                /*
                
                if ("ping".equals(msg.getConversationId())) {
                    
                    //To retrieve the agent that responded
                    String sender = msg.getSender().getLocalName();

                    //To check if the agent was blaclisted 
                    /*Yes: one considers that the agent is become to alive
                     *
                     * No: one condiders that the agent is ever alive
                     *
                     * */
                    /*boolean wasBlacklisted = blacklist.remove(sender);
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
                        agreeMoving = new Boolean[listagent.size()];
                        for (int i = 0; i < listagent.size(); i++) {
                            agreeMoving[i] = false;
                        }
                        sendToken();
                    }

                }
*/




                try {
                    // To receive TOKEN
                    if (content.equals("TOKEN")) {
                        System.out.println(getLocalName() + " start");

                        SequentialBehaviour sequential = new SequentialBehaviour();
                        
                        /*sequential.addSubBehaviour(new OneShotBehaviour() {
                            public void action() {
                                updateAgentList();
                                nextagent();
                            }
                        });
*/


                        // Step 1: Local sensor reading
                        sequential.addSubBehaviour(new OneShotBehaviour() {
                            public void action() {
                                System.out.println(getLocalName() + " local sensor...");
                                addDB.saveMeasurementToDatabase(idKit);
                            }
                        });

                        // Step 2: Ask for peer sensor data
                        ParallelBehaviour askPeers = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
                        for (String peer : listagent) {
                            if (!peer.equals(getLocalName())) {
                                askPeers.addSubBehaviour(new OneShotBehaviour() {
                                    public void action() {
                                        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                                        req.addReceiver(new AID(peer, AID.ISLOCALNAME));
                                        req.setContent("DATA?");
                                        send(req);
                                        System.out.println(getLocalName() + " request to " + peer);
                                    }
                                });
                            }
                        }
                        sequential.addSubBehaviour(askPeers);

                      

                        // Step 3: Ask for movement approval
                        
                        //To calculate new position
                        Double[] studyLocation = addDB.actualLocationStudy(1);
                        double[] newPoint= GeoRandomPoint.generateRandomPointAroundCenter(studyLocation[0],studyLocation[1],20);
                        double distanceCheck = GeoRandomPoint.haversine(studyLocation[0], studyLocation[1], newPoint[0], newPoint[1]);
                        System.out.printf("DEBUG: Point generated (%.10f, %.10f), distance to study center = %.2f m%n",
                                newPoint[0], newPoint[1], distanceCheck);

                        //To ask peers if kit can move
                        ParallelBehaviour askPeers2 = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
                        updateAgentList();
                        for (String peer : listagent) {
                            if (!peer.equals(getLocalName())) {
                                askPeers2.addSubBehaviour(new OneShotBehaviour() {
                                    public void action() {
                                        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                                        req.addReceiver(new AID(peer, AID.ISLOCALNAME));
                                        req.setContent("Moving data," + newPoint[0] + "," + newPoint[1]);
                                        send(req);
                                        System.out.println(getLocalName() + " sending moving data to " + peer);
                                    }
                                });
                            }
                        }
                        sequential.addSubBehaviour(askPeers2);
                        // Step 4: Wait and evaluate agreement
                        sequential.addSubBehaviour(new WakerBehaviour(myAgent, 5000) {
                            protected void onWake() {
                                int numberOfAgree = 0;
                                for (boolean agree : agreeMoving) {
                                    if (agree) numberOfAgree++;
                                }

                                System.out.println(getLocalName() + " received " + numberOfAgree + " agreements out of " + agreeMoving.length);

                                if (numberOfAgree == agreeMoving.length) {
                                    addDB.updateKitCoordinates(idKit, newPoint[0], newPoint[1]);
                                    System.out.println("Kit updated after consensus.");
                                } else {
                                    System.out.println("Not enough consensus for movement.");
                                }


                                updateAgentList();
                                nextagent();

                                sendToken();
                            }
                        });

                        addBehaviour(sequential);
                    }
                    
                     // To get last measures of sensor from kit
                    else if (content.equals("DATA?")) {
                        String result = addDB.getLastMeasurementsByKit(idKit);
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent(result);
                        send(reply);
                    }

                    // Calculating the distance between the sending agent and the receiving agent
                    else if (content.startsWith("Moving data,")) {

                        System.out.println(getLocalName() + " received Moving data message: " + content);
                        String[] parts = content.split(",");

                        double latitude = Double.parseDouble(parts[1].trim());
                        double longitude = Double.parseDouble(parts[2].trim());
                        Double[] currentLocation = addDB.actualKitLocation();


                        double distance=GeoRandomPoint.haversine(currentLocation[0],currentLocation[1],latitude,longitude);
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);

                        boolean agree = abs(distance) > 0.65;
                        System.out.printf("\n Distance from centre : %.2f m%n", distance);
                        reply.setContent("Response:" + agree + ":" + getLocalName());
                        send(reply);

                        System.out.println(getLocalName() + " is " + (agree ? "far" : "close") + " from the study area.");
                    }

                    //authorize agent to move with the agreement of another agent
                    else if (content.startsWith("Response:")) {
                        String[] parts = content.split(":");

                        boolean response = Boolean.parseBoolean(parts[1]);
                        String agentName = parts[2];

                        for (int i = 0; i < listagent.size(); i++) {
                            if (listagent.get(i).equals(agentName)) {
                                agreeMoving[i] = response;
                                break;
                            }
                        }
                    }
                    
                    

                   
                    
                    /*
                    
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


*/


                    // STOP
                    else if (content.equals("STOP")) {
                        System.out.println(getLocalName() + " received STOP. Terminating.");
                        doDelete();
                    }

                    // General INFORM

                    else if (performative == ACLMessage.INFORM) {
                        System.out.println(getLocalName() + " reads : " + content);
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });



    }





  /**
 * Builds a search template to find agents offering a "sensor" type service.
 * This template is used with DFService.search(...) to locate sensor agents registered in the Directory Facilitator (DF).
 *
 * @return A DFAgentDescription object configured to search for "sensor" services.
 */
private DFAgentDescription buildSensorSearchTemplate() {
    // Create a new agent description template for the search
    DFAgentDescription template = new DFAgentDescription();

    // Create a service description for the type we want to find
    ServiceDescription sd5 = new ServiceDescription();
    sd5.setType("sensor");  // We are searching for services of type "sensor"

    // Add the service description to the agent description template
    template.addServices(sd5);

    // Return the template ready to be used with DFService.search(...)
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
        listagent.clear();

        // Iterate over the search results
        for (DFAgentDescription agentDesc : result) {
            String name = agentDesc.getName().getLocalName();

            // Add agent to list if it's not self and not in blacklist
            if (!name.equals(getLocalName()) /*&& !blacklist.contains(name)*/) {
                listagent.add(name);
            }
        }

        // Display the updated list of agents
        System.out.println(getLocalName() + " - List of agent after update : " + listagent);

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
        for (String name : listagent) {
            
                agentNames.add(name);
            
            /*
            if (!blacklist.contains(name)) {
                agentNames.add(name);
            }*/
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
