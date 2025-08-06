package io.jenkins.plugins.sample;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.util.UUID;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;

@Extension
public class PipelineDataPublisher extends RunListener<Run<?, ?>> {

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

        // Fetch Api Key
        CredentialUtil credentialManager = new CredentialUtil();
        String authToken = credentialManager.getSecretToken("dx_token", listener);
        if (authToken == null) {
            listener.getLogger().println("Authentication token not found for key: dx_token");
            return;
        }
        String path = credentialManager.getSecretToken("dx_path", listener);
        if (path == null) {
            listener.getLogger().println("Authentication token not found for key: dx_path");
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

        DxDataSender.sendData(
                path + "/api/pipelineRuns.sync",
                "{" + "\"pipeline_name\": \""
                        + jobName + "\"," + "\"pipeline_source\": \"Jenkins\","
                        + "\"reference_id\": \""
                        + referenceId + "\"," + "\"started_at\": \""
                        + startTime + "\"," + "\"finished_at\": \""
                        + finishTime + "\"," + "\"status\": \""
                        + status + "\"" + "}",
                authToken,
                listener);
    }
}
