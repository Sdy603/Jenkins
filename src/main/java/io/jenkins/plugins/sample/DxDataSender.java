package io.jenkins.plugins.sample;

import hudson.model.TaskListener;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Simple HTTP client for sending data to DX. */
public class DxDataSender {

    private static final Logger LOGGER = Logger.getLogger(DxDataSender.class.getName());

    private final DxGlobalConfiguration config;
    private final TaskListener listener;

    public DxDataSender(DxGlobalConfiguration config, TaskListener listener) {
        this.config = config;
        this.listener = listener;
    }

    public void send(String endpoint, String payload, String token) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            HttpURLConnection localConn;
            if (config.hasProxy()) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
                localConn = (HttpURLConnection) url.openConnection(proxy);
            } else {
                localConn = (HttpURLConnection) url.openConnection();
            }
            conn = localConn;

            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                if (config.isDebugLogging()) {
                    listener.getLogger().println("DX: payload sent successfully. Response code: " + code);
                }
            } else {
                listener.getLogger().println("DX: failed to send payload. Response code: " + code);
            }
        } catch (Exception e) {
            listener.getLogger().println("DX: error sending data - " + e.getMessage());
            if (config.isDebugLogging()) {
                LOGGER.log(Level.WARNING, "Error sending data to DX", e);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}

