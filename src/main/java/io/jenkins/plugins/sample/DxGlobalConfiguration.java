package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import java.util.Collections;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;

/** Global configuration for the DX data sharing plugin. */
@Extension
public class DxGlobalConfiguration extends GlobalConfiguration {

    private String credentialsId;
    private String proxyHost;
    private int proxyPort;
    private boolean debugLogging;
    private String includeRepoPattern;
    private String excludeRepoPattern;
    private String includeJobPattern;
    private String excludeJobPattern;
    private String includeBranchPattern;
    private String excludeBranchPattern;

    public DxGlobalConfiguration() {
        load();
    }

    public static DxGlobalConfiguration get() {
        return GlobalConfiguration.all().get(DxGlobalConfiguration.class);
    }

    public ListBoxModel doFillCredentialsIdItems() {
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                        ACL.SYSTEM,
                        Jenkins.get(),
                        StringCredentials.class,
                        Collections.<DomainRequirement>emptyList(),
                        c -> true);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
        save();
    }

    public String getProxyHost() {
        return proxyHost;
    }

    @DataBoundSetter
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        save();
    }

    public int getProxyPort() {
        return proxyPort;
    }

    @DataBoundSetter
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        save();
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    @DataBoundSetter
    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
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

    public String getExcludeRepoPattern() {
        return excludeRepoPattern;
    }

    @DataBoundSetter
    public void setExcludeRepoPattern(String excludeRepoPattern) {
        this.excludeRepoPattern = excludeRepoPattern;
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

    public String getExcludeJobPattern() {
        return excludeJobPattern;
    }

    @DataBoundSetter
    public void setExcludeJobPattern(String excludeJobPattern) {
        this.excludeJobPattern = excludeJobPattern;
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

    public String getExcludeBranchPattern() {
        return excludeBranchPattern;
    }

    @DataBoundSetter
    public void setExcludeBranchPattern(String excludeBranchPattern) {
        this.excludeBranchPattern = excludeBranchPattern;
        save();
    }

    public boolean hasProxy() {
        return proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0;
    }

    public boolean isConfigured() {
        return credentialsId != null && !credentialsId.isBlank();
    }

    public boolean shouldProcess(String repo, String job, String branch) {
        if (matches(excludeRepoPattern, repo)
                || matches(excludeJobPattern, job)
                || matches(excludeBranchPattern, branch)) {
            return false;
        }
        if (!matchesOrBlank(includeRepoPattern, repo)
                || !matchesOrBlank(includeJobPattern, job)
                || !matchesOrBlank(includeBranchPattern, branch)) {
            return false;
        }
        return true;
    }

    private boolean matchesOrBlank(String pattern, String value) {
        return pattern == null || pattern.isEmpty() || matches(pattern, value);
    }

    private boolean matches(String pattern, String value) {
        return pattern != null && !pattern.isEmpty() && value != null && value.matches(pattern);
    }
}

