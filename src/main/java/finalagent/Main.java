package finalagent;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.Enumeration;


public class Main {
    
    private static String getLocalIp() {
    try {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (!addr.isLoopbackAddress() && addr.getHostAddress().startsWith("192.")) {
                    return addr.getHostAddress();
                }
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return "127.0.0.1"; // Fallback
}
    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASSWORD = "1234";

    public static void main(String[] args) {
        try {
            String myIP = getLocalIp();

            System.out.println("Dmarrage sur l'IP locale : " + myIP);

            // Connexion  la base pour obtenir la liste des agents et IPs
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, ip_adresse FROM disponibilite ORDER BY id");

            Map<String, String> agentIpMap = new LinkedHashMap<>();
            while (rs.next()) {
                String id = rs.getString("id");
                String ip = rs.getString("ip_adresse");
                String agentName = "Z" + id;
                agentIpMap.put(agentName, ip);
            }
            conn.close();

            List<String> agentList = new ArrayList<>(agentIpMap.keySet());

            // Vrification si un agent doit tre lanc sur cette IP
            boolean found = false;
            for (int i = 0; i < agentList.size(); i++) {
                String agent = agentList.get(i);
                String ip = agentIpMap.get(agent);

                if (ip.equals(myIP)) {
                    found = true;
                    System.out.println("Tentative de lancement de " + agent + " sur " + myIP + " (attendu: " + ip + ")");

                    // Dtermination du suivant et s'il est le dernier
                    String nextAgent = agentList.get((i + 1) % agentList.size());
                    boolean isLast = (i == agentList.size() - 1);

                    // Cration du container
                    Profile p;
                    ContainerController cc;
                    Runtime rt = Runtime.instance();

                    String firstAgent = agentList.get(0);
                    String firstIp = agentIpMap.get(firstAgent);

                    if (myIP.equals(firstIp)) {
                        // Cette machine heberge l'agent avec le plus petit ID ? main container
                        p = new ProfileImpl();
                        p.setParameter(Profile.GUI, "true");
                        p.setParameter(Profile.LOCAL_PORT, "60000");
                        cc = rt.createMainContainer(p);
                    } else {
                       
                    Thread.sleep(5000); 

                        p = new ProfileImpl(firstIp, 60000, null);
                        cc = rt.createAgentContainer(p);
                        System.out.println("?? Demarrage en tant que container secondaire");
                    }


                    // Prparation des arguments pour SensorAgent
                    Object[] agentArgs = { nextAgent, agentList.toArray(new String[0]), agentIpMap, isLast };

                    // Dmarrage
                    AgentController ac = cc.createNewAgent(agent, "finalagent.SensorAgent", agentArgs);
                    ac.start();

                    System.out.println("Agent " + agent + " lanc sur " + myIP);
                }
            }

            if (!found) {
                System.out.println("? Aucune correspondance IP trouve. Aucun agent lanc.");
            } else {
                System.out.println("? Configuration termine. Agent lanc localement selon l'IP.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
