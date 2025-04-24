package agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class Interpretation extends Agent {

    protected void setup() {
        System.out.println(" Agent active, waiting for messages...");

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    System.out.println("Message received: " + msg.getContent());
                } else {
                    block();
                }
            }
        });
    }
}
