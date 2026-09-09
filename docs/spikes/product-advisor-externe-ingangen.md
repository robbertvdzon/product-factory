# Spike — bestaande externe ingangen voor Product Advisor

Datum: 2026-09-09

## Agent Runtime en publieke productbron

Product Advisor gebruikt het reeds gemigreerde Runtime-v2-consumercontract. Een beurt bevat een
exacte publieke Git-revisie en geen projectcredential tenzij die naam expliciet aan de advisorrol is
toegekend. Hetzelfde upload-, idempotentie-, status-, resultaat- en artifactpad als de overige
Product Factory-agents wordt gebruikt; er is geen productspecifiek Runtime-endpoint toegevoegd.

## Software Factory v2

De gewone bugfixroute accepteert ProductRequest-ID en requestversie als bestaande
`sourceStoryId`/`sourceStoryVersion`. De stateful mock en integratietest bewijzen dat een verloren
create-response via dezelfde idempotentiesleutel naar één externe story herstelt. Software Factory
hoefde hiervoor niet te worden gewijzigd.

## Bestaande dashboard-story-API

De bestaande productie-ingang accepteert een kortlevend Bearer-token, `hotfix=true`, `start=true`,
automatische approval, vragen en notificatie-events. Een gecontroleerde probe maakte `SF-2377` en
de detailroute bevestigde de volledige payload. De lijstendpoint toonde deze story echter niet en
kon de unieke ProductRequest-marker dus niet terugvinden.

Conclusie: status en create-contract zijn bruikbaar, maar automatische hotfixroutering voldoet nog
niet aan de verplichte lost-response-idempotentie. De Product Factory-adapter, tokenstatus en rotatie
zijn gereed; productie blijft fail-closed met `PF_SOFTWARE_FACTORY_HOTFIX_ENABLED=false`. Er zijn
geen wijzigingen in Software Factory of PvdD gedaan.
