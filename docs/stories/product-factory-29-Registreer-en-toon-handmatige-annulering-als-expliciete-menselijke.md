# product-factory-29 - Registreer en toon handmatige annulering als expliciete menselijke beslissing

## Story

Registreer en toon handmatige annulering als expliciete menselijke beslissing

<!-- refined-by-factory -->

## Scope

- Voeg per productcyclus maximaal één optioneel beslisrecord toe met uitsluitend `iterationId`, `actorType`, `mechanism`, `reasonCode` en `decidedAt`.
- Maak het optionele record beschikbaar in zowel de lijst- als detailrepresentatie van een cyclus. Cycli zonder record blijven geldige invoer en geldige API-responses.
- Laat de bestaande annuleeractie bij een geslaagde overgang van `QUEUED` of `RUNNING` naar `FAILED` in dezelfde transactie het beslisrecord vastleggen met:
  - `actorType = HUMAN`;
  - `mechanism = MANUAL_CANCELLATION`;
  - `reasonCode = MANUALLY_CANCELLED`;
  - `decidedAt` exact gelijk aan het opgeslagen tijdstip van de terminale overgang.
- Schrijf het beslisrecord uitsluitend wanneer de terminale overgang werkelijk slaagt. Een afgewezen annulering of rollback mag geen los beslisrecord achterlaten.
- Neem in het beslisrecord geen naam, e-mailadres, account-id, aangeleverde annuleerreden of ander vrijetekstveld op. Een bestaande foutmelding mag buiten het beslisrecord blijven bestaan, maar wordt niet gebruikt als provenance wanneer het expliciete record aanwezig is.
- Toon voor een cyclus met dit expliciete record in het overzicht zichtbaar en toegankelijk:
  - `Beslisbron: Mens`;
  - `Reden: Handmatig geannuleerd`.
- Expliciete provenance heeft voorrang op iedere uit `status`, `criticVerdict` en `errorMessage` afgeleide beslisbron. Voor deze cyclus wordt `Technische fout` niet als beslisbron of vervangende verklaring gepresenteerd.
- Toon in het detail exact dezelfde bron en reden als in het overzicht, aangevuld met:
  - `Mechanisme: Handmatige annulering`;
  - het beslissingstijdstip uit `decidedAt`.
- Koppel alle weergegeven beslisinformatie aan de juiste `iterationId`; bron en reden zijn in het overzicht zichtbaar zonder eerst het detail te openen.
- Gebruik voor historische cycli zonder beslisrecord ongewijzigd de bestaande conservatieve fallbackclassificatie. Markeer deze fallback zichtbaar én in de toegankelijke naam met `Afgeleid`. Een niet-classificeerbare combinatie toont `Onbekend` en blijft eveneens herkenbaar als afgeleide uitkomst.
- Voer geen backfill van historische cycli uit.
- Wijzig geen bestaande statusovergangen, criticusverdicts, foutmeldingsopslag, hervatgedrag, kandidaatselectie of leveringsgedrag.

## Acceptance criteria

- Een geautomatiseerde migratie- en repositorytest bewijst dat het optionele beslisrecord met de vijf voorgeschreven velden kan worden opgeslagen en opgehaald en dat per cyclus hoogstens één record bestaat.
- Het opslagmodel bevat geen veld voor naam, e-mailadres, account-id of vrije tekst en kopieert geen aangeleverde annuleerreden naar het beslisrecord.
- Een API-integratietest annuleert programmatisch een lopende cyclus en bewijst dat de status en het beslisrecord samen worden vastgelegd met de voorgeschreven vaste waarden.
- De integratietest bewijst dat `decidedAt` exact gelijk is aan `completedAt` van dezelfde terminale overgang en dat beide waarden ook via de API worden teruggegeven.
- Een transactie- of conflictregressietest bewijst dat een mislukte of afgewezen annulering niet kan resulteren in slechts een statuswijziging of slechts een beslisrecord.
- Een regressietest met `status = FAILED`, een gevulde `errorMessage` en het expliciete annuleringsrecord toont in de cyclusregel `Beslisbron: Mens` en `Reden: Handmatig geannuleerd`; de afgeleide beslisbron `Technische fout` krijgt voor deze cyclus geen voorrang.
- Een component- of end-to-endtest bewijst dat het detail voor dezelfde cyclus exact dezelfde bron en reden toont, aangevuld met `Mechanisme: Handmatige annulering` en het uit hetzelfde record afkomstige beslissingstijdstip.
- Geautomatiseerde fallbacktests bewijzen dat een historische cyclus zonder beslisrecord dezelfde beslisbronclassificatie houdt als vóór deze story, met `Afgeleid` in zowel de zichtbare tekst als de toegankelijke naam.
- Een niet-classificeerbare historische combinatie zonder record toont `Onbekend` en wordt niet ten onrechte als menselijk besluit of technische fout aangeduid.
- Contracttests bewijzen dat een ontbrekend beslisrecord geldig blijft, dat de migratie geen historische records aanmaakt en dat bestaande waarden voor `status`, `criticVerdict`, fout- en leveringsvelden niet door deze wijziging veranderen.
- Widget- en toegankelijkheidstests met meerdere cycli bewijzen dat bron en reden aan de juiste `iterationId` gekoppeld zijn, niet uitsluitend via kleur worden gecommuniceerd en geen ernstige of kritieke axe-equivalente overtredingen introduceren.
- Bestaande regressietests voor annuleren, write-once terminale statussen, criticusgedrag, hervatten en levering blijven slagen.

## Aannames

- Een cyclus kan binnen deze story maximaal één expliciete terminale beslissing hebben; `iterationId` vormt daarom de unieke koppeling van het record.
- De bestaande optionele annuleerreden en `errorMessage` blijven backward compatible, maar worden nooit onderdeel van het beslisrecord en bepalen de getoonde provenance niet wanneer een expliciet record aanwezig is.
- `decidedAt` en `completedAt` worden vanuit één opgeslagen tijdswaarde gezet, zodat gelijkheid niet afhankelijk is van afronding of twee afzonderlijke klokmetingen.
- Het beslissingstijdstip wordt in de bestaande lokale datum- en tijdnotatie van het dashboard getoond.
- `Afgeleid` kwalificeert de herkomst van de fallback; het verandert de bestaande fallbackwaarde zelf niet en maakt geen beslisrecord aan.
- Er is geen infrastructuurwijziging of handmatige dataconversie nodig.

## Eindsamenvatting

Voor de PO: handmatige annulering wordt nu als expliciete menselijke beslissing vastgelegd en in zowel overzicht als detail getoond. Het record bevat uitsluitend cyclus-id, actor, mechanisme, vaste redencode en tijdstip. Opslag gebeurt atomair met de terminale statusovergang; `decidedAt` en `completedAt` gebruiken exact hetzelfde tijdstip.

Expliciete annuleringsinformatie krijgt voorrang op afgeleide classificatie. Na review zijn de misleidende badge en verklaring “Technische fout” bij handmatige annulering onderdrukt. Historische cycli behouden hun bestaande classificatie, zichtbaar en toegankelijk gemarkeerd als “Afgeleid”.

Getest: Maven-verificatie met 141 tests, Flutter-analyse zonder issues en 227 Flutter-tests, allemaal groen. Daarbij zijn migratie, uniciteit, API-contracten, rollback/conflicten, privacy, lijst/detailweergave, cycluskoppeling en toegankelijkheid afgedekt. De storybrede testtaak is goedgekeurd. Docker-imagebuilds zijn lokaal bewust niet uitgevoerd omdat ze niet agent-runnable zijn en in CI draaien.

Bewust niet gedaan: geen historische backfill, geen vrije annuleerreden in het beslisrecord en geen wijziging aan bestaande statusovergangen, hervatten, kandidaatselectie of levering.

<!-- deploy-summary:start -->
Bij een handmatig geannuleerde productcyclus zie je voortaan duidelijk dat een mens de beslissing nam en waarom. In de details staat ook wanneer de annulering plaatsvond, zonder dat deze ten onrechte als technische fout wordt gepresenteerd.
<!-- deploy-summary:end -->
