-- ============================================================================
-- Seed: dados do artigo "Pegada de Carbono em Ambientes Acadêmicos"
--       (Corrêa, Cardoso, Kawasaki — FACOMP/UFPA)
--
-- Fontes:
--   [ART]  PDF do artigo (seções/páginas indicadas em cada bloco)
--   [REPO] Repositório de dados citado na nota de rodapé 1 do artigo (Seção 3):
--          https://github.com/AlanWSC/TCC-Artigo
--          O PDF só traz a Tabela 1 (modelo, CPU, RAM, monitor). TDP, GPU,
--          armazenamento, watts dos monitores e as leituras do wattímetro
--          estão nas planilhas desse repositório.
--
-- Convenções:
--   * UUIDs com prefixo 5eed ("seed" em hex). O prefixo literal 'seed' não é
--     hexadecimal válido e seria rejeitado pela coluna UUID do Postgres.
--   * Somente leituras reais. As linhas que os autores preencheram com a média
--     do grupo (imputadas) foram excluídas:
--       Lab 1: PCs 16–30 = média dos PCs 1–15
--       Lab 2: PC 10 = média dos PCs 1–9; PCs 18–30 = média dos PCs 11–17
--       Lab 3: PCs 6–10 = média dos PCs 1–5; PCs 22–30 = média dos PCs 11–21
--   * average_watts = média das 3 leituras instantâneas (a cada 4 ou 5 min).
--     Não foi usada a energia acumulada (kWh) porque em vários PCs ela implica
--     potência média acima do máximo registrado (ex.: Lab 2 PC 13, Lab 3 PC 11).
--     O kWh original fica registrado em conditions.
--   * target_type = 'COMPUTER': o artigo e os protocolos não dizem se o monitor
--     estava ligado no mesmo nobreak que o wattímetro media.
--     -- approximate: pode ser que as leituras incluam o monitor (seria 'COMBINED').
--   * measurement_date: o artigo não informa a data das medições.
--     -- approximate: 2025-03-28 é a data de acesso aos fatores do MCTI na
--        referência [MCTI 2025], usada como limite superior (o cálculo das
--        emissões, etapa 6, veio depois das medições, etapa 5).
--   * core_count = NULL: o número de núcleos não consta no artigo nem no repo.
-- ============================================================================

DO $$
BEGIN
  -- ==========================================================================
  -- 0. institution — FACOMP (Faculdade de Computação, UFPA)
  -- ==========================================================================
  INSERT INTO institution (id, name, acronym, city, state)
  VALUES ('5eed0000-0000-0000-0000-000000000001',
          'Faculdade de Computação — UFPA', 'FACOMP', 'Belém', 'PA');

  PERFORM set_config('app.current_institution', '5eed0000-0000-0000-0000-000000000001', true);

  -- ==========================================================================
  -- 1. equipment_model
  --    [ART] Tabela 1, Seção 3.1 (p. 4): modelo, CPU, RAM
  --    [REPO] Levantamentos dos Computadores/*.csv: TDP CPU, GPU, TDP GPU,
  --           RAM da GPU, armazenamento
  --    Obs.: as GPUs são integradas; os TDPs de GPU são os informados pelos
  --    autores e não devem ser somados ao TDP da CPU sem cuidado.
  -- ==========================================================================
  INSERT INTO equipment_model (id, institution_id, name, equipment_type, processor, tdp_watts, core_count, memory_gb, gpu_model, gpu_tdp_watts, has_integrated_screen, description)
  VALUES
    -- "HP 1" — Lab 1 (30 un.), Lab 2 (3 un.), Lab 3 (22 un.) — Seção 3.1
    ('5eed0001-0001-0001-0001-000000000001', '5eed0000-0000-0000-0000-000000000001',
     'HP EliteDesk 800 G1 SFF', 'Desktop', 'Intel Core i5-4570 (3.20–3.60 GHz)', 84, NULL, 8,
     'Intel HD Graphics 4600', 45, false,
     '"HP 1" no artigo. RAM da GPU: 8 GB. Armazenamento: 400 GB HD (Labs 1 e 3), 500 GB (Lab 2). Quantidade: Lab 1 = 30, Lab 2 = 3, Lab 3 = 22.'),

    -- "HP 2" — Lab 2 (11 un.) — Seção 3.1
    ('5eed0001-0001-0001-0001-000000000002', '5eed0000-0000-0000-0000-000000000001',
     'HP EliteDesk 800 G4 SFF', 'Desktop', 'Intel Core i7-8700 (3.20–4.60 GHz)', 65, NULL, 8,
     'Intel UHD Graphics 630', 15, false,
     '"HP 2" no artigo. RAM da GPU: 8 GB. Armazenamento: 500 GB. Quantidade: Lab 2 = 11.'),

    -- "MAC" — Lab 2 (16 un.) — Seção 3.1
    ('5eed0001-0001-0001-0001-000000000003', '5eed0000-0000-0000-0000-000000000001',
     'Mac mini 7,1', 'Desktop', 'Intel Core i5-4278U (2.60–3.10 GHz)', 28, NULL, 8,
     'Intel Iris Graphics 5100', 30, false,
     '"MAC" no artigo. RAM da GPU: 2 GB. Armazenamento: 500 GB. Quantidade: Lab 2 = 16.'),

    -- "DELL" — Lab 3 (8 un.) — Seção 3.1
    -- [REPO] TDP listado como "65W - 117W"; 65 W = TDP base, 117 W = turbo.
    ('5eed0001-0001-0001-0001-000000000004', '5eed0000-0000-0000-0000-000000000001',
     'Dell OptiPlex 7000', 'Desktop', 'Intel Core i5-12500 (3.00–4.60 GHz)', 65, NULL, 16,
     'Intel UHD Graphics 770', 15, false,
     '"DELL" no artigo. TDP da CPU informado como 65–117 W (base–turbo). RAM da GPU: 8 GB. Armazenamento: 237 GB SSD. Quantidade: Lab 3 = 8.')
  ;

  -- ==========================================================================
  -- 2. monitor
  --    [ART] Tabela 1 (p. 4) cita apenas HP E231, HP E233 e Dell P2422H.
  --    [REPO] Levantamentos dos Computadores/*.csv: demais monitores e watts.
  --    Os watts são os valores de consumo levantados pelos autores
  --    (especificação), NÃO foram medidos com wattímetro.
  --    Nomes mantidos como aparecem na planilha.
  -- ==========================================================================
  INSERT INTO monitor (id, institution_id, name, watts)
  VALUES
    -- Lab 1 (30), Lab 2 (13: HP 2 ×11, MAC ×2), Lab 3 (22)
    ('5eed0002-0002-0002-0002-000000000001', '5eed0000-0000-0000-0000-000000000001', 'HP EliteDisplay E231 (1920x1080)', 33),
    -- Lab 2 (MAC ×11)
    ('5eed0002-0002-0002-0002-000000000002', '5eed0000-0000-0000-0000-000000000001', 'HP E233 (1920x1080)', 19),
    -- Lab 3 (DELL ×8)
    ('5eed0002-0002-0002-0002-000000000003', '5eed0000-0000-0000-0000-000000000001', 'Dell P2422H (1920x1080)', 12),
    -- Lab 2 (MAC ×2) — só no [REPO]; grafado "HP V255Hz" na planilha
    ('5eed0002-0002-0002-0002-000000000004', '5eed0000-0000-0000-0000-000000000001', 'HP V255Hz (1920x1080)', 20),
    -- Lab 2 (MAC ×1) — só no [REPO]
    ('5eed0002-0002-0002-0002-000000000005', '5eed0000-0000-0000-0000-000000000001', 'Samsung SA300/SA350 (1366x768)', 29),
    -- Lab 2 (HP 1 ×1) — só no [REPO]; grafado "Dell 2219H" na planilha
    ('5eed0002-0002-0002-0002-000000000006', '5eed0000-0000-0000-0000-000000000001', 'Dell 2219H (1920x1080)', 14),
    -- Lab 2 (HP 1 ×1) — só no [REPO]
    ('5eed0002-0002-0002-0002-000000000007', '5eed0000-0000-0000-0000-000000000001', 'Samsung BX1940 (1366x768)', 25),
    -- Lab 2 (HP 1 ×1) — só no [REPO]; resolução não informada
    ('5eed0002-0002-0002-0002-000000000008', '5eed0000-0000-0000-0000-000000000001', 'AOC E2050s', 18)
  ;

  -- ==========================================================================
  -- 3. operating_system
  --    [ART] Seções 3.3 (p. 5) e 3.4 (p. 5): Windows 10, Windows 11, Kubuntu.
  --    A versão do Kubuntu não é informada.
  --    ON CONFLICT: a tabela tem UNIQUE (institution_id, name).
  -- ==========================================================================
  INSERT INTO operating_system (id, institution_id, name)
  VALUES
    ('5eed0003-0003-0003-0003-000000000001', '5eed0000-0000-0000-0000-000000000001', 'Windows 10'),
    ('5eed0003-0003-0003-0003-000000000002', '5eed0000-0000-0000-0000-000000000001', 'Windows 11'),
    ('5eed0003-0003-0003-0003-000000000003', '5eed0000-0000-0000-0000-000000000001', 'Kubuntu'),
    ('5eed0003-0003-0003-0003-000000000004', '5eed0000-0000-0000-0000-000000000001', 'macOS')
  ON CONFLICT (institution_id, name) DO NOTHING;

  -- ==========================================================================
  -- 4. consumption_measurement
  --    Experimentais: 12 min (leitura a cada 4 min) e 15 min (a cada 5 min)
  --    Finais: 12 min, leitura a cada 4 min, um PC por nobreak
  --    Contagem: Lab 1 = 15, Lab 2 = 16 (o artigo diz 17), Lab 3 = 16
  -- ==========================================================================
  INSERT INTO consumption_measurement (id, institution_id, target_type, equipment_model_id, operating_system_id, monitor_id, average_watts, duration_minutes, reading_interval_minutes, measurement_date, conditions)
  VALUES
  -- ---------- Medições experimentais (Seção 3.3, p. 5; Protocolo para medição experimental; Medições/Medição Experimental - Laboratório.csv) ----------
    -- Experimental — Dell OptiPlex 7000 / Windows 11 / 12 min / computador sozinho no nobreak
    -- approximate: equipamento identificado pelo SO (Seção 3.3: DELL com Windows 11, HP 1 com Windows 10); service tag FKPTXT3 não aparece no artigo
    ('5eed0004-0004-0004-0004-000000000001', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000004', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 11'), NULL, 54.03, 12, 4, '2025-03-28', 'Fase experimental (teste de protocolo), computador sozinho no nobreak. PC FKPTXT3 (Lab 3). Leituras: 46.1/71.2/44.8 W; mín 24.9 W; máx 125.4 W; energia acumulada 0.01 kWh. Uso simulado de aula (protocolo FACOMP): Google Chrome reproduzindo vídeo do YouTube em resolução máxima + documentação Python; VS Code editando scripts Python; leitura de PDFs (Edge quando não havia leitor). Wattímetro digital bivolt no nobreak.'),
    -- Experimental — Dell OptiPlex 7000 / Windows 11 / 15 min / computador sozinho no nobreak
    ('5eed0004-0004-0004-0004-000000000002', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000004', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 11'), NULL, 56.43, 15, 5, '2025-03-28', 'Fase experimental (teste de protocolo), computador sozinho no nobreak. PC FKPTXT3 (Lab 3). Leituras: 67/44.6/57.7 W; mín 24.7 W; máx 125.8 W; energia acumulada 0.014 kWh. Uso simulado de aula (protocolo FACOMP): Google Chrome reproduzindo vídeo do YouTube em resolução máxima + documentação Python; VS Code editando scripts Python; leitura de PDFs (Edge quando não havia leitor). Wattímetro digital bivolt no nobreak.'),
    -- Experimental — Dell OptiPlex 7000 / Windows 11 / 12 min / com outro computador conectado ao mesmo nobreak (desligado)
    ('5eed0004-0004-0004-0004-000000000003', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000004', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 11'), NULL, 64.00, 12, 4, '2025-03-28', 'Fase experimental (teste de protocolo), com outro computador conectado ao mesmo nobreak (desligado). PC FKPTXT3 (Lab 3). Leituras: 44.8/75/72.2 W; mín 41.9 W; máx 126.2 W; energia acumulada 0.011 kWh. Uso simulado de aula (protocolo FACOMP): Google Chrome reproduzindo vídeo do YouTube em resolução máxima + documentação Python; VS Code editando scripts Python; leitura de PDFs (Edge quando não havia leitor). Wattímetro digital bivolt no nobreak.'),
    -- Experimental — Dell OptiPlex 7000 / Windows 11 / 15 min / com outro computador conectado ao mesmo nobreak (desligado)
    ('5eed0004-0004-0004-0004-000000000004', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000004', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 11'), NULL, 54.37, 15, 5, '2025-03-28', 'Fase experimental (teste de protocolo), com outro computador conectado ao mesmo nobreak (desligado). PC FKPTXT3 (Lab 3). Leituras: 47.3/70.1/45.7 W; mín 39.3 W; máx 125.9 W; energia acumulada 0.013 kWh. Uso simulado de aula (protocolo FACOMP): Google Chrome reproduzindo vídeo do YouTube em resolução máxima + documentação Python; VS Code editando scripts Python; leitura de PDFs (Edge quando não havia leitor). Wattímetro digital bivolt no nobreak.'),
    -- Experimental — HP EliteDesk 800 G1 SFF / Windows 10 / 12 min / computador sozinho no nobreak
    ('5eed0004-0004-0004-0004-000000000005', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 96.87, 12, 4, '2025-03-28', 'Fase experimental (teste de protocolo), computador sozinho no nobreak. PC Labcomp 3 - PC-17 (Lab 3). Leituras: 99.6/97.7/93.3 W; mín 52.9 W; máx 101.1 W; energia acumulada 0.021 kWh. Uso simulado de aula (protocolo FACOMP): Google Chrome reproduzindo vídeo do YouTube em resolução máxima + documentação Python; VS Code editando scripts Python; leitura de PDFs (Edge quando não havia leitor). Wattímetro digital bivolt no nobreak.'),
    -- Experimental — HP EliteDesk 800 G1 SFF / Windows 10 / 15 min / computador sozinho no nobreak
    ('5eed0004-0004-0004-0004-000000000006', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 97.47, 15, 5, '2025-03-28', 'Fase experimental (teste de protocolo), computador sozinho no nobreak. PC Labcomp 3 - PC-17 (Lab 3). Leituras: 95.2/99.5/97.7 W; mín 57.3 W; máx 104.0 W; energia acumulada 0.027 kWh. Uso simulado de aula (protocolo FACOMP): Google Chrome reproduzindo vídeo do YouTube em resolução máxima + documentação Python; VS Code editando scripts Python; leitura de PDFs (Edge quando não havia leitor). Wattímetro digital bivolt no nobreak.'),
    -- Experimental — HP EliteDesk 800 G1 SFF / Windows 10 / 12 min / com outro computador conectado ao mesmo nobreak (desligado)
    ('5eed0004-0004-0004-0004-000000000007', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 102.33, 12, 4, '2025-03-28', 'Fase experimental (teste de protocolo), com outro computador conectado ao mesmo nobreak (desligado). PC Labcomp 3 - PC-17 (Lab 3). Leituras: 103.2/101.1/102.7 W; mín 52.5 W; máx 109.7 W; energia acumulada 0.02 kWh. Uso simulado de aula (protocolo FACOMP): Google Chrome reproduzindo vídeo do YouTube em resolução máxima + documentação Python; VS Code editando scripts Python; leitura de PDFs (Edge quando não havia leitor). Wattímetro digital bivolt no nobreak.'),
    -- Experimental — HP EliteDesk 800 G1 SFF / Windows 10 / 15 min / com outro computador conectado ao mesmo nobreak (desligado)
    ('5eed0004-0004-0004-0004-000000000008', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 99.00, 15, 5, '2025-03-28', 'Fase experimental (teste de protocolo), com outro computador conectado ao mesmo nobreak (desligado). PC Labcomp 3 - PC-17 (Lab 3). Leituras: 99.5/99.3/98.2 W; mín 62.2 W; máx 101.2 W; energia acumulada 0.026 kWh. Uso simulado de aula (protocolo FACOMP): Google Chrome reproduzindo vídeo do YouTube em resolução máxima + documentação Python; VS Code editando scripts Python; leitura de PDFs (Edge quando não havia leitor). Wattímetro digital bivolt no nobreak.'),

  -- ---------- Medição final — Laboratório 1 (Seção 3.4, p. 5–6; Medições/Medição dos computadores - Laboratório 1.csv) ----------
    -- Lab 1, PC 1 — HP EliteDesk 800 G1 SFF / Windows 10
    ('5eed0004-0004-0004-0004-000000000009', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 77.00, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 1, sozinho no nobreak. Leituras a cada 4 min: 78.4/80.8/71.8 W; mín 41.5 W; máx 85.0 W; energia acumulada 0.017 kWh em 12 min. Uso simulado de aula (protocolo FACOMP): Google Chrome reproduzindo vídeo do YouTube em resolução máxima + documentação Python; VS Code editando scripts Python; leitura de PDFs (Edge quando não havia leitor). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 2
    ('5eed0004-0004-0004-0004-000000000010', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 71.23, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 2, sozinho no nobreak. Leituras a cada 4 min: 50.6/81.1/82.0 W; mín 42.8 W; máx 87.1 W; energia acumulada 0.02 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 3
    ('5eed0004-0004-0004-0004-000000000011', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 101.37, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 3, sozinho no nobreak. Leituras a cada 4 min: 102.1/101.8/100.2 W; mín 64.0 W; máx 107.5 W; energia acumulada 0.02 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 4
    ('5eed0004-0004-0004-0004-000000000012', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 100.53, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 4, sozinho no nobreak. Leituras a cada 4 min: 100.1/102.7/98.8 W; mín 54.4 W; máx 107.7 W; energia acumulada 0.021 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 5
    ('5eed0004-0004-0004-0004-000000000013', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 70.93, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 5, sozinho no nobreak. Leituras a cada 4 min: 83.7/73.9/55.2 W; mín 40.4 W; máx 85.8 W; energia acumulada 0.014 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 6
    ('5eed0004-0004-0004-0004-000000000014', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 94.20, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 6, sozinho no nobreak. Leituras a cada 4 min: 94.9/93.9/93.8 W; mín 54.2 W; máx 100.3 W; energia acumulada 0.02 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 7
    ('5eed0004-0004-0004-0004-000000000015', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 89.90, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 7, sozinho no nobreak. Leituras a cada 4 min: 92.9/86.5/90.3 W; mín 50.7 W; máx 95.1 W; energia acumulada 0.017 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 8
    ('5eed0004-0004-0004-0004-000000000016', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 87.10, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 8, sozinho no nobreak. Leituras a cada 4 min: 84.2/89.0/88.1 W; mín 42.1 W; máx 92.2 W; energia acumulada 0.015 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 9
    ('5eed0004-0004-0004-0004-000000000017', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 101.70, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 9, sozinho no nobreak. Leituras a cada 4 min: 103.6/97.0/104.5 W; mín 63.5 W; máx 109.7 W; energia acumulada 0.018 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 10
    ('5eed0004-0004-0004-0004-000000000018', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 100.17, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 10, sozinho no nobreak. Leituras a cada 4 min: 103.5/104.9/92.1 W; mín 60.4 W; máx 106.2 W; energia acumulada 0.024 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 11
    ('5eed0004-0004-0004-0004-000000000019', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 81.27, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 11, sozinho no nobreak. Leituras a cada 4 min: 80.9/81.1/81.8 W; mín 40.9 W; máx 84.7 W; energia acumulada 0.017 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 12
    ('5eed0004-0004-0004-0004-000000000020', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 79.63, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 12, sozinho no nobreak. Leituras a cada 4 min: 81.7/81.4/75.8 W; mín 41.4 W; máx 86.1 W; energia acumulada 0.015 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 13
    ('5eed0004-0004-0004-0004-000000000021', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 92.40, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 13, sozinho no nobreak. Leituras a cada 4 min: 93.4/90.9/92.9 W; mín 56.4 W; máx 96.71 W; energia acumulada 0.014 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 14
    ('5eed0004-0004-0004-0004-000000000022', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 76.03, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 14, sozinho no nobreak. Leituras a cada 4 min: 77.1/76.7/74.3 W; mín 40.3 W; máx 83.3 W; energia acumulada 0.015 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 1, PC 15
    ('5eed0004-0004-0004-0004-000000000023', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Windows 10'), NULL, 83.33, 12, 4, '2025-03-28', 'Medição final, Lab 1, PC 15, sozinho no nobreak. Leituras a cada 4 min: 88.5/82.1/79.4 W; mín 46.1 W; máx 93.9 W; energia acumulada 0.019 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),

  -- ---------- Medição final — Laboratório 2 (Seção 3.4, p. 5–6; Medições/Medição dos computadores - Laboratório 2.csv) ----------
    -- Lab 2, PC 1 — HP EliteDesk 800 G4 SFF / Kubuntu
    -- approximate: linhas 1–9 do CSV atribuídas ao HP 2 (Seção 3.4 cita 9 HP 2); o CSV de medição não informa o modelo
    ('5eed0004-0004-0004-0004-000000000024', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000002', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 58.37, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 1, sozinho no nobreak. Leituras a cada 4 min: 62.7/54.4/58.0 W; mín 45.0 W; máx 75.1 W; energia acumulada 0.012 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 2
    ('5eed0004-0004-0004-0004-000000000025', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000002', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 65.97, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 2, sozinho no nobreak. Leituras a cada 4 min: 65.1/66.0/66.8 W; mín 35.6 W; máx 67.0 W; energia acumulada 0.013 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 3
    ('5eed0004-0004-0004-0004-000000000026', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000002', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 60.10, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 3, sozinho no nobreak. Leituras a cada 4 min: 63.4/66.0/50.9 W; mín 30.7 W; máx 94.6 W; energia acumulada 0.017 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 4
    ('5eed0004-0004-0004-0004-000000000027', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000002', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 68.70, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 4, sozinho no nobreak. Leituras a cada 4 min: 66.7/70.4/69.0 W; mín 40.4 W; máx 71.4 W; energia acumulada 0.013 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 5
    ('5eed0004-0004-0004-0004-000000000028', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000002', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 63.03, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 5, sozinho no nobreak. Leituras a cada 4 min: 63.5/63.1/62.5 W; mín 28.9 W; máx 65.7 W; energia acumulada 0.012 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 6
    ('5eed0004-0004-0004-0004-000000000029', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000002', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 57.23, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 6, sozinho no nobreak. Leituras a cada 4 min: 55.8/57.6/58.3 W; mín 27.5 W; máx 60.2 W; energia acumulada 0.011 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 7
    ('5eed0004-0004-0004-0004-000000000030', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000002', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 55.57, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 7, sozinho no nobreak. Leituras a cada 4 min: 55.6/56.6/54.5 W; mín 24.5 W; máx 58.0 W; energia acumulada 0.01 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 8
    ('5eed0004-0004-0004-0004-000000000031', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000002', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 59.67, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 8, sozinho no nobreak. Leituras a cada 4 min: 58.2/60.3/60.5 W; mín 26.6 W; máx 61.7 W; energia acumulada 0.012 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 9
    ('5eed0004-0004-0004-0004-000000000032', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000002', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 61.10, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 9, sozinho no nobreak. Leituras a cada 4 min: 61.4/61.8/60.1 W; mín 32.4 W; máx 69.2 W; energia acumulada 0.013 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 11 — Mac mini 7,1 / Kubuntu
    -- approximate: linhas 11–17 atribuídas ao MAC pelo agrupamento dos autores
    ('5eed0004-0004-0004-0004-000000000033', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000003', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 86.57, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 11, sozinho no nobreak. Leituras a cada 4 min: 100.1/52.2/107.4 W; mín 26.0 W; máx 142.5 W; energia acumulada 0.023 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 12
    ('5eed0004-0004-0004-0004-000000000034', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000003', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 72.93, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 12, sozinho no nobreak. Leituras a cada 4 min: 63.3/73.3/82.2 W; mín 43.9 W; máx 98.2 W; energia acumulada 0.016 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 13
    ('5eed0004-0004-0004-0004-000000000035', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000003', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 112.67, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 13, sozinho no nobreak. Leituras a cada 4 min: 108.3/121.2/108.5 W; mín 94.4 W; máx 130.6 W; energia acumulada 0.037 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 14
    ('5eed0004-0004-0004-0004-000000000036', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000003', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 79.47, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 14, sozinho no nobreak. Leituras a cada 4 min: 90.3/76.2/71.9 W; mín 50.4 W; máx 109.9 W; energia acumulada 0.015 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 15
    ('5eed0004-0004-0004-0004-000000000037', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000003', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 58.80, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 15, sozinho no nobreak. Leituras a cada 4 min: 65.0/50.3/61.1 W; mín 39.7 W; máx 100.6 W; energia acumulada 0.013 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 16
    ('5eed0004-0004-0004-0004-000000000038', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000003', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 61.03, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 16, sozinho no nobreak. Leituras a cada 4 min: 63.5/59.7/59.9 W; mín 35.0 W; máx 94.9 W; energia acumulada 0.012 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 2, PC 17
    ('5eed0004-0004-0004-0004-000000000039', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000003', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 125.90, 12, 4, '2025-03-28', 'Medição final, Lab 2, PC 17, sozinho no nobreak. Leituras a cada 4 min: 134.2/130.2/113.3 W; mín 44.1 W; máx 141.7 W; energia acumulada 0.024 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),

  -- ---------- Medição final — Laboratório 3 (Seção 3.4, p. 5–6; Medições/Medição dos computadores - Laboratório 3.csv) ----------
    -- Lab 3, PC 1 — Dell OptiPlex 7000 / Kubuntu
    ('5eed0004-0004-0004-0004-000000000040', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000004', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 57.10, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 1, sozinho no nobreak. Leituras a cada 4 min: 47.7/70.8/52.8 W; mín 39.4 W; máx 108.2 W; energia acumulada 0.013 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 2
    ('5eed0004-0004-0004-0004-000000000041', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000004', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 57.87, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 2, sozinho no nobreak. Leituras a cada 4 min: 61.7/50.7/61.2 W; mín 41.3 W; máx 115.2 W; energia acumulada 0.012 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 3
    ('5eed0004-0004-0004-0004-000000000042', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000004', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 60.63, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 3, sozinho no nobreak. Leituras a cada 4 min: 57.4/63.3/61.2 W; mín 38.7 W; máx 96.4 W; energia acumulada 0.011 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 4
    ('5eed0004-0004-0004-0004-000000000043', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000004', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 46.00, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 4, sozinho no nobreak. Leituras a cada 4 min: 47.1/48.3/42.6 W; mín 39.4 W; máx 82.9 W; energia acumulada 0.009 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 5
    ('5eed0004-0004-0004-0004-000000000044', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000004', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 37.07, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 5, sozinho no nobreak. Leituras a cada 4 min: 37.4/29.9/43.9 W; mín 31.6 W; máx 88.1 W; energia acumulada 0.008 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 11 — HP EliteDesk 800 G1 SFF / Kubuntu
    ('5eed0004-0004-0004-0004-000000000045', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 98.83, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 11, sozinho no nobreak. Leituras a cada 4 min: 99.6/98.8/98.1 W; mín 55.4 W; máx 106.2 W; energia acumulada 0.029 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 12
    ('5eed0004-0004-0004-0004-000000000046', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 102.03, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 12, sozinho no nobreak. Leituras a cada 4 min: 87.1/109.1/109.9 W; mín 47.9 W; máx 115.1 W; energia acumulada 0.022 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 13
    ('5eed0004-0004-0004-0004-000000000047', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 101.27, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 13, sozinho no nobreak. Leituras a cada 4 min: 105.7/91.9/106.2 W; mín 56.7 W; máx 109.1 W; energia acumulada 0.022 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 14
    ('5eed0004-0004-0004-0004-000000000048', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 81.83, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 14, sozinho no nobreak. Leituras a cada 4 min: 82.4/81.8/81.3 W; mín 54.9 W; máx 104.6 W; energia acumulada 0.017 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 15
    ('5eed0004-0004-0004-0004-000000000049', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 103.77, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 15, sozinho no nobreak. Leituras a cada 4 min: 102.4/101.9/107.0 W; mín 58.2 W; máx 122.3 W; energia acumulada 0.021 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 16
    ('5eed0004-0004-0004-0004-000000000050', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 93.27, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 16, sozinho no nobreak. Leituras a cada 4 min: 92.7/93.4/93.7 W; mín 55.1 W; máx 96.9 W; energia acumulada 0.016 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 17
    ('5eed0004-0004-0004-0004-000000000051', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 78.23, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 17, sozinho no nobreak. Leituras a cada 4 min: 54.9/89.9/89.9 W; mín 50.2 W; máx 92.9 W; energia acumulada 0.017 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 18
    ('5eed0004-0004-0004-0004-000000000052', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 108.47, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 18, sozinho no nobreak. Leituras a cada 4 min: 85.8/86.7/152.9 W; mín 43.5 W; máx 165.2 W; energia acumulada 0.02 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 19
    ('5eed0004-0004-0004-0004-000000000053', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 78.50, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 19, sozinho no nobreak. Leituras a cada 4 min: 84.5/74.0/77.0 W; mín 42.1 W; máx 86.7 W; energia acumulada 0.018 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 20
    ('5eed0004-0004-0004-0004-000000000054', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 81.87, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 20, sozinho no nobreak. Leituras a cada 4 min: 81.9/81.2/82.5 W; mín 36.5 W; máx 84.9 W; energia acumulada 0.017 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.'),
    -- Lab 3, PC 21
    ('5eed0004-0004-0004-0004-000000000055', '5eed0000-0000-0000-0000-000000000001', 'COMPUTER', '5eed0001-0001-0001-0001-000000000001', (SELECT id FROM operating_system WHERE institution_id = '5eed0000-0000-0000-0000-000000000001' AND name = 'Kubuntu'), NULL, 87.47, 12, 4, '2025-03-28', 'Medição final, Lab 3, PC 21, sozinho no nobreak. Leituras a cada 4 min: 87.1/86.6/88.7 W; mín 42.0 W; máx 90.8 W; energia acumulada 0.016 kWh em 12 min. Uso simulado de aula (protocolo FACOMP). Wattímetro digital bivolt no nobreak.');

END $$;
