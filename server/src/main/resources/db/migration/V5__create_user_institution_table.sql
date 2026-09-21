CREATE TABLE user_institution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user (id),
    institution_id UUID NOT NULL REFERENCES institution (id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('GESTOR', 'PESQUISADOR')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'PENDING')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_user_institution_unique ON user_institution (user_id, institution_id);
CREATE INDEX idx_user_institution_user_id ON user_institution (user_id);
CREATE INDEX idx_user_institution_institution_id ON user_institution (institution_id);
