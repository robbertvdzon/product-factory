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

## Afsluitende productiecontrole

De latere AI-uitvoer gebruikte vraag-ID’s als `processedSignalIds`. De backend weigerde publicatie
terecht; de proef bereikte daardoor aanvankelijk niet de afsluitende PO-/architectgoedkeuring. Revisie
`d8ab466` beperkt deze uitvoer tot actuele signaal-ID’s (bij geen signalen: een lege lijst) en
geeft de validatiefout mee aan herhalingen. Die revisie is volledig automatisch getest en de
productie-release/smoketest is geslaagd. De laatste echte AI-herhaling en beide uiteindelijke
goedkeuringen zijn op 2026-09-13 om 16:13 UTC ook op productie bevestigd:

- AI-taak `99e07241-87c7-4a43-b7a5-8135870c82bf` slaagde met promptversie 7 op revisie `d8ab466`.
- Dezelfde epic `b07e7ca2-3d51-4364-a652-bf867631818e` kreeg inhoudsversie 3, met beide eerdere
  antwoorden verwerkt, tien bewaarde UX-beelden en zeven impactcategorieën.
- Alle tien ontwerpbeelden waren met PO-rechten bereikbaar (HTTP 200, afbeeldingscontenttype).
- Alleen PO-akkoord gaf geen vrijgave: architectbeoordeling bleef verplicht wegens product-AI-impact.
- Expliciet architectakkoord voor de geïsoleerde proef gaf `ready=true`, zonder blockers,
  voor inhoudsversie 3 en beleidsversie 2. Herhaling met dezelfde idempotentiesleutel resulteerde
  in precies twee besluiten: één PO-besluit en één architectbesluit.
- Voortgang meldde ontwerp afgerond en beschikbaar voor planning; bouw en verificatie stonden
  terecht nog op wachtend, zonder stories of verzonnen implementatieresultaten.

Na expliciete toestemming van de gebruiker is uitsluitend de bestaande `PF_DEBUG_TOKEN`-sleutel
rechtstreeks naar het geheugen van het testproces gelezen. De sleutel, sessiecookies en CSRF-token
zijn niet in chat, testuitvoer of bestanden opgenomen. De eerdere automatische weigering is daarmee
opgelost. De proef heeft de actieve rol teruggezet naar factory owner en het testproduct inactief
gemaakt. Dispatch en productschedules bleven uit; uit deze proef zijn geen applicatiewijzigingen
via Software Factory verstuurd.

## Nog te controleren bij ingebruikname

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

## Antwoordbeelden in de chat — 14 september 2026

- Product Advisor promptversie 5 kan vier optionele PNG-artifacts opleveren. Screenshots verwijzen
  naar de geconfigureerde productie- of acceptatie-origin; ontwerpen en illustraties hebben een eigen label.
- V36 koppelt gevalideerde, duurzaam bewaarde Runtime-artifacts aan het antwoordbericht.
  Persoonlijke gesprekstoegang geldt ook voor de beeldroute; querystrings verdwijnen uit de bronvermelding.
- De gedeelde chat toont beelden met vergroten, zoomen en downloaden. Bij vervolgvragen kunnen
  recente beelden binnen het Runtime-inputbudget opnieuw worden aangeboden.
- Lokale verificatie: 188 backendtests en 55 frontendtests geslaagd; waaronder de echte PNG-kopie
  via het v2-contract, berichtpaginering, duurzame retentie, hergebruik, vreemde bron/taak weigeren,
  persoonlijke autorisatie en vergroting op 320px. Dit zijn geautomatiseerde tests met een mockruntime;
  een echte browseropname door de productie-AI vraagt aanvullend een live controle.

Live gecontroleerd op productie met revisie `b74da6167d627b08c8a2d7259ddd3a1254edde9a`:
release 34846938704 en smokechecks voor acceptatie/productie geslaagd. Als PO robbertvdzon
binnen HKH is een nieuw persoonlijk gesprek gestart met de vraag om het publieke homescherm.
AI leverde een herkenbare browseropname met label **Screenshot · Productie** en bron
`https://hkh.vdzonsoftware.nl/`. Vergroten en downloaden werkten; de download is een geldige
PNG van 1440×1000 pixels (61.003 bytes). Op een vervolgvraag om een uitlegplaatje maakte AI een
diagram van de onderdelen van die screenshot, zichtbaar met het aparte label **Illustratie**.
De twee beelden staan bij hun eigen antwoord. De tijdelijke PO-rolweergave is beëindigd en de
controletabs zijn gesloten; het gesprek met de voorbeelden blijft beschikbaar bij Mijn vragen aan AI.

## Leesbare epicinhoud en verwijderen (2026-09-14)

De epicinhoud gebruikt selecteerbare Markdown met koppen, alinea's, lijsten, nadruk en code.
De AI krijgt dezelfde opmaakinstructie voor nieuwe inhoud en revisies. Bestaande lange tekst
krijgt waar mogelijk extra alinea's en genummerde inline-opsommingen worden lijsten; deze
presentatie verandert geen opgeslagen inhoudsversies of goedkeuringen. Schermafbeeldingen
blijven beschikbaar onder Schermen.

Een PO (of factory owner) kan een epic via **Epic verwijderen** en een bevestiging verwijderen.
De architect heeft deze actie niet. `DELETE /api/epics/{id}` controleert productrol, CSRF,
actuele versie en idempotentie. Migratie V39 voegt een verwijdermarkering toe; overzichten
filteren deze epics weg. Historie, beoordelingen en relaties blijven bewaard voor herleidbaarheid.
Openstaande stories worden geannuleerd; voor lopende uitvoering gebruikt de dispatcher het
bestaande externe annuleringsprotocol. Opgeleverde wijzigingen blijven bestaan.

Verificatie: volledige lokale verify, migratie en dispatcher-integratietest (inclusief externe
annulering), PO-/architectautorisatie, CSRF, versieconflict en herhaling. Widgettests controleren
Markdown en de verwijderbevestiging op 320px bij 200% tekstgrootte; Annuleren behoudt de epic en
Verwijderen houdt deze ook na verversen uit het overzicht.

Productiecontrole: release `5815e9e692a50691ba408c09cc1907ab3d2cfce0` doorliep CI
en beide smoke-tests succesvol. In de PO-weergave van HKH zijn de koppen, alinea's,
genummerde scope en verwijderknop visueel gecontroleerd. De bevestiging toont de epictitel
en gevolgen voor openstaand/lopend werk. **Annuleren** behield de bestaande epic. Er is
geen productie-epic verwijderd.
