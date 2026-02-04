package io.jenkins.plugins.sample;

import hudson.Extension;
import hudson.util.FormValidation;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

@Extension
public class DxGlobalConfiguration extends GlobalConfiguration {

    private String dxBaseUrl;
    private String pipelineSource = "Jenkins";
    private String includeRepoPattern = "";
    private String includeJobPattern = ".*";
    private String includeBranchPattern = ".*";

    public DxGlobalConfiguration() {
        load();
    }

    public static DxGlobalConfiguration get() {
        return GlobalConfiguration.all().get(DxGlobalConfiguration.class);
    }

    @Nullable
    public String getDxBaseUrl() {
        return dxBaseUrl;
    }

    @DataBoundSetter
    public void setDxBaseUrl(@Nullable String dxBaseUrl) {
        this.dxBaseUrl = dxBaseUrl;
        save();
    }

    @Nullable
    public String getPipelineSource() {
        return pipelineSource;
    }

    @DataBoundSetter
    public void setPipelineSource(@Nullable String pipelineSource) {
        this.pipelineSource = pipelineSource;
        save();
    }

    @Nullable
    public String getIncludeRepoPattern() {
        return includeRepoPattern != null ? includeRepoPattern : "";
    }

    @DataBoundSetter
    public void setIncludeRepoPattern(@Nullable String includeRepoPattern) {
        this.includeRepoPattern = includeRepoPattern;
        save();
    }

    @Nullable
    public String getIncludeJobPattern() {
        return includeJobPattern != null ? includeJobPattern : ".*";
    }

    @DataBoundSetter
    public void setIncludeJobPattern(@Nullable String includeJobPattern) {
        this.includeJobPattern = includeJobPattern;
        save();
    }

    @Nullable
    public String getIncludeBranchPattern() {
        return includeBranchPattern != null ? includeBranchPattern : ".*";
    }

    @DataBoundSetter
    public void setIncludeBranchPattern(@Nullable String includeBranchPattern) {
        this.includeBranchPattern = includeBranchPattern;
        save();
    }

    public boolean isConfigured() {
        return dxBaseUrl != null && !dxBaseUrl.trim().isEmpty();
    }

    public boolean shouldProcess(@Nullable String repo, @Nullable String jobName, @Nullable String branch) {
        if (!isConfigured()) {
            return false;
        }
        return !isDenied(getIncludeRepoPattern(), repo)
                && matches(getIncludeJobPattern(), jobName)
                && matches(getIncludeBranchPattern(), branch);
    }

    private boolean isDenied(@Nullable String pattern, @Nullable String value) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        if (value == null) {
            return false;
        }
        try {
            final Pattern compiled = Pattern.compile(pattern);
            return compiled.matcher(value).find();
        } catch (Exception e) {
            return false; // fail open if regex is invalid
        }
    }

    private boolean matches(@Nullable String pattern, @Nullable String value) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        try {
            final Pattern compiled = Pattern.compile(pattern);
            return compiled.matcher(value).find();
        } catch (Exception e) {
            return true; // fail open if regex is invalid
        }
    }

    @Override
    public boolean configure(@Nonnull StaplerRequest req, @Nonnull JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public FormValidation doCheckDxBaseUrl(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("DX API base URL is empty.");
        }
        if (!value.startsWith("https://")) {
            return FormValidation.warning("DX base URL should start with https://");
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckIncludeRepoPattern(@QueryParameter String value) {
        return validateRegex(value, "Repository denylist pattern");
    }

    public FormValidation doCheckIncludeJobPattern(@QueryParameter String value) {
        return validateRegex(value, "Job name pattern");
    }

    public FormValidation doCheckIncludeBranchPattern(@QueryParameter String value) {
        return validateRegex(value, "Branch pattern");
    }

    private FormValidation validateRegex(@Nullable String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            return FormValidation.ok();
        }
        try {
            Pattern.compile(value);
            return FormValidation.ok();
        } catch (Exception e) {
            return FormValidation.error(fieldName + " has an invalid regex pattern.");
        }
    }
}
