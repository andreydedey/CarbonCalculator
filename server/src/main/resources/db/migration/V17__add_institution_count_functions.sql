-- SECURITY DEFINER functions bypass RLS, allowing cross-tenant counts
-- for the institution listing endpoint (which has no tenant context).

CREATE FUNCTION count_labs_for_institution(inst_id UUID) RETURNS BIGINT
LANGUAGE SQL SECURITY DEFINER STABLE AS $$
    SELECT count(*) FROM laboratory WHERE institution_id = inst_id;
$$;

CREATE FUNCTION sum_equipment_for_institution(inst_id UUID) RETURNS BIGINT
LANGUAGE SQL SECURITY DEFINER STABLE AS $$
    SELECT coalesce(sum(le.quantity), 0)
    FROM laboratory_equipment le
    JOIN laboratory l ON le.laboratory_id = l.id
    WHERE l.institution_id = inst_id;
$$;
