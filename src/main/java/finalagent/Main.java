package finalagent;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.GUI, "true");
            p.setParameter(Profile.LOCAL_PORT, "60000");
            ContainerController cc = rt.createMainContainer(p);

            // Connexion à la base
            Connection conn =
                    DriverManager.
                    getConnection("jdbc:postgresql://localhost:5432/postgres",
                    "postgres", "1234");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id FROM kit ORDER BY id");

            List<String> agentNames = new ArrayList<>();
            while (rs.next()) {
                int id = rs.getInt("id");
                agentNames.add("Z" + id);
            }

            String[] allAgents = agentNames.toArray(new String[0]);

            for (int i = 0; i < allAgents.length; i++) {
                String currentAgent = allAgents[i];
                String nextAgent = allAgents[(i + 1) % allAgents.length];
                Boolean lastAgent = false;

                if(i==allAgents.length-1) {
                    lastAgent = true;
                }

                Object[] agentArgs = {nextAgent, allAgents,lastAgent };



                AgentController ac = cc.createNewAgent(currentAgent,
                        "finalagent.SensorAgent", agentArgs);
                ac.start();
            }

            System.out.println("Tous les agents ont été créés dynamiquement à partir des Kits");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
