# Stap 5 — Productontwerp MVP

## Implementatierecord

De actieve provider is `product-design-impl-mvp` met variant `single-agent`. Migratie V7 bewaart
epics, onveranderlijke epicversies, hervatbare processessies, bevroren bronnen en geheugenversies,
AI-taakcorrelaties, lifecycle-idempotentie en duurzame annuleringsoperaties. De publieke REST- en
UI-route gebruikt rechtstreeks `runProcessSession(productId)`; Operatie toont dezelfde sessie- en
AI-taakgegevens. Gerichte integratietests bewijzen wachten zonder duplicaat, geldige publicatie,
ongeldige output zonder bijeffecten, bewuste retry, no-op, parallelle producten en de publieke
epiclevenscyclus. Testbed 0.5.0 registreert versieerbare ontwerpresultaatscenario's; de daadwerkelijke
mockuitvoering blijft server-side eigendom van Agent Runtime.

## Doel en eindtoestand

Laat één Productontwerperagent relevante productinput omzetten in complete, duidelijke en
behapbare epics, inclusief UX-ontwerp wanneer zichtbaar gedrag verandert. Na deze stap kunnen
handmatige starts en de nog uitgeschakelde schedulerroute dezelfde duurzame processessie starten of
hervatten. Productontwerp maakt geen stories en start Productplanning niet.

## Ingangseisen

- Stap 4 staat gezond op acceptatie en productie.
- De rollen- en jobconfiguratie voor `PRODUCT_DESIGNER_MVP` is actief.
- Product, besluiten, signalen, vragen, agentgeheugen en AI-uitvoering zijn via publieke API's
  beschikbaar.
- De Runtime-acceptatieomgeving kan per scenario een gestructureerd ontwerpresultaat leveren.

## Normatieve bronnen

- [Productontwerp-API](../processen/productontwerp/api.md)
- [Productontwerp MVP](../processen/productontwerp/mvp.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
- [Frontend](../stakeholder/frontend.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)

## Concrete opleveringen

### Module, registratie en gegevens

- Maak `product-design-impl-mvp` de enige actieve provider van het publieke `design`-contract.
  Neem geen code of provider uit de uitgebreide variant op.
- Registreer implementatie-ID, -versie, artifact en broncommit in `ImplementationManifest` en op
  iedere nieuwe `ProcessSession`.
- Voeg migraties toe voor processessies, sessiestappen/claims, exact gebruikte bron- en
  geheugenversies, AI-taakreferenties, publicatiereferenties, epics en onveranderlijke epicversies.
- Bewaar technische claim- en idempotentiegegevens waarmee een verlopen claim veilig hervat en
  dubbele publicatie voorkomen wordt.
- Garandeer maximaal één onafgeronde logische ontwerpsessie per product; verschillende producten
  mogen parallel werken en een wachtende sessie houdt geen database- of serverlock vast.

### Publieke API en epiclevenscyclus

Implementeer alle commands en queries uit de Productontwerp-API:

- `runProcessSession(productId)` is de enige ingang die nieuwe ontwerp-AI-taken mag aanvragen;
- get/find voor epics en processessies ondersteunen de gespecificeerde filters, historie en
  sortering;
- claim, actief maken, gereed voor verificatie en verificatie registreren controleren exact epic-ID,
  versie, actor, idempotentiesleutel en toegestane statusovergang;
- intrekken is alleen toegestaan voor een nog beschikbare epic en bewaart een reden;
- annuleren legt eerst duurzaam de operatie vast, roept idempotent
  `cancelStoriesForEpic(...)` aan en sluit pas na bevestiging de epic af;
- een gekozen epicversie wordt inhoudelijk nooit gewijzigd; nieuwe kennis wordt een nieuwe epic of
  latere versie zolang de oude nog `AVAILABLE` is.

Implementeer de volledige publieke lifecycle
`AVAILABLE → IN_PLANNING → ACTIVE → VERIFYING → COMPLETED|NOT_SUCCESSFUL|ACTIVE`, plus
`SUPERSEDED`, `WITHDRAWN` en `CANCELLED`, inclusief `BLOCKED`-verificatie die op `VERIFYING` blijft.
Commands van latere capabilities zijn nu al correct en testbaar via contractfakes, zonder dat
Productontwerp hun tabellen kent.

### Verloop van één processessie

1. **Claim of hervat.** Hervat altijd eerst de bestaande wachtende/geblokkeerde sessie. Een tweede
   actieve functiecall voor hetzelfde product geeft `ProcessAlreadyRunning`; zonder zinvol werk
   ontstaat een zichtbare succesvolle no-op.
2. **Bevries input.** Lees via publieke queries de actuele productopdracht, geldige besluiten,
   relevante open/in-review signalen, vragen van de eigen rol, reeds beschikbare epics en alleen de
   al actieve downstreaminformatie. Leg iedere bron-ID en -versie vast.
3. **Bevries Git en geheugen.** Los de publieke Gitref read-only op naar een volledige commit-SHA,
   leg URL/SHA vast, laad alleen actueel geheugen van `PRODUCT_DESIGNER_MVP` en registreer exacte
   geheugenversies. De server checkt de repository niet uit.
4. **Vraag één complete AI-taak aan.** Bouw een vaste prompt en responseschema uit harde regels,
   bevroren bronnen en onvertrouwde context. Bevries jobconfiguratie en prompttemplateversie. Zet de
   sessie op `WAITING_FOR_AI` en retourneer zonder open thread.
5. **Hervat.** Zolang de taak niet terminaal is, blijft dezelfde sessie wachten zonder nieuwe taak.
   Bij terminale technische fout wordt zij zichtbaar `BLOCKED`; een bewuste nieuwe logische taak
   gebruikt dezelfde bevroren doelstelling maar een nieuwe idempotentiesleutel.
6. **Valideer deterministisch.** Controleer schema, bronversies, grenzen, geen stories, geen directe
   externe instructies en het volledige Epiccontract.
7. **Publiceer atomair.** Schrijf epicversie, sessie-uitkomst, publicatiereferentie,
   signaalverwerking en geldige eigen geheugenacties als herstelbare, idempotente effecten. Markeer
   geen bron als verwerkt wanneer de epic niet geldig gepubliceerd is.

### Verplicht Epiccontract

Iedere gepubliceerde epic bevat opgeslagen `title` en `summary` plus:

- één concreet gebruikersprobleem;
- de gekozen oplossing, scope en reden waarom zij het probleem oplost;
- verwijzingen naar productdoel en/of geldige besluiten;
- `uxDesign` wanneer gedrag of interactie zichtbaar wijzigt, anders geen kunstmatig UX-veld;
- concrete, observeerbare, testbare acceptatiecriteria;
- uitleg waarom de epic volledig maar behapbaar genoeg is voor zelfstandige stories;
- technische metadata, product, versie, status en exact gebruikte bronversies.

Een epic bevat geen storylijst, intern onderzoek, chain-of-thought of vrije technische
uitvoeringsinstructies. Titel en samenvatting zijn opgeslagen presentatievelden en spreken de
volledige inhoud niet tegen.

### Vragen, besluiten, signalen en geheugen

- Publiceer een voorgestelde tijdelijke vraag alleen via vertrouwd `askStakeholder(...)` met de
  echte rol en processessie. Sla haar niet op als geheugen.
- Registreer een groot, blijvend en richtinggevend Factorybesluit alleen via het Besluitenregister;
  normale ontwerpkeuzes blijven in de epic.
- Behandel een signaal als input, niet als opdracht. Bewaar oorspronkelijke tekst en verwerk status
  of epiclink alleen via de productcommands.
- Pas eigen geheugenacties pas toe na geldige epicpublicatie. Een afgekeurde of mislukte taak leert
  niets.

### HTTP, frontend, Testbed en operatie

- Voeg bevoegde handmatige `runProcessSession(productId)`-REST/UI-acties toe met duidelijke 409 bij
  een werkelijk actieve call en hervatting bij een niet-actieve wachtende sessie.
- Bouw **Ontwerp** met epiclijsten per status, opgeslagen titel/samenvatting, volledige detailinhoud,
  versiehistorie, relaties en toegestane acties voor herprioriteren, intrekken of annuleren.
- Toon sessiestatus, gebruikte implementatie, inputs, Git-SHA, AI-taak, publicaties, no-op,
  blokkade en fout in Operatie.
- Voeg versieerbare Runtime-mockscenario's toe voor geldige epic, geen zinvol werk, UX vereist,
  vraag aan Stakeholder, ongeldig resultaat, wachtende taak en terminale fout.
- Sluit alleen de capabilities aan die op dit moment actief zijn. Story- en kwaliteitscontracten
  mogen bestaan, maar ontbrekende providers worden niet aangeroepen.

## Uitvoeringsvolgorde

1. Herstel eventuele verschillen tussen publieke API, MVP-specificatie en bestaande contracts.
2. Voeg module, composition-rootselectie, manifestregistratie en rol/jobregistratie toe.
3. Voeg migraties, repositories, sessieclaiming en epiclevenscyclus toe.
4. Implementeer bevroren input-, Git-, geheugen- en promptopbouw.
5. Implementeer aanvragen, wachten, hervatten, deterministic validation en atomische publicatie.
6. Implementeer de cross-module commands voor signalen, vragen, besluiten en annulering.
7. Voeg HTTP, frontend, Operatie en Testbedfixtures toe.
8. Voer de verplichte bewijzen uit en release via `main`.

## Verplichte automatische bewijzen

- botsende call, wachtende hervatting, succesvolle no-op en parallelle producten;
- exact één AI-taak per sessiestap ondanks herhaling of crash;
- bron- en geheugenversiebevriezing en negeren van later gewijzigde input;
- afwijzing van ontbrekende epicvelden, ontestbare criteria, vereist maar ontbrekend UX en stories
  in agentoutput;
- atomische publicatie, idempotente replay en geen geheugen- of signaalmutatie bij ongeldige output;
- supersede/withdraw/claim/cancel en alle toegestane verificatieovergangen;
- openbare Git-SHA wordt bevroren en alleen de Runtime-worker krijgt checkoutverantwoordelijkheid;
- REST/frontend/Testbed/PostgreSQL/releasecontrole volgens de vaste afronding.

## Aanbevolen commitgrenzen

1. contracten, module en migraties;
2. sessie- en epiclevenscyclus;
3. AI-input, hervatting, validatie en publicatie;
4. cross-module effecten, frontend, Testbed en Operatie;
5. tests, documentatie en releasecorrecties.

## Buiten scope

Er komen geen droom-, onderzoeks- of gespecialiseerde agents uit `uitgebreid.md`. Productontwerp
maakt geen stories, backlog, verificaties of dispatchpogingen. Productplanning en
Kwaliteitsbewaking worden pas in stap 6 en 7 als actieve providers aangesloten; automatische
scheduleclaims blijven tot stap 9 uit.

## Definitie van klaar

Stap 5 is klaar wanneer een bevoegde handmatige of gesimuleerde geplande start via één
Productontwerperagent een geldige, complete en geversioneerde epic kan publiceren; wachten,
hervatten, botsingen, fouten en no-op verklaarbaar zijn; geen stories ontstaan; alle automatische
bewijzen groen zijn; en dezelfde MVP-provider gezond op acceptatie en productie draait.
