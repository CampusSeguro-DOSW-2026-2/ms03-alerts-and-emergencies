package edu.eci.dosw.campusseguro.alertas.client.catalog;

import java.util.Set;

public record ResolvedUser(
        String id,
        String email,
        String name,
        Set<String> roles,
        String state) {

    public ResolvedUser(String id, String email, String name) {
        this(id, email, name, Set.of(), "ACTIVO");
    }

    public boolean active() {
        return "ACTIVO".equalsIgnoreCase(state);
    }

    public String displayName() {
        return name;
    }
}
