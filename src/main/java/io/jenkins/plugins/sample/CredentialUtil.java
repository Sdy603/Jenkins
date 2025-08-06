package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.security.ACL;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import hudson.model.TaskListener;

public class CredentialUtil {

    public static String getSecretToken(String credentialId, TaskListener listener) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            listener.getLogger().println("Jenkins instance is not available.");
            return null;
        }

        // Fetch the secret text credential
        StringCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StringCredentials.class, jenkins, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialId));

        if (credentials != null) {
            // Return the secret text
            return credentials.getSecret().getPlainText();
        } else {
            return null;
        }
    }
}
