-- Koppelt een storykandidaat optioneel aan het roadmapthema waarvoor STORY_WRITER 'm heeft geschreven
-- (zie ShadowIterationEngine.storyPrompt), zodat de roadmap-sessie later kan zien welke thema's al
-- concrete stories hebben opgeleverd.
alter table story_candidate add column theme_id varchar(120) references roadmap_theme(id);
