# Runbook — agentgeheugen en AI-instellingen

Dit runbook beschrijft de operationele controle en foutafhandeling van de in stap 3 actieve
capabilities. De functionele invarianten blijven normatief vastgelegd in
[Agentgeheugen](../gedeelde-modules/agentgeheugen.md) en
[AI-uitvoering](../gedeelde-modules/ai-uitvoering.md).

## Actieve implementatie

`GET /api/foundation/implementations` moet de volgende selecties tonen:

| Capability | Artifact | Variant |
|---|---|---|
| `agent-memory` | `agent-memory-impl` | `append-only-jdbc` |
| `ai-execution` | `ai-execution-impl` | `settings-only` |

Flywayversies 4 en 5 zijn respectievelijk eigenaar van het agentgeheugen en de globale
AI-jobinstellingen. Er bestaan in deze release bewust geen AI-taak-, attempt-, lease-,
credentialgrant- of outboxtabellen.

## Operationele controles

1. Controleer `GET /api/operations/step-3`. Ieder actief product heeft vijf vertrouwd
   geregistreerde rollen en alle vijf vaste jobkeys zijn aanwezig.
2. Controleer onder **Beheer → Agentgeheugen** een huidig item, het budget en de historie. Een
   peildatum gebruikt het einde van die productdag in de producttijdzone.
3. Controleer onder **Beheer → AI-modellen** dat boven de joblijst **Geldt voor alle producten**
   staat en dat provider, model, enabled-status en configuratieversie zichtbaar zijn.
4. Controleer op acceptatie via Testbed het scenario `memory-and-ai-settings`. Een reset laadt
   uitsluitend synthetische rollen, actuele/vervangen/ingetrokken items, leesaudit en
   mockconfiguraties.

## Foutgedrag en herstel

- HTTP 409 bij vervangen, intrekken of een AI-modelwijziging betekent dat de verwachte versie
  verouderd is. Vernieuw de gegevens en dien een nieuw command met een nieuwe idempotentiesleutel in.
- Een geheugenbudgetoverschrijding wordt volledig afgewezen; vergroot limieten niet buiten een
  expliciete, geversioneerde configuratiewijziging om.
- Een ongeldige rol, actor, meetingstatus, provider of model wordt fail-closed afgewezen.
- `MOCKED` wordt in productie zowel bij configureren als gebruiken geweigerd.
- Iedere AI-taakaanvraag retourneert in stap 3 `CapabilityNotAvailable` (HTTP 501). Dit is gezond
  gedrag tot stap 4 en vereist geen herstelactie.
- Een mislukte meetingbatch schrijft niets. Lees de actuele versies opnieuw en pas de volledige
  batch opnieuw toe; voer geen gedeeltelijke databasecorrectie uit.

## Releasecontrole

De releaseworkflow bouwt één immutable backend- en frontendartifact en promoveert dezelfde digests
eerst naar acceptatie en daarna naar productie. Vergelijk op beide omgevingen `GET /api/version`
met de vrijgegeven Git-revisie en controleer daarna `GET /actuator/health`. Productiegegevens worden
niet via Testbed of fixtures aangepast.
