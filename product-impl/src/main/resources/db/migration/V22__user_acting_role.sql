-- Rol waarmee een gebruiker nu werkt. Een factory owner kan tijdelijk als product owner werken;
-- NULL betekent de hoogste toegekende rol.
ALTER TABLE pf_user_account ADD COLUMN acting_role VARCHAR(30)
    CHECK (acting_role IS NULL OR acting_role IN ('FACTORY_OWNER','PRODUCT_OWNER'));
