package io.jenkins.plugins.sample;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.json.JSONObject;

@Extension
public class PipelineDataPublisher extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(PipelineDataPublisher.class.getName());

    private String getGitRemoteUrl(Run<?, ?> run, TaskListener listener) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "config", "--get", "remote.origin.url");
            pb.directory(run.getRootDir().getParentFile());
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String url = reader.readLine();
            int exitCode = process.waitFor();
            if (exitCode == 0 && url != null) {
                return url;
            }
        } catch (Exception e) {
            listener.getLogger().println("Failed to retrieve Git remote URL: " + e.getMessage());
            LOGGER.log(Level.WARNING, "Error retrieving Git remote URL", e);
        }
        return "";
    }

    private String extractRepositoryName(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isEmpty()) return "";
        String[] parts = sourceUrl.split("[/:]");
        String repoWithGit = parts[parts.length - 1];
        return repoWithGit.replaceAll("\\.git$", "");
    }

    @Override
    public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {
        super.onCompleted(run, listener);

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            listener.getLogger().println("Jenkins instance is not available.");
            return;
        }

        // Extract build data
        String jobName = run.getParent().getFullName();
        String referenceId = UUID.randomUUID().toString();
        int startTime = (int) (run.getStartTimeInMillis() / 1000);
        int duration = (int) (run.getDuration() / 1000);
        int finishTime = startTime + duration;
        String status = run.getResult() != null ? run.getResult().toString().toLowerCase() : "unknown";

        EnvVars env;
        try {
            env = run.getEnvironment(listener);
        } catch (IOException | InterruptedException e) {
            listener.getLogger().println("Failed to retrieve environment variables: " + e.getMessage());
            env = new EnvVars();
        }

        String sourceUrl = getGitRemoteUrl(run, listener);
        String repository = extractRepositoryName(sourceUrl);
        String commitSha = env.getOrDefault("GIT_COMMIT", "");
        String headBranch = env.getOrDefault("GIT_BRANCH", "");
        String prNumber = env.getOrDefault("CHANGE_ID", "");
        String email = env.getOrDefault("GIT_AUTHOR_EMAIL", env.getOrDefault("CHANGE_AUTHOR_EMAIL", ""));

        CredentialUtil credentialManager = new CredentialUtil();
        String authToken = credentialManager.getSecretToken("dx_token", listener);
        String path = credentialManager.getSecretToken("dx_path", listener);

        if (authToken == null || path == null) {
            listener.getLogger().println("Missing credentials dx_token or dx_path.");
            return;
        }

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

        listener.getLogger().println("Sending pipeline metadata to DX...");
        DxDataSender.sendData(path + "/api/pipelineRuns.sync", payload.toString(), authToken, listener);
    }
}
