package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Collections;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/** Utility class for retrieving credentials from Jenkins. */
public class CredentialUtil {

    private CredentialUtil() {}

    /**
     * Returns the secret text for the given credential ID.
     */
    public static String getSecretToken(String credentialId, Run<?, ?> run, TaskListener listener) {
        if (credentialId == null || credentialId.isEmpty()) {
            if (listener != null) {
                listener.getLogger().println("DX: credentials ID is not configured.");
            }
            return null;
        }
        StringCredentials creds = CredentialsProvider.findCredentialById(
                credentialId,
                StringCredentials.class,
                run,
                Collections.<DomainRequirement>emptyList());
        if (creds != null) {
            return creds.getSecret().getPlainText();
        }
        if (listener != null) {
            listener.getLogger().println("DX: credentials not found for ID: " + credentialId);
        }
        return null;
    }
}

