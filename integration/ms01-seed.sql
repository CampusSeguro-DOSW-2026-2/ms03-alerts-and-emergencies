INSERT INTO usuarios (id, primer_nombre, primer_apellido, correo, password_hash, estado)
VALUES (
    'b4051a1f-8f5c-4b79-b57e-dcbf68166abf',
    'Auditor',
    'Integracion',
    'auditor.integracion@escuelaing.edu.co',
    'no-login',
    'ACTIVO'
)
ON CONFLICT (correo) DO NOTHING;

INSERT INTO usuario_rol (usuario_id, rol)
SELECT id, 'AUDITOR'
FROM usuarios
WHERE correo = 'auditor.integracion@escuelaing.edu.co'
ON CONFLICT DO NOTHING;
