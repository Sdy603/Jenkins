package io.jenkins.plugins.sample;

import hudson.model.TaskListener;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

public class DxDataSender {

    private static final Logger LOGGER = Logger.getLogger(DxDataSender.class.getName());

    public static void sendData(String endpoint, String payload, String token, TaskListener listener) {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes());
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                listener.getLogger().println("Successfully sent pipeline run data to DX.");
            } else {
                listener.getLogger().println("Failed to send data to DX. Response code: " + responseCode);
            }
        } catch (Exception e) {
            listener.getLogger().println("Error sending data to DX: " + e.getMessage());
            LOGGER.warning("Error sending data to DX: " + e.getMessage());
        }
    }
}
