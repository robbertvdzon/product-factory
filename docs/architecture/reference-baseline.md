# Software Factory-referentie voor fase 2

De Product Factory-bootstrap is gebaseerd op Software Factory-commit
`edb323183854a2c85f5a1cd67f20a0af8ccd8c37` van 6 augustus 2026.

Overgenomen patronen:

- een root-Maven-reactor met centraal versiebeheer en losse contracts-, common-, runtime-,
  agentworker- en dashboard-backendmodules;
- Kotlin, JDK 21, Spring Boot 3.5.14, Kotlin 2.1.21 en Spring Modulith 1.4.11;
- componentgerichte repositoryverificatie en één stabiele aggregatiecheck;
- een losse Flutter-dashboardfrontend en losse containerimages;
- Kustomize-basismappen voor een eigen OpenShift-namespace;
- gelaagde rootconfiguratie met `properties.default.env`, `properties.env`, `secrets.env` en
  proces-environmentvariabelen;
- een zelfstandig agentresultaatcontract en een containerized agentworker.

Bewuste afwijkingen:

- Product Factory heeft eigen packages onder `nl.vdzon.productfactory`, eigen Maven-artifacts,
  database en images; er is geen dependency op Software Factory-code;
- alle bedrijfsmodulegrenzen zijn vanaf de bootstrap fail-closed. Alleen de expliciete
  contractmodule is toegestaan waar API-DTO's nodig zijn;
- het dashboard communiceert rechtstreeks via een beveiligde dashboard-backend in plaats van via
  de Software Factory-bridge, omdat Product Factory één eigen runtime beheert;
- Git-publicatie is beperkt tot `product-factory-workspace` en gebruikt een apart credential;
- er bestaat in fase 2 nog geen koppeling die externe Software Factory-stories aanmaakt.
