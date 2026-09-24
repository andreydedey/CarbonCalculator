-- ============================================================
-- Seed data — runs after every Flyway migration
-- All INSERTs use ON CONFLICT DO NOTHING so re-runs are safe
-- ============================================================

-- Fixed UUIDs for referencing across tables
-- Institutions
-- UFPA:    11111111-1111-1111-1111-111111111111
-- UNICAMP: 22222222-2222-2222-2222-222222222222

-- Users (password: 'password')
-- admin:       aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
-- Maria Silva: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab
-- João Santos: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaac
-- Ana Oliveira:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaad

-- ======================== USERS ========================

INSERT INTO app_user (id, name, email, password_hash, is_admin)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Admin', 'admin@admin.com',
   '$2a$10$kcIXNpYdP1F1btV2qKAs5O799fRY9zBDTd52Tlw/1Bf72e/QoHKeG', true),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab', 'Maria Silva', 'maria@ufpa.br',
   '$2a$10$kcIXNpYdP1F1btV2qKAs5O799fRY9zBDTd52Tlw/1Bf72e/QoHKeG', false),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaac', 'João Santos', 'joao@ufpa.br',
   '$2a$10$kcIXNpYdP1F1btV2qKAs5O799fRY9zBDTd52Tlw/1Bf72e/QoHKeG', false),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaad', 'Ana Oliveira', 'ana@unicamp.br',
   '$2a$10$kcIXNpYdP1F1btV2qKAs5O799fRY9zBDTd52Tlw/1Bf72e/QoHKeG', false)
ON CONFLICT (email) DO NOTHING;

-- ==================== INSTITUTIONS =====================

INSERT INTO institution (id, name, acronym, city, state)
VALUES
  ('11111111-1111-1111-1111-111111111111',
   'Universidade Federal do Pará', 'UFPA', 'Belém', 'PA'),
  ('22222222-2222-2222-2222-222222222222',
   'Universidade Estadual de Campinas', 'UNICAMP', 'Campinas', 'SP')
ON CONFLICT (acronym) DO NOTHING;

-- ================ USER ↔ INSTITUTION ===================

INSERT INTO user_institution (id, user_id, user_email, institution_id, role, status)
VALUES
  -- Admin is MANAGER in both
  ('bbbbbbbb-0001-0001-0001-000000000001',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin@admin.com',
   '11111111-1111-1111-1111-111111111111', 'MANAGER', 'ACTIVE'),
  ('bbbbbbbb-0001-0001-0001-000000000002',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin@admin.com',
   '22222222-2222-2222-2222-222222222222', 'MANAGER', 'ACTIVE'),
  -- Maria is MANAGER at UFPA
  ('bbbbbbbb-0001-0001-0001-000000000003',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab', 'maria@ufpa.br',
   '11111111-1111-1111-1111-111111111111', 'MANAGER', 'ACTIVE'),
  -- João is RESEARCHER at UFPA
  ('bbbbbbbb-0001-0001-0001-000000000004',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaac', 'joao@ufpa.br',
   '11111111-1111-1111-1111-111111111111', 'RESEARCHER', 'ACTIVE'),
  -- Ana is MANAGER at UNICAMP
  ('bbbbbbbb-0001-0001-0001-000000000005',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaad', 'ana@unicamp.br',
   '22222222-2222-2222-2222-222222222222', 'MANAGER', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- ============================================================
-- RLS-protected tables: set app.current_institution per block
-- ============================================================

-- ======================== UFPA ==========================

DO $$
BEGIN
  PERFORM set_config('app.current_institution', '11111111-1111-1111-1111-111111111111', true);

  -- Laboratories
  INSERT INTO laboratory (id, institution_id, name, description)
  VALUES
    ('cccccccc-0001-0001-0001-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'LABCOMP-01',
     'Laboratório de Computação 01 — Bloco A, térreo. Usado para aulas de graduação em Ciência da Computação.'),
    ('cccccccc-0001-0001-0001-000000000002',
     '11111111-1111-1111-1111-111111111111',
     'LABCOMP-02',
     'Laboratório de Computação 02 — Bloco A, 2º andar. Aulas práticas de Engenharia de Software.'),
    ('cccccccc-0001-0001-0001-000000000003',
     '11111111-1111-1111-1111-111111111111',
     'LABIA',
     'Laboratório de Inteligência Artificial — Bloco C. Equipado com workstations para treinamento de modelos.')
  ON CONFLICT DO NOTHING;

  -- Equipment models
  INSERT INTO equipment_model (id, institution_id, name, equipment_type, processor, tdp_watts, core_count, memory_gb, has_integrated_screen)
  VALUES
    ('dddddddd-0001-0001-0001-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'Dell OptiPlex 7090', 'Desktop',
     'Intel Core i7-10700', 65, 8, 16, false),
    ('dddddddd-0001-0001-0001-000000000002',
     '11111111-1111-1111-1111-111111111111',
     'Dell OptiPlex 3090', 'Desktop',
     'Intel Core i5-10500', 65, 6, 8, false)
  ON CONFLICT DO NOTHING;

  INSERT INTO equipment_model (id, institution_id, name, equipment_type, processor, tdp_watts, core_count, memory_gb, gpu_model, gpu_tdp_watts, has_integrated_screen, description)
  VALUES
    ('dddddddd-0001-0001-0001-000000000003',
     '11111111-1111-1111-1111-111111111111',
     'Lenovo ThinkStation P340', 'Desktop',
     'Intel Core i7-11700', 65, 8, 32,
     'NVIDIA RTX 3060', 170, false,
     'Workstation para pesquisa em IA e deep learning')
  ON CONFLICT DO NOTHING;

  -- Monitors
  INSERT INTO monitor (id, institution_id, name, watts)
  VALUES
    ('eeeeeeee-0001-0001-0001-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'Dell P2422H 24"', 21),
    ('eeeeeeee-0001-0001-0001-000000000002',
     '11111111-1111-1111-1111-111111111111',
     'Dell E2220H 22"', 18)
  ON CONFLICT DO NOTHING;

  -- Configurations
  INSERT INTO configuration (id, institution_id, equipment_model_id, operating_system, monitor_id)
  VALUES
    -- OptiPlex 7090 + Windows 10 + Dell P2422H
    ('ffffffff-0001-0001-0001-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'dddddddd-0001-0001-0001-000000000001', 'Windows 10',
     'eeeeeeee-0001-0001-0001-000000000001'),
    -- OptiPlex 3090 + Linux + Dell E2220H
    ('ffffffff-0001-0001-0001-000000000002',
     '11111111-1111-1111-1111-111111111111',
     'dddddddd-0001-0001-0001-000000000002', 'Linux',
     'eeeeeeee-0001-0001-0001-000000000002'),
    -- ThinkStation P340 + Linux + Dell P2422H
    ('ffffffff-0001-0001-0001-000000000003',
     '11111111-1111-1111-1111-111111111111',
     'dddddddd-0001-0001-0001-000000000003', 'Linux',
     'eeeeeeee-0001-0001-0001-000000000001')
  ON CONFLICT DO NOTHING;

  -- Laboratory ↔ Configuration (equipment links)
  INSERT INTO laboratory_equipment (id, laboratory_id, configuration_id, quantity)
  VALUES
    -- LABCOMP-01: 30 Dell OptiPlex 7090 (Windows 10)
    ('aabbccdd-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000001',
     'ffffffff-0001-0001-0001-000000000001', 30),
    -- LABCOMP-02: 25 Dell OptiPlex 3090 (Linux)
    ('aabbccdd-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002',
     'ffffffff-0001-0001-0001-000000000002', 25),
    -- LABIA: 10 ThinkStation P340 (Linux)
    ('aabbccdd-0001-0001-0001-000000000003',
     'cccccccc-0001-0001-0001-000000000003',
     'ffffffff-0001-0001-0001-000000000003', 10),
    -- LABIA: 5 Dell OptiPlex 7090 (Windows 10)
    ('aabbccdd-0001-0001-0001-000000000004',
     'cccccccc-0001-0001-0001-000000000003',
     'ffffffff-0001-0001-0001-000000000001', 5)
  ON CONFLICT DO NOTHING;

END $$;

-- ====================== UNICAMP =========================

DO $$
BEGIN
  PERFORM set_config('app.current_institution', '22222222-2222-2222-2222-222222222222', true);

  -- Laboratories
  INSERT INTO laboratory (id, institution_id, name, description)
  VALUES
    ('cccccccc-0002-0002-0002-000000000001',
     '22222222-2222-2222-2222-222222222222',
     'LCC-A',
     'Laboratório de Ciência da Computação A — Instituto de Computação, térreo. 40 estações para graduação.'),
    ('cccccccc-0002-0002-0002-000000000002',
     '22222222-2222-2222-2222-222222222222',
     'LCC-B',
     'Laboratório de Ciência da Computação B — Instituto de Computação, 1º andar. Misto desktop/notebook.')
  ON CONFLICT DO NOTHING;

  -- Equipment models
  INSERT INTO equipment_model (id, institution_id, name, equipment_type, processor, tdp_watts, core_count, memory_gb, has_integrated_screen)
  VALUES
    ('dddddddd-0002-0002-0002-000000000001',
     '22222222-2222-2222-2222-222222222222',
     'HP ProDesk 400 G7', 'Desktop',
     'Intel Core i5-10500', 65, 6, 8, false),
    ('dddddddd-0002-0002-0002-000000000002',
     '22222222-2222-2222-2222-222222222222',
     'Dell Inspiron 15 3000', 'Notebook',
     'Intel Core i5-1135G7', 28, 4, 8, true)
  ON CONFLICT DO NOTHING;

  -- Monitors
  INSERT INTO monitor (id, institution_id, name, watts)
  VALUES
    ('eeeeeeee-0002-0002-0002-000000000001',
     '22222222-2222-2222-2222-222222222222',
     'LG 24MK430H 24"', 25)
  ON CONFLICT DO NOTHING;

  -- Configurations
  INSERT INTO configuration (id, institution_id, equipment_model_id, operating_system, monitor_id)
  VALUES
    -- HP ProDesk 400 G7 + Windows 11 + LG 24MK430H
    ('ffffffff-0002-0002-0002-000000000001',
     '22222222-2222-2222-2222-222222222222',
     'dddddddd-0002-0002-0002-000000000001', 'Windows 11',
     'eeeeeeee-0002-0002-0002-000000000001')
  ON CONFLICT DO NOTHING;

  -- Dell Inspiron (notebook, tela integrada — sem monitor)
  INSERT INTO configuration (id, institution_id, equipment_model_id, operating_system)
  VALUES
    ('ffffffff-0002-0002-0002-000000000002',
     '22222222-2222-2222-2222-222222222222',
     'dddddddd-0002-0002-0002-000000000002', 'Windows 11')
  ON CONFLICT DO NOTHING;

  -- Laboratory ↔ Configuration
  INSERT INTO laboratory_equipment (id, laboratory_id, configuration_id, quantity)
  VALUES
    -- LCC-A: 40 HP ProDesk (Windows 11)
    ('aabbccdd-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001',
     'ffffffff-0002-0002-0002-000000000001', 40),
    -- LCC-B: 15 HP ProDesk (Windows 11)
    ('aabbccdd-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000002',
     'ffffffff-0002-0002-0002-000000000001', 15),
    -- LCC-B: 5 Dell Inspiron notebook (Windows 11)
    ('aabbccdd-0002-0002-0002-000000000003',
     'cccccccc-0002-0002-0002-000000000002',
     'ffffffff-0002-0002-0002-000000000002', 5)
  ON CONFLICT DO NOTHING;

END $$;
