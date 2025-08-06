package io.jenkins.plugins.sample;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import hudson.model.TaskListener;

public class DxDataSender {
    public static void sendData(String apiUrl, String dataJson, String authToken, TaskListener listener) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();

            // Set request method to POST
            connection.setRequestMethod("POST");

            // Set request headers
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + authToken);

            // Enable input and output streams
            connection.setDoOutput(true);

            // Send the request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = dataJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check the response
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            listener.getLogger().println("Response Code: " + responseCode);

            InputStream responseStream = responseCode >= 300 ? connection.getErrorStream() : connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
                response.append(System.lineSeparator());
            }

            if (responseCode >= 300) {
                JSONObject jsonResponse = new JSONObject(response.toString());
                System.out.println("Error Message: " + jsonResponse.getString("error"));
                listener.getLogger().println("Error Message: " + jsonResponse.getString("error"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
