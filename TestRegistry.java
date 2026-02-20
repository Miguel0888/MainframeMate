import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TestRegistry {
    public static void main(String[] args) throws Exception {
        // Test 1: list endpoint
        System.out.println("=== LIST ===");
        String list = httpGet("https://registry.modelcontextprotocol.io/v0.1/servers?limit=3");
        System.out.println(list.substring(0, Math.min(list.length(), 2000)));

        System.out.println("\n=== DETAIL ===");
        // Test 2: detail endpoint
        String detail = httpGet("https://registry.modelcontextprotocol.io/v0.1/servers/" +
                URLEncoder.encode("@anthropic/claude-code", "UTF-8") + "/versions/latest");
        System.out.println(detail.substring(0, Math.min(detail.length(), 2000)));
    }

    static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        int status = conn.getResponseCode();
        System.out.println("HTTP " + status + " from " + urlStr);
        InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}

