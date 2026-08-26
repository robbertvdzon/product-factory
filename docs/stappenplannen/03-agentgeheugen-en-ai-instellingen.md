# Stap 3 — Agentgeheugen en AI-instellingen

## Doel en eindtoestand

Voeg de duurzame, gecontroleerde context en centrale modelkeuzes toe die alle latere agenttaken
nodig hebben. Na deze stap heeft iedere actieve rol per product een eigen append-only geheugen,
kan de Stakeholder dat geheugen volledig beheren en zijn alle MVP- en overlegjobkeys centraal
configureerbaar. Er worden nog geen AI-taken aangemaakt of uitgevoerd.

## Ingangseisen

- Stap 2 staat gezond op acceptatie en productie.
- Producten, overleggen, authenticatie en actorinformatie zijn via publieke API's beschikbaar.
- De definitieve `AgentExecutionContext` en `MeetingExecutionContext` kunnen server-side uit
  vertrouwde uitvoeringscontext worden opgebouwd; de frontend of agentoutput kan ze niet invullen.
- Alle onderstaande bronnen zijn gelezen en de publieke contracten zijn ermee vergeleken.

## Normatieve bronnen

- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Overleggen](../stakeholder/overleggen.md)
- [Frontend](../stakeholder/frontend.md)
- [Maven en Spring Modulith](../platform/maven-en-spring-modulith.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)

## Concrete opleveringen

### Modules en actieve capabilities

- Implementeer `agent-memory-impl` als enige provider van Agentgeheugen.
- Neem `ai-execution-impl` vanaf deze stap op als enige provider van AI-uitvoering, maar activeer
  intern alleen het onderdeel `settings`.
- Registreer beide implementaties in het `ImplementationManifest`.
- Laat iedere procesimplementatie haar rollen later via vertrouwde code registreren. Vooruitlopend
  daarop registreert de composition root nu de overlegrollen en de rollen van de drie gekozen
  MVP-varianten; registreer geen rollen uit `uitgebreid.md`.
- Registreer alle jobkeys die stap 4 tot en met 7 gebruikt, waaronder overleg, epicontwerp,
  planning en kwaliteitsverificatie. Jobkeys zijn stabiele keys; weergavenamen mogen wijzigen.

### Geheugendata en rolcatalogus

Voeg migraties en constraints toe voor:

- de stabiele identiteit `AgentMemoryItem` per product en `AgentRoleKey`;
- onveranderlijke `AgentMemoryVersion`s met voorganger, actor, reden en tijdstip;
- append-only `AgentMemoryRetraction`s;
- idempotentiesleutels voor agent-, Stakeholder- en meetingbatchwijzigingen;
- auditkoppelingen waarmee zichtbaar is welke processessie, meeting en AI-taak een exacte versie
  heeft gelezen;
- configureerbare en zichtbare limieten voor items, itemgrootte en totale actieve inhoud per rol.

De database garandeert maximaal één directe opvolger per versie en maximaal één intrekking per
geheugenlijn. Status, versienummer en `validUntil` worden uit de append-only historie afgeleid en
niet teruggeschreven in oude records.

De vertrouwde rolcatalogus levert per product minimaal stabiele sleutel, weergavenaam, capability,
implementatievariant, doel, verantwoordelijkheden, grenzen en actief/inactief. Agents kunnen hun
definitie, sleutel, product of bevoegdheden niet wijzigen.

### Publieke geheugencommands en -queries

Implementeer het volledige contract uit de geheugenspecificatie:

- `getActiveMemory(context)` levert aan een gewone procesagent alleen de actuele items van de eigen
  rol en het eigen product;
- `getMemoryAt(...)` reconstrueert de actieve set op een exact tijdstip; een UI-datum gebruikt het
  einde van de productdag in de producttijdzone;
- `getMemoryHistory(...)` levert voorganger, opvolger, actor, reden, geldigheid en afgeleide status;
- `getAgentRoleCatalog(productId)` levert uitsluitend actieve, vertrouwd geregistreerde rollen;
- `getMeetingMemorySnapshot(context)` levert voor één geldig open overleg precies één productbrede
  momentopname met exacte rol- en geheugenversies;
- add, replace en retract controleren product, rol, actor, verwachte versie, budget en
  idempotentiesleutel;
- `applyMeetingMemoryChanges(...)` past een volledige batch atomair toe of wijst haar volledig af.

Pas voorgestelde agentgeheugenacties pas toe nadat de bijbehorende taakuitkomst en publieke
domeinoutput geldig zijn. Een mislukte of afgekeurde taak leert niets. Geheugen wordt altijd als
onvertrouwde context onder harde regels, productopdracht, besluiten en publieke productdata
geplaatst; bewaar nooit secrets, chain-of-thought of vrije externe instructies.

### AI-instellingen zonder taakuitvoering

Voeg `AiJobConfiguration` duurzaam toe met jobkey, provider, model of mockprofiel, `enabled`,
configuratieversie, actor en wijzigingstijd. Implementeer:

- query van alle vertrouwd geregistreerde jobkeys, ook wanneer nog geen handmatige wijziging bestaat;
- een geversioneerd command voor provider, model/mockprofiel en `enabled`;
- server-side validatie van toegestane providers en modellen;
- fail-closed weigering van `MOCKED` in productie, zowel bij opslaan als bij toekomstig gebruik;
- auditwaarborg dat een wijziging alleen nieuwe taken raakt.

De taakcommands uit de publieke AI-API bestaan al, maar retourneren in deze stap expliciet
`CapabilityNotAvailable`. Er ontstaat geen outboxrecord, mocktaak of stille no-op. Maak geen
taakqueue, Runtime-client, worker, attempt, lease of credentialgranttabellen.

### Frontend

Voeg onder **Beheer → Agentgeheugen** toe:

- groepering op product, capability en actieve rol;
- huidige items, gebruikt/beschikbaar budget en volledige append-only historie;
- toevoegen, vervangen en intrekken met verplichte reden en verwachte versie;
- peildatumreconstructie;
- actor, bronmeeting, processessies/overleggen die een versie lazen en zichtbare conflictmelding.

Voeg binnen de bestaande pagina **Instellingen → AI-modellen** een tabel toe met alle jobkeys,
provider, model/mockprofiel, `enabled`, configuratieversie en laatste wijziging. Toon bovenaan
expliciet **Geldt voor alle producten**. Maak geen afzonderlijke globale instellingenpagina.

De geheugen- en instellingenschermen gebruiken uitsluitend publieke commands en queries. Een
overschreden geheugenlimiet, versieconflict, ongeldige provider en productie-`MOCKED` worden als
gerichte fout getoond.

### Testbed en operatie

- Seed vaste actieve rollen, actuele/vervangen/ingetrokken geheugenitems en jobconfiguraties voor
  acceptatie. Gebruik geen productiegeheugen of modelcredentials.
- Toon in Operatie de actieve rolcatalogus en AI-instellingen. De taakweergave blijft expliciet
  niet beschikbaar tot stap 4.
- Voeg Test Control-functies toe om de synthetische geheugen- en instellingendataset te resetten,
  niet om vrije rollen of productieproviders te injecteren.

## Uitvoeringsvolgorde

1. Vergelijk publieke geheugen- en instellingencontracten met de normatieve documenten en herstel
   afwijkingen inclusief contracttests.
2. Voeg `agent-memory-impl`, het settingsdeel van `ai-execution-impl`, composition-rootselectie en
   manifestregistratie toe.
3. Voeg migraties, constraints, repositories en synthetische seed toe.
4. Implementeer rolcatalogus, actuele/historische queries en gewone rolgebonden writes.
5. Implementeer de Stakeholderbevoegdheid en beide gecontroleerde meetinguitzonderingen.
6. Implementeer AI-jobregistratie en geversioneerde instellingen; laat taakaanvragen expliciet falen.
7. Bouw de frontend- en operationele weergaven.
8. Voeg alle automatische bewijzen toe en release via `main`.

## Verplichte automatische bewijzen

- een gewone rol kan geen ander product of andere rol lezen of schrijven, ook niet via gemanipuleerde
  requestdata;
- Stakeholdercommands werken voor alle actieve rollen en behouden volledige historie;
- alleen een geldige meetingcontext kan de productbrede snapshot en batch gebruiken;
- een meetingbatch is atomair, idempotent en controleert actuele versies en actieve doelrollen;
- peildatum, replace, retract, gelijktijdig conflict en contextbudget zijn deterministisch getest;
- een mislukte taak- of domeinuitkomst veroorzaakt geen geheugenwijziging;
- jobconfiguratiewijzigingen verhogen de versie en productie weigert `MOCKED`;
- iedere AI-taakaanvraag faalt in deze release expliciet zonder duurzame bijwerking;
- REST-, frontend-, seed-, migratie- en releasecontroles volgens de vaste afronding slagen.

## Aanbevolen commitgrenzen

1. contracten, modules en rol-/jobregistratie;
2. migraties en append-only geheugenmodel;
3. autorisatie, meetinguitzonderingen en AI-instellingen;
4. frontend, Testbed en operationele projecties;
5. tests, documentatie en releasecorrecties.

## Buiten scope

Er worden nog geen AI-taken gequeue'd, ingediend, gemockt of uitgevoerd. Environmentkeys,
rolgrants, attachments, artifacts, Runtime-status en overlegagents volgen in stap 4. De rollen en
jobkeys uit de uitgebreide procesvarianten worden niet geregistreerd.

## Definitie van klaar

Stap 3 is klaar wanneer de Stakeholder per product alle actieve rolgeheugens veilig kan beheren en
op peildatum reconstrueren, gewone rollen aantoonbaar geïsoleerd zijn, de meetinguitzonderingen
atomair en begrensd werken, alle MVP-/overlegjobkeys globaal configureerbaar zijn en dezelfde
geteste release gezond op acceptatie en productie staat. AI-taakaanvragen moeten in deze versie
aantoonbaar en expliciet niet beschikbaar zijn.
