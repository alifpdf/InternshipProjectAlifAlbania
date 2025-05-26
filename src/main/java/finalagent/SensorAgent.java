package finalagent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;

import static java.lang.Math.abs;


public class SensorAgent extends Agent {
    private Integer idKit;
    private String nextAgent;
    private String[] listagent;
    private Boolean lastegent;
    private AddDB addDB;

    private Boolean[] agreeMoving;


    @Override
    protected void setup() {
        addDB = new AddDB();
        Object[] args = getArguments();

        nextAgent = (String) args[0];
        listagent = (String[]) args[1];
        lastegent = (Boolean) args[2];

        String localName = getLocalName().toLowerCase();
        idKit = Integer.parseInt(localName.replaceAll("[^0-9]", ""));

        agreeMoving = new Boolean[listagent.length];
        for (int i = 0; i < listagent.length; i++) {
            agreeMoving[i] = false;
        }

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
                            notifyPeersAgentDead();
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
                        System.out.printf("🧭 DEBUG: Point generated (%.10f, %.10f), distance to study center = %.2f m%n",
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
                        Double[] currentLocation = addDB.actualKitLocation(idKit);


                        double distance=GeoRandomPoint.haversine(currentLocation[0],currentLocation[1],latitude,longitude);
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);

                        boolean agree = abs(distance) > 0.65;
                        System.out.printf("\n📏 Distance from centre : %.2f m%n", distance);
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
                    else if (performative == ACLMessage.INFORM) {
                        System.out.println(getLocalName() + " reads : " + content);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
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



}
