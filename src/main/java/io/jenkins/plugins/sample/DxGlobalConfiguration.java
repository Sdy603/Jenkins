package io.jenkins.plugins.sample;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundSetter;

/** Global configuration for the DX data sharing plugin. */
@Extension
public class DxGlobalConfiguration extends GlobalConfiguration {

    private String dxBaseUrl;
    private String includeRepoPattern;
    private String includeJobPattern;
    private String includeBranchPattern;

    public DxGlobalConfiguration() {
        load();
    }

    public static DxGlobalConfiguration get() {
        return GlobalConfiguration.all().get(DxGlobalConfiguration.class);
    }

    @Override
    public String getDisplayName() {
        return "DX Data Sharing Configuration";
    }

    public String getDxBaseUrl() {
        return dxBaseUrl;
    }

    @DataBoundSetter
    public void setDxBaseUrl(String dxBaseUrl) {
        this.dxBaseUrl = dxBaseUrl;
        save();
    }

    public String getIncludeRepoPattern() {
        return includeRepoPattern;
    }

    @DataBoundSetter
    public void setIncludeRepoPattern(String includeRepoPattern) {
        this.includeRepoPattern = includeRepoPattern;
        save();
    }

    public String getIncludeJobPattern() {
        return includeJobPattern;
    }

    @DataBoundSetter
    public void setIncludeJobPattern(String includeJobPattern) {
        this.includeJobPattern = includeJobPattern;
        save();
    }

    public String getIncludeBranchPattern() {
        return includeBranchPattern;
    }

    @DataBoundSetter
    public void setIncludeBranchPattern(String includeBranchPattern) {
        this.includeBranchPattern = includeBranchPattern;
        save();
    }

    public boolean isConfigured() {
        return dxBaseUrl != null && !dxBaseUrl.isBlank();
    }

    public boolean shouldProcess(String repo, String job, String branch) {
        return matchesOrBlank(includeRepoPattern, repo)
                && matchesOrBlank(includeJobPattern, job)
                && matchesOrBlank(includeBranchPattern, branch);
    }

    private boolean matchesOrBlank(String pattern, String value) {
        return pattern == null || pattern.isEmpty() || matches(pattern, value);
    }

    private boolean matches(String pattern, String value) {
        return pattern != null && !pattern.isEmpty() && value != null && value.matches(pattern);
    }
}
