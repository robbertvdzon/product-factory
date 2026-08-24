# Product Factory — start hier voor de nieuwbouw

Deze map is het zelfstandige ontwerp- en overdrachtspakket voor de volledige vervanging van de
huidige Product Factory. Een uitvoerende agent heeft geen context uit eerdere gesprekken nodig,
maar moet wel de leesvolgorde en grenzen in dit document volgen.

## Huidige status

- De v2-specificaties, MVP-stappen, technische overnamebestanden en het UX-concept zijn voorbereid.
- De nieuwe implementatie is nog niet begonnen; de repository bevat nog de huidige v1-code.
- De eerstvolgende uitvoeringsstap is
  [Stap 1 — Technische fundering](docs/stappenplannen/01-technische-fundering.md).
- Het implementatiedoel is uitsluitend de MVP uit stappen 1 tot en met 9.
- De documenten met de naam `uitgebreid.md` zijn toekomstontwerpen en worden niet gebouwd of
  gedeeltelijk voorbereid binnen de MVP-route.

Werk deze status na iedere afgeronde implementatiestap bij, zodat een volgende agent zonder
gesprekscontext kan zien waar hij moet hervatten.

## Wat staat waar?

| Map | Betekenis |
|---|---|
| [`docs`](docs/overzicht.md) | Productbedoeling, publieke contracten, moduleontwerp, technische architectuur, scenario's en uitvoeringsstappen. |
| [`files`](files/README.md) | Tijdelijke kopieën van bruikbare v1-infrapatronen. Dit zijn invoerbestanden, geen kant-en-klare nieuwe implementatie. |
| [`ux`](ux/README.md) | Klikbaar visueel concept met synthetische data. Het is een referentie voor informatiehiërarchie en interactie, geen normatieve frontendimplementatie. |

`secrets.env` blijft gitignored in de repositoryroot en staat daarom niet in deze map. Toon, kopieer
of commit de inhoud nooit.

## Leesvolgorde voor een uitvoerende agent

Wanneer de opdracht is om de nieuwe Product Factory te implementeren:

1. Lees dit document volledig.
2. Lees het [functionele overzicht](docs/overzicht.md) om het doel en de hele productketen te
   begrijpen.
3. Lees het [overzicht van de MVP-stappen](docs/stappenplannen/README.md).
4. Lees het document van de eerstvolgende onafgeronde stap volledig.
5. Lees vervolgens alleen de specificatie-, API-, MVP-, platform- en ADR-documenten waarnaar die
   stap verwijst. Lees een geselecteerd document altijd volledig.
6. Controleer vóór wijzigingen de actuele repository, tests, infrastructuur en geheimen zonder
   secretwaarden te tonen.
7. Implementeer en verifieer één stap tegelijk. Loop niet vooruit op latere capabilities en gebruik
   niets uit `uitgebreid.md`.
8. Werk na afronding de status in dit document en de werkelijk geraakte documentatie bij.

De gedetailleerde eerste vervangingsopdracht staat in
[Stap 1 — Technische fundering](docs/stappenplannen/01-technische-fundering.md). Dat document bevat
ook de speciale veiligheidsregels voor het verwijderen van v1-code, het behouden van
`secrets.env`, databaseback-ups en de eerste deployments.

## Welke documentatie is leidend?

Ieder document heeft een eigen functie:

1. Een actuele, expliciete opdracht van de Stakeholder of gebruiker is altijd leidend.
2. Een geaccepteerde ADR legt de technische architectuurbeslissing voor haar onderwerp vast.
3. Een proces- of capability-API legt de publieke modulegrens, commands, queries, DTO's en
   eigenaarschap vast.
4. De specifieke module-, platform- en MVP-documenten leggen het gedrag en de interne implementatie
   binnen die grenzen vast.
5. De [ketenscenario's](docs/ketenscenarios.md) leggen vast welk zichtbaar gedrag de complete keten
   moet kunnen aantonen.
6. Het [overzicht](docs/overzicht.md) legt de samenhang in eenvoudige taal uit.
7. De stappenplannen bepalen volgorde en scope, maar zijn geen tweede set specificaties.
8. `ux` maakt de gewenste informatiehiërarchie zichtbaar, maar introduceert geen contracten,
   entiteiten of architectuurbesluiten.

Bij een echte tegenspraak tussen twee normatieve documenten: gok niet en bouw geen van beide
interpretaties stilzwijgend. Benoem de tegenspraak en pas eerst de documentatie aan of vraag om een
besluit. Een specifieker document wint alleen wanneer de teksten naast elkaar kunnen bestaan.

## MVP-grenzen die niet opnieuw gekozen hoeven te worden

- Er is één globale Stakeholder voor alle producten.
- Productontwerp maakt complete epics met UX en maakt geen stories.
- Productplanning maakt en ordent stories; de backlog is een query op open stories.
- Kwaliteitsbewaking publiceert bewijs en bugs en wijzigt geen objecten van andere modules.
- De dispatcher gebruikt geen AI en stuurt maximaal één story per product wanneer Software Factory
  voor dat product geen open story heeft.
- De Stakeholder beheert per product het automatische schema van ieder uitvoerend onderdeel met
  gewone weekdagen en tijden of een interval; handmatig starten blijft daarnaast beschikbaar.
- Alleen een geplande of bevoegde handmatige processessie mag AI-taken aanvragen.
- AI-uitvoering is een generieke queue en begrijpt geen agentrollen of productobjecten.
- Iedere agentrol leest uitsluitend haar eigen versieerbare geheugen.
- De applicatie gebruikt harde Maven-API-/implementatiegrenzen en Spring Modulith binnen de
  implementatiemodules.
- Voor de MVP worden alleen de drie MVP-procesimplementaties geselecteerd.

De volledige onderbouwing en uitzonderingen staan in de gekoppelde specificaties; deze korte lijst
vervangt die documenten niet.

## Verwachte uitvoeringsroute

1. [Technische fundering](docs/stappenplannen/01-technische-fundering.md)
2. [Product- en stakeholderbasis](docs/stappenplannen/02-product-en-stakeholderbasis.md)
3. [Agentgeheugen en AI-instellingen](docs/stappenplannen/03-agentgeheugen-en-ai-instellingen.md)
4. [AI-uitvoering](docs/stappenplannen/04-ai-uitvoering.md)
5. [Productontwerp MVP](docs/stappenplannen/05-productontwerp-mvp.md)
6. [Productplanning MVP](docs/stappenplannen/06-productplanning-mvp.md)
7. [Kwaliteitsbewaking MVP](docs/stappenplannen/07-kwaliteitsbewaking-mvp.md)
8. [Software Factory-dispatcher](docs/stappenplannen/08-software-factory-dispatcher.md)
9. [Volledige MVP-productflow](docs/stappenplannen/09-volledige-mvp-productflow.md)

Een stap is pas afgerond wanneer haar eigen definitie van klaar is aangetoond. Een agent krijgt door
de documentatie geen onbeperkte toestemming voor externe of destructieve handelingen; de concrete
opdracht en de geldende uitvoeringsregels blijven daarvoor bepalend.
