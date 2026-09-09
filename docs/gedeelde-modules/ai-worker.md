# Agent Runtime-worker

Product Factory bezit geen modelworker en geen providercredential. Zij dient alleen
`APPLICATION_WORK`/`STRUCTURED_GENERATION`-jobs in bij Agent Runtime `/v2`. Agent Runtime kiest
uitsluitend een worker die de exact gevraagde `vendorId`, `model`, `mode`, task type,
environmentkeygrants en gereedschappen aanbiedt; er is geen model- of modefallback.

## Uitvoering

Productie gebruikt standaard `openai/gpt-5.6-sol/SUBSCRIPTION`. Subscription- en eventuele
API-credentials blijven in Agent Runtime of haar workers. Acceptatie gebruikt
`mock/mock/MOCK`; deze uitvoering is server-side en heeft geen laptopworker nodig.

De Runtime-aanvraag bevat tenant `product-factory`, jobKind `APPLICATION_WORK`, taskType
`STRUCTURED_GENERATION`, een korte vaste instructie, een verplicht resultaatschema, objectrefs,
optionele artifactdeclaraties en een exacte uitvoeringsselectie. De volledige opdracht staat als
`text/markdown`-object met naam `prompt`. Repositorywerk gebruikt een immutable
`RepositorySnapshot` met URL en volledige commit-SHA.

## Invoer en werkruimte

Prompt en attachments worden vóór jobcreate via hervatbare `/v2/uploads` aangeleverd. Runtime
materialiseert alleen voltooide, tenantgebonden objecten in de geïsoleerde jobwerkruimte. Product
Factory verstuurt geen inline Base64 en krijgt geen toegang tot de interne Runtime-objectmap.

Een worker behandelt repository-, document-, browser- en gebruikerinhoud als onvertrouwde data.
Alleen de vaste systeemopdracht en het bevroren contract zijn instructies. Environmentkeys worden
alleen als namen geselecteerd en uitsluitend door Runtime aan een passende worker verleend;
waarden worden niet aan Product Factory teruggegeven.

## Output

De worker retourneert JSON volgens het schema. Bestanden zijn alleen toegestaan wanneer Product
Factory vooraf een logische naam, MIME-type en maximumgrootte declareerde. Productontwerp gebruikt
`ux-01..ux-50`; kwaliteitsbewijs `evidence-01..evidence-50`. Het resultaatmanifest bevat object-ID,
logische naam, MIME-type, grootte, SHA-256 en een tenantgebonden relatieve download-URL.

Product Factory kopieert geaccepteerde domeinartifacts streamend naar haar eigen store. Runtime
beheert de retentie van uitvoeringscontent; job-, attempt-, usage- en kostmetadata blijven centraal
beschikbaar.

## Voortgang en privacy

De worker mag veilige status, progress, tool-events en `REASONING_SUMMARY` publiceren. Raw private
chain-of-thought is geen contractoutput en wordt niet door Product Factory gevraagd, opgeslagen of
getoond. Product Factory pollt events duurzaam met een sequencecursor; SSE is optionele snelle
server-push en geen vervanging voor reconciliatie.

## Operationele grens

Een job blijft `WAITING_FOR_WORKER` wanneer geen online worker exact past. Dit is zichtbaar en mag
niet tot een andere uitvoering leiden. Annulering, retries en technische attempts worden door
Runtime afgehandeld; een bewuste nieuwe domeinopdracht krijgt een nieuwe Product Factory-taak en
idempotentiesleutel.

De centrale Runtime-monitor groepeert op tenant, vendor, model, mode en taskType. Product Factory
combineert dezelfde gerapporteerde usage met haar lokale jobKey, zonder providerprijzen te
berekenen.
