-- Notulen van een afgesloten overleg worden, net als cyclus-dossiers, gepubliceerd naar
-- product-factory-workspace (zie MeetingChatService.closeOut / WorkspacePublicationPort). Deze drie
-- kolommen leggen de publicatiereferentie vast zodat het dashboard er direct naar kan linken. De
-- publicatie is best-effort (bv. niet mogelijk bij workspace_ownership 'owner'): blijft leeg als hij
-- niet is gelukt, het overleg zelf is dan alsnog gewoon afgesloten.
alter table meeting add column workspace_run_id varchar(120);
alter table meeting add column workspace_pull_request_url varchar(1000);
alter table meeting add column workspace_commit_sha varchar(64);
