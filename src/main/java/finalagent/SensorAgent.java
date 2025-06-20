package finalagent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;
import java.math.BigDecimal;
import jade.wrapper.StaleProxyException;
import static java.lang.Math.abs;

import java.sql.*;
import java.time.Instant;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;




public class SensorAgent extends Agent {
    private Integer idKit;
    private String nextAgent;
    private String[] listagent;
    private Map<String, String> agentIpMap;
    private boolean lastegent;
    private AddDB addDB;
private Map<String, Boolean> pongReplies = new HashMap<>();

    private Boolean[] agreeMoving;


    @Override
    protected void setup() {
        addDB = new AddDB();
        Object[] args = getArguments();
if (args == null || args.length < 4) {
    System.err.println("? Agent " + getLocalName() + " lance sans arguments requis. Arret.");
    doDelete();
    return;
}


        nextAgent = (String) args[0];
        listagent = (String[]) args[1];
        agentIpMap = (Map<String, String>) args[2];
        lastegent = (boolean) args[3];

        String localName = getLocalName().toLowerCase();
        idKit = Integer.parseInt(localName.replaceAll("[^0-9]", ""));


        agreeMoving = new Boolean[listagent.length];
        for (int i = 0; i < listagent.length; i++) {
            agreeMoving[i] = false;
        }

        nextagent();

              // Find the agent with the smallest ID 
        int minId = Integer.MAX_VALUE;
        for (String agentName : agentIpMap.keySet()) {
            int currentId = Integer.parseInt(agentName.replaceAll("[^0-9]", ""));
            if (currentId < minId) {
                minId = currentId;
            }
        }

        // Si l'agent actuel a le plus petit ID, envoyer le jeton initial
        if (idKit == minId) {
            addBehaviour(new WakerBehaviour(this, 30_000) {
                protected void onWake() {
                    System.out.println(getLocalName() + " ready to send initial token.");
                    sendToken();
                }
            });
        }


        
        
        
         addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();

                if (msg == null) {
                    block();
                    return;
                }

                String content = msg.getContent();
                int performative = msg.getPerformative();

                try {
                    // TOKEN
                    if (content.equals("TOKEN")) {
                        System.out.println(getLocalName() + " start");
                        nextagent();

                        if (lastegent && !addDB.isKitCountEqualToAgents(listagent)) {
                            addAnotherAgent();
                            notifynewPeerrAgent();
                        }

                        if (!addDB.hasSensorInDatabase(idKit)) {
                            System.out.println("Agent " + getLocalName() + " has no sensor. It will pass the token and delete itself.");
                            addDB.deleteKit(idKit);
                            notifyPeersAgentDead(getLocalName());
                            sendToken();
                            doDelete();
                            return;
                        }

                        SequentialBehaviour sequential = new SequentialBehaviour();
                         // Step 1: Local sensor reading
                        sequential.addSubBehaviour(new OneShotBehaviour() {
                            public void action() {
                                System.out.println(getLocalName() + " local sensor...");
                                addDB.saveMeasurementToDatabase(idKit);
                            }
                        });

                        // Step 2: Ask for peer data
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

                        // Step 3: Ask for movement approval
                        Double[] studyLocation = addDB.actualLocationStudy(1);
                        double[] newPoint= GeoRandomPoint.generateRandomPointAroundCenter(studyLocation[0],studyLocation[1],20);
                        double distanceCheck = GeoRandomPoint.haversine(studyLocation[0], studyLocation[1], newPoint[0], newPoint[1]);
                        System.out.printf("?? DEBUG: Point generated (%.10f, %.10f), distance to study center = %.2f m%n",
                                newPoint[0], newPoint[1], distanceCheck);

                        ParallelBehaviour askPeers2 = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
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

                                if (numberOfAgree == agreeMoving.length-1) {
                                    addDB.updateKitCoordinates(idKit, newPoint[0], newPoint[1]);
                                    System.out.println("Kit updated after consensus.");
                                } else {
                                    System.out.println("Not enough consensus for movement.");
                                }

                                sendToken();
                            }
                        });

                        addBehaviour(sequential);
                    }
                    
                    // MOVING DATA
                   else if ((performative == ACLMessage.REQUEST || performative == ACLMessage.INFORM)
                            && content.startsWith("Moving data,")) {

                        System.out.println(getLocalName() + " received Moving data message: " + content);
                        String[] parts = content.split(",");

                        double latitude = Double.parseDouble(parts[1].trim());
                        double longitude = Double.parseDouble(parts[2].trim());
                        Double[] currentLocation = addDB.actualKitLocation();


                        double distance=GeoRandomPoint.haversine(currentLocation[0],currentLocation[1],latitude,longitude);
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);

                        boolean agree = abs(distance) > 0.65;
                        System.out.printf("\n?? Distance from centre : %.2f m%n", distance);
                        reply.setContent("Response:" + agree + ":" + getLocalName());
                        send(reply);

                        System.out.println(getLocalName() + " is " + (agree ? "far" : "close") + " from the study area.");
                    }

                    // RESPONSE to movement
                    else if (performative == ACLMessage.INFORM && content.startsWith("Response:")) {
                        String[] parts = content.split(":");

                        boolean response = Boolean.parseBoolean(parts[1]);
                        String agentName = parts[2];

                        for (int i = 0; i < listagent.length; i++) {
                            if (listagent[i].equals(agentName)) {
                                agreeMoving[i] = response;
                                break;
                            }
                        }
                    }

                    // DONNEE?
                    else if (performative == ACLMessage.REQUEST && content.equals("DONNEE?")) {
                        String result = addDB.getLastMeasurementsByKit(idKit);
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent(result);
                        send(reply);
                    }

                    // DEAD_AGENT
                    else if (performative == ACLMessage.INFORM && content.startsWith("DEAD_AGENT:")) {
                        String deadAgent = content.split(":")[1];
                        java.util.List<String> updatedPeers = new java.util.ArrayList<>();
                        for (String p : listagent) {
                            if (!p.equals(deadAgent)) {
                                updatedPeers.add(p);
                            }
                        }
                        listagent = updatedPeers.toArray(new String[0]);
                        System.out.println(getLocalName() + " removed " + deadAgent + " from list.");
                    }

                    // NEW_AGENT
                    else if (performative == ACLMessage.INFORM && content.startsWith("NEW_AGENT")) {
                        listagent = addDB.addnewAgentOnlist(listagent);
                    }

                    // STOP
                    else if (content.equals("STOP")) {
                        System.out.println(getLocalName() + " received STOP. Terminating.");
                        doDelete();
                    }

                    // General INFORM
                  
                    else if (performative == ACLMessage.REQUEST && content.equals("PING")) {
    ACLMessage reply = msg.createReply();
    reply.setPerformative(ACLMessage.INFORM);
    reply.setContent("PONG");
    send(reply);
    System.out.println(getLocalName() + " replied PONG to " + msg.getSender().getLocalName());
}
                    else if (performative == ACLMessage.INFORM && content.equals("PONG")) {
    String sender = msg.getSender().getLocalName().toLowerCase();
    pongReplies.put(sender, true);
    System.out.println(getLocalName() + " received PONG from " + sender);
}
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

    
    
    
    /*private void sendToken() {
        ACLMessage token = new ACLMessage(ACLMessage.INFORM);
        token.setContent("TOKEN");
        token.addReceiver(new AID(nextAgent, AID.ISLOCALNAME));
        send(token);
        System.out.println(getLocalName() + " gives token to " + nextAgent);
    }*/
    
    
private void sendToken() {
    System.out.println(getLocalName() + " trying to send token to " + nextAgent);

    String key = nextAgent.toLowerCase();
    pongReplies.put(key, false); // Reset l'etat de PONG

    ACLMessage ping = new ACLMessage(ACLMessage.REQUEST);
    ping.addReceiver(new AID(nextAgent, AID.ISLOCALNAME));
    ping.setContent("PING");
    send(ping);
    System.out.println(getLocalName() + " sent PING to " + nextAgent);

    addBehaviour(new WakerBehaviour(this, 9000) {
        protected void onWake() {
            boolean responded = pongReplies.getOrDefault(key, false);

            if (!responded) {
                System.out.println("?? " + nextAgent + " did not respond. Marking as DEAD_AGENT.");
                notifyPeersAgentDead(nextAgent);
                listagent = removeAgentFromList(listagent, nextAgent);
                nextagent();

                if (!nextAgent.equals(getLocalName())) {
                    sendToken();
                } else {
                    System.out.println(getLocalName() + " is the last agent remaining. Token retained.");
                }
            } else {
                ACLMessage token = new ACLMessage(ACLMessage.INFORM);
                token.setContent("TOKEN");
                token.addReceiver(new AID(nextAgent, AID.ISLOCALNAME));
                send(token);
                System.out.println(getLocalName() + " gives token to " + nextAgent);
            }

           //To clean pongReplies 
            pongReplies.remove(key);
        }
    });
}




 private void notifyPeersAgentDead(String agent) {
        ACLMessage deathNotice = new ACLMessage(ACLMessage.INFORM);
        deathNotice.setContent("DEAD_AGENT:" + agent);
        for (String peer : listagent) {
            if (!peer.equals(agent)) {
                deathNotice.addReceiver(new AID(peer, AID.ISLOCALNAME));
            }
        }
        send(deathNotice);// Send the notification message to peers
        System.out.println(getLocalName() + " has notified others of its termination.");

    }



   /* private void notifyPeersAgentDead() {
        ACLMessage deathNotice = new ACLMessage(ACLMessage.INFORM);
        deathNotice.setContent("DEAD_AGENT:" + getLocalName());
        for (String peer : listagent) {
            if (!peer.equals(getLocalName())) {
                deathNotice.addReceiver(new AID(peer, AID.ISLOCALNAME));
            }
        }
        send(deathNotice);// Send the notification message to peers
        System.out.println(getLocalName() + " has notified others of its termination.");

    }*/

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

    private void nextagent() {
    String current = getLocalName();
    int index = -1;

    for (int i = 0; i < listagent.length; i++) {
        if (listagent[i].equalsIgnoreCase(current)) {
            index = i;
            break;
        }
    }

    if (index == -1) {
        System.err.println("ERROR: Agent " + current + " not found in listagent!");
        return;
    }

    String calculatedNext = listagent[(index + 1) % listagent.length];
    System.out.println(current + " sets nextAgent = " + calculatedNext);
    nextAgent = calculatedNext;  // force update
}



    public void addAnotherAgent() throws SQLException, ControllerException {

                int lastkit_id = addDB.toHavetheLastkit();
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


private String[] removeAgentFromList(String[] list, String target) {
    List<String> updated = new ArrayList<>();
    for (String agent : list) {
        if (!agent.equalsIgnoreCase(target)) {
            updated.add(agent);
        }
    }
    return updated.toArray(new String[0]);
}



}
