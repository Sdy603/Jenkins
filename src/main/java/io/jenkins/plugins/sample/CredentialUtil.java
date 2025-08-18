package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Run;
import java.util.Collections;
import java.util.logging.Logger;

/** Utility class for retrieving credentials from Jenkins. */
public class CredentialUtil {

    private static final Logger LOGGER = Logger.getLogger(CredentialUtil.class.getName());

    private CredentialUtil() {}

    /**
     * Resolves username/password credentials for the given ID using the run context.
     *
     * @param credentialsId ID of the Jenkins credentials to look up
     * @param run the build run providing context for the lookup
     * @return the matching credentials or {@code null} if not found
     */
    public static StandardUsernamePasswordCredentials getCredentials(String credentialsId, Run<?, ?> run) {
        if (credentialsId == null || credentialsId.isEmpty()) {
            LOGGER.info("DX: credentials ID is not configured.");
            return null;
        }
        if (run == null) {
            LOGGER.warning("DX: run context is null. Cannot look up credentials.");
            return null;
        }
        StandardUsernamePasswordCredentials creds =
                CredentialsProvider.findCredentialById(
                        credentialsId,
                        StandardUsernamePasswordCredentials.class,
                        run,
                        Collections.<DomainRequirement>emptyList());
        if (creds == null) {
            LOGGER.warning("DX: credentials not found for ID: " + credentialsId);
        }
        return creds;
    }
}

