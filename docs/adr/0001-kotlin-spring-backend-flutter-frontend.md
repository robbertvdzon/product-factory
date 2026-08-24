# 0001 - Kotlin/Spring-backend met een Flutter-webfrontend

- Status: Accepted
- Datum: 2026-08-24

## Context

Product Factory wordt opnieuw opgebouwd. De backend bevat één modulair monoliet met duurzame
productdata, queues, schedulers en externe adapters. De Stakeholder gebruikt één webinterface voor
productbediening en operationeel inzicht. Backend en frontend hebben verschillende toolchains en
worden als afzonderlijke containers gebouwd en gedeployed.

De harde backendmodulegrenzen en verwisselbare implementaties zijn afzonderlijk vastgelegd in
ADR-0004. Deze ADR legt alleen de technologiestacks en deploybare componenten vast.

## Decision

- De backend gebruikt Kotlin en Java op Spring Boot, gebouwd met Maven.
- `product-factory-app` is de enige uitvoerbare Spring Boot composition root.
- De backendcapabilities leven in één gedeeld publiek API-artifact en afzonderlijke
  implementatie-artifacts volgens ADR-0004.
- De webfrontend gebruikt Flutter/Dart en communiceert uitsluitend met de publieke backend-API.
- Backend en frontend krijgen afzonderlijke multi-stage containerimages, immutable bronrevisies en
  buildtijden.
- De frontend is geen autorisatiegrens en heeft geen directe database- of moduletoegang.

## Consequences

- Maven/JVM en Flutter/Dart blijven twee expliciet beheerde build-toolchains.
- Een wijziging van een gedeeld HTTP-contract vereist gecoördineerde backend- en
  frontendaanpassingen en contracttests.
- Backend en frontend kunnen afzonderlijk worden uitgerold, maar de UI toont beide actieve
  bronrevisies en meldt een bekende contractincompatibiliteit duidelijk.
- Er bestaan geen aparte v1-orchestrator of dashboard-bridge in de nieuwe architectuur; alle
  backendfunctionaliteit composeert in `product-factory-app`.

## Gerelateerde documenten

- [Technische basis](../platform/technische-basis.md)
- [Maven en Spring Modulith](../platform/maven-en-spring-modulith.md)
- [Deployment en operatie](../platform/deployment-en-operatie.md)
- [Frontend](../stakeholder/frontend.md)
