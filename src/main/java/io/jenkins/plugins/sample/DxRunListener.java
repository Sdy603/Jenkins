package io.jenkins.plugins.sample;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.git.util.BuildData;
import javax.annotation.Nonnull;
import org.json.JSONObject;

/** Listener that publishes pipeline run metadata to the DX API. */
@Extension
public class DxRunListener extends RunListener<Run<?, ?>> {

    @Override
    public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {
        Result result = run.getResult();
        if (result == null || !result.equals(Result.SUCCESS)) {
            return;
        }

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
            listener.getLogger().println("DX: build filtered out.");
            return;
        }

        long start = run.getStartTimeInMillis();
        long finish = start + run.getDuration();
        String status = mapResult(result);

        String repositoryName = extractRepositoryName(repoUrl);

        JSONObject payload = new JSONObject();
        payload.put("pipeline_name", jobName);
        payload.put("pipeline_source", "jenkins");
        payload.put("reference_id", String.valueOf(run.getNumber()));
        payload.put("started_at", start);
        payload.put("finished_at", finish);
        payload.put("status", status);
        payload.put("repository", repositoryName);
        payload.put("source_url", repoUrl);
        payload.put("branch", branch);
        payload.put("commit_sha", commitSha);
        payload.put("pr_number", prNumber);
        payload.put("email", email);

        DxDataSender dxSender = new DxDataSender(config, listener);
        dxSender.send(payload.toString(), run);
    }

    static String mapResult(Result result) {
        if (result == null) {
            return "unknown";
        }
        if (result.equals(Result.SUCCESS)) {
            return "success";
        } else if (result.equals(Result.FAILURE)) {
            return "failure";
        } else if (result.equals(Result.ABORTED)) {
            return "cancelled"; // DX API uses 'cancelled' with two Ls
        } else if (result.equals(Result.UNSTABLE)) {
            return "failure";
        } else {
            return "unknown";
        }
    }

    private static String extractRepositoryName(String repoUrl) {
        if (repoUrl == null || repoUrl.isEmpty()) {
            return "";
        }
        String cleaned = repoUrl.replaceAll("\\.git$", "");
        String[] parts = cleaned.split("[/:]");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }
        return cleaned;
    }
}

