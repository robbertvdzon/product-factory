# Runbook — samenwerken aan epics

## Rollen en eerste inrichting

Factorybeheer maakt een product en de productopdracht aan. Via **Beheer → Leden** voegt de
factory owner het Google-e-mailadres toe en kent per product `PRODUCT_OWNER` of `ARCHITECT` toe.
Een gebruiker met een actief lidmaatschap kan inloggen zonder een wijziging van de serverallowlist.
Google-handtekening, issuer, audience, verloopdatum en geverifieerd e-mailadres blijven verplicht.
Een ingetrokken rol verliest meteen toegang; bestaande sessies geven geen blijvende bevoegdheid.

Via **Productbesturing** kiest factorybeheer menselijke of AI-besturing voor beide rollen.
De architect legt via **Productafspraken** architectuurregels, product-AI-regels, maandbudget,
extra jobs per dag, groeigrens en expliciet automatisch toegestane impactcategorieën vast.
Lege AI-grenzen geven geen onbeperkt mandaat. Een persoon met meerdere rollen wisselt expliciet;
factorybeheer zelf neemt geen inhoudelijk epicbesluit en wijzigt geen productbudgetten.

Voor PvdD wordt Marc product owner en Robbert architect. E-mailadressen en repositorygegevens
worden bij inrichting ingevuld; de implementatie bevat geen hardgecodeerde voorbeeldtoewijzingen.
Voor autonome producten staan beide besturingsrollen op AI. Binnen het vastgelegde mandaat legt
de backend automatische beoordelingen vast; buiten het mandaat blijft een verklaarde blokkade.
Een AI-rol verleent geen impliciete menselijke rechten aan de factory owner.

## Van idee naar uitvoering

De PO gebruikt **Mijn werk → Nieuw idee**. Het gesprek met Product Advisor levert een voorstel.
Na **Laat dit voorstel uitwerken** maakt Productontwerp een versie met functionele uitwerking,
acceptatiecriteria, scherminventaris, bewaarde desktop-/mobielbeelden, risico's en impact.
Bij AI-productbesturing wordt een epicvoorstel automatisch doorgezet.

In de epic staan gesprek en dossier bij elkaar. Feedback neemt de expliciet bevestigde vraag en
recente gesprekscontext mee naar de bestaande ontwerpflow. Feedback op een scherm benoemt de
inhoudsversie, screenKey, viewport en artifact. Nieuwe inhoud vraagt opnieuw functioneel akkoord.
Het behouden, vervangen of verwijderen van bestaande UX-artifacts blijft expliciet gevalideerd.

De architect ziet korte impactregels voor database, migratie, externe systemen, frontend,
toegang en AI in het product. Uitklappen toont bewijs en alternatieven; **Onderzoek met AI** start
een duurzaam epicgesprek. **Vraag onderzoek** bewaart een open besluit, **Vraag aanpassing**
stuurt de epic terug naar Productontwerp. Product-AI beschrijft de belasting van het te wijzigen
product, niet de agentkosten van de factory.

Goedkeuringen horen bij `contentVersion` én de versie van productafspraken. Lifecycle-overgangen
verhogen `version`, maar behouden `contentVersion`. Verouderde besluiten blijven auditinformatie;
een nieuwe inhouds- of beleidsversie maakt ze ongeldig. Automatische beoordelingen worden ook
na beleidswijzigingen opnieuw bepaald. Onbekende technische impact blijft geblokkeerd.

Planningclaim en iedere nieuwe dispatch toetsen de actuele beoordelingen. Nieuwe impact wordt
via verfijning teruggestuurd; afhankelijk toekomstig werk stopt. Reeds bestaand extern werk wordt
als zodanig gevolgd en krijgt geen verzonnen annulering of nieuwe identiteit. Vragen hebben een
productrol en epic-/storycontext. Open vragen blokkeren gekoppelde nieuwe dispatch; antwoorden
maken het werk weer beschikbaar voor de bestaande scheduler. Er is geen nieuwe Software
Factory-vragencallback toegevoegd: alleen reeds beschikbare signalen en vraagcommands worden gebruikt.

De PO houdt zicht op stories, bugs, hertests en verificatie. **Code geleverd** is geen claim dat
productie gereed is. De voortgang toont de ingestelde acceptatie-/productielinks; een geslaagde
epicverificatie wordt afzonderlijk benoemd. Architect- en PO-notificaties worden duurzaam bewaard.

## Publieke endpoints

| Endpoint | Gebruik |
|---|---|
| `GET/PUT /api/products/{id}/governance` | Geversioneerde productbesturing en afspraken |
| `GET/POST /api/epics/{id}/reviews` | Besluithistorie en versiegebonden besluit |
| `GET/POST /api/epics/{id}/discussions` | Epicgesprek voor de handelende productrol |
| `POST /api/epics/{id}/feedback` | Feedback op uitwerking/scherm naar duurzame revisie |
| `GET /api/epics/{id}/ux-artifacts?name=...` | Alleen een bewaard rasterbeeld van deze epic |
| `GET /api/epics/{id}/progress` | Implementatie, vragen, blokkades en verificatie |
| `GET /api/my/notifications` | Meldingen binnen de actuele producttoegang |

Mutaties gebruiken sessie, Origin, CSRF, verwachte versie en idempotentiesleutel. Productrollen
worden bij de backend gecontroleerd. Een directe call naar de oude factory-ownerapproval wordt
geweigerd. Het algemene oude approvalcommand kan de nieuwe governance niet omzeilen.

## Migratie en herstel

Flyway 26–29 voegt architectlidmaatschappen, productafspraken/historie, epicimpact/besluithistorie
en rolgerichte epicgesprekken/vragen toe. Oude approvals blijven intact als historische data;
niemand wordt automatisch architect. Bestaande epics zonder impact blijven onder hun oude
lifecycle zolang het product niet is ingericht. Zodra governance is ingesteld, blokkeert
ontbrekende impact en moet de epic worden verfijnd. Nieuwe runtimeoutput bevat altijd impact.

Een versieconflict vraagt verversen en opnieuw beoordelen. Een niet-onderbouwde impact vraagt
onderzoek of verfijning; een ontbrekende rol wordt via Leden toegewezen. Factorybeheer houdt
runs, planning, kwaliteit, Runtime-instellingen en herstelacties in de bestaande werkplek.
