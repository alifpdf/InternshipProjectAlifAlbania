package finalagent;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class Main {

    public static void main(String[] args) {
        
        
        boolean a = true;
      
        
        ContainerController cc = null;
        Runtime rt = Runtime.instance();

        try {
     
            
            

            if (args.length<2) {
                Profile p = new ProfileImpl("localhost", 60000, null);
                p.setParameter(Profile.GUI, "true");
                p.setParameter(Profile.LOCAL_PORT, "60000");
                
                p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://0.0.0.0:8888/acc)");
               cc = rt.createMainContainer(p);
            } else {
                Profile p = new ProfileImpl(args[1], 60000, null);
                //p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://" + myIP + ":8888/acc)");
                cc = rt.createAgentContainer(p);
                System.out.println("Starting as a secondary container");
            }

            Object[] agentArgs = { args.length < 2, Integer.parseInt(args[0]) };

           // String name="Z"+args[0];
            
             AgentController ac = cc.createNewAgent("Z"+args[0], "finalagent.SensorAgent", agentArgs);
            ac.start();

           // System.out.println("Z" + lastPart + " launch on " + myIP);

        } catch (Exception e) {
            e.printStackTrace();


        }
    }
}
