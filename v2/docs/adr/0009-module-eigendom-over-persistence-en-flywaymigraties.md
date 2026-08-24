# 0009 - Module-eigendom over persistence en Flywaymigraties

- Status: Accepted
- Datum: 2026-08-24

## Context

In `personal-news-feed-by-claude-code` zitten repositories in het private
`infrastructure`-package van hun functionele module. Andere modules gebruiken de publieke service
of een publiek event en injecteren niet rechtstreeks zo'n repository. Flywaymigraties bepalen het
schema en repositories maken de SQL- en databasemapping expliciet.

Software Factory groepeert repositories eveneens bij de capability die de data bezit. Publieke
ports schermen de opslag af en Modulith-tests verhinderen dat een willekeurige module interne
repositorypackages gebruikt.

Product Factory v2 gebruikt één fysieke database, maar dat betekent niet dat iedere module alle
tabellen mag lezen of schrijven. Zonder logisch eigendom worden domeininvarianten, transacties en
migraties alsnog via de database aan elkaar gekoppeld.

## Decision

Iedere capability is exclusief eigenaar van haar duurzame data:

- repositories, persistencerecords, mappings en migraties staan in de owning
  implementatiemodule;
- alleen die implementatiemodule leest of schrijft haar tabellen rechtstreeks;
- andere capabilities gebruiken publieke commands en read-only queries uit de API-module;
- een publieke DTO is geen database-entiteit en verraadt geen tabel- of ORM-structuur;
- een application service bepaalt de transactiegrens binnen de eigen capability;
- een flow over meerdere capabilities gebruikt idempotente commands en duurzaam herstelbare
  vervolgstappen en doet niet alsof één transactie alle module-aggregates bezit;
- Flywaymigraties zijn de enige bron van schemawijzigingen; productie gebruikt geen automatische
  schema-aanmaak, `clean` of impliciete ORM-update;
- migraties blijven additief zolang terugschakelen tussen ondersteunde implementaties mogelijk
  moet zijn.

Deze ADR schrijft geen JDBC, JPA of andere concrete persistencebibliotheek voor. Een
implementatiemodule mag die intern kiezen zolang de publieke grens en migratieregels intact
blijven.

## Consequences

- Tabellen hebben een duidelijke eigenaar en kunnen niet als ongedocumenteerde integratie-API
  worden gebruikt.
- Businessinvarianten blijven afdwingbaar via de owning service en kunnen niet door een andere
  module worden omzeild.
- Cross-capabilityflows moeten expliciet omgaan met gedeeltelijke uitvoering, idempotentie en
  herstel; een brede database-transactie maskeert dat niet.
- Sommige read-modellen vereisen een publieke query of een bewust door de eigenaar opgebouwde
  projectie in plaats van een snelle cross-module join.
- Databasewijzigingen worden zichtbaar, reviewbaar en reproduceerbaar via migraties.
