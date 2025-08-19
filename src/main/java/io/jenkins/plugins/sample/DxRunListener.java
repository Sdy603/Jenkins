package io.jenkins.plugins.sample;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.model.listeners.RunListener;
import hudson.plugins.git.util.BuildData;
import hudson.tasks.Mailer;
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

        String headBranch = "";
        if (buildData != null && buildData.getLastBuiltRevision() != null
                && !buildData.getLastBuiltRevision().getBranches().isEmpty()) {
            headBranch = buildData.getLastBuiltRevision().getBranches().iterator().next().getName();
        }
        if (headBranch == null || headBranch.isEmpty()) {
            headBranch = env.getOrDefault(
                    "GITHUB_HEAD_REF",
                    env.getOrDefault(
                            "CHANGE_BRANCH",
                            env.getOrDefault("GIT_BRANCH", env.getOrDefault("BRANCH_NAME", ""))));
        }
        if (headBranch != null && headBranch.startsWith("origin/")) {
            headBranch = headBranch.substring("origin/".length());
        }

        String prNumber = env.getOrDefault("CHANGE_ID", "");

        String email = "";
        for (Cause cause : run.getCauses()) {
            if (cause instanceof Cause.UserIdCause) {
                Cause.UserIdCause userCause = (Cause.UserIdCause) cause;
                try {
                    String causeEmail = userCause.getUserEmail();
                    if (causeEmail != null && !causeEmail.isEmpty()) {
                        email = causeEmail;
                        break;
                    }
                } catch (NoSuchMethodError ignored) {
                    // getUserEmail not available
                }
                String userId = userCause.getUserId();
                if (userId != null) {
                    User u = User.getById(userId, false);
                    if (u != null) {
                        Mailer.UserProperty prop = u.getProperty(Mailer.UserProperty.class);
                        if (prop != null && prop.getAddress() != null) {
                            email = prop.getAddress();
                            break;
                        }
                    }
                }
            }
        }
        if (email.isEmpty()) {
            try {
                Executor exec = run.getExecutor();
                if (exec != null && exec.getOwner() != null && exec.getOwner().getUser() != null) {
                    User u = exec.getOwner().getUser();
                    if (u != null) {
                        Mailer.UserProperty prop = u.getProperty(Mailer.UserProperty.class);
                        if (prop != null && prop.getAddress() != null) {
                            email = prop.getAddress();
                        }
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        String jobName = run.getParent().getFullName();
        if (!config.shouldProcess(repoUrl, jobName, headBranch)) {
            listener.getLogger().println("DX: build filtered out.");
            return;
        }

        long start = run.getStartTimeInMillis() / 1000;
        long finish = (run.getStartTimeInMillis() + run.getDuration()) / 1000;
        String status = mapResult(result);
        if (status == null || status.isEmpty()) {
            status = "unknown";
        }

        String repositoryName = extractRepositoryName(repoUrl);

        String pipelineName = jobName;
        if (pipelineName == null || pipelineName.isEmpty()) {
            pipelineName = "jenkins-" + jobName;
        }
        String referenceId = jobName + " #" + run.getNumber();
        String sourceId = jobName;

        JSONObject payload = new JSONObject();
        payload.put("pipeline_name", pipelineName);
        payload.put("pipeline_source", "jenkins");
        payload.put("reference_id", referenceId);
        payload.put("source_id", sourceId);
        payload.put("started_at", start);
        payload.put("finished_at", finish);
        payload.put("status", status);
        payload.put("repository", repositoryName);
        payload.put("source_url", repoUrl);
        payload.put("head_branch", headBranch != null ? headBranch : "");
        payload.put("commit_sha", commitSha != null ? commitSha : "");
        if (prNumber != null && !prNumber.isEmpty()) {
            payload.put("pr_number", prNumber);
        }
        payload.put("email", email != null ? email : "");

        System.out.println("DX Payload:");
        System.out.println(payload.toString(2));

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
        return parts[parts.length - 1];
    }
}

