# product-174 - Worklog

Story-context:
Uitklapbare cycluskaarten en betrouwbare opbrengstkoppeling.

Stappenplan:
- [x] Factory-instructies, story en bestaande frontendarchitectuur lezen.
- [x] Pure, defensieve koppellogica met unittests implementeren.
- [x] Afzonderlijke laadstatussen en toegankelijke cycluskaarten implementeren.
- [x] Widget-, semantics-, responsive-, contrast- en regressietests toevoegen.
- [x] Gewijzigde Dart-bestanden formatteren en zelfreview uitvoeren.
- [x] Het volledige verplichte vangnet groen afronden.

Gedaan / rationale:
- De scope is bewust beperkt tot het dashboard: de story vraagt client-side groepering van reeds
  geladen records en sluit wijzigingen aan backend, API-contracten en infrastructuur uit.
- De bestaande globale kandidaten- en leveringssecties blijven leidend voor hun huidige gedrag;
  gekoppelde opbrengst is een aanvullende presentatie per cyclus.
- `groupIterationResults` gebruikt alleen de contractuele product+cyclusnummer- en
  product+cyclus-id-sleutels. Ontbrekende, anders getypeerde, kruisproduct- en ambigue relaties
  komen één keer in de niet-koppelbare categorie terecht.
- Cycli, kandidaten en leveringen laden onafhankelijk. Kaarten tonen per bron geladen aantallen,
  laden of niet beschikbaar; het globale niet-koppelbare totaal verschijnt alleen na drie
  succesvolle bronnen.
- De eigen cycluskaart gebruikt een native knop met expanded-semantiek en expliciete focusrand.
  Alleen de gekoppelde groepen worden uitgeklapt; de bestaande beslisbronknop opent het detail.
- Nieuwe tests dekken pure koppeling, de aanvankelijk door de 5-itemsbeperking verborgen cyclus,
  gedeeltelijk laden en fouten, muis/toetsenbord/focus, onafhankelijke kaarten, refreshbehoud,
  semantics, lege groepen, lange tekst bij 320 CSS-pixels/200%, WCAG-contrast en golden-output.
- Definitief vangnet: `mvn -B --no-transfer-progress clean verify` gaf `BUILD SUCCESS` met 0
  failures en 0 errors; `flutter analyze` gaf `No issues found`; `flutter test` gaf 243 tests,
  `All tests passed`.

Review 2026-08-12:
- [info] De volledige story-diff `main...HEAD` op revision
  `dd9a6b4ed8f9a17348e52e2c66fdae57f3a98855` is beoordeeld. De gerichte regressieset voor
  koppeling, cycluskaarten, gedeeltelijke/foutstatussen, beslisdetail en de prominente startactie
  gaf 24/24 groen; de worktree bleef daarna ongewijzigd.
- [bug] `main.dart:788` gebruikt alleen het cyclus-id als sibling-key voor de stateful kaart. Twee
  geladen cycli met hetzelfde id — precies de fixture die een ambigue leveringskoppeling moet
  afhandelen — leveren daardoor duplicate keys op in `LimitedListSection` in plaats van twee
  defensief gerenderde kaarten met een niet-gekoppelde levering. De pure groeperingstest rendert
  deze ambigue invoer niet en vangt de regressie daarom niet.
- [blocker] De contrasttest in `iteration_cycle_card_test.dart:292` pompt geen enkele widget en
  inspecteert geen gerenderde voorgrond/achtergrond. Hij vergelijkt uitsluitend geëxporteerde
  kleurconstanten en bewijst daardoor niet het expliciete criterium voor daadwerkelijk gerenderde
  gesloten, geopende, fout- en focustoestanden.
- [blocker] In de beschikbare task-context en repository staat geen agentworker-gemeten bewijs met
  commandresultaten die aan dezelfde HEAD/worktree-tree zijn gebonden. De developercomment en dit
  worklog bevatten alleen handgeschreven groen proza; volgens de reviewer-regels is dat geen geldig
  volledig testbewijs.
