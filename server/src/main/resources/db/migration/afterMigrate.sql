-- ============================================================
-- Seed data — runs after every Flyway migration
-- Truncates all tables and re-populates from scratch
-- ============================================================

-- Reset everything (order respects FK constraints)
TRUNCATE emission_snapshot,
         emission_factor, consumption_measurement,
         class_occurrence, laboratory_schedule, academic_period_shift, academic_period_holiday, academic_period,
         laboratory_equipment, configuration, operating_system, monitor, equipment_model,
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

  -- Operating systems
  INSERT INTO operating_system (id, institution_id, name)
  VALUES
    ('0a0a0001-0001-0001-0001-000000000001', '11111111-1111-1111-1111-111111111111', 'Windows 10'),
    ('0a0a0001-0001-0001-0001-000000000002', '11111111-1111-1111-1111-111111111111', 'Linux')
  ;

  -- Configurations
  INSERT INTO configuration (id, institution_id, equipment_model_id, operating_system_id, monitor_id)
  VALUES
    -- OptiPlex 7090 + Windows 10 + Dell P2422H
    ('ffffffff-0001-0001-0001-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'dddddddd-0001-0001-0001-000000000001', '0a0a0001-0001-0001-0001-000000000001',
     'eeeeeeee-0001-0001-0001-000000000001'),
    -- OptiPlex 3090 + Linux + Dell E2220H
    ('ffffffff-0001-0001-0001-000000000002',
     '11111111-1111-1111-1111-111111111111',
     'dddddddd-0001-0001-0001-000000000002', '0a0a0001-0001-0001-0001-000000000002',
     'eeeeeeee-0001-0001-0001-000000000002'),
    -- ThinkStation P340 + Linux + Dell P2422H
    ('ffffffff-0001-0001-0001-000000000003',
     '11111111-1111-1111-1111-111111111111',
     'dddddddd-0001-0001-0001-000000000003', '0a0a0001-0001-0001-0001-000000000002',
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
  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  VALUES
    ('cccc0001-0001-0001-0001-000000000001',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 1, '{1,2,3,4,5}', '{28,30,24,18,26}'),
    ('cccc0001-0001-0001-0001-000000000002',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 2, '{1,2,3,4,5}', '{30,24,18,26,28}'),
    ('cccc0001-0001-0001-0001-000000000003',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 3, '{1,2,3,4,5}', '{24,18,26,28,30}'),
    ('cccc0001-0001-0001-0001-000000000004',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 4, '{1,2,3,4,5}', '{18,26,28,30,24}'),
    ('cccc0001-0001-0001-0001-000000000005',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000001', 5, '{1,2,3,4,5}', '{26,28,30,24,18}')
  ;

  -- Schedule: LABCOMP-02 — Manhã slots 1-5 + Tarde slots 1-3, seg-sex
  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  VALUES
    -- Manhã seg-sex (5 slots)
    ('cccc0001-0001-0001-0001-000000000011',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 1, '{1,2,3,4,5}', '{22,25,18,20,15}'),
    ('cccc0001-0001-0001-0001-000000000012',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 2, '{1,2,3,4,5}', '{25,18,20,15,22}'),
    ('cccc0001-0001-0001-0001-000000000013',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 3, '{1,2,3,4,5}', '{18,20,15,22,25}'),
    ('cccc0001-0001-0001-0001-000000000014',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 4, '{1,2,3,4,5}', '{20,15,22,25,18}'),
    ('cccc0001-0001-0001-0001-000000000015',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000002', 5, '{1,2,3,4,5}', '{15,22,25,18,20}'),
    -- Tarde seg-sex (3 de 5 slots)
    ('cccc0001-0001-0001-0001-000000000016',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 1, '{1,2,3}', '{22,25,18}'),
    ('cccc0001-0001-0001-0001-000000000017',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 2, '{1,2,3}', '{25,18,20}'),
    ('cccc0001-0001-0001-0001-000000000018',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 3, '{1,2,3}', '{18,20,15}'),
    ('cccc0001-0001-0001-0001-000000000019',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 4, '{1,2,3}', '{20,15,22}'),
    ('cccc0001-0001-0001-0001-000000000020',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000002', 5, '{1,2,3}', '{15,22,25}')
  ;

  -- Schedule: LABIA (15 stations) — Tarde ter/qui slots 1-4, Manhã qua slots 1-2
  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  VALUES
    ('cccc0001-0001-0001-0001-000000000021',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000003', 2, '{1,2,3,4}', '{12,15,10,8}'),
    ('cccc0001-0001-0001-0001-000000000022',
     '55550001-0001-0001-0001-000000000002',
     'cccccccc-0001-0001-0001-000000000003', 4, '{1,2,3,4}', '{15,12,12,10}'),
    ('cccc0001-0001-0001-0001-000000000023',
     '55550001-0001-0001-0001-000000000001',
     'cccccccc-0001-0001-0001-000000000003', 3, '{1,2}', '{9,14}')
  ;

  -- Academic Period: 2026.2 (in progress — feeds the realized vs. projected views)
  INSERT INTO academic_period (id, institution_id, name, start_date, end_date)
  VALUES
    ('aaaa0001-0001-0001-0001-000000000002',
     '11111111-1111-1111-1111-111111111111',
     '2026.2', '2026-08-03', '2026-12-11')
  ;

  INSERT INTO academic_period_shift (id, academic_period_id, shift_type, start_time, end_time, classes_per_day, class_duration_minutes, break_duration_minutes, active_days, enabled)
  VALUES
    ('55550001-0001-0001-0001-000000000011',
     'aaaa0001-0001-0001-0001-000000000002',
     'MORNING', '07:30', '11:50', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550001-0001-0001-0001-000000000012',
     'aaaa0001-0001-0001-0001-000000000002',
     'AFTERNOON', '13:30', '17:50', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550001-0001-0001-0001-000000000013',
     'aaaa0001-0001-0001-0001-000000000002',
     'EVENING', '18:50', '22:20', 4, 50, 10, '{1,2,3,4,5}', false)
  ;

  INSERT INTO academic_period_holiday (id, academic_period_id, date, description, type)
  VALUES
    ('bbbb0001-0001-0001-0001-000000000011',
     'aaaa0001-0001-0001-0001-000000000002',
     '2026-09-07', 'Independência do Brasil', 'NATIONAL'),
    ('bbbb0001-0001-0001-0001-000000000012',
     'aaaa0001-0001-0001-0001-000000000002',
     '2026-10-12', 'Nossa Senhora Aparecida', 'NATIONAL'),
    ('bbbb0001-0001-0001-0001-000000000013',
     'aaaa0001-0001-0001-0001-000000000002',
     '2026-11-02', 'Finados', 'NATIONAL'),
    ('bbbb0001-0001-0001-0001-000000000014',
     'aaaa0001-0001-0001-0001-000000000002',
     '2026-11-20', 'Dia da Consciência Negra', 'NATIONAL')
  ;

  -- Same weekly grid as 2025.1
  INSERT INTO laboratory_schedule (shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  SELECT CASE ls.shift_id
           WHEN '55550001-0001-0001-0001-000000000001' THEN '55550001-0001-0001-0001-000000000011'::uuid
           ELSE '55550001-0001-0001-0001-000000000012'::uuid
         END,
         ls.laboratory_id, ls.day_of_week, ls.occupied_slots, ls.stations_used
  FROM laboratory_schedule ls
  WHERE ls.shift_id IN ('55550001-0001-0001-0001-000000000001',
                        '55550001-0001-0001-0001-000000000002');

  -- Exceptions: cancelled, adjusted and extra classes around early October 2026
  INSERT INTO class_occurrence (institution_id, shift_id, laboratory_id, date, slot, stations_used)
  VALUES
    -- LABCOMP-01, Wed afternoon, 2nd class cancelled
    ('11111111-1111-1111-1111-111111111111', '55550001-0001-0001-0001-000000000012',
     'cccccccc-0001-0001-0001-000000000001', '2026-09-30', 2, 0),
    -- LABCOMP-02, Thu morning, 1st class with fewer stations
    ('11111111-1111-1111-1111-111111111111', '55550001-0001-0001-0001-000000000011',
     'cccccccc-0001-0001-0001-000000000002', '2026-10-01', 1, 12),
    -- LABCOMP-02, Fri afternoon, extra 5th class (free in the grid)
    ('11111111-1111-1111-1111-111111111111', '55550001-0001-0001-0001-000000000012',
     'cccccccc-0001-0001-0001-000000000002', '2026-10-02', 5, 20),
    -- LABCOMP-01, Mon afternoon, 1st class with fewer stations
    ('11111111-1111-1111-1111-111111111111', '55550001-0001-0001-0001-000000000012',
     'cccccccc-0001-0001-0001-000000000001', '2026-10-05', 1, 15),
    -- LABCOMP-02, Wed morning, 4th class already known to be cancelled
    ('11111111-1111-1111-1111-111111111111', '55550001-0001-0001-0001-000000000011',
     'cccccccc-0001-0001-0001-000000000002', '2026-10-07', 4, 0)
  ;

  INSERT INTO academic_period (id, institution_id, name, start_date, end_date)
  VALUES ('aaaa0001-0001-0001-0002-000000000001', '11111111-1111-1111-1111-111111111111', '2025.2', '2025-08-11', '2025-12-12');

  INSERT INTO academic_period_shift (id, academic_period_id, shift_type, start_time, end_time, classes_per_day, class_duration_minutes, break_duration_minutes, active_days, enabled)
  VALUES
    ('55550001-0002-0002-0002-000000000001', 'aaaa0001-0001-0001-0002-000000000001', 'MORNING',   '07:30', '11:50', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550001-0002-0002-0002-000000000002', 'aaaa0001-0001-0001-0002-000000000001', 'AFTERNOON', '13:30', '17:50', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550001-0002-0002-0002-000000000003', 'aaaa0001-0001-0001-0002-000000000001', 'EVENING',   '18:50', '22:20', 4, 50, 10, '{1,2,3,4,5}', false);

  INSERT INTO academic_period_holiday (id, academic_period_id, date, description, type)
  VALUES
    ('bbbb0001-0002-0002-0002-000000000001', 'aaaa0001-0001-0001-0002-000000000001', '2025-09-07', 'Dia da Independência',    'NATIONAL'),
    ('bbbb0001-0002-0002-0002-000000000002', 'aaaa0001-0001-0001-0002-000000000001', '2025-11-02', 'Finados',                  'NATIONAL'),
    ('bbbb0001-0002-0002-0002-000000000003', 'aaaa0001-0001-0001-0002-000000000001', '2025-11-15', 'Proclamação da República', 'NATIONAL'),
    ('bbbb0001-0002-0002-0002-000000000004', 'aaaa0001-0001-0001-0002-000000000001', '2025-11-20', 'Consciência Negra',        'NATIONAL');

  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  VALUES
    ('cccc0001-0002-0002-0002-000000000001', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000001', 1, '{1,2,3,4,5}', '{28,30,24,18,26}'),
    ('cccc0001-0002-0002-0002-000000000002', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000001', 2, '{1,2,3,4,5}', '{30,24,18,26,28}'),
    ('cccc0001-0002-0002-0002-000000000003', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000001', 3, '{1,2,3,4,5}', '{24,18,26,28,30}'),
    ('cccc0001-0002-0002-0002-000000000004', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000001', 4, '{1,2,3,4,5}', '{18,26,28,30,24}'),
    ('cccc0001-0002-0002-0002-000000000005', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000001', 5, '{1,2,3,4,5}', '{26,28,30,24,18}'),
    ('cccc0001-0002-0002-0002-000000000011', '55550001-0002-0002-0002-000000000001', 'cccccccc-0001-0001-0001-000000000002', 1, '{1,2,3,4,5}', '{22,25,18,20,15}'),
    ('cccc0001-0002-0002-0002-000000000012', '55550001-0002-0002-0002-000000000001', 'cccccccc-0001-0001-0001-000000000002', 2, '{1,2,3,4,5}', '{25,18,20,15,22}'),
    ('cccc0001-0002-0002-0002-000000000013', '55550001-0002-0002-0002-000000000001', 'cccccccc-0001-0001-0001-000000000002', 3, '{1,2,3,4,5}', '{18,20,15,22,25}'),
    ('cccc0001-0002-0002-0002-000000000014', '55550001-0002-0002-0002-000000000001', 'cccccccc-0001-0001-0001-000000000002', 4, '{1,2,3,4,5}', '{20,15,22,25,18}'),
    ('cccc0001-0002-0002-0002-000000000015', '55550001-0002-0002-0002-000000000001', 'cccccccc-0001-0001-0001-000000000002', 5, '{1,2,3,4,5}', '{15,22,25,18,20}'),
    ('cccc0001-0002-0002-0002-000000000016', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000002', 1, '{1,2,3}', '{22,25,18}'),
    ('cccc0001-0002-0002-0002-000000000017', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000002', 2, '{1,2,3}', '{25,18,20}'),
    ('cccc0001-0002-0002-0002-000000000018', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000002', 3, '{1,2,3}', '{18,20,15}'),
    ('cccc0001-0002-0002-0002-000000000019', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000002', 4, '{1,2,3}', '{20,15,22}'),
    ('cccc0001-0002-0002-0002-000000000020', '55550001-0002-0002-0002-000000000002', 'cccccccc-0001-0001-0001-000000000002', 5, '{1,2,3}', '{15,22,25}');

  INSERT INTO academic_period (id, institution_id, name, start_date, end_date)
  VALUES ('aaaa0001-0001-0001-0003-000000000001', '11111111-1111-1111-1111-111111111111', '2026.1', '2026-03-02', '2026-07-10');

  INSERT INTO academic_period_shift (id, academic_period_id, shift_type, start_time, end_time, classes_per_day, class_duration_minutes, break_duration_minutes, active_days, enabled)
  VALUES
    ('55550001-0003-0003-0003-000000000001', 'aaaa0001-0001-0001-0003-000000000001', 'MORNING',   '07:30', '11:50', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550001-0003-0003-0003-000000000002', 'aaaa0001-0001-0001-0003-000000000001', 'AFTERNOON', '13:30', '17:50', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550001-0003-0003-0003-000000000003', 'aaaa0001-0001-0001-0003-000000000001', 'EVENING',   '18:50', '22:20', 4, 50, 10, '{1,2,3,4,5}', false);

  INSERT INTO academic_period_holiday (id, academic_period_id, date, description, type)
  VALUES
    ('bbbb0001-0003-0003-0003-000000000001', 'aaaa0001-0001-0001-0003-000000000001', '2026-04-03', 'Paixão de Cristo',          'NATIONAL'),
    ('bbbb0001-0003-0003-0003-000000000002', 'aaaa0001-0001-0001-0003-000000000001', '2026-04-21', 'Tiradentes',                'NATIONAL'),
    ('bbbb0001-0003-0003-0003-000000000003', 'aaaa0001-0001-0001-0003-000000000001', '2026-05-01', 'Dia do Trabalho',           'NATIONAL'),
    ('bbbb0001-0003-0003-0003-000000000004', 'aaaa0001-0001-0001-0003-000000000001', '2026-06-04', 'Corpus Christi',            'NATIONAL');

  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  VALUES
    ('cccc0001-0003-0003-0003-000000000001', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000001', 1, '{1,2,3,4,5}', '{28,30,24,18,26}'),
    ('cccc0001-0003-0003-0003-000000000002', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000001', 2, '{1,2,3,4,5}', '{30,24,18,26,28}'),
    ('cccc0001-0003-0003-0003-000000000003', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000001', 3, '{1,2,3,4,5}', '{24,18,26,28,30}'),
    ('cccc0001-0003-0003-0003-000000000004', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000001', 4, '{1,2,3,4,5}', '{18,26,28,30,24}'),
    ('cccc0001-0003-0003-0003-000000000005', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000001', 5, '{1,2,3,4,5}', '{26,28,30,24,18}'),
    ('cccc0001-0003-0003-0003-000000000011', '55550001-0003-0003-0003-000000000001', 'cccccccc-0001-0001-0001-000000000002', 1, '{1,2,3,4,5}', '{22,25,18,20,15}'),
    ('cccc0001-0003-0003-0003-000000000012', '55550001-0003-0003-0003-000000000001', 'cccccccc-0001-0001-0001-000000000002', 2, '{1,2,3,4,5}', '{25,18,20,15,22}'),
    ('cccc0001-0003-0003-0003-000000000013', '55550001-0003-0003-0003-000000000001', 'cccccccc-0001-0001-0001-000000000002', 3, '{1,2,3,4,5}', '{18,20,15,22,25}'),
    ('cccc0001-0003-0003-0003-000000000014', '55550001-0003-0003-0003-000000000001', 'cccccccc-0001-0001-0001-000000000002', 4, '{1,2,3,4,5}', '{20,15,22,25,18}'),
    ('cccc0001-0003-0003-0003-000000000015', '55550001-0003-0003-0003-000000000001', 'cccccccc-0001-0001-0001-000000000002', 5, '{1,2,3,4,5}', '{15,22,25,18,20}'),
    ('cccc0001-0003-0003-0003-000000000016', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000002', 1, '{1,2,3}', '{22,25,18}'),
    ('cccc0001-0003-0003-0003-000000000017', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000002', 2, '{1,2,3}', '{25,18,20}'),
    ('cccc0001-0003-0003-0003-000000000018', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000002', 3, '{1,2,3}', '{18,20,15}'),
    ('cccc0001-0003-0003-0003-000000000019', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000002', 4, '{1,2,3}', '{20,15,22}'),
    ('cccc0001-0003-0003-0003-000000000020', '55550001-0003-0003-0003-000000000002', 'cccccccc-0001-0001-0001-000000000002', 5, '{1,2,3}', '{15,22,25}');

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

  -- Operating systems
  INSERT INTO operating_system (id, institution_id, name)
  VALUES
    ('0a0a0002-0002-0002-0002-000000000001', '22222222-2222-2222-2222-222222222222', 'Windows 11')
  ;

  -- Configurations
  INSERT INTO configuration (id, institution_id, equipment_model_id, operating_system_id, monitor_id)
  VALUES
    -- HP ProDesk 400 G7 + Windows 11 + LG 24MK430H
    ('ffffffff-0002-0002-0002-000000000001',
     '22222222-2222-2222-2222-222222222222',
     'dddddddd-0002-0002-0002-000000000001', '0a0a0002-0002-0002-0002-000000000001',
     'eeeeeeee-0002-0002-0002-000000000001')
  ;

  -- Dell Inspiron (notebook, tela integrada — sem monitor)
  INSERT INTO configuration (id, institution_id, equipment_model_id, operating_system_id)
  VALUES
    ('ffffffff-0002-0002-0002-000000000002',
     '22222222-2222-2222-2222-222222222222',
     'dddddddd-0002-0002-0002-000000000002', '0a0a0002-0002-0002-0002-000000000001')
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
  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  VALUES
    ('cccc0002-0002-0002-0002-000000000001',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 1, '{1,2,3,4,5}', '{36,40,32,28,38}'),
    ('cccc0002-0002-0002-0002-000000000002',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 2, '{1,2,3,4,5}', '{40,32,28,38,36}'),
    ('cccc0002-0002-0002-0002-000000000003',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 3, '{1,2,3,4,5}', '{32,28,38,36,40}'),
    ('cccc0002-0002-0002-0002-000000000004',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 4, '{1,2,3,4,5}', '{28,38,36,40,32}'),
    ('cccc0002-0002-0002-0002-000000000005',
     '55550002-0002-0002-0002-000000000001',
     'cccccccc-0002-0002-0002-000000000001', 5, '{1,2,3,4,5}', '{38,36,40,32,28}'),
    ('cccc0002-0002-0002-0002-000000000006',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 1, '{1,2,3,4}', '{36,40,32,28}'),
    ('cccc0002-0002-0002-0002-000000000007',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 2, '{1,2,3,4}', '{40,32,28,38}'),
    ('cccc0002-0002-0002-0002-000000000008',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 3, '{1,2,3,4}', '{32,28,38,36}'),
    ('cccc0002-0002-0002-0002-000000000009',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 4, '{1,2,3,4}', '{28,38,36,40}'),
    ('cccc0002-0002-0002-0002-000000000010',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000001', 5, '{1,2,3,4}', '{38,36,40,32}')
  ;

  -- Schedule: LCC-B — Tarde seg-qua-sex, slots 1-4
  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  VALUES
    ('cccc0002-0002-0002-0002-000000000011',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000002', 1, '{1,2,3,4}', '{18,20,14,16}'),
    ('cccc0002-0002-0002-0002-000000000012',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000002', 3, '{1,2,3,4}', '{14,16,18,20}'),
    ('cccc0002-0002-0002-0002-000000000013',
     '55550002-0002-0002-0002-000000000002',
     'cccccccc-0002-0002-0002-000000000002', 5, '{1,2,3,4}', '{18,20,14,16}')
  ;

  INSERT INTO academic_period (id, institution_id, name, start_date, end_date)
  VALUES ('aaaa0002-0002-0002-0003-000000000001', '22222222-2222-2222-2222-222222222222', '2025.2', '2025-08-04', '2025-12-05');

  INSERT INTO academic_period_shift (id, academic_period_id, shift_type, start_time, end_time, classes_per_day, class_duration_minutes, break_duration_minutes, active_days, enabled)
  VALUES
    ('55550002-0003-0003-0003-000000000001', 'aaaa0002-0002-0002-0003-000000000001', 'MORNING',   '08:00', '12:20', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550002-0003-0003-0003-000000000002', 'aaaa0002-0002-0002-0003-000000000001', 'AFTERNOON', '14:00', '17:30', 4, 50, 10, '{1,2,3,4,5}', true);

  INSERT INTO academic_period_holiday (id, academic_period_id, date, description, type)
  VALUES
    ('bbbb0002-0003-0003-0003-000000000001', 'aaaa0002-0002-0002-0003-000000000001', '2025-09-07', 'Dia da Independência',    'NATIONAL'),
    ('bbbb0002-0003-0003-0003-000000000002', 'aaaa0002-0002-0002-0003-000000000001', '2025-11-02', 'Finados',                  'NATIONAL'),
    ('bbbb0002-0003-0003-0003-000000000003', 'aaaa0002-0002-0002-0003-000000000001', '2025-11-15', 'Proclamação da República', 'NATIONAL'),
    ('bbbb0002-0003-0003-0003-000000000004', 'aaaa0002-0002-0002-0003-000000000001', '2025-11-20', 'Consciência Negra',        'NATIONAL');

  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  VALUES
    ('cccc0002-0003-0003-0003-000000000001', '55550002-0003-0003-0003-000000000001', 'cccccccc-0002-0002-0002-000000000001', 1, '{1,2,3,4,5}', '{36,40,32,28,38}'),
    ('cccc0002-0003-0003-0003-000000000002', '55550002-0003-0003-0003-000000000001', 'cccccccc-0002-0002-0002-000000000001', 2, '{1,2,3,4,5}', '{40,32,28,38,36}'),
    ('cccc0002-0003-0003-0003-000000000003', '55550002-0003-0003-0003-000000000001', 'cccccccc-0002-0002-0002-000000000001', 3, '{1,2,3,4,5}', '{32,28,38,36,40}'),
    ('cccc0002-0003-0003-0003-000000000004', '55550002-0003-0003-0003-000000000001', 'cccccccc-0002-0002-0002-000000000001', 4, '{1,2,3,4,5}', '{28,38,36,40,32}'),
    ('cccc0002-0003-0003-0003-000000000005', '55550002-0003-0003-0003-000000000001', 'cccccccc-0002-0002-0002-000000000001', 5, '{1,2,3,4,5}', '{38,36,40,32,28}'),
    ('cccc0002-0003-0003-0003-000000000006', '55550002-0003-0003-0003-000000000002', 'cccccccc-0002-0002-0002-000000000001', 1, '{1,2,3,4}', '{36,40,32,28}'),
    ('cccc0002-0003-0003-0003-000000000007', '55550002-0003-0003-0003-000000000002', 'cccccccc-0002-0002-0002-000000000001', 2, '{1,2,3,4}', '{40,32,28,38}'),
    ('cccc0002-0003-0003-0003-000000000008', '55550002-0003-0003-0003-000000000002', 'cccccccc-0002-0002-0002-000000000001', 3, '{1,2,3,4}', '{32,28,38,36}'),
    ('cccc0002-0003-0003-0003-000000000009', '55550002-0003-0003-0003-000000000002', 'cccccccc-0002-0002-0002-000000000001', 4, '{1,2,3,4}', '{28,38,36,40}'),
    ('cccc0002-0003-0003-0003-000000000010', '55550002-0003-0003-0003-000000000002', 'cccccccc-0002-0002-0002-000000000001', 5, '{1,2,3,4}', '{38,36,40,32}'),
    ('cccc0002-0003-0003-0003-000000000011', '55550002-0003-0003-0003-000000000002', 'cccccccc-0002-0002-0002-000000000002', 1, '{1,2,3,4}', '{18,20,14,16}'),
    ('cccc0002-0003-0003-0003-000000000012', '55550002-0003-0003-0003-000000000002', 'cccccccc-0002-0002-0002-000000000002', 3, '{1,2,3,4}', '{14,16,18,20}'),
    ('cccc0002-0003-0003-0003-000000000013', '55550002-0003-0003-0003-000000000002', 'cccccccc-0002-0002-0002-000000000002', 5, '{1,2,3,4}', '{18,20,14,16}');

  INSERT INTO academic_period (id, institution_id, name, start_date, end_date)
  VALUES ('aaaa0002-0002-0002-0003-000000000002', '22222222-2222-2222-2222-222222222222', '2026.1', '2026-02-23', '2026-07-03');

  INSERT INTO academic_period_shift (id, academic_period_id, shift_type, start_time, end_time, classes_per_day, class_duration_minutes, break_duration_minutes, active_days, enabled)
  VALUES
    ('55550002-0004-0004-0004-000000000001', 'aaaa0002-0002-0002-0003-000000000002', 'MORNING',   '08:00', '12:20', 5, 50, 10, '{1,2,3,4,5}', true),
    ('55550002-0004-0004-0004-000000000002', 'aaaa0002-0002-0002-0003-000000000002', 'AFTERNOON', '14:00', '17:30', 4, 50, 10, '{1,2,3,4,5}', true);

  INSERT INTO academic_period_holiday (id, academic_period_id, date, description, type)
  VALUES
    ('bbbb0002-0004-0004-0004-000000000001', 'aaaa0002-0002-0002-0003-000000000002', '2026-04-03', 'Paixão de Cristo',          'NATIONAL'),
    ('bbbb0002-0004-0004-0004-000000000002', 'aaaa0002-0002-0002-0003-000000000002', '2026-04-21', 'Tiradentes',                'NATIONAL'),
    ('bbbb0002-0004-0004-0004-000000000003', 'aaaa0002-0002-0002-0003-000000000002', '2026-05-01', 'Dia do Trabalho',           'NATIONAL'),
    ('bbbb0002-0004-0004-0004-000000000004', 'aaaa0002-0002-0002-0003-000000000002', '2026-06-04', 'Corpus Christi',            'NATIONAL');

  INSERT INTO laboratory_schedule (id, shift_id, laboratory_id, day_of_week, occupied_slots, stations_used)
  VALUES
    ('cccc0002-0004-0004-0004-000000000001', '55550002-0004-0004-0004-000000000001', 'cccccccc-0002-0002-0002-000000000001', 1, '{1,2,3,4,5}', '{36,40,32,28,38}'),
    ('cccc0002-0004-0004-0004-000000000002', '55550002-0004-0004-0004-000000000001', 'cccccccc-0002-0002-0002-000000000001', 2, '{1,2,3,4,5}', '{40,32,28,38,36}'),
    ('cccc0002-0004-0004-0004-000000000003', '55550002-0004-0004-0004-000000000001', 'cccccccc-0002-0002-0002-000000000001', 3, '{1,2,3,4,5}', '{32,28,38,36,40}'),
    ('cccc0002-0004-0004-0004-000000000004', '55550002-0004-0004-0004-000000000001', 'cccccccc-0002-0002-0002-000000000001', 4, '{1,2,3,4,5}', '{28,38,36,40,32}'),
    ('cccc0002-0004-0004-0004-000000000005', '55550002-0004-0004-0004-000000000001', 'cccccccc-0002-0002-0002-000000000001', 5, '{1,2,3,4,5}', '{38,36,40,32,28}'),
    ('cccc0002-0004-0004-0004-000000000006', '55550002-0004-0004-0004-000000000002', 'cccccccc-0002-0002-0002-000000000001', 1, '{1,2,3,4}', '{36,40,32,28}'),
    ('cccc0002-0004-0004-0004-000000000007', '55550002-0004-0004-0004-000000000002', 'cccccccc-0002-0002-0002-000000000001', 2, '{1,2,3,4}', '{40,32,28,38}'),
    ('cccc0002-0004-0004-0004-000000000008', '55550002-0004-0004-0004-000000000002', 'cccccccc-0002-0002-0002-000000000001', 3, '{1,2,3,4}', '{32,28,38,36}'),
    ('cccc0002-0004-0004-0004-000000000009', '55550002-0004-0004-0004-000000000002', 'cccccccc-0002-0002-0002-000000000001', 4, '{1,2,3,4}', '{28,38,36,40}'),
    ('cccc0002-0004-0004-0004-000000000010', '55550002-0004-0004-0004-000000000002', 'cccccccc-0002-0002-0002-000000000001', 5, '{1,2,3,4}', '{38,36,40,32}'),
    ('cccc0002-0004-0004-0004-000000000011', '55550002-0004-0004-0004-000000000002', 'cccccccc-0002-0002-0002-000000000002', 1, '{1,2,3,4}', '{18,20,14,16}'),
    ('cccc0002-0004-0004-0004-000000000012', '55550002-0004-0004-0004-000000000002', 'cccccccc-0002-0002-0002-000000000002', 3, '{1,2,3,4}', '{14,16,18,20}'),
    ('cccc0002-0004-0004-0004-000000000013', '55550002-0004-0004-0004-000000000002', 'cccccccc-0002-0002-0002-000000000002', 5, '{1,2,3,4}', '{18,20,14,16}');

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

-- =================== EMISSION FACTORS (SIN — MCTI) ===================
-- Monthly average CO₂ emission factors for the Brazilian National Interconnected System (SIN)
-- Source: MCTI — Fator médio de emissão de CO₂ pela geração de energia elétrica no SIN

INSERT INTO emission_factor (reference_month, value, source, institution_id) VALUES
  ('2025-01-01', 0.0501, 'MCTI — Fator médio SIN, jan/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-02-01', 0.0623, 'MCTI — Fator médio SIN, fev/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-03-01', 0.0425, 'MCTI — Fator médio SIN, mar/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-04-01', 0.0450, 'MCTI — Fator médio SIN, abr/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-05-01', 0.0480, 'MCTI — Fator médio SIN, mai/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-06-01', 0.0510, 'MCTI — Fator médio SIN, jun/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-07-01', 0.0530, 'MCTI — Fator médio SIN, jul/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-08-01', 0.0560, 'MCTI — Fator médio SIN, ago/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-09-01', 0.0490, 'MCTI — Fator médio SIN, set/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-10-01', 0.0440, 'MCTI — Fator médio SIN, out/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-11-01', 0.0410, 'MCTI — Fator médio SIN, nov/2025', '11111111-1111-1111-1111-111111111111'),
  ('2025-12-01', 0.0390, 'MCTI — Fator médio SIN, dez/2025', '11111111-1111-1111-1111-111111111111'),
  ('2026-01-01', 0.0485, 'MCTI — Fator médio SIN, jan/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-02-01', 0.0598, 'MCTI — Fator médio SIN, fev/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-03-01', 0.0412, 'MCTI — Fator médio SIN, mar/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-04-01', 0.0438, 'MCTI — Fator médio SIN, abr/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-05-01', 0.0465, 'MCTI — Fator médio SIN, mai/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-06-01', 0.0495, 'MCTI — Fator médio SIN, jun/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-07-01', 0.0518, 'MCTI — Fator médio SIN, jul/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-08-01', 0.0542, 'MCTI — Fator médio SIN, ago/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-09-01', 0.0478, 'MCTI — Fator médio SIN, set/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-10-01', 0.0425, 'MCTI — Fator médio SIN, out/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-11-01', 0.0398, 'MCTI — Fator médio SIN, nov/2026', '11111111-1111-1111-1111-111111111111'),
  ('2026-12-01', 0.0375, 'MCTI — Fator médio SIN, dez/2026', '11111111-1111-1111-1111-111111111111'),
  -- UNICAMP
  ('2025-01-01', 0.0501, 'MCTI — Fator médio SIN, jan/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-02-01', 0.0623, 'MCTI — Fator médio SIN, fev/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-03-01', 0.0425, 'MCTI — Fator médio SIN, mar/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-04-01', 0.0450, 'MCTI — Fator médio SIN, abr/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-05-01', 0.0480, 'MCTI — Fator médio SIN, mai/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-06-01', 0.0510, 'MCTI — Fator médio SIN, jun/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-07-01', 0.0530, 'MCTI — Fator médio SIN, jul/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-08-01', 0.0560, 'MCTI — Fator médio SIN, ago/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-09-01', 0.0490, 'MCTI — Fator médio SIN, set/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-10-01', 0.0440, 'MCTI — Fator médio SIN, out/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-11-01', 0.0410, 'MCTI — Fator médio SIN, nov/2025', '22222222-2222-2222-2222-222222222222'),
  ('2025-12-01', 0.0390, 'MCTI — Fator médio SIN, dez/2025', '22222222-2222-2222-2222-222222222222'),
  ('2026-01-01', 0.0485, 'MCTI — Fator médio SIN, jan/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-02-01', 0.0598, 'MCTI — Fator médio SIN, fev/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-03-01', 0.0412, 'MCTI — Fator médio SIN, mar/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-04-01', 0.0438, 'MCTI — Fator médio SIN, abr/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-05-01', 0.0465, 'MCTI — Fator médio SIN, mai/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-06-01', 0.0495, 'MCTI — Fator médio SIN, jun/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-07-01', 0.0518, 'MCTI — Fator médio SIN, jul/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-08-01', 0.0542, 'MCTI — Fator médio SIN, ago/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-09-01', 0.0478, 'MCTI — Fator médio SIN, set/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-10-01', 0.0425, 'MCTI — Fator médio SIN, out/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-11-01', 0.0398, 'MCTI — Fator médio SIN, nov/2026', '22222222-2222-2222-2222-222222222222'),
  ('2026-12-01', 0.0375, 'MCTI — Fator médio SIN, dez/2026', '22222222-2222-2222-2222-222222222222');

DO $$
DECLARE
  d          DATE;
  dow        INT;
  factor_val NUMERIC(10,6);
  energy_kwh NUMERIC(12,4);
  station_cnt INT;
  rec        RECORD;
  holidays   DATE[];
BEGIN
  PERFORM set_config('app.current_institution', '11111111-1111-1111-1111-111111111111', true);
  FOR rec IN
    SELECT id, start_date, end_date FROM academic_period
    WHERE institution_id = '11111111-1111-1111-1111-111111111111'
    ORDER BY start_date
  LOOP
    SELECT array_agg(date) INTO holidays
    FROM academic_period_holiday WHERE academic_period_id = rec.id;

    d := rec.start_date;
    WHILE d <= rec.end_date AND d < CURRENT_DATE LOOP
      dow := EXTRACT(ISODOW FROM d)::INT;
      IF dow IN (6, 7) OR (holidays IS NOT NULL AND d = ANY(holidays)) THEN
        d := d + 1; CONTINUE;
      END IF;
      SELECT value INTO factor_val FROM emission_factor
      WHERE institution_id = '11111111-1111-1111-1111-111111111111'
        AND reference_month = DATE_TRUNC('month', d)::DATE;
      IF factor_val IS NULL THEN d := d + 1; CONTINUE; END IF;

      energy_kwh  := 24.5833;
      station_cnt := 55;

      INSERT INTO emission_snapshot
        (institution_id, academic_period_id, snapshot_date, day_of_week,
         daily_energy_kwh, daily_emission_kg, emission_factor_value, station_count, is_school_day)
      VALUES
        ('11111111-1111-1111-1111-111111111111', rec.id, d, dow::SMALLINT,
         energy_kwh, ROUND(energy_kwh * factor_val, 4), factor_val, station_cnt, true)
      ON CONFLICT (institution_id, snapshot_date) DO NOTHING;
      d := d + 1;
    END LOOP;
  END LOOP;

  PERFORM set_config('app.current_institution', '22222222-2222-2222-2222-222222222222', true);
  FOR rec IN
    SELECT id, start_date, end_date FROM academic_period
    WHERE institution_id = '22222222-2222-2222-2222-222222222222'
    ORDER BY start_date
  LOOP
    SELECT array_agg(date) INTO holidays
    FROM academic_period_holiday WHERE academic_period_id = rec.id;

    d := rec.start_date;
    WHILE d <= rec.end_date AND d < CURRENT_DATE LOOP
      dow := EXTRACT(ISODOW FROM d)::INT;
      IF dow IN (6, 7) OR (holidays IS NOT NULL AND d = ANY(holidays)) THEN
        d := d + 1; CONTINUE;
      END IF;
      SELECT value INTO factor_val FROM emission_factor
      WHERE institution_id = '22222222-2222-2222-2222-222222222222'
        AND reference_month = DATE_TRUNC('month', d)::DATE;
      IF factor_val IS NULL THEN d := d + 1; CONTINUE; END IF;

      IF dow IN (1, 3, 5) THEN
        energy_kwh  := 31.9667;
        station_cnt := 60;
      ELSE
        energy_kwh  := 27.0000;
        station_cnt := 40;
      END IF;

      INSERT INTO emission_snapshot
        (institution_id, academic_period_id, snapshot_date, day_of_week,
         daily_energy_kwh, daily_emission_kg, emission_factor_value, station_count, is_school_day)
      VALUES
        ('22222222-2222-2222-2222-222222222222', rec.id, d, dow::SMALLINT,
         energy_kwh, ROUND(energy_kwh * factor_val, 4), factor_val, station_cnt, true)
      ON CONFLICT (institution_id, snapshot_date) DO NOTHING;
      d := d + 1;
    END LOOP;
  END LOOP;
END $$;
