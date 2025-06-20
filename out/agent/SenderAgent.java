package agent;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class SenderAgent extends Agent {
    @Override
    protected void setup() {
        System.out.println(getLocalName() + " pret a envoyer.");

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setContent("Salut Receiver !");
        msg.addReceiver(new AID("receiver", AID.ISLOCALNAME));

        send(msg);

        System.out.println("Message envoye !");
    }
}
