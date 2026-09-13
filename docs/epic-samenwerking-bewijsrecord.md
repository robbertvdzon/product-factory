# Bewijsrecord — epic-samenwerking

Datum: 2026-09-13. Ontwerp en schermafspraken staan in
[het voorstel](voorstellen/epic-samenwerking-po-architect.md) en
[de UX-overdracht](ux/epic-samenwerking/README.md). Inrichting en migratie staan in
[het runbook](platform/epic-samenwerking-runbook.md).

## Geautomatiseerd bewijs

De volledige `./product-factory verify` is uitgevoerd met Java 21 en Flutter 3.44.6:
169 backendtests geslaagd, inclusief echte PostgreSQL-migraties vanaf leeg en vanaf oudere
releases, en 41 frontendtests geslaagd. Analyse en releasebuild zijn geslaagd.

Nieuwe gerichte scenario's in `ProductDesignMvpIntegrationTest` bewijzen:

- menselijk PO-akkoord met automatische architectbeoordeling binnen mandaat;
- blokkade van planning vóór akkoord en behoud van akkoord bij statusovergangen;
- architectreview bij materiële product-AI-impact en onbekende kosten;
- onderzoeksverzoek blijft open totdat een nieuw besluit volgt;
- beleidswijziging maakt eerdere besluiten ongeldig;
- autonome beoordelingen zonder menselijke stap;
- onbekende technische impact kan niet worden goedgekeurd;
- late verfijning blokkeert oude geplande inhoud;
- nieuwe inhoud vraagt nieuwe goedkeuring, met behoud van besluithistorie;
- rolgerichte planningsvraag verwerkt een idempotent antwoord eerst in nieuwe uitwerking;
- factory-owner-, idempotentie-, versie- en ingetrokken-rolcontroles;
- vraag-ID’s kunnen niet als signalen worden verwerkt; het AI-responseschema begrenst de
  toegestane signaal-ID’s en herhalingen ontvangen de concrete validatiefout;
- hervatting van dezelfde ontwerpvraag nadat de AI nieuw geheugen heeft gepubliceerd;
- twee opeenvolgende vragen binnen één sessie, met afzonderlijke idempotentiesleutels voor
  vragen/geheugen en zonder dat het eerste antwoord de tweede open vraag passeert;
- bestaande eigen API/data veroorzaakt geen fictieve externe-bronneneis.

`AuthenticationFlowTest` bewijst login van een uitgenodigde architect buiten de serverallowlist,
rolgebonden producttoegang, weigering van beheer en PO-mutaties en onmiddellijke intrekking.
Bestaande tests blijven de requestroute, gerichte vragen en hervatting, UX-artifactbehoud,
planning, dispatcher, verificatie en herstelpaden controleren.

`epic_collaboration_test.dart` controleert de PO- en architectwerkplek op 320px met 200% tekst,
rolafhankelijke acties en het onderscheid tussen versieconflicten en ontbrekende optionele
omgevingsconfiguratie. Dezelfde test controleert het publieke viewport-mapcontract en het ophalen van het juiste
beeld bij wisselen tussen desktop en mobiel. De echte gebouwde frontend wordt daarnaast in
Chromium gecontroleerd.

## Operationele verificatie

De productieproef gebruikt het herkenbare product `pf-epic-smoke-1789308159914`, met echte
Agent Runtime-jobs en uitgeschakelde dispatch en productschedules. De proef is uitgevoerd via
de publieke, geauthenticeerde API en Chromium op de echte productie-frontend. De gebruikte
accounts kregen productrollen op dit testproduct; geen Marc-account of PvdD-product is verzonnen.

Gecontroleerd tijdens de proef:

- Factorybeheer kan de besturingsrollen instellen, maar geen productbudget wijzigen.
- De architect kan productafspraken wijzigen, maar geen PO-idee of factorybeheer uitvoeren.
- De PO kan een duurzaam idee bespreken met de echte Product Advisor en het voorstel bevestigen.
- Productontwerp publiceert een echte epic met zeven impactcategorieën, product-AI-risico's en
  tien PNG-ontwerpen: vijf toestanden met telkens een desktop- en mobiele variant.
- Het voorstel “iedere tien minuten AI” veroorzaakt een echte, persoonlijk aan de architect
  gerichte vraag. De vraag en het antwoord zijn duurzaam gekoppeld aan dezelfde epic/request.
- Een apart nieuw product zonder testomgeving of bestaand agentgeheugen kan zijn eerste
  AI-gesprek starten (revisie `f83067b80886375e8dbd9516d6b42e924b1487ea`). Deze extra opstarttaak
  is daarna gecontroleerd geannuleerd en het product inactief gemaakt.

De productieproef vond en reproduceerde problemen met de optionele eerste productomgeving,
initieel agentgeheugen, geheugen na een vervolgvraag, de brede externe-datawoordherkenning,
het viewport-mapcontract, opeenvolgende vragen en verwarring tussen vraag- en signaal-ID’s.
De fixes hebben gerichte regressiedekking; de volledige verificatie is na de laatste
codewijziging opnieuw geslaagd.

Backend en frontend op productie draaien op `d8ab4660fd99a3a2dd979a0537fce19444269d42`.
De officiële `tools/smoke-test.sh production d8ab4660fd99a3a2dd979a0537fce19444269d42` slaagt;
deployment en frontend-/backendidentiteit zijn rechtstreeks op de openbare routes gecontroleerd.
[GitHub-verificatie](https://github.com/robbertvdzon/product-factory/actions/runs/34765673988) en
[release via acceptatie naar productie](https://github.com/robbertvdzon/product-factory/actions/runs/34765848698)
zijn geslaagd. Argo CD heeft de immutable imagepins uitgerold.

Dezelfde ontwerpsessie is na de reparaties hervat met het bewaarde architectantwoord.
Op revisie `6f1fbdd310a5817980682494b9f7fbb5682cb174` is de volgende functionele vraag in de echte
PO-werkplek via **Vragen → Beantwoord vraag → Bevestigen** beantwoord (HTTP 204). De eerdere
architectreactie bleef bewaard en Productontwerp hervatte dezelfde epic met beide antwoorden.
De PO-/architectwerkplekken, het wisselen van desktop-/mobiele UX-varianten en de factory-owner-
werkplek zijn in Chromium gecontroleerd; de ontwerpbeelden werden met PO-rechten als HTTP 200
opgehaald. [Schermafbeeldingen en echte AI-uitvoer](ux/epic-samenwerking/README.md#productieproef).

## Openstaande eindcontrole

De latere AI-uitvoer gebruikte vraag-ID’s als `processedSignalIds`. De backend weigerde publicatie
terecht; de proef bereikte daardoor nog niet de afsluitende PO-/architectgoedkeuring. Revisie
`d8ab466` beperkt deze uitvoer tot actuele signaal-ID’s (bij geen signalen: een lege lijst) en
geeft de validatiefout mee aan herhalingen. Die revisie is volledig automatisch getest en de
productie-release/smoketest is geslaagd, maar de laatste echte AI-herhaling en beide uiteindelijke
goedkeuringen zijn nog niet op productie bevestigd.

De automatische goedkeuringscontrole heeft het openen van een nieuwe test-debugsessie geweigerd:
het gebruik van `PF_DEBUG_TOKEN` voor de productie-eindcontrole vraagt expliciete toestemming.
De eerder gestarte proef heeft het testproduct inactief gemaakt. Dispatch en productschedules
bleven uit; uit deze proef zijn geen applicatiewijzigingen via Software Factory verstuurd.

Bij de gewone Google-login in de Codex-browser meldde Google: `The given origin is not allowed
for the given client ID.` Dit is afzonderlijk van de geverifieerde serverrollen; de origin-
configuratie van de ingestelde Google OAuth-client moet worden gecontroleerd voordat de normale
login voor Marc als werkend kan worden afgetekend. Een gewone Chrome-login is niet geverifieerd.

## Bewuste integratiegrenzen

Er is geen nieuwe Software Factory-callback voor ontwikkelaarsvragen verzonnen. De bestaande
vraagcommands ondersteunen rol- en epic/storycontext; ontvangen vragen blokkeren gekoppeld
nieuw werk. Reeds extern gestart werk behoudt zijn identiteit en wordt gevolgd. De factory
claimt niet dat een lokaal gewijzigd epic reeds extern werk heeft gestopt.

Bestaande epics zonder impact worden pas onder de nieuwe poorten gebracht wanneer het product
wordt ingericht; ontbrekende impact vraagt dan verfijning. Oude factory-ownerbesluiten blijven
historisch bewijs en geven geen nieuwe architectbevoegdheid.
