package edu.eci.dosw.campusseguro.alertas.security;

public enum Permission {

    ALERT_MANAGE("ms03:alert:manage"),
    ALERT_MANAGE_DRILL("ms03:alert:manage_drill"),
    ALERT_READ_HISTORY("ms03:alert:read_history"),
    ALERT_RECEIVE("ms03:alert:receive");

    private final String authority;

    Permission(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }
}
