package io.jenkins.plugins.sample;

import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.regex.Pattern;

@Extension
public class DxGlobalConfiguration extends GlobalConfiguration {

    private String apiKey;
    private String apiUrl;
    private String includeRegex;

    public DxGlobalConfiguration() {
        load();
    }

    public static DxGlobalConfiguration get() {
        return GlobalConfiguration.all().get(DxGlobalConfiguration.class);
    }

    public String getApiKey() {
        return apiKey;
    }

    @DataBoundSetter
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        save();
    }

    public String getApiUrl() {
        return apiUrl;
    }

    @DataBoundSetter
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
        save();
    }

    // Needed by DxDataSender
    public String getDxBaseUrl() {
        return getApiUrl();
    }

    public String getIncludeRegex() {
        return includeRegex;
    }

    @DataBoundSetter
    public void setIncludeRegex(String includeRegex) {
        this.includeRegex = includeRegex;
        save();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() &&
               apiUrl != null && !apiUrl.isEmpty();
    }

    public boolean shouldProcess(String repoUrl, String jobName, String branch) {
        if (!isConfigured()) {
            return false;
        }
        if (includeRegex == null || includeRegex.isEmpty()) {
            return true;
        }

        String target = (repoUrl != null ? repoUrl : "") + "|" +
                        (jobName != null ? jobName : "") + "|" +
                        (branch != null ? branch : "");

        try {
            return Pattern.compile(includeRegex).matcher(target).find();
        } catch (Exception e) {
            return true; // Fail open if regex is invalid
        }
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public FormValidation doCheckApiKey() {
        return (apiKey == null || apiKey.isEmpty())
                ? FormValidation.warning("API key is empty.")
                : FormValidation.ok();
    }

    public FormValidation doCheckApiUrl() {
        return (apiUrl == null || apiUrl.isEmpty())
                ? FormValidation.warning("API URL is empty.")
                : FormValidation.ok();
    }
}
