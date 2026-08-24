# 0006 - DTO's op alle publieke grenzen

- Status: Accepted
- Datum: 2026-08-24

## Context

In `personal-news-feed-by-claude-code` worden domeinobjecten voor de contractzware endpoints
expliciet omgezet naar types zoals `FeedItemDto`, `RssItemDto` en `SharedFeedItemDto`. JSON-namen en
Jackson-annotaties staan op die DTO's en niet langer op het domeinmodel. Een contracttest bewaakt
dat het verplaatsen van request-DTO's het wirecontract niet verandert.

Software Factory gebruikt aparte request- en responsemodellen voor onder meer authenticatie, de
bridge en de Product Factory-integratie. De aparte `factory-contracts`-module bevat alleen lichte
wiretypes en readers en mag geen Spring- of persistenceafhankelijkheden krijgen.

Product Factory v2 heeft zowel externe HTTP-grenzen als publieke Maven-grenzen tussen
capabilities. Als een database-entiteit of intern domeinaggregate daar direct wordt blootgesteld,
wordt iedere interne wijziging onbedoeld een contractwijziging.

## Decision

Alle publieke grenzen gebruiken expliciete contracttypen:

- HTTP-ingangen en -uitgangen gebruiken request- en response-DTO's;
- capability-API-modules gebruiken commands, commandresultaten en read-only DTO's;
- database-entiteiten, repositoryrecords en interne aggregates verlaten hun
  implementatiemodule nooit;
- DTO's bevatten alleen gegevens die de consumer volgens het contract nodig heeft;
- wire-specifieke namen, defaults en serialisatieannotaties staan op het wire-DTO;
- mappings tussen wire-DTO, application command, domeinmodel en persistence gebeuren expliciet aan
  de betreffende grens;
- publieke query-DTO's zijn read-only momentopnamen en geven geen mutabele domeinobjecten of
  repositories vrij;
- contractwijzigingen zijn bij voorkeur additief en houden rekening met nog actieve oudere
  frontend- of integratieclients.

Een toevallig gelijkvormig domeinobject is geen reden om de DTO weg te laten. Eenvoudige scalars en
stabiele ID- of enumtypen uit een API-module mogen wel rechtstreeks onderdeel van een contract zijn.

## Consequences

- Interne domein- en persistencewijzigingen kunnen plaatsvinden zonder automatisch het HTTP- of
  capabilitycontract te veranderen.
- Security en privacy worden beter controleerbaar doordat alleen expliciet gekozen velden de grens
  passeren.
- JSON-contracten en modulecontracten kunnen gericht worden getest.
- Er is extra mappingcode. Die duplicatie is bewust en heeft de voorkeur boven impliciete koppeling
  tussen transport, domein en database.
- DTO-namen en packages moeten duidelijk maken voor welke grens zij bedoeld zijn; één universele
  DTO voor transport, domein en persistence is niet toegestaan.
