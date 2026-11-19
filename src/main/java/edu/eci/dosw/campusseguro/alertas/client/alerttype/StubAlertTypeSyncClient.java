package edu.eci.dosw.campusseguro.alertas.client.alerttype;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Versión local y de pruebas del catálogo de MS06.
 *
 * <p>Devuelve los mismos tipos que la migración inicial para que el servicio funcione sin MS06 y
 * sin red. La lista vacía nunca se simula aquí: la tolerancia a fallos se prueba con dobles.
 */
@Component
@Profile({"local", "test"})
public class StubAlertTypeSyncClient implements AlertTypeSyncClient {

    @Override
    public List<RemoteAlertType> fetch() {
        return List.of(
                new RemoteAlertType("SISMO", "Sismo"),
                new RemoteAlertType("INCENDIO", "Incendio"),
                new RemoteAlertType("DISTURBIOS", "Disturbios"),
                new RemoteAlertType("INUNDACION", "Inundación"),
                new RemoteAlertType("FUGA_QUIMICA", "Fuga de Sustancias Químicas"),
                new RemoteAlertType("OTRO", "Otro"));
    }
}
