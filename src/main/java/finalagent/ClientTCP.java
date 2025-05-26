package finalagent;

import java.io.*;
import java.net.*;

public class ClientTCP {
    public static void main(String[] args) {
        String ipRaspberry = "192.168.0.108"; // IP de ta Pi
        int port = 12345;

        try (Socket socket = new Socket(ipRaspberry, port)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );
            String message = reader.readLine();
            int valeur = Integer.parseInt(message);
            System.out.println("Entier reçu : " + valeur);
        } catch (IOException | NumberFormatException ex) {
            ex.printStackTrace();
        }
    }
}
