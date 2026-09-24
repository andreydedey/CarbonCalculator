CREATE TABLE laboratory_equipment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    laboratory_id UUID NOT NULL REFERENCES laboratory (id),
    equipment_model_id UUID NOT NULL REFERENCES equipment_model (id) ON DELETE RESTRICT,
    operating_system VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (laboratory_id, equipment_model_id, operating_system)
);

CREATE INDEX idx_laboratory_equipment_laboratory_id ON laboratory_equipment (laboratory_id);
CREATE INDEX idx_laboratory_equipment_equipment_model_id ON laboratory_equipment (equipment_model_id);
