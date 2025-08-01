package finalagent;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class Main {

    public static void main(String[] args) {
        ContainerController cc = null;
        Runtime rt = Runtime.instance();

        try {
            // If fewer than 4 arguments are provided, this is the main container
            if (args.length < 4) {
                Profile p = new ProfileImpl("localhost", 60000, null);
                p.setParameter(Profile.GUI, "true"); // Enable the JADE GUI
                p.setParameter(Profile.LOCAL_PORT, "60000"); // Set the default port
                p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://0.0.0.0:8888/acc)");
                cc = rt.createMainContainer(p); // Create the main container
            } else {
                // If 4 arguments or more, create a secondary container and connect to the remote host
                Profile p = new ProfileImpl(args[3], 60000, null);
                p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://0.0.0.0:8888/acc)");
                cc = rt.createAgentContainer(p); // Join the existing platform
                System.out.println("Starting as a secondary container");
            }

            // Prepare arguments to pass to the agent:
            // [isMainContainer, ID, x-coordinate, y-coordinate]
            Object[] agentArgs = {
                args.length < 4,
                Integer.parseInt(args[0]),
                Double.parseDouble(args[1]),
                Double.parseDouble(args[2])
            };

            // Create and start the agent named Z{ID}
            AgentController ac = cc.createNewAgent("Z" + args[0], "finalagent.SensorAgent", agentArgs);
            ac.start();

        } catch (Exception e) {
            // Print stack trace on error
            e.printStackTrace();
        }
    }
}
