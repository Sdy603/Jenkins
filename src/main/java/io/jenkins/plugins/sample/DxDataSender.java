package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.security.ACL;
import hudson.model.TaskListener;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/** Simple HTTP client for sending data to DX. */
public class DxDataSender {

    private static final Logger LOGGER = Logger.getLogger(DxDataSender.class.getName());

    private final DxGlobalConfiguration config;
    private final TaskListener listener;

    public DxDataSender(DxGlobalConfiguration config, TaskListener listener) {
        this.config = config;
        this.listener = listener;
    }

    public void send(String payload) {
        String credentialsId = config.getDxTokenCredentialId();
        if (credentialsId == null || credentialsId.isBlank()) {
            listener.getLogger().println("DX: API token credential not configured. Skipping.");
            return;
        }
        String dxPath = config.getDxPath();
        if (dxPath == null || dxPath.isBlank()) {
            listener.getLogger().println("DX: API base path not configured. Skipping.");
            return;
        }

        String fullUrl = dxPath + "/api/pipelineRuns.sync";
        String dxToken = resolveToken(credentialsId);
        if (dxToken == null) {
            listener.getLogger().println("DX: credentials not found for ID: " + credentialsId);
            return;
        }
        if (config.isDebugLogging()) {
            listener.getLogger().println("DX: using credential ID: " + credentialsId);
            listener.getLogger().println("DX: sending to URL: " + fullUrl);
            listener.getLogger().println("DX: payload: " + payload);
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(fullUrl);
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
            conn.setRequestProperty("Authorization", "Bearer " + dxToken);
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

    public static String resolveToken(String credentialsId) {
        if (credentialsId == null || credentialsId.isBlank()) {
            return null;
        }
        Jenkins jenkins = Jenkins.get();
        if (jenkins == null) {
            return null;
        }
        List<StringCredentials> creds = CredentialsProvider.lookupCredentials(
                StringCredentials.class,
                jenkins,
                ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList());
        StringCredentials match = CredentialsMatchers.firstOrNull(creds, CredentialsMatchers.withId(credentialsId));
        if (match != null) {
            return match.getSecret().getPlainText();
        }
        return null;
    }
}

