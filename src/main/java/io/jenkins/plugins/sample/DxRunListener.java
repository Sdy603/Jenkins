package io.jenkins.plugins.sample;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.git.util.BuildData;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.json.JSONObject;

/** Listener that publishes pipeline run metadata to the DX API. */
@Extension
public class DxRunListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(DxRunListener.class.getName());

    @Override
    public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {
        DxGlobalConfiguration config = DxGlobalConfiguration.get();
        if (config == null || !config.isConfigured()) {
            listener.getLogger().println("DX: plugin not configured. Skipping.");
            return;
        }

        EnvVars env;
        try {
            env = run.getEnvironment(listener);
        } catch (Exception e) {
            env = new EnvVars();
        }

        BuildData buildData = run.getAction(BuildData.class);
        String repoUrl = buildData != null && !buildData.getRemoteUrls().isEmpty()
                ? buildData.getRemoteUrls().iterator().next()
                : env.get("GIT_URL", "");
        String commitSha = buildData != null && buildData.getLastBuiltRevision() != null
                ? buildData.getLastBuiltRevision().getSha1String()
                : env.get("GIT_COMMIT", "");
        String branch = env.getOrDefault("BRANCH_NAME", env.getOrDefault("GIT_BRANCH", ""));
        String prNumber = env.getOrDefault("CHANGE_ID", "");
        String email = env.getOrDefault("GIT_AUTHOR_EMAIL", env.getOrDefault("CHANGE_AUTHOR_EMAIL", ""));

        String jobName = run.getParent().getFullName();
        if (!config.shouldProcess(repoUrl, jobName, branch)) {
            if (config.isDebugLogging()) {
                listener.getLogger().println("DX: build filtered out.");
            }
            return;
        }

        String token = CredentialUtil.getSecretToken(config.getCredentialsId(), listener);
        if (token == null || token.isEmpty()) {
            listener.getLogger().println("DX: API token not found. Skipping.");
            return;
        }

        long start = run.getStartTimeInMillis();
        long finish = start + run.getDuration();
        String status = mapResult(run.getResult());

        String sourceUrl = "";
        Jenkins jenkins = Jenkins.get();
        if (jenkins != null && jenkins.getRootUrl() != null) {
            sourceUrl = jenkins.getRootUrl() + run.getUrl();
        }

        JSONObject payload = new JSONObject();
        payload.put("pipeline_name", jobName);
        payload.put("pipeline_source", "jenkins");
        payload.put("reference_id", String.valueOf(run.getNumber()));
        payload.put("started_at", start);
        payload.put("finished_at", finish);
        payload.put("status", status);
        payload.put("repository", repoUrl);
        payload.put("commit_sha", commitSha);
        payload.put("pr_number", prNumber);
        payload.put("email", email);
        payload.put("source_url", sourceUrl);

        DxDataSender sender = new DxDataSender(config, listener);
        sender.send("https://api.getdx.com/pipelineRuns.sync", payload.toString(), token);
    }

    static String mapResult(Result result) {
        if (result == null) {
            return "failed";
        }
        if (result.equals(Result.SUCCESS)) {
            return "success";
        }
        if (result.equals(Result.ABORTED)) {
            return "aborted";
        }
        return "failed";
    }
}

