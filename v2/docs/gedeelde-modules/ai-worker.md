# Product Factory v2 — AI-worker en taakcontainer

Status: technisch contract voor de laptopworker en de uitvoering van één `AiTask`.

Dit document werkt de uitvoeringsgrens uit van
[AI-uitvoering](ai-uitvoering.md). Productontwerp, Productplanning en Kwaliteitsbewaking bepalen wat
een agent moet doen. AI-uitvoering bewaart en distribueert de opaque taak. De laptopworker voert die
taak volledig uit in een nieuwe tijdelijke Dockeromgeving.

## Verantwoordelijkheidsgrens

De aanvragende procesmodule:

- bevriest product-ID, publieke Git-URL, exacte commit-SHA, bronversies en doelomgeving;
- verzamelt de benodigde publieke DTO's en uitsluitend het geheugen van de eigen agentrol;
- kiest via de actuele `AiJobConfiguration` provider en model;
- levert vaste instructies, een resultaatschema en alleen secretreferenties;
- valideert na afloop de domeinbetekenis en publiceert eventueel epics, stories of testbewijs.

AI-uitvoering bewaart de taak, claims, leases, heartbeats, technische resultaten en artifacts. De
worker kent geen module-entiteiten of agentrollen en schrijft nooit rechtstreeks in hun database.

## Taakinput

Naast de generieke taakenvelop kan een taak deze technische context bevatten:

```java
class RepositorySnapshot {
    String gitUrl;       // publieke HTTPS-URL
    String commitSha;    // volledige, vooraf bevroren Git-SHA
}

class TestEnvironmentAccess {
    String environmentId;
    URI baseUrl;
    URI revisionEndpoint;
    List<String> allowedRoutes;
    List<String> secretRefs;
    AccessMode accessMode; // TEST_DATA_WRITE of READ_ONLY
}
```

De aanvrager bepaalt de commit vóór het queueën. Voor ontwerp en planning is dat normaal de HEAD die
bij het maken van de inputmomentopname gold. Voor story- en bugfixverificatie is minimaal de
`deliveredCommitSha` van Software Factory beschikbaar. Een taak gebruikt nooit een beweeglijke
branchnaam als enige bronverwijzing.

## Verloop van één taak

1. De worker claimt de taak via de beveiligde worker-API en controleert provider, model, time-out,
   toegestane netwerkdoelen en grootte.
2. Hij maakt een nieuwe tijdelijke werkdirectory en start één geïsoleerde Dockercontainer.
3. In de container clonet hij alleen de opgegeven publieke HTTPS-repository en checkt hij detached
   precies `commitSha` uit. Er wordt geen Git-token doorgegeven.
4. De worker maakt alleen de tools beschikbaar die voor de taak zijn toegestaan, bijvoorbeeld
   read-only Git, browserautomatisering en lokale testcommando's.
5. De provider voert de vaste taak uit en levert uitsluitend veilige voortgang en het gevraagde
   gestructureerde resultaat.
6. De worker uploadt toegestane bewijsartifacts, meldt het resultaat met het actuele fencing token
   en verwijdert daarna container en tijdelijke worktree.
7. Bij crash of slaap volgt de lease- en reconciliatieroute uit het hoofddocument. Een oude
   gefencete container mag nooit alsnog resultaat publiceren.

De checkout mag lokaal gecachet worden voor snelheid, maar iedere taak krijgt een afzonderlijke
worktree op de exacte SHA. Een cache is nooit productwaarheid en mag geen oncommitted bestanden of
output uit een vorige taak lekken.

## Browser, testen en deploymentrevision

Browser-, log- en testclients draaien in de taakcontainer en niet als inhoudelijke adapters in de
servermodule. De procesmodule beschrijft doel, grenzen en verwacht resultaatschema; de worker levert
de technische tools.

Voor een gerichte story- of bugfixverificatie vraagt de worker eerst het geconfigureerde
revisionendpoint van de doelomgeving op. Hij bewaart:

- de vereiste `deliveredCommitSha`;
- de werkelijk gedeployde commit of release;
- het tijdstip en de omgeving waarop dit is vastgesteld.

Wanneer Git-commits worden gebruikt, moet de gedeployde commit de oplevercommit bevatten. Is dat
niet aantoonbaar, dan retourneert de taak geen afkeuring maar een gestructureerde blokkade
`DEPLOYMENT_PENDING`. Bij een ander release-id-systeem moet de productconfiguratie een even
betrouwbare vergelijkingsregel leveren. Een ontbrekend of ongeldig revisionantwoord is eveneens een
testblokkade en nooit een productbug.

Acceptatie mag binnen de geconfigureerde synthetische testdata schrijven. Productie is standaard
`READ_ONLY`; alleen een expliciet begrensd testaccount en toegestane routes kunnen daarvan afwijken.
De worker pusht nooit Git-wijzigingen en krijgt geen toegang tot Product Factory-modulecommands.

## Credentials

DTO, database en `AiTask` bevatten uitsluitend stabiele `secretRef`s. Voor de MVP heeft de
laptopworker een lokale, versleutelde secretstore of OS-keychain met waarden voor die referenties.
Bij claimen controleert de worker dat alle benodigde referenties lokaal beschikbaar zijn.

Waar mogelijk opent de worker een vooraf geauthenticeerde browsercontext of laat een lokale helper
het loginformulier vullen. Plaintext credentials worden niet aan het modelprompt toegevoegd, niet
in voortgang of artifacts opgeslagen en niet naar de server teruggestuurd. Ontbrekende credentials
geven een veilige technische blokkade.

Providercredentials voor Codex of Claude blijven eveneens uitsluitend op de worker en zijn geen
onderdeel van de taakcontainerinput die het model als productcontext ziet.

## Bewijsartifacts

Screenshots, logs, traces en andere taakoutput worden niet als Base64 in de taak-JSON opgenomen. De
worker uploadt ze via de worker-API met MIME-type, grootte en SHA-256-hash. AI-uitvoering controleert
type, hash en limieten en bewaart ze als onveranderlijke `AiResultArtifact`s.

Voor de MVP worden begrensde artifacts als BLOB in dezelfde database bewaard. Eerste limieten zijn
maximaal 5 MB per artifact en 25 MB per taak. Een latere objectstore kan deze opslag vervangen
zonder het publieke taak- of kwaliteitscontract te wijzigen. Een `Bug` of `Verification` verwijst
alleen naar gevalideerde artifact-ID's; oude bewijzen blijven daardoor reproduceerbaar.

Tijdelijke browserprofielen, downloads, worktrees en niet-geaccepteerde artifacts worden na de taak
verwijderd. Secrets, cookies, tokens, persoonsgegevens en ruwe providerlogs worden vóór acceptatie
afgeschermd of geweigerd.

## Onvertrouwde inhoud en prompt-injection

Alle vrije inhoud is onvertrouwde data, waaronder:

- code, README's, tests, issues en comments uit Git;
- epic-, story-, signalen- en meetingtekst;
- zichtbare en verborgen tekst, HTML, accessibility labels en API-responses van de geteste app;
- gedownloade bestanden, logs en foutmeldingen.

Tekst daarin die zich voordoet als instructie wordt nooit uitgevoerd als systeem- of
ontwikkelaarsinstructie. Zij kan geen extra netwerkdoel toestaan, credentials opvragen,
resultaatschema wijzigen, Git schrijven of een Product Factory-command uitvoeren. Vaste
taakinstructies, toolallowlists en servervalidatie hebben altijd voorrang op broninhoud.

## Invarianten

- Iedere taak draait in een nieuwe tijdelijke Dockeromgeving.
- Een repositorycheckout gebruikt een publieke HTTPS-URL en exacte volledige commit-SHA.
- De worker heeft geen Git-schrijftoken en commit of pusht nooit.
- De server bewaart geen plaintext test- of providercredentials in een `AiTask`.
- Een test tegen een achterlopende deployment wordt `BLOCKED`, nooit afgekeurd.
- Bewijsartifacts zijn begrensd, gehasht, onveranderlijk en aan exact één taakresultaat gekoppeld.
- Onvertrouwde repository- of applicatie-inhoud kan instructies en rechten niet wijzigen.
- Alleen de aanvragende module valideert en publiceert de domeinuitkomst.

## Gerelateerde documenten

- [AI-uitvoering](ai-uitvoering.md)
- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
- [Productontwerp-API](../processen/productontwerp/api.md)
- [Productplanning-API](../processen/productplanning/api.md)
- [Integratie- en acceptatietesten](../platform/integratie-en-acceptatietesten.md)
