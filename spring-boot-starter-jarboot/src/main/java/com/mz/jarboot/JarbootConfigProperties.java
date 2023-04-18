package com.mz.jarboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author majianzheng
 */
@ConfigurationProperties(JarbootConfigProperties.PREFIX)
public class JarbootConfigProperties {

    /**
     * Prefix of {@link JarbootConfigProperties}.
     */
    public static final String PREFIX = "spring.jarboot";

    private boolean failedAutoExit = true;
    private String serverAddr = "127.0.0.1:9899";
    private String username = "jarboot";
    private String password = "jarboot";
    private boolean enabled = true;

    public boolean isFailedAutoExit() {
        return failedAutoExit;
    }

    public void setFailedAutoExit(boolean failedAutoExit) {
        this.failedAutoExit = failedAutoExit;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
