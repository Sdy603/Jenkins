import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import java.util.Collections;

public class CredentialUtil {

    public static StandardUsernamePasswordCredentials lookupCredentials() {
        return CredentialsProvider
            .lookupCredentials(
                StandardUsernamePasswordCredentials.class,
                (hudson.model.ItemGroup<?>) Jenkins.get(),  // Explicit cast
                ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList()
            )
            .stream()
            .findFirst()
            .orElse(null);
    }
}
