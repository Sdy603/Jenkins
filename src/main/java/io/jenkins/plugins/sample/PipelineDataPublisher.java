package io.jenkins.plugins.sample;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.json.JSONObject;

@Extension
public class PipelineDataPublisher extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(PipelineDataPublisher.class.getName());

    public static String generateUUID() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    @Override
    public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {
        super.onCompleted(run, listener);

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            listener.getLogger().println("Jenkins instance is not available.");
            LOGGER.log(Level.FINE, "Jenkins instance is not available, skipping run data publishing");
            return;
        }

        // prepare data
        String jobName = run.getParent().getFullName();
        String referenceId = generateUUID();
        Integer startTime = (int) (run.getStartTimeInMillis() / 1000);
        Integer duration = (int) (run.getDuration() / 1000);
        Integer finishTime = startTime + duration;
        Result result = run.getResult();
        String status;
        if (result != null) {
            status = result.toString().toLowerCase();
        } else {
            status = "unknown";
        }

        EnvVars env;
        try {
            env = run.getEnvironment(listener);
        } catch (IOException | InterruptedException e) {
            listener.getLogger().println("Failed to retrieve environment variables: " + e.getMessage());
            LOGGER.log(Level.FINE, "Failed to retrieve environment variables", e);
            env = new EnvVars();
        }

        String sourceUrl = env.getOrDefault("GIT_URL", "");
        String repository = extractRepositoryName(sourceUrl);
        String commitSha = env.getOrDefault("GIT_COMMIT", "");
        String headBranch = env.getOrDefault("BRANCH_NAME", env.getOrDefault("GIT_BRANCH", ""));
        String prNumber = env.getOrDefault("CHANGE_ID", "");
        String email = env.getOrDefault("GIT_AUTHOR_EMAIL", env.getOrDefault("CHANGE_AUTHOR_EMAIL", ""));

        // Fetch Api Key
        CredentialUtil credentialManager = new CredentialUtil();
        String authToken = credentialManager.getSecretToken("dx_token", listener);
        if (authToken == null) {
            listener.getLogger().println("Authentication token not found for key: dx_token");
            LOGGER.log(Level.FINE, "Missing dx_token credential, skipping run data publishing");
            return;
        }
        String path = credentialManager.getSecretToken("dx_path", listener);
        if (path == null) {
            listener.getLogger().println("Authentication token not found for key: dx_path");
            LOGGER.log(Level.FINE, "Missing dx_path credential, skipping run data publishing");
            return;
        }

        // Print extracted data
        listener.getLogger().println("Sending run data to DX:");
        listener.getLogger().println("pipeline_name: " + jobName);
        listener.getLogger().println("pipeline_source: Jenkins");
        listener.getLogger().println("reference_id: " + referenceId);
        listener.getLogger().println("started_at: " + startTime);
        listener.getLogger().println("finished_at: " + finishTime);
        listener.getLogger().println("status: " + status);
        listener.getLogger().println("source_url: " + sourceUrl);
        listener.getLogger().println("repository: " + repository);
        listener.getLogger().println("commit_sha: " + commitSha);
        listener.getLogger().println("head_branch: " + headBranch);
        listener.getLogger().println("pr_number: " + prNumber);
        listener.getLogger().println("email: " + email);

        JSONObject payload = new JSONObject();
        payload.put("pipeline_name", jobName);
        payload.put("pipeline_source", "Jenkins");
        payload.put("reference_id", referenceId);
        payload.put("started_at", startTime);
        payload.put("finished_at", finishTime);
        payload.put("status", status);
        payload.put("source_url", sourceUrl);
        payload.put("repository", repository);
        payload.put("commit_sha", commitSha);
        payload.put("head_branch", headBranch);
        payload.put("pr_number", prNumber);
        payload.put("email", email);

        DxDataSender.sendData(
                path + "/api/pipelineRuns.sync",
                payload.toString(),
                authToken,
                listener);
    }

    private static String extractRepositoryName(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            return "";
        }
        String repoName = sourceUrl.substring(sourceUrl.lastIndexOf('/') + 1);
        if (repoName.endsWith(".git")) {
            repoName = repoName.substring(0, repoName.length() - 4);
        }
        return repoName;
    }
}
