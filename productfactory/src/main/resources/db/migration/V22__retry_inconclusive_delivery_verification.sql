-- Een technisch mislukte oplevercheck moet na herstel van de browseromgeving opnieuw kunnen lopen,
-- maar mag niet iedere roadmap-sessie blijven bezetten wanneer een oordeel echt onzeker blijft.
alter table delivery_verification add column attempt_count integer not null default 1;
