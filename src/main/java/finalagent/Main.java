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
     
            
            

            if (args.length<4) {
                Profile p = new ProfileImpl("localhost", 60000, null);
                p.setParameter(Profile.GUI, "true");
                p.setParameter(Profile.LOCAL_PORT, "60000");
                
                p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://0.0.0.0:8888/acc)");
               cc = rt.createMainContainer(p);
            } else {
                Profile p = new ProfileImpl(args[3], 60000, null);
                p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://0.0.0.0:8888/acc)");
                cc = rt.createAgentContainer(p);
                System.out.println("Starting as a secondary container");
            }

            Object[] agentArgs = { args.length < 4, Integer.parseInt(args[0]), Double.parseDouble(args[1]), Double.parseDouble(args[2])  };            
             AgentController ac = cc.createNewAgent("Z"+args[0], "finalagent.SensorAgent", agentArgs);
            ac.start();

         
        } catch (Exception e) {
            e.printStackTrace();


        }
    }
}
