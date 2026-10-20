package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import java.util.UUID;

public interface ConfirmationService {

    ConfirmationResult confirm(UUID alertId, AccessActor actor);
}
