# Bewijsrecord — epic-samenwerking

Datum: 2026-09-13. Ontwerp en schermafspraken staan in
[het voorstel](voorstellen/epic-samenwerking-po-architect.md) en
[de UX-overdracht](ux/epic-samenwerking/README.md). Inrichting en migratie staan in
[het runbook](platform/epic-samenwerking-runbook.md).

## Geautomatiseerd bewijs

De volledige `./product-factory verify` is uitgevoerd met Java 21 en Flutter 3.44.6:
167 backendtests geslaagd, inclusief echte PostgreSQL-migraties vanaf leeg en vanaf oudere
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
- rolgerichte planningsvraag blokkeert dispatch tot een idempotent antwoord;
- factory-owner-, idempotentie-, versie- en ingetrokken-rolcontroles.

`AuthenticationFlowTest` bewijst login van een uitgenodigde architect buiten de serverallowlist,
rolgebonden producttoegang, weigering van beheer en PO-mutaties en onmiddellijke intrekking.
Bestaande tests blijven de requestroute, gerichte vragen en hervatting, UX-artifactbehoud,
planning, dispatcher, verificatie en herstelpaden controleren.

`epic_collaboration_test.dart` controleert de PO- en architectwerkplek op 320px met 200% tekst,
rolafhankelijke acties en het onderscheid tussen versieconflicten en ontbrekende optionele
omgevingsconfiguratie. De echte gebouwde frontend wordt daarnaast in Chromium gecontroleerd.

## Operationele verificatie

Productie wordt na de main-release gecontroleerd met een afzonderlijk herkenbaar testproduct,
uitgeschakelde dispatch en uitgeschakelde productschedules. Werkelijke uitkomsten, revisie en
screenshots worden na de uitrol aan dit record toegevoegd. Een synthetische lokale browserfixture
is geen bewijs dat de productieketen werkelijk is uitgevoerd.

## Bewuste integratiegrenzen

Er is geen nieuwe Software Factory-callback voor ontwikkelaarsvragen verzonnen. De bestaande
vraagcommands ondersteunen rol- en epic/storycontext; ontvangen vragen blokkeren gekoppeld
nieuw werk. Reeds extern gestart werk behoudt zijn identiteit en wordt gevolgd. De factory
claimt niet dat een lokaal gewijzigd epic reeds extern werk heeft gestopt.

Bestaande epics zonder impact worden pas onder de nieuwe poorten gebracht wanneer het product
wordt ingericht; ontbrekende impact vraagt dan verfijning. Oude factory-ownerbesluiten blijven
historisch bewijs en geven geen nieuwe architectbevoegdheid.
