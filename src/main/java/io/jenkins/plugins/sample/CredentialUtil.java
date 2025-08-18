package io.jenkins.plugins.sample;

import hudson.model.TaskListener;
import java.util.logging.Logger;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class CredentialUtil {

    private static final Logger LOGGER = Logger.getLogger(CredentialUtil.class.getName());

    public String getSecretToken(String id, TaskListener listener) {
        return Secret.toString(
            com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                    com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials.class,
                    Jenkins.getInstance(),
                    null,
                    null
                ),
                com.cloudbees.plugins.credentials.CredentialsMatchers.withId(id)
            ).getPassword()
        );
    }
}
