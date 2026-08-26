# Product Factory — UX-referentie

De frontend gebruikt het Product Factory v2-UX-concept uit commit
[`3d6cbe4`](https://github.com/robbertvdzon/product-factory/tree/3d6cbe4/v2/ux) als vaste visuele
referentie. Het concept werd vóór de technische herbouw gemaakt en is geen bron van domeinlogica.
De huidige Flutter-frontend blijft de uitvoerbare implementatie.

## Visuele uitgangspunten

- een donkergroene, rustige applicatieschil met de primaire productnavigatie links;
- een lichte werkruimte met veel witruimte, duidelijke paginatitels en weinig dashboardruis;
- het overzicht toont alleen productdoel, actueel werk en concrete aandachtspunten;
- dagelijkse producttaken staan direct in de navigatie;
- minder dagelijkse configuratie en techniek staan achter **Beheer**;
- dezelfde informatiehiërarchie blijft bruikbaar op mobiel via een navigatielade;
- producttaal staat voorop; technische details staan in **Operatie**.

De oorspronkelijke desktop- en mobiele referentiebeelden zijn in de historische UX-map te vinden.
Ze blijven leidend voor kleur, ritme, navigatie, kaarten en responsive gedrag. De actuele functionele
specificaties en API-contracten zijn leidend wanneer de voorbeelddata of een interactie uit het
prototype daarvan afwijkt.

## Schermindeling

| UX-scherm | Huidige inhoud |
| --- | --- |
| Overzicht | Productdoel, actieve epic en story, voortgang en aandachtspunten |
| Ontwerp | Epics, ontwerpen, historie en handmatige ontwerpactie |
| Planning | Geordende backlog, stories, sessies en prioriteitsacties |
| Kwaliteit | Snapshot, historie, bugs, verificaties, testwerk en retries |
| Signalen | Gebruikerssignalen en hun verwerking |
| Overleggen | Agentvragen, gesprekken, notulen en acties |
| Instellingen | Productstatus, dispatching, Software Factory-koppeling, schedules, globale AI-modellen en Runtime-keygrants |
| Besluiten | Actuele besluiten, peildatum en append-only historie |
| Agentgeheugen | Geheugen per product en agentrol, budget en versiehistorie |
| Operatie | Processessies, delivery attempts, dispatcher, AI-taken en annuleren |
| Release-informatie | Frontend-, backend- en omgevingsidentiteit |
| Acceptatietesten | Alleen op acceptatie: synthetische testdata bedienen |

## Bewuste uitbreidingen ten opzichte van het prototype

De volgende functies stonden niet of niet volledig in het klikbare concept, maar horen wel bij de
latere specificaties en blijven daarom behouden:

- producten aanmaken en activeren;
- open vragen van agents beantwoorden;
- Runtime-projectprefix en environmentkeynamen beheren, inclusief rolgrants;
- AI-taken volgen en veilig annuleren;
- frontend- en backendbuildidentiteit en vernieuwmelding;
- Google-login en logout;
- technische dispatch- en deliverydiagnostiek.

Deze uitbreidingen mogen de dagelijkse productschermen niet onrustiger maken. Daarom staan ze in
de bestaande UX-hiërarchie onder **Overleggen**, **Instellingen**, **Agentgeheugen**, **Operatie** en
**Release-informatie**.
