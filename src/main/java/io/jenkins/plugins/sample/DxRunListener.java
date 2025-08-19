package io.jenkins.plugins.sample;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.git.util.BuildData;
import java.io.File;
import javax.annotation.Nonnull;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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

        String branchName = "";
        if (buildData != null && buildData.getLastBuiltRevision() != null
                && !buildData.getLastBuiltRevision().getBranches().isEmpty()) {
            branchName =
                    buildData.getLastBuiltRevision().getBranches().iterator().next().getName();
        }
        if (branchName == null || branchName.isEmpty()) {
            branchName =
                    env.getOrDefault(
                            "GITHUB_HEAD_REF",
                            env.getOrDefault(
                                    "CHANGE_BRANCH",
                                    env.getOrDefault(
                                            "GIT_BRANCH", env.getOrDefault("BRANCH_NAME", ""))));
        }
        if (branchName != null && !branchName.isEmpty()) {
            branchName =
                    branchName
                            .replaceFirst("^refs/heads/", "")
                            .replaceFirst("^refs/remotes/origin/", "")
                            .replaceFirst("^origin/", "");
        }

        String prNumber = env.getOrDefault("CHANGE_ID", "");

        String userEmail = "";
        if (commitSha != null && !commitSha.isEmpty()) {
            String gitDir = env.get("GIT_DIR");
            if (gitDir == null || gitDir.isEmpty()) {
                String workTree = env.getOrDefault("GIT_WORK_TREE", env.get("WORKSPACE"));
                if (workTree != null && !workTree.isEmpty()) {
                    gitDir = workTree + "/.git";
                }
            }
            if (gitDir != null && !gitDir.isEmpty()) {
                try (Repository repo =
                                new FileRepositoryBuilder()
                                        .setGitDir(new File(gitDir))
                                        .readEnvironment()
                                        .build();
                        RevWalk walk = new RevWalk(repo)) {
                    RevCommit commit = walk.parseCommit(ObjectId.fromString(commitSha));
                    userEmail = commit.getAuthorIdent().getEmailAddress();
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        String jobName = run.getParent().getFullName();
        if (!config.shouldProcess(repoUrl, jobName, branchName)) {
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
        payload.put("head_branch", branchName);
        payload.put("commit_sha", commitSha != null ? commitSha : "");
        if (prNumber != null && !prNumber.isEmpty()) {
            payload.put("pr_number", prNumber);
        }
        payload.put("email", userEmail);

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

