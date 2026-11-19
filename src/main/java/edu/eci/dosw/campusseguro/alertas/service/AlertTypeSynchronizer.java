package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.client.alerttype.AlertTypeSyncClient;
import edu.eci.dosw.campusseguro.alertas.client.alerttype.RemoteAlertType;
import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import edu.eci.dosw.campusseguro.alertas.repository.AlertTypeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replica en MS03 el catálogo de tipos de alerta publicado por MS06.
 *
 * <p>Solo toca las filas con {@code source=MS06}: actualiza las que siguen vigentes, crea las
 * nuevas y desactiva las que MS06 dejó de publicar. Nunca borra filas, porque las alertas
 * históricas siguen apuntando a ellas, y nunca modifica las filas locales como {@code OPERATIVA}.
 *
 * <p>Una respuesta vacía o nula se trata como un fallo: no se escribe nada y se conserva la copia
 * local. Las excepciones del cliente se propagan para que el llamador las registre.
 */
@Service
public class AlertTypeSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(AlertTypeSynchronizer.class);

    private static final int MAX_CODE_LENGTH = 40;

    private static final int MAX_LABEL_LENGTH = 80;

    private static final int MIN_LABEL_LENGTH = 3;

    private final AlertTypeRepository repository;

    private final AlertTypeSyncClient client;

    public AlertTypeSynchronizer(AlertTypeRepository repository, AlertTypeSyncClient client) {
        this.repository = repository;
        this.client = client;
    }

    /**
     * Sincroniza el catálogo local con MS06 en una transacción independiente.
     *
     * @return {@code true} si se aplicó una respuesta utilizable; {@code false} si MS06 no devolvió
     *         datos y no se escribió nada
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean synchronize() {
        List<RemoteAlertType> remote = client.fetch();

        if (remote == null || remote.isEmpty()) {
            log.warn("MS06 devolvio un catalogo de tipos vacio; se conserva la copia local de MS03");
            return false;
        }

        Instant now = Instant.now();
        Map<String, AlertTypeEntity> allRows = new LinkedHashMap<>();
        Map<String, AlertTypeEntity> remoteRows = new LinkedHashMap<>();
        List<AlertTypeEntity> existing = repository.findAllByOrderByDisplayOrderAsc();
        if (existing != null) {
            for (AlertTypeEntity entity : existing) {
                if (entity == null) {
                    continue;
                }
                allRows.put(entity.getCode(), entity);
                if (AlertTypeEntity.SOURCE_MS06.equals(entity.getSource())) {
                    remoteRows.put(entity.getCode(), entity);
                }
            }
        }

        int nextDisplayOrder = remoteRows.values().stream()
                .mapToInt(AlertTypeEntity::getDisplayOrder)
                .max()
                .orElse(0) + 1;

        Set<String> published = new LinkedHashSet<>();
        List<AlertTypeEntity> touched = new ArrayList<>();

        for (RemoteAlertType item : remote) {
            if (item == null || item.code() == null || item.code().isBlank()) {
                log.warn("MS06 devolvio un tipo de alerta sin codigo; se ignora");
                continue;
            }

            String code = item.code().strip();
            if (code.length() > MAX_CODE_LENGTH) {
                log.warn("MS06 devolvio un codigo de tipo de alerta demasiado largo: {}", code);
                continue;
            }
            AlertTypeEntity current = allRows.get(code);
            if (current != null && !AlertTypeEntity.SOURCE_MS06.equals(current.getSource())) {
                log.warn("MS06 intento publicar el tipo local {}; se conserva la fila de MS03", code);
                continue;
            }
            if (!published.add(code)) {
                continue;
            }

            String label = labelFor(item.name(), code);
            AlertTypeEntity entity = remoteRows.get(code);
            if (entity == null) {
                entity = new AlertTypeEntity(
                        code,
                        label,
                        nextDisplayOrder++,
                        true,
                        AlertTypeEntity.SOURCE_MS06);
            } else {
                entity.setLabel(label);
                entity.setActive(true);
            }
            entity.setSource(AlertTypeEntity.SOURCE_MS06);
            entity.setSyncedAt(now);
            touched.add(entity);
        }

        if (published.isEmpty()) {
            log.warn("MS06 no devolvio ningun tipo de alerta utilizable; se conserva la copia local");
            return false;
        }

        for (AlertTypeEntity entity : remoteRows.values()) {
            if (!published.contains(entity.getCode())) {
                entity.setActive(false);
                touched.add(entity);
            }
        }

        repository.saveAll(touched);
        return true;
    }

    private String labelFor(String name, String code) {
        String label = name == null ? "" : name.strip();
        if (label.length() < MIN_LABEL_LENGTH) {
            label = "Tipo " + code;
        }
        return label.length() > MAX_LABEL_LENGTH
                ? label.substring(0, MAX_LABEL_LENGTH)
                : label;
    }
}
