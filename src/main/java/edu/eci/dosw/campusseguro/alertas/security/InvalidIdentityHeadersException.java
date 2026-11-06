package edu.eci.dosw.campusseguro.alertas.security;

import org.springframework.security.core.AuthenticationException;

public class InvalidIdentityHeadersException extends AuthenticationException {

    public InvalidIdentityHeadersException(String message) {
        super(message);
    }
}
