# Runbook — samenwerken aan epics

## Rollen en eerste inrichting

Factorybeheer maakt een product en de productopdracht aan. Via **Beheer → Leden** voegt de
factory owner het Google-e-mailadres toe en kent per product `PRODUCT_OWNER` of `ARCHITECT` toe.
Een gebruiker met een actief lidmaatschap kan inloggen zonder een wijziging van de serverallowlist.
Google-handtekening, issuer, audience, verloopdatum en geverifieerd e-mailadres blijven verplicht.
De productie-origin moet ook zijn toegestaan in de Google OAuth-client achter `PF_GOOGLE_CLIENT_ID`.
Bij Googles melding “origin is not allowed” moet die externe origin-configuratie worden gecontroleerd;
een productlidmaatschap kan een geweigerde Google-origin niet oplossen.
Een ingetrokken rol verliest meteen toegang; bestaande sessies geven geen blijvende bevoegdheid.

Via **Productbesturing** kiest factorybeheer menselijke of AI-besturing voor beide rollen.
De architect legt via **Productafspraken** architectuurregels, product-AI-regels, maandbudget,
extra jobs per dag, groeigrens en expliciet automatisch toegestane impactcategorieën vast.
Lege AI-grenzen geven geen onbeperkt mandaat. De factory owner is superuser en kan alle
productinstellingen, budgetten en PO-/architectbesluiten beheren.

Voor PvdD wordt Marc product owner en Robbert architect. E-mailadressen en repositorygegevens
worden bij inrichting ingevuld; de implementatie bevat geen hardgecodeerde voorbeeldtoewijzingen.
Voor autonome producten staan beide besturingsrollen op AI. Binnen het vastgelegde mandaat legt
de backend automatische beoordelingen vast; buiten het mandaat blijft een verklaarde blokkade.
De factory owner kan in iedere menselijke productrol handelen; automatische AI-besluiten blijven
gebonden aan het vastgelegde mandaat.

## Van idee naar uitvoering

De PO opent **Mijn epics → Nieuwe epic**, kiest een project en beschrijft de wens in de chat.
PNG-, JPEG- en WebP-beelden kunnen worden toegevoegd. Product Advisor vraagt door als informatie
ontbreekt en stuurt een voldoende uitgewerkte wens automatisch naar Productontwerp. Er is geen
aparte voorstelgoedkeuring nodig. Productontwerp maakt de eerste epicversie met inhoud,
acceptatiecriteria, schermen en architectuurimpact. Bestaande gesprekken en testepics blijven behouden.

**Mijn epics** toont standaard alle niet-afgeronde epics. De architect opent **Epics**, standaard
gefilterd op eigen open vragen en benodigde architectuurgoedkeuring. Beide rollen kunnen wisselen
naar alle of afgeronde epics. **Alle projecten** combineert uitsluitend projecten waarvoor de
actieve rol toegang heeft en geldt ook voor beide vragensecties.

PO en architect zien hetzelfde dossier: **Inhoud**, **Schermen**, **Architectuur**, **Goedkeuring**
en **Voortgang**, met een gedeeld epicgesprek ernaast. Dossier en chat scrollen onafhankelijk
binnen de beschikbare schermhoogte. De chatinvoer blijft onderaan staan. Op smalle schermen
wisselt de gebruiker tussen **Epic** en **Gesprek**. De chat opent bij de nieuwste 30 berichten;
ouder materiaal laadt bij omhoog scrollen. Nieuwe berichten worden vanaf het laatste opgehaalde
bericht geladen. Wie oudere berichten leest, blijft op dezelfde plek en ziet **Nieuw bericht ↓**.
Wijzigingsvoorstellen en terugdraaien staan bij de bijbehorende chatmelding.
De inhoud wordt via AI gewijzigd.
Er is één invoerveld: **Stel een vraag of beschrijf je wens**. AI beantwoordt gewone vragen,
vraagt bij twijfel door en stuurt duidelijke wijzigingswensen met de actuele inhoudsversie en
beelden naar Productontwerp. Het gesprek toont wanneer de voorstelversie klaar is en wat veranderde.
De gebruiker kan verder bijstellen via de chat, vragen het voorstel terug te draaien of
**Voorstel terugdraaien** kiezen. Terugdraaien herstelt de vorige inhoud, schermen en architectuur
als een nieuwe versie; de volledige historie blijft behouden. Het kan alleen voor het laatste
actuele voorstel, zolang uitvoering nog niet gestart is. Nieuwe inhoud vraagt opnieuw de
benodigde goedkeuringen, ook na terugdraaien; oude besluiten worden niet opnieuw geldig.
Het behouden, vervangen of verwijderen van bestaande UX-artifacts blijft expliciet gevalideerd.

De architectuur is ook zichtbaar voor de PO. Impactregels beschrijven database, migratie, externe
systemen, frontend, toegang en product-AI. **Onderzoek met AI** vult een vraag voor het epicgesprek
in. Product-AI beschrijft de belasting van het te wijzigen product, niet de agentkosten van de factory.

**Vragen voor mij** toont echte open en beantwoorde procesvragen, inclusief project, afzender en
gekoppelde epic. **Mijn vragen aan AI** bevat persoonlijke gesprekken over de applicatie; deze
maken nooit automatisch een epic. Het oude notificatieblok met losse vinkjes vervalt in deze werkplek.

Beelden zijn maximaal 4 MiB per stuk, 6 per bericht en samen 8 MiB per bericht. Een gesprek bevat
maximaal 10 beelden en 10 MiB. Ze worden duurzaam opgeslagen, alleen met product-/gesprekstoegang
opgehaald en als IMAGE-bestanden aan de betreffende AI-opdracht doorgegeven.

Goedkeuringen horen bij `contentVersion` én de versie van productafspraken. Lifecycle-overgangen
verhogen `version`, maar behouden `contentVersion`. Verouderde besluiten blijven auditinformatie;
een nieuwe inhouds- of beleidsversie maakt ze ongeldig. Automatische beoordelingen worden ook
na beleidswijzigingen opnieuw bepaald. Onbekende technische impact blijft geblokkeerd.

Planningclaim en iedere nieuwe dispatch toetsen de actuele beoordelingen. Nieuwe impact wordt
via verfijning teruggestuurd; afhankelijk toekomstig werk stopt. Reeds bestaand extern werk wordt
als zodanig gevolgd en krijgt geen verzonnen annulering of nieuwe identiteit. Vragen hebben een
productrol en epic-/storycontext. Open vragen blokkeren gekoppelde nieuwe dispatch. Een planningsantwoord stuurt de gekoppelde
epic eerst terug naar uitwerking en retireert het toekomstige pakket; het antwoord wordt in de
nieuwe inhoud verwerkt, waarna beoordeling en planning opnieuw volgen. Er is geen nieuwe Software
Factory-vragencallback toegevoegd: alleen reeds beschikbare signalen en vraagcommands worden gebruikt.

De PO houdt zicht op stories, bugs, hertests en verificatie. **Code geleverd** is geen claim dat
productie gereed is. De voortgang toont de ingestelde acceptatie-/productielinks; een geslaagde
epicverificatie wordt afzonderlijk benoemd. Architect- en PO-notificaties worden duurzaam bewaard.

## Eenvoudige productinstellingen

De PO ziet de productopdracht zonder testomgevingen, productbesturing of productafspraken.
Architect en factory owner behouden hun technische instellingen. Architectuurimpact in de epic
blijft voor beide rollen zichtbaar. Het aparte doelgroepveld vervalt; alleen het productdoel
is verplicht. Bestaande doelgroepinformatie blijft bewaard als AI-context en wordt bij het
bewerken niet gewist. Als doelgroep voor een nieuwe epic van belang is, kan die in het gesprek
worden verduidelijkt.

## Publieke endpoints

| Endpoint | Gebruik |
|---|---|
| `GET/PUT /api/products/{id}/governance` | Geversioneerde productbesturing en afspraken |
| `GET/POST /api/epics/{id}/reviews` | Besluithistorie en versiegebonden besluit |
| `GET/POST /api/epics/{id}/discussions` | Gedeeld epicgesprek voor PO en architect |
| `POST /api/epics/{id}/feedback` | Feedback op uitwerking/scherm naar duurzame revisie |
| `GET /api/epics/{id}/ux-artifacts?name=...` | Alleen een bewaard rasterbeeld van deze epic |
| `POST /api/products/{id}/conversations` | Nieuw gesprek met purpose EPIC of QUESTION |
| `GET /api/epics/{id}/messages` | Gedeelde chatpagina, inclusief eerdere gekoppelde gesprekken |
| `GET /api/conversations/{id}/messages` | Persoonlijke of nieuwe epicchat: nieuwste 30, eerder via before, nieuwer via after; maximum 100 |
| `POST /api/conversations/{id}/messages` | Bericht en images; intent AUTO laat AI de bedoeling bepalen, met expectedEpicVersion bij een epic (legacy DISCUSS/UPDATE_EPIC blijven ondersteund) |
| `POST /api/conversations/{id}/revert-epic-change` | Laatste actuele voorstel terugdraaien met expectedVersion en expectedEpicVersion |
| `GET /api/conversation-images/{id}` | Geautoriseerd ophalen van een chatbeeld |
| `GET /api/epics/{id}/progress` | Implementatie, vragen, blokkades en verificatie |
| `GET /api/my/notifications` | Meldingen binnen de actuele producttoegang |
| `DELETE /api/products/{id}` | Product en alle lokale Product Factory-data definitief verwijderen |
| `DELETE /api/admin/users/{id}` | Gebruiker deactiveren, sessies en lidmaatschappen intrekken |

Gesprekslijsten, gespreksdetails en epicdiscussions ondersteunen `includeMessages=false`.
De werkplek gebruikt deze metadataweergave en haalt berichten afzonderlijk op. De AI-context
behoudt de volledige beschikbare gesprekshistorie; paginering begrenst uitsluitend de weergave.
Cursors zijn bericht-ID’s die binnen het toegestane gesprek moeten vallen. De sorteervolgorde
gebruikt tijdstip, gesprek en berichtvolgnummer zodat gelijke tijdstippen geen gaten veroorzaken.

Mutaties gebruiken sessie, Origin, CSRF, verwachte versie en idempotentiesleutel. Productrollen
worden bij de backend gecontroleerd. De factory owner mag reviews expliciet als product owner of
architect vastleggen. Het algemene oude approvalcommand kan de nieuwe governance niet omzeilen.

## Migratie en herstel

Flyway 26–29 voegt architectlidmaatschappen, productafspraken/historie, epicimpact/besluithistorie
en rolgerichte epicgesprekken/vragen toe. Oude approvals blijven intact als historische data;
niemand wordt automatisch architect. Bestaande epics zonder impact blijven onder hun oude
lifecycle zolang het product niet is ingericht. Zodra governance is ingesteld, blokkeert
ontbrekende impact en moet de epic worden verfijnd. Nieuwe runtimeoutput bevat altijd impact.

Flyway 33 koppelt bestaande aanvraaggesprekken aan hun epic en voegt gespreksdoel, berichtrol,
beelden en duurzame hervatting van chatwijzigingen toe. Er wordt geen bestaande epic verwijderd.

Flyway 34 bewaart de inhoudsversie waarop AI zich baseert en de vorige/nieuwe voorstelversie,
wijzigingssamenvatting en terugdraaimarkering. Bestaande gesprekken blijven intact; oude wijzigingen
zonder vastgelegde resultaatversie krijgen geen terugdraaiactie. Als de inhoud tijdens een
AI-beurt verandert, kan die beurt de nieuwere inhoud niet overschrijven.

Flyway 35 voegt een index toe voor het gericht ophalen van berichtpagina’s.

Een versieconflict vraagt verversen en opnieuw beoordelen. Een niet-onderbouwde impact vraagt
onderzoek of verfijning; een ontbrekende rol wordt via Leden toegewezen. Factorybeheer houdt
runs, planning, kwaliteit, Runtime-instellingen en herstelacties in de bestaande werkplek.
