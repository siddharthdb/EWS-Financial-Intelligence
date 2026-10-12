package org.ewsfi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local-development credentials. The defaults are for the dev stack only and must be replaced
 * before any shared environment.
 */
@ConfigurationProperties(prefix = "ews.local-security")
public class LocalSecurityProperties {

    private String analystPassword = "analyst";
    private String supervisorPassword = "supervisor";

    public String getAnalystPassword() {
        return analystPassword;
    }

    public void setAnalystPassword(String analystPassword) {
        this.analystPassword = analystPassword;
    }

    public String getSupervisorPassword() {
        return supervisorPassword;
    }

    public void setSupervisorPassword(String supervisorPassword) {
        this.supervisorPassword = supervisorPassword;
    }
}
