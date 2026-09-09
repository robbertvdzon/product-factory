# AI-uitvoering

## Verantwoordelijkheid

Product Factory beheert de domeincorrelatie, configuratie, hervatting en publicatie van AI-taken.
Agent Runtime voert ze uit. Product Factory roept geen modelprovider rechtstreeks aan en gebruikt
voor alle zeven bestaande jobtypes Agent Runtime `/v2` met tenant `product-factory`.

De tijdelijke configuratie `PF_AGENT_RUNTIME_API_VERSION=v1|v2` bestaat uitsluitend als
release-rollback. Acceptatie draait op `v2`; productie wordt na het acceptatiebewijs omgezet. De
v1-adapter en oude databasekolom blijven tijdens de expand/contract-fase leesbaar.

## Uitvoeringsselectie en jobtypes

Iedere `AiJobConfiguration` en iedere bevroren `AiTask` bevat exact:

```json
{"vendorId":"openai","model":"gpt-5.6-sol","mode":"SUBSCRIPTION"}
```

Geldige modes zijn `SUBSCRIPTION`, `API` en `MOCK`. Productie gebruikt standaard
`openai/gpt-5.6-sol/SUBSCRIPTION`; acceptatie uitsluitend `mock/mock/MOCK`. Er is geen fallback.
Alle huidige taken zijn Runtime `APPLICATION_WORK` plus `STRUCTURED_GENERATION` en hebben een
verplicht Draft 2020-12-resultaatschema:

- `MEETING.CONVERSE`
- `MEETING.SUMMARIZE`
- `PRODUCT_DESIGN.CREATE_EPIC`
- `PLANNING.SELECT_WORK`
- `PLANNING.SLICE_EPIC`
- `QUALITY.VERIFY_EPIC`
- `PRODUCT_ADVISOR.CONVERSE`

Een advisorbeurt bevriest vóór indiening de exacte publieke Git-SHA, productopdracht,
testconfiguratie, geldige besluiten, uitsluitend het eigen `PRODUCT_ADVISOR`-geheugen, eerdere
gespreksberichten, provider/model/mode en configuratie- en promptversie. Gebruikers- en
repositorytekst staat in de prompt expliciet als onvertrouwde data. De agent kan alleen een strikt
JSON-antwoord voorstellen; uitsluitend latere deterministische backendcommands mogen muteren.

Nieuwe jobkeys verschijnen zonder Runtime-codewijziging in tenant- en catalogusoverzichten doordat
Runtime `taskType`, uitvoering en tenant uit de ingediende job registreert. Product Factory groepeert
dezelfde usage lokaal via de eigen `jobKey`-correlatie.

## Duurzame aanvraag en uploads

`RequestAiTaskCommand` bevat de expliciete uitvoering, configuratie- en prompttemplateversie,
resultaatschema, optionele exacte `RepositorySnapshot`, attachments, artifactdeclaraties,
timeout en idempotentiesleutel. De service schrijft taak, lokale inputobjecten en outbox atomair.

De volledige prompt is altijd lokaal object `prompt` (`prompt.md`, `text/markdown`, rol `PROMPT`).
Andere tekst-, JSON-, document- en afbeeldingsinputs volgen hetzelfde pad. De Runtime-jobaanvraag
bevat alleen voltooide object-ID's en metadata, nooit inline promptbytes of Base64.

De dispatcher reserveert per object een hervatbare `/v2/uploads`, bewaart upload-ID, URL en offset,
stuurt begrensde chunks met `PATCH`, controleert met `HEAD` en voltooit pas na grootte- en
SHA-256-validatie. Claims worden tijdens lange uploads vernieuwd. Een restart hervat vanaf de
bevestigde offset. `INPUT_OBJECT_NOT_READY` laat verlopen correlaties veilig los en bouwt dezelfde
logische aanvraag opnieuw op.

De uiteindelijke job-POST wordt volledig bevroren opgeslagen. Retries sturen exact dezelfde body en
idempotency key. Daardoor maakt een verloren create-response geen tweede Runtime-job. De logische
sleutel verandert alleen bij een bewuste nieuwe Product Factory-taak.

## Resultaat, artifacts en retentie

Product Factory leest het gevalideerde JSON-resultaat, usage en de volledige artifactmanifestlijst
via `GET /v2/jobs/{jobId}/result`. Downloadlocaties zijn ondoorzichtige relatieve `/v2/`-URL's;
absolute URL's worden geweigerd zodat het consumertoken nooit naar een andere host kan lekken.
Product Factory mount of doorzoekt geen Runtime-fileshare.

Artifacts zijn vooraf gedeclareerd. Productontwerp gebruikt optionele PNG-slots `ux-01` tot en met
`ux-50`; kwaliteitsbewijs gebruikt `evidence-01` tot en met `evidence-50`. Iedere JSON-verwijzing
moet exact in het manifest voorkomen en MIME-type, grootte en SHA-256 moeten overeenkomen.
Kwaliteitsbewijs onderscheidt `EXTERNAL_URL` expliciet van `RUNTIME_ARTIFACT`.

Runtime-content is tijdelijk. Product Factory kopieert ieder resultaatartifact streamend en
hervatbaar naar haar eigen artifact-PVC en promoveert het pas atomair na hashcontrole. De bestaande
downloadroute levert correct MIME-type, filename, `Content-Length`, ETag en single-range 206.
Publicatie van een epic of kwaliteitsbewijs legt een domeinreferentie vast. Niet-gepubliceerde
kopieën verlopen standaard na zeven dagen; gerefereerde domeinartifacts worden niet opgeruimd.
Lokale invoerbytes worden na geaccepteerde Runtime-job gewist en verlaten uploadcorrelaties worden
na de veilige bewaartermijn verwijderd. Runtime ruimt haar eigen uploads en jobcontent op; job-,
usage- en kostmetadata blijven daar bestaan.

## Status, events, attempts en kosten

De duurzame bron is periodieke reconciliatie van jobstatus en events. De eventcursor maakt restart
veilig. Alleen status, progress, tool-events en begrensde `REASONING_SUMMARY` worden opgeslagen en
getoond. Product Factory vraagt, bewaart of toont geen raw chain-of-thought. SSE kan later als
snelle server-pushlaag worden toegevoegd; gewone polling blijft de correctheidsfallback.

Runtime-attempts worden afzonderlijk gelezen, ook na een mislukte of geannuleerde job. Product
Factory telt tokenmetrics en gerapporteerde kosten op zonder zelf prijzen te berekenen. De
usagekwaliteit is `COMPLETE`, `PARTIAL`, `UNAVAILABLE` of `MOCK`; onbekende usage wordt nooit als nul
gepresenteerd. Taakdetail toont uitvoering, Runtime-job, events, attempts, usage en kostensnapshot.
De Runtime-monitor blijft leidend voor cross-consumerkosten en technische attemptdetails.

## Annulering en herstel

Annulering markeert de lokale intentie direct en probeert een al bekende Runtime-job te annuleren.
Een nog niet gemaakte job wordt niet alsnog ingestuurd. Reconciliatie is idempotent voor status,
events, resultaten, artifactkopieën en usage. Transiënte upload-, netwerk- en kopieerfouten blijven
retrybaar; contract-, schema-, manifest- en hashfouten eindigen veilig en zichtbaar.

Een bewuste domeinretry maakt een nieuwe lokale AI-taak. Dit heet in UI en documentatie
`nieuwe AI-taak` of `taakpoging`; Runtime-attempts blijven interne providerpogingen binnen dezelfde
job.

## Acceptatie-testbesturing

Alle acceptatieconfiguraties gebruiken exact `mock/mock/MOCK`. De alleen in het acceptanceprofiel
beschikbare Product Factory-façade `/api/test-control/ai/mock-responses` valideert een fixture tegen
het werkelijk bevroren jobschema en de artifactdeclaraties, vertaalt haar naar de exacte Runtime-
idempotency key en ondersteunt resultaten, invalid-outputreeksen, fouten, delays en artifacts.

De façade gebruikt uitsluitend `PF_AGENT_RUNTIME_TEST_CONTROL_TOKEN`. Dat token is anders gescoped
dan het consumertoken, ontbreekt in productie en wordt nooit gelogd. Een Testbedreset wist ook de
Runtime-mockfixtures wanneer `PF_TESTBED_RUNTIME_FIXTURE_RESET_ENABLED=true`.

## Beveiliging en configuratie

Productie vereist HTTPS of de expliciet toegestane interne Runtime-service, een niet-leeg
consumertoken en een niet-MOCK-selectie. Acceptatie staat alleen de vaste Runtime-
acceptatieomgeving toe, vereist `v2`, `mock/mock/MOCK` en het afzonderlijke test-control-token, en
weigert admin-, worker- en providercredentials. OpenAI- en Anthropiccredentials horen uitsluitend
bij Agent Runtime en haar workers.

## Operationele rollback

Zolang de expandrelease actief is kan Product Factory via `PF_AGENT_RUNTIME_API_VERSION=v1` naar de
oude adapter terug. De additieve kolommen en v2-jobs blijven staan; verwijder bij rollback geen
uploads, jobs of artifacts. Na herstel gaat reconciliatie verder vanuit opgeslagen correlaties.
De v1-adapter en flag worden pas in een afzonderlijke cleanuprelease verwijderd nadat productie
aantoonbaar stabiel op v2 draait.
