package com.cwnupaa.backend.dto;

public class SmtpConfigDTO {
    private boolean enabled;
    private String host;
    private int port = 587;
    private String username;
    private String password;
    private String protocol = "smtp";
    private boolean auth = true;
    private boolean sslEnabled;
    private boolean startTlsEnabled = true;
    private String fromName = "BESTI ACM";
    private boolean passwordConfigured;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public boolean isAuth() { return auth; }
    public void setAuth(boolean auth) { this.auth = auth; }
    public boolean isSslEnabled() { return sslEnabled; }
    public void setSslEnabled(boolean sslEnabled) { this.sslEnabled = sslEnabled; }
    public boolean isStartTlsEnabled() { return startTlsEnabled; }
    public void setStartTlsEnabled(boolean startTlsEnabled) { this.startTlsEnabled = startTlsEnabled; }
    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public boolean isPasswordConfigured() { return passwordConfigured; }
    public void setPasswordConfigured(boolean passwordConfigured) { this.passwordConfigured = passwordConfigured; }
}
