package agent;

import com.fazecast.jSerialComm.SerialPort;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.Random;

public class Detection extends Agent {
    SerialPort serialPort;

    protected void setup() {
        System.out.println("Detection agent started.");
        serialPort = SerialPort.getCommPort("COM3");

        addBehaviour(new TickerBehaviour(this, 2000) {
            protected void onTick() {
                if (serialPort.openPort()) {
                    System.out.println("Serial port opened.");
                }

                // Simulated values
                Random random = new Random();

                float temperature = 20.0f + random.nextFloat() * 10.0f;  // Range: 20.0 to 30.0
                float pH = 6.0f + random.nextFloat() * 2.0f;             // Range: 6.0 to 8.0
                float orp = 600.0f + random.nextFloat() * 200.0f;        // Range: 600.0 to 800.0
                boolean levelOK = random.nextBoolean();

                String content = String.format(
                        "temp=%.1f;pH=%.2f;orp=%.1f;level=%s",
                        temperature, pH, orp, levelOK ? "OK" : "LOW"
                );

                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID("analyze", AID.ISLOCALNAME));
                msg.setContent(content);
                send(msg);
            }
        });
    }
}
