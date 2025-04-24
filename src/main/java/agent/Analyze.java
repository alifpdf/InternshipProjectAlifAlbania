package agent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class Analyze extends Agent {

    protected void setup() {
        System.out.println("Agent ready.");

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    System.out.println("[Analyze] Message received: " + msg.getContent());

                    ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                    reply.addReceiver(new AID("interpretation", AID.ISLOCALNAME));
                    reply.setContent("very oxidizing");
                    send(reply);

                    System.out.println("Result sent to interpretation");
                } else {
                    block();
                }
            }
        });
    }
}
