-- RFC 001, Opção 3: Separar monitor em entidade própria, adicionar GPU ao modelo de computador.
-- Transforma o schema monolítico (V10/V11) no modelo composto.

-- 1. Criar tabela monitor
CREATE TABLE monitor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institution (id),
    name VARCHAR(255) NOT NULL,
    watts INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_monitor_institution_id ON monitor (institution_id);

ALTER TABLE monitor ENABLE ROW LEVEL SECURITY;
ALTER TABLE monitor FORCE ROW LEVEL SECURITY;

CREATE POLICY monitor_institution_isolation ON monitor
    USING (institution_id = current_setting('app.current_institution', true)::uuid);

-- 2. Migrar monitores existentes do equipment_model para a tabela monitor
INSERT INTO monitor (institution_id, name, watts)
SELECT DISTINCT em.institution_id, em.monitor_name, em.monitor_watts
FROM equipment_model em
WHERE em.monitor_name IS NOT NULL;

-- 3. Adicionar colunas novas ao equipment_model
ALTER TABLE equipment_model
    ADD COLUMN gpu_model VARCHAR(255),
    ADD COLUMN gpu_tdp_watts INTEGER,
    ADD COLUMN has_integrated_screen BOOLEAN NOT NULL DEFAULT FALSE;

-- 4. Adicionar FK monitor_id ao laboratory_equipment
ALTER TABLE laboratory_equipment
    ADD COLUMN monitor_id UUID REFERENCES monitor (id) ON DELETE RESTRICT;

CREATE INDEX idx_laboratory_equipment_monitor_id ON laboratory_equipment (monitor_id);

-- 5. Preencher monitor_id nos registros existentes de laboratory_equipment
UPDATE laboratory_equipment le
SET monitor_id = m.id
FROM equipment_model em
JOIN monitor m
    ON m.institution_id = em.institution_id
    AND m.name = em.monitor_name
    AND (m.watts IS NOT DISTINCT FROM em.monitor_watts)
WHERE le.equipment_model_id = em.id
    AND em.monitor_name IS NOT NULL;

-- 6. Remover colunas migradas do equipment_model
ALTER TABLE equipment_model
    DROP COLUMN monitor_name,
    DROP COLUMN monitor_watts,
    DROP COLUMN operating_system;

-- 7. Atualizar unique constraint do laboratory_equipment para incluir monitor_id
ALTER TABLE laboratory_equipment
    DROP CONSTRAINT laboratory_equipment_laboratory_id_equipment_model_id_opera_key;

-- Usar NULLS NOT DISTINCT para tratar NULL como valor igual na constraint
ALTER TABLE laboratory_equipment
    ADD CONSTRAINT uq_lab_equipment_config
    UNIQUE NULLS NOT DISTINCT (laboratory_id, equipment_model_id, operating_system, monitor_id);
