package main;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

import java.io.IOException;

public class Main {
        public static void main(String[] args) throws StaleProxyException, IOException {
                // Command to start the JADE main container externally
                String[] cmd = {
                        "java",
                        "-cp",
                        "lib/jade.jar",
                        "jade.Boot",
                        "-local-port",
                        "12345"
                };

                // Create a connection to the JADE platform
                Runtime rt = Runtime.instance();
                Profile p = new ProfileImpl();
                p.setParameter(Profile.MAIN_HOST, "127.0.0.1");
                p.setParameter(Profile.MAIN_PORT, "12345");

                // Connect to the external JADE platform
                ContainerController container =  rt.createAgentContainer(p);
;

                // Start agents inside the connected container
                container.createNewAgent("detection", "agent.Detection", null).start();
                container.createNewAgent("analyze", "agent.Analyze", null).start();
                container.createNewAgent("interpretation", "agent.Interpretation", null).start();

                System.out.println("Agents connected to the external JADE platform.");
        }


}
