# product-factory-32 - Toon afgeronde Product Factory-cycli als compacte bewijsregels

## Story

Toon afgeronde Product Factory-cycli als compacte bewijsregels

<!-- refined-by-factory -->

## Scope

Vervang op het hoofdscherm uitsluitend terminale cycli van het product met productslug `product-factory` door compacte, niet-uitklapbare bewijsregels. Dit betreft de statussen `ACCEPTED`, `NEEDS_REVISION`, `REJECTED`, `NO_CHANGE` en `FAILED`, inclusief een handmatig geannuleerde cyclus die als `FAILED` met een expliciet handmatig-annuleringsrecord is vastgelegd.

Iedere bewijsregel vormt één semantisch gegroepeerde container en toont afzonderlijk gelabelde waarden voor:

- `Datum`: de bestaande weergegeven startdatum en -tijd, gebaseerd op `startedAt` met `createdAt` als fallback en in de lokale browsertijd;
- `Cyclusuitkomst`: de bestaande gebruikersgerichte uitkomstclassificatie; bij expliciete handmatige annulering wordt de bewezen annulering getoond en niet een afgeleide technische fout;
- `Reden`: de bestaande voor presentatie bedoelde operationele reden, of de expliciete annuleringsreden; ontbrekende of onbekende presentatiegegevens worden als `Onbekend` weergegeven;
- `Beslisbron`: de bestaande expliciete provenance of, als die ontbreekt, de bestaande conservatieve afleiding inclusief de kwalificatie `Afgeleid` of `Onbekend`;
- `Gekoppelde opbrengst`: het aantal geladen Software Factory-stories dat via de bestaande exacte product- en cycluskoppeling aan deze cyclus is gekoppeld.

Een native knop met zichtbaar label `Bekijk bewijs` opent het bestaande detail van precies dezelfde cyclus. De toegankelijke naam bevat de product- en cycluscontext. Na sluiten via de sluitactie of Escape keert de focus terug naar dezelfde knop.

Cycli met status `QUEUED` of `RUNNING`, alle cycli van andere producten, het bestaande cyclusdetail, de koppellogica, bronstatussen en de overige presentatie van Software Factory-leveringen blijven ongewijzigd. Er worden geen API’s, contractvelden, opslag, migraties, telemetrie of proceswijzigingen toegevoegd.

## Acceptance criteria

- Alleen cycli met exact productslug `product-factory` en een ondersteunde terminale status worden als compacte, niet-uitklapbare bewijsregel weergegeven.
- Widgettests bewijzen dat `QUEUED` en `RUNNING` hun bestaande actieve weergave behouden en dat zowel actieve als terminale cycli van andere producten hun bestaande kaartweergave behouden.
- Voor representatieve fixtures van `ACCEPTED`, `NEEDS_REVISION`, `REJECTED`, `NO_CHANGE`, technisch `FAILED` en expliciet handmatig geannuleerd verifiëren tests de zichtbare en afzonderlijk gelabelde datum, cyclusuitkomst, reden, beslisbron en gekoppelde opbrengst.
- De vijf waarden en de bewijsactie bevinden zich binnen één semantisch gegroepeerde container en zijn ook zonder kleur of visuele positie begrijpelijk.
- De gekoppelde opbrengst telt uitsluitend geladen Software Factory-leveringen die door de bestaande exacte productslug- en cyclus-idkoppeling uniek aan deze cyclus zijn toegewezen. Interne kandidaten worden niet als opgeleverde story meegeteld en kandidaat en levering worden daardoor niet dubbel geteld.
- Ontbrekende, verkeerd getypeerde, kruisproduct- en ambigue koppelingen tellen niet mee. Een ladende of mislukte leveringsbron wordt als `laden…` respectievelijk `niet beschikbaar` getoond en nooit als nul.
- De beslisbron gebruikt rechtstreeks de bestaande presentatie- en classificatielogica. Een gekoppeld expliciet handmatig-annuleringsrecord heeft voorrang; zonder geldig expliciet record wordt nooit een menselijke beslisser geclaimd.
- De reden gebruikt uitsluitend de bestaande gelabelde operationele reden of de expliciete, gecodeerde annuleringsreden. Tokens, prompts, ruwe foutmeldingen of foutpayloads, stacktraces, persoonsgegevens, artefactinhoud en gegevens van andere producten verschijnen niet in de bewijsregel.
- `Bekijk bewijs` opent met muis, Enter en Spatie het bestaande detail van de bijbehorende cyclus. Een geautomatiseerde toetsenbordtest verifieert openen, focusbegrenzing, sluiten via zichtbare actie en Escape, en focusherstel naar dezelfde knop.
- Widgettests op representatieve smalle en brede viewports en bij 200% tekstvergroting bewijzen dat alle kernwaarden en de actie zonder horizontale pagina-scroll, overlap of ontoegankelijke afkapping bruikbaar blijven.
- Semantiek, tekstcontrast, bedieningscontrast en zichtbare focus voldoen aan WCAG 2.2 AA.
- Het bestaande detailvenster, de globale Software Factory-leveringsweergave en de onderliggende laad-, filter- en koppellogica blijven functioneel ongewijzigd.

## Aannames

- `product-factory` is de exacte, hoofdlettergevoelige productslug waarmee de speciale bewijsweergave wordt afgebakend.
- Handmatige annulering is geen afzonderlijke status: zij wordt alleen herkend aan een geldig, aan dezelfde cyclus gekoppeld expliciet beslisrecord bij status `FAILED`.
- `Datum` behoudt de bestaande betekenis van de cyclusdatum: `startedAt`, met `createdAt` als fallback. Ontbrekende of onleesbare waarden worden met de bestaande neutrale fallback weergegeven.
- `Cyclusuitkomst` is de bestaande gebruikersgerichte classificatie en niet de ruwe backendstatus. Expliciete handmatige annulering wordt als zodanig benoemd om een onjuiste technische-foutconclusie te voorkomen.
- Met `voortgekomen stories` worden daadwerkelijk gekoppelde Software Factory-leveringen bedoeld; interne kandidaten zijn nog geen opgeleverde story.
- De bestaande lijstbeperking, sorteervolgorde en automatische verversing blijven gelden.

## Eindsamenvatting

De afgeronde Product Factory-cycli worden nu als compacte bewijsregels getoond. Elke regel vermeldt datum, uitkomst, reden, beslisbron en het aantal daadwerkelijk gekoppelde leveringen, met `Onbekend` als veilige fallback. Een bewezen handmatige annulering krijgt voorrang op een afgeleide technische fout. Via `Bekijk bewijs` blijft het bestaande detail toegankelijk, inclusief toetsenbordbediening en focusherstel.

Actieve cycli en cycli van andere producten behouden hun bestaande kaartweergave. API’s, opslag, contracten, koppellogica, detailinhoud en de globale leveringsweergave zijn bewust niet gewijzigd.

De volledige frontend-analyse en alle 291 frontendtests zijn geslaagd; ook de backendbuild met 142 tests was groen. Aanvullend zijn 34 gerichte tests en een browsercontrole op de preview uitgevoerd voor koppeling, bronstatussen, muis- en toetsenbordbediening, focusbegrenzing en een smalle viewport. De frontend-imagebuild is lokaal bewust niet uitgevoerd omdat die volgens het factory-vangnet uitsluitend in CI draait. Documentatie, merge en productie-uitrol volgen nog in hun eigen subtaken.

<!-- deploy-summary:start -->
Afgeronde Product Factory-cycli zijn voortaan sneller te beoordelen doordat datum, uitkomst, reden, beslisbron en opbrengst compact bij elkaar staan. Lopende cycli en cycli van andere producten blijven eruitzien en werken zoals voorheen. Vanuit iedere bewijsregel kan het volledige detail worden geopend.
<!-- deploy-summary:end -->
