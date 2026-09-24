-- Seed: admin user (password: 'password')
INSERT INTO app_user (name, email, password_hash, is_admin)
VALUES ('admin', 'admin@admin.com', '$2a$10$kcIXNpYdP1F1btV2qKAs5O799fRY9zBDTd52Tlw/1Bf72e/QoHKeG', true)
ON CONFLICT (email) DO NOTHING;
