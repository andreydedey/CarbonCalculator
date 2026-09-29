-- ============================================================
-- Seed data — runs after every Flyway migration
-- Truncates all tables and re-populates from scratch
-- ============================================================

-- Reset everything (order respects FK constraints)
TRUNCATE laboratory_schedule, academic_period_shift, academic_period_holiday, academic_period,
         laboratory_equipment, configuration, monitor, equipment_model,
         laboratory, user_institution, app_user, institution
CASCADE;

-- ======================== USERS ========================
-- All passwords: 'password'

INSERT INTO app_user (id, name, email, password_hash, is_admin)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Admin', 'admin@admin.com',
   '$2a$10$kcIXNpYdP1F1btV2qKAs5O799fRY9zBDTd52Tlw/1Bf72e/QoHKeG', true),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab', 'Maria Silva', 'maria@ufpa.br',
   '$2a$10$kcIXNpYdP1F1btV2qKAs5O799fRY9zBDTd52Tlw/1Bf72e/QoHKeG', false),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaac', 'João Santos', 'joao@ufpa.br',
   '$2a$10$kcIXNpYdP1F1btV2qKAs5O799fRY9zBDTd52Tlw/1Bf72e/QoHKeG', false),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaad', 'Ana Oliveira', 'ana@unicamp.br',
   '$2a$10$kcIXNpYdP1F1btV2qKAs5O799fRY9zBDTd52Tlw/1Bf72e/QoHKeG', false);

-- ==================== INSTITUTIONS =====================

INSERT INTO institution (id, name, acronym, city, state)
VALUES
  ('11111111-1111-1111-1111-111111111111',
   'Universidade Federal do Pará', 'UFPA', 'Belém', 'PA'),
  ('22222222-2222-2222-2222-222222222222',
   'Universidade Estadual de Campinas', 'UNICAMP', 'Campinas', 'SP');

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
;

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
  ;

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
  ;

  INSERT INTO equipment_model (id, institution_id, name, equipment_type, processor, tdp_watts, core_count, memory_gb, gpu_model, gpu_tdp_watts, has_integrated_screen, description)
  VALUES
    ('dddddddd-0001-0001-0001-000000000003',
     '11111111-1111-1111-1111-111111111111',
     'Lenovo ThinkStation P340', 'Desktop',
     'Intel Core i7-11700', 65, 8, 32,
     'NVIDIA RTX 3060', 170, false,
     'Workstation para pesquisa em IA e deep learning')
  ;

  -- Monitors
  INSERT INTO monitor (id, institution_id, name, watts)
  VALUES
    ('eeeeeeee-0001-0001-0001-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'Dell P2422H 24"', 21),
    ('eeeeeeee-0001-0001-0001-000000000002',
     '11111111-1111-1111-1111-111111111111',
     'Dell E2220H 22"', 18)
  ;

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
  ;

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
  ;

  -- Academic Period: 2025.1
  INSERT INTO academic_period (id, institution_id, name, start_date, end_date)
  VALUES
    ('aaaa0001-0001-0001-0001-000000000001',
     '11111111-1111-1111-1111-111111111111',
     '2025.1', '2025-03-10', '2025-07-18')
  ;

  -- Shifts for 2025.1: Manhã 5×50+10, Tarde 5×50+10, Noite 4×50+10 (disabled)
  INSERT INTO academic_period_shift (id, academic_period_id, shift_type, start_time, end_time, classes_per_day, class_duration_minutes, break_duration_minutes, active_days, enabled)
  VALUES
    ('55550001-0001-0001-0001-000000000001',
     'aaaa0001-0001-0001-0001-000000000001',
     'MORNING', '07:30', '11:50', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550001-0001-0001-0001-000000000002',
     'aaaa0001-0001-0001-0001-000000000001',
     'AFTERNOON', '13:30', '17:50', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550001-0001-0001-0001-000000000003',
     'aaaa0001-0001-0001-0001-000000000001',
     'EVENING', '18:50', '22:20', 4, 50, 10, '{1,2,3,4,5}', false)
  ;

  -- Holidays for 2025.1 (with type)
  INSERT INTO academic_period_holiday (id, academic_period_id, date, description, type)
  VALUES
    ('bbbb0001-0001-0001-0001-000000000001',
     'aaaa0001-0001-0001-0001-000000000001',
     '2025-04-18', 'Sexta-feira Santa', 'NATIONAL'),
    ('bbbb0001-0001-0001-0001-000000000002',
     'aaaa0001-0001-0001-0001-000000000001',
     '2025-04-21', 'Tiradentes', 'NATIONAL'),
    ('bbbb0001-0001-0001-0001-000000000003',
     'aaaa0001-0001-0001-0001-000000000001',
     '2025-05-01', 'Dia do Trabalho', 'NATIONAL'),
    ('bbbb0001-0001-0001-0001-000000000004',
     'aaaa0001-0001-0001-0001-000000000001',
     '2025-06-19', 'Corpus Christi', 'NATIONAL'),
    ('bbbb0001-0001-0001-0001-000000000005',
     'aaaa0001-0001-0001-0001-000000000001',
     '2025-03-03', 'Carnaval', 'RECESS'),
    ('bbbb0001-0001-0001-0001-000000000006',
     'aaaa0001-0001-0001-0001-000000000001',
     '2025-03-04', 'Carnaval', 'RECESS'),
    ('bbbb0001-0001-0001-0001-000000000007',
     'aaaa0001-0001-0001-0001-000000000001',
     '2025-03-05', 'Quarta de Cinzas', 'RECESS')
  ;

  -- Schedule: LABCOMP-01 — Tarde seg-sex, slots 1-5 (todas as aulas)
  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots)
  VALUES
    ('cccc0001-0001-0001-0001-000000000001',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 1, '{1,2,3,4,5}'),
    ('cccc0001-0001-0001-0001-000000000002',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 2, '{1,2,3,4,5}'),
    ('cccc0001-0001-0001-0001-000000000003',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 3, '{1,2,3,4,5}'),
    ('cccc0001-0001-0001-0001-000000000004',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 4, '{1,2,3,4,5}'),
    ('cccc0001-0001-0001-0001-000000000005',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 5, '{1,2,3,4,5}')
  ;

  -- Schedule: LABCOMP-02 — Manhã slots 1-5 + Tarde slots 1-3, seg-sex
  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots)
  VALUES
    -- Manhã seg-sex (5 slots)
    ('cccc0001-0001-0001-0001-000000000011',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 1, '{1,2,3,4,5}'),
    ('cccc0001-0001-0001-0001-000000000012',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 2, '{1,2,3,4,5}'),
    ('cccc0001-0001-0001-0001-000000000013',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 3, '{1,2,3,4,5}'),
    ('cccc0001-0001-0001-0001-000000000014',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 4, '{1,2,3,4,5}'),
    ('cccc0001-0001-0001-0001-000000000015',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 5, '{1,2,3,4,5}'),
    -- Tarde seg-sex (3 de 5 slots)
    ('cccc0001-0001-0001-0001-000000000016',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 1, '{1,2,3}'),
    ('cccc0001-0001-0001-0001-000000000017',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 2, '{1,2,3}'),
    ('cccc0001-0001-0001-0001-000000000018',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 3, '{1,2,3}'),
    ('cccc0001-0001-0001-0001-000000000019',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 4, '{1,2,3}'),
    ('cccc0001-0001-0001-0001-000000000020',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 5, '{1,2,3}')
  ;

  -- LABIA: no schedule (AC-064 — zero hours)

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
  ;

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
  ;

  -- Monitors
  INSERT INTO monitor (id, institution_id, name, watts)
  VALUES
    ('eeeeeeee-0002-0002-0002-000000000001',
     '22222222-2222-2222-2222-222222222222',
     'LG 24MK430H 24"', 25)
  ;

  -- Configurations
  INSERT INTO configuration (id, institution_id, equipment_model_id, operating_system, monitor_id)
  VALUES
    -- HP ProDesk 400 G7 + Windows 11 + LG 24MK430H
    ('ffffffff-0002-0002-0002-000000000001',
     '22222222-2222-2222-2222-222222222222',
     'dddddddd-0002-0002-0002-000000000001', 'Windows 11',
     'eeeeeeee-0002-0002-0002-000000000001')
  ;

  -- Dell Inspiron (notebook, tela integrada — sem monitor)
  INSERT INTO configuration (id, institution_id, equipment_model_id, operating_system)
  VALUES
    ('ffffffff-0002-0002-0002-000000000002',
     '22222222-2222-2222-2222-222222222222',
     'dddddddd-0002-0002-0002-000000000002', 'Windows 11')
  ;

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
  ;

  -- Academic Period: 2025.1
  INSERT INTO academic_period (id, institution_id, name, start_date, end_date)
  VALUES
    ('aaaa0002-0002-0002-0002-000000000001',
     '22222222-2222-2222-2222-222222222222',
     '2025.1', '2025-03-10', '2025-07-11')
  ;

  -- Shifts for 2025.1: Manhã 5×50+10, Tarde 4×50+10
  INSERT INTO academic_period_shift (id, academic_period_id, shift_type, start_time, end_time, classes_per_day, class_duration_minutes, break_duration_minutes, active_days, enabled)
  VALUES
    ('55550002-0002-0002-0002-000000000001',
     'aaaa0002-0002-0002-0002-000000000001',
     'MORNING', '08:00', '12:20', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550002-0002-0002-0002-000000000002',
     'aaaa0002-0002-0002-0002-000000000001',
     'AFTERNOON', '14:00', '17:30', 4, 50, 10, '{1,2,3,4,5}', true)
  ;

  -- Holidays for 2025.1 (with type)
  INSERT INTO academic_period_holiday (id, academic_period_id, date, description, type)
  VALUES
    ('bbbb0002-0002-0002-0002-000000000001',
     'aaaa0002-0002-0002-0002-000000000001',
     '2025-04-18', 'Sexta-feira Santa', 'NATIONAL'),
    ('bbbb0002-0002-0002-0002-000000000002',
     'aaaa0002-0002-0002-0002-000000000001',
     '2025-04-21', 'Tiradentes', 'NATIONAL'),
    ('bbbb0002-0002-0002-0002-000000000003',
     'aaaa0002-0002-0002-0002-000000000001',
     '2025-05-01', 'Dia do Trabalho', 'NATIONAL'),
    ('bbbb0002-0002-0002-0002-000000000004',
     'aaaa0002-0002-0002-0002-000000000001',
     '2025-06-19', 'Corpus Christi', 'NATIONAL')
  ;

  -- Schedule: LCC-A — Manhã slots 1-5 + Tarde slots 1-4, seg-sex
  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots)
  VALUES
    ('cccc0002-0002-0002-0002-000000000001',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 1, '{1,2,3,4,5}'),
    ('cccc0002-0002-0002-0002-000000000002',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 2, '{1,2,3,4,5}'),
    ('cccc0002-0002-0002-0002-000000000003',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 3, '{1,2,3,4,5}'),
    ('cccc0002-0002-0002-0002-000000000004',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 4, '{1,2,3,4,5}'),
    ('cccc0002-0002-0002-0002-000000000005',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 5, '{1,2,3,4,5}'),
    ('cccc0002-0002-0002-0002-000000000006',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 1, '{1,2,3,4}'),
    ('cccc0002-0002-0002-0002-000000000007',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 2, '{1,2,3,4}'),
    ('cccc0002-0002-0002-0002-000000000008',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 3, '{1,2,3,4}'),
    ('cccc0002-0002-0002-0002-000000000009',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 4, '{1,2,3,4}'),
    ('cccc0002-0002-0002-0002-000000000010',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 5, '{1,2,3,4}')
  ;

  -- Schedule: LCC-B — Tarde seg-qua-sex, slots 1-4
  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots)
  VALUES
    ('cccc0002-0002-0002-0002-000000000011',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000002', 1, '{1,2,3,4}'),
    ('cccc0002-0002-0002-0002-000000000012',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000002', 3, '{1,2,3,4}'),
    ('cccc0002-0002-0002-0002-000000000013',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000002', 5, '{1,2,3,4}')
  ;

END $$;

-- ======================== GRANTS ========================
-- The 'app' role (non-superuser) is used by the application so that
-- RLS policies are enforced. Grant DML on all tables and usage on sequences.
DO $$
BEGIN
  IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'app') THEN
    EXECUTE (
      SELECT string_agg('GRANT SELECT, INSERT, UPDATE, DELETE ON ' || quote_ident(tablename) || ' TO app;', E'\n')
      FROM pg_tables WHERE schemaname = 'public'
    );
    EXECUTE (
      SELECT coalesce(string_agg('GRANT USAGE, SELECT ON SEQUENCE ' || quote_ident(sequencename) || ' TO app;', E'\n'), '')
      FROM pg_sequences WHERE schemaname = 'public'
    );
  END IF;
END $$;
