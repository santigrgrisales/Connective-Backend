INSERT INTO roles (name) VALUES ('ROLE_ADMIN');
INSERT INTO roles (name) VALUES ('ROLE_CONSULTOR');


-- Ejemplo: hash BCrypt de "AdminPass123!" (genera el tuyo en ambiente seguro)
INSERT INTO users (email, password, full_name, enabled) VALUES
('admin@example.com', '$2a$12$9sbQusGMmHbN2JzgBv106eBJpl/SyTnVIRqXdQ3MpvPOJQe7nLjJa', 'Administrador', true);


-- Asignar rol admin (ajusta user_id si no es 1)
INSERT INTO users_roles (user_id, role_id) VALUES (1, 1);