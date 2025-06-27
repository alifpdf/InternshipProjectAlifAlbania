package finalagent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.*;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.Enumeration;
public class SimpleIPReader {

    public static String findIpWithSmallestLastOctet(String filePath) {
        List<String> ipAddresses = new ArrayList<>();

        // Lire les adresses IP depuis le fichier
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                ipAddresses.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Aucune adresse IP trouvée.";
        }

        // Trouver l'adresse IP avec le plus petit quatrième octet
        if (ipAddresses.isEmpty()) {
            return "Aucune adresse IP trouvée.";
        }

        String minIp = ipAddresses.get(0);
        int minOctet = Integer.parseInt(minIp.split("\\.")[3]);

        for (String ip : ipAddresses) {
            int currentOctet = Integer.parseInt(ip.split("\\.")[3]);
            if (currentOctet < minOctet) {
                minOctet = currentOctet;
                minIp = ip;
            }
        }

        return minIp;
    }
    
    public static String getLocalIp() {
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
    
    public static boolean equality(){
        
        String result = findIpWithSmallestLastOctet("active_ips.txt");
        String result1=getLocalIp();
        return result.equals(result1);
        }

    // Exemple d'utilisation
    public static void main(String[] args) {
        String result = findIpWithSmallestLastOctet("active_ips.txt");
        String result1=getLocalIp();
        System.out.println(result==result1);
    }
}
