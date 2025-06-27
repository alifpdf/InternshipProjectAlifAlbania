package finalagent;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class Main {

    public static void main(String[] args) {
        
        
        boolean a = true;
       /* String myIP = null;
        String lastPart = null;
        */
        
        
        ContainerController cc = null;
        Runtime rt = Runtime.instance();

        try {
            /*
            myIP = SimpleIPReader.getLocalIp();
            a = SimpleIPReader.equality(); // Renvoie true si main container
            lastPart = myIP.substring(myIP.lastIndexOf('.') + 1);*/
            
            

            if (a) {
                Profile p = new ProfileImpl("localhost", 60000, null);
                p.setParameter(Profile.GUI, "true");
                p.setParameter(Profile.LOCAL_PORT, "60000");
                
                p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://0.0.0.0:8888/acc)");
               cc = rt.createMainContainer(p);
            } else {
                Profile p = new ProfileImpl("192.168.224.130", 60000, null);
                //p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://" + myIP + ":8888/acc)");
                cc = rt.createAgentContainer(p);
                System.out.println("Starting as a secondary container");
            }

            Object[] agentArgs = { a };
            //AgentController ac = cc.createNewAgent("Z" + lastPart, "finalagent.SensorAgent", agentArgs);
             AgentController ac = cc.createNewAgent("Z1", "finalagent.SensorAgent", agentArgs);
            ac.start();

           // System.out.println("Z" + lastPart + " launch on " + myIP);

        } catch (Exception e) {
            e.printStackTrace();


     /*
            try {
                // Si une erreur a eu lieu, tente de relancer un container de secours
                if (!a) {
                    Profile p = new ProfileImpl("localhost", 60000, null);
                    p.setParameter(Profile.GUI, "true");
                    p.setParameter(Profile.LOCAL_PORT, "60000");
                    p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://" + myIP + ":8888/acc)");
                    cc = rt.createMainContainer(p);
                } else {
                    Profile p = new ProfileImpl("192.168.224.131", 60000, null);
                    p.setParameter(Profile.MTPS, "jade.mtp.http.MessageTransportProtocol(http://" + myIP + ":8888/acc)");
                    cc = rt.createAgentContainer(p);
                    System.out.println("Starting as a secondary container");
                }

                Object[] agentArgs = { !a };
                AgentController ac = cc.createNewAgent("Z" + lastPart, "finalagent.SensorAgent", agentArgs);
                ac.start();

                System.out.println("Z" + lastPart + " recovery launch on " + myIP);
            } catch (Exception inner) {
                inner.printStackTrace();
            }
            
            */
        }
    }
}
