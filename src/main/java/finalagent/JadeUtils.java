package finalagent;

import java.net.Socket;
import java.net.HttpURLConnection;
import java.net.URL;

public class JadeUtils {
    public static boolean isJadeMTPAvailable(String host, int port) {
        try {
            URL url = new URL("http://" + host + ":" + port + "/acc");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
