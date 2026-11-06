-- Datos de ejemplo SOLO PARA DESARROLLO: una alerta en cada estado del ciclo de vida.
-- Los IDs de edificios, zonas, roles y usuarios corresponden a src/main/resources/catalog-stub.json.
-- Se carga al crear el volumen; para recargar: docker compose down -v && docker compose up -d
-- La alerta ACTIVE tiene como destinatario el rol STUDENT, así que el token local RECIPIENT
-- (usr-002) puede verla y confirmarla.

INSERT INTO alerts (id, type, title, status, instructions, origin, simulation_id, created_by,
                    activation_at, activated_at, ended_at, archived_at, created_at, updated_at)
VALUES
    ('a0000000-0000-4000-8000-000000000001', 'FIRE', 'Conato de incendio en Laboratorios', 'DRAFT',
     'Evacuar el edificio de Laboratorios por la salida norte y dirigirse al punto de encuentro.',
     'MANUAL', NULL, 'local-coordinator',
     NULL, NULL, NULL, NULL, now() - interval '1 hour', now() - interval '1 hour'),
    ('a0000000-0000-4000-8000-000000000002', 'EARTHQUAKE', 'Simulacro nacional de sismo', 'SCHEDULED',
     'Al sonar la alarma: agacharse, cubrirse y sostenerse. Luego evacuar a la plazoleta.',
     'MANUAL', NULL, 'local-coordinator',
     now() + interval '7 days', NULL, NULL, NULL, now() - interval '2 hours', now() - interval '2 hours'),
    ('a0000000-0000-4000-8000-000000000003', 'SIMULATION', 'Simulacro de evacuación - Bloque estudiantes', 'ACTIVE',
     'Simulacro en curso. Diríjase con calma al punto de encuentro y confirme su recepción.',
     'SIMULATION', 'b0000000-0000-4000-8000-000000000001', 'SERVICE_MS06',
     NULL, now() - interval '10 minutes', NULL, NULL, now() - interval '1 day', now() - interval '10 minutes'),
    ('a0000000-0000-4000-8000-000000000004', 'OTHER', 'Corte de energía en Biblioteca', 'ENDED',
     'Permanezca en su lugar hasta que el personal de seguridad indique la salida.',
     'MANUAL', NULL, 'local-coordinator',
     NULL, now() - interval '3 days', now() - interval '3 days' + interval '2 hours', NULL,
     now() - interval '4 days', now() - interval '3 days' + interval '2 hours'),
    ('a0000000-0000-4000-8000-000000000005', 'EARTHQUAKE', 'Sismo de magnitud 4.8', 'ARCHIVED',
     'Revisar daños estructurales antes de reingresar a los edificios.',
     'MANUAL', NULL, 'local-coordinator',
     NULL, now() - interval '30 days', now() - interval '30 days' + interval '1 hour',
     now() - interval '20 days', now() - interval '31 days', now() - interval '20 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO alert_scope_items (id, alert_id, scope_type, scope_ref_id)
VALUES
    ('c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'BUILDING', 'BLD-003'),
    ('c0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002', 'CAMPUS', NULL),
    ('c0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000003', 'ZONE', 'ZON-001'),
    ('c0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000004', 'BUILDING', 'BLD-002'),
    ('c0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000005', 'CAMPUS', NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO alert_recipients (id, alert_id, recipient_type, recipient_ref_id)
VALUES
    ('d0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'ROLE', 'STUDENT'),
    ('d0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002', 'ROLE', 'STAFF'),
    ('d0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000002', 'USER', 'usr-006'),
    ('d0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000003', 'ROLE', 'STUDENT'),
    ('d0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000004', 'USER', 'usr-003'),
    ('d0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000005', 'ROLE', 'TEACHER')
ON CONFLICT (id) DO NOTHING;

INSERT INTO notification_deliveries (id, alert_id, user_id, email, status, error_reason, sent_at)
VALUES
    ('e0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000003', 'usr-002',
     'andres.rojas@example.edu.co', 'SENT', NULL, now() - interval '10 minutes'),
    ('e0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000004', 'usr-003',
     'marta.pineda@example.edu.co', 'SENT', NULL, now() - interval '3 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO alert_confirmations (id, alert_id, user_id, confirmed_at)
VALUES
    ('f0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000004', 'usr-003',
     now() - interval '3 days' + interval '5 minutes')
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_events (id, alert_id, action, actor, from_status, to_status, details, created_at)
VALUES
    ('90000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
     'CREATE', 'local-coordinator', NULL, 'DRAFT', 'Datos de ejemplo', now() - interval '1 hour'),
    ('90000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002',
     'CREATE', 'local-coordinator', NULL, 'DRAFT', 'Datos de ejemplo', now() - interval '2 hours'),
    ('90000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000002',
     'SCHEDULE', 'local-coordinator', 'DRAFT', 'SCHEDULED', 'Programada a 7 días', now() - interval '2 hours'),
    ('90000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000003',
     'CREATE', 'SERVICE_MS06', NULL, 'DRAFT', 'Datos de ejemplo', now() - interval '1 day'),
    ('90000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000003',
     'ACTIVATE', 'SERVICE_MS06', 'DRAFT', 'ACTIVE', 'Alert activated from DRAFT', now() - interval '10 minutes'),
    ('90000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000003',
     'NOTIFICATION_SENT', 'SYSTEM_NOTIFIER', 'ACTIVE', 'ACTIVE', 'Notificaciones de alerta: enviadas=1, fallidas=0',
     now() - interval '10 minutes'),
    ('90000000-0000-4000-8000-000000000007', 'a0000000-0000-4000-8000-000000000004',
     'CREATE', 'local-coordinator', NULL, 'DRAFT', 'Datos de ejemplo', now() - interval '4 days'),
    ('90000000-0000-4000-8000-000000000008', 'a0000000-0000-4000-8000-000000000004',
     'ACTIVATE', 'local-coordinator', 'DRAFT', 'ACTIVE', 'Alert activated from DRAFT', now() - interval '3 days'),
    ('90000000-0000-4000-8000-000000000009', 'a0000000-0000-4000-8000-000000000004',
     'CONFIRM', 'usr-003', 'ACTIVE', 'ACTIVE', 'Recepción confirmada por usr-003',
     now() - interval '3 days' + interval '5 minutes'),
    ('90000000-0000-4000-8000-000000000010', 'a0000000-0000-4000-8000-000000000004',
     'END', 'local-coordinator', 'ACTIVE', 'ENDED', 'Alert ended', now() - interval '3 days' + interval '2 hours'),
    ('90000000-0000-4000-8000-000000000011', 'a0000000-0000-4000-8000-000000000005',
     'CREATE', 'local-coordinator', NULL, 'DRAFT', 'Datos de ejemplo', now() - interval '31 days'),
    ('90000000-0000-4000-8000-000000000012', 'a0000000-0000-4000-8000-000000000005',
     'ACTIVATE', 'local-coordinator', 'DRAFT', 'ACTIVE', 'Alert activated from DRAFT', now() - interval '30 days'),
    ('90000000-0000-4000-8000-000000000013', 'a0000000-0000-4000-8000-000000000005',
     'END', 'local-coordinator', 'ACTIVE', 'ENDED', 'Alert ended', now() - interval '30 days' + interval '1 hour'),
    ('90000000-0000-4000-8000-000000000014', 'a0000000-0000-4000-8000-000000000005',
     'ARCHIVE', 'local-coordinator', 'ENDED', 'ARCHIVED', 'Alert archived', now() - interval '20 days')
ON CONFLICT (id) DO NOTHING;
