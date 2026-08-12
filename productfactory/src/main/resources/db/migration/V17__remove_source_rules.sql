-- sourceRules was een los, in de UI onzichtbaar tekstveld per product en werd los daarvan nog
-- gebruikt om te eisen dat elke onderzoeksbevinding naar een reeds gedocumenteerde bron verwijst.
-- Beide zijn op verzoek verwijderd (zie ShadowIterationEngine.validateResearch).
alter table product_definition drop column source_rules;
