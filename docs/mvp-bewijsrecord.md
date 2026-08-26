# MVP-bewijsrecord

Status van dit record: `VERIFIED` voor release 0.9.0. De drie documenten `uitgebreid.md` en de in
het stappenplan genoemde handmatige Stakeholdercontroles vallen expliciet buiten deze technische
MVP-afsluiting.

## De 19 ketenscenario's

Alle scenario's zijn als vaste `mvp-01` tot en met `mvp-19` Testbeddefinities opgenomen in dataset
`complete-mvp-v1`. Test Control reset alleen synthetische bronnen en externe fixtures; de genoemde
tests gebruiken publieke procesfuncties en domeincommands.

| ID | Uitkomst | Automatisch bewijs |
|---|---|---|
| `mvp-01-happy-flow` | `VERIFIED` | `MvpHappyFlowIntegrationTest` |
| `mvp-02-prioritized-backlog` | `VERIFIED` | `ProductPlanningMvpIntegrationTest.complete epic` |
| `mvp-03-stakeholder-priority` | `VERIFIED` | `ProductPlanningMvpIntegrationTest.urgente prioriteit` |
| `mvp-04-story-rejected` | `VERIFIED` | `QualityMvpIntegrationTest.storycontrole` |
| `mvp-05-bugfix-not-resolved` | `VERIFIED` | `QualityMvpIntegrationTest.bugfix` |
| `mvp-06-factory-cancelled` | `VERIFIED` | `SoftwareFactoryDispatcherIntegrationTest.CANCELLED` |
| `mvp-07-temporarily-untestable` | `VERIFIED` | `QualityMvpIntegrationTest.retry now` |
| `mvp-08-missing-epic-coverage` | `VERIFIED` | `QualityMvpIntegrationTest.epicbevindingen` |
| `mvp-09-bug-in-epic-check` | `VERIFIED` | `QualityMvpIntegrationTest.epicbevindingen` |
| `mvp-10-epic-check-blocked` | `VERIFIED` | `QualityMvpIntegrationTest.geblokkeerde epiccontrole` |
| `mvp-11-goal-not-reached` | `VERIFIED` | `QualityMvpIntegrationTest.NOT_SUCCESSFUL` |
| `mvp-12-stakeholder-cancels-epic` | `VERIFIED` | `ProductPlanningMvpIntegrationTest.annuleringsmarker` |
| `mvp-13-factory-temporarily-offline` | `VERIFIED` | `SoftwareFactoryDispatcherIntegrationTest.verloren response` |
| `mvp-14-planning-terminal-failure` | `VERIFIED` | `ProductPlanningMvpIntegrationTest.terminale plannertaak` |
| `mvp-15-deployment-pending` | `VERIFIED` | `QualityMvpIntegrationTest.DEPLOYMENT_PENDING` |
| `mvp-16-two-products` | `VERIFIED` | `ProductProcessSchedulerIntegrationTest.twee producten` en capabilityconcurrentietests |
| `mvp-17-dependency-cancelled` | `VERIFIED` | `ProductPlanningMvpIntegrationTest.geannuleerde dependency` |
| `mvp-18-process-schedules` | `VERIFIED` | `ProductProcessSchedulerIntegrationTest` en `ProductAndDecisionIntegrationTest` |
| `mvp-19-agent-meeting` | `VERIFIED` | `AiExecutionRuntimeIntegrationTest.meeting agent en notulenagent` en `ProductAndDecisionIntegrationTest` |

## Dekkingsmatrix per documentgroep

| Documentgroep | Eigenaarstap | Implementatiepad | Verificatie | Status |
|---|---:|---|---|---|
| technische basis, configuratie, secrets, deployment en operatie | 1, 4, 8, 9 | `foundation-impl`, runtimeguards, Kustomize, releaseworkflow, operatierunbooks | foundation-, config-, deployment- en migratietests | `VERIFIED` |
| Maven/composition en publieke modulegrenzen | 1–8 | `product-factory-api`, provider-modules, `product-factory-app` | reactorbuild en `ImplementationManifest` | `VERIFIED` |
| integratie- en acceptatietestbed | 1–9 | acceptance-profiel, fixturecontributors, Test Control, server-side mocks | acceptance safety/fixturetests en 19 scenario-IDs | `VERIFIED` |
| product, signalen, vragen, overleggen en frontendbasis | 2, 4, 9 | `product-impl`, Meeting AI, Flutterproductworkspace | product-, meeting-, HTTP- en widgettests | `VERIFIED` |
| besluitenregister | 2, 4 | `decisions-impl` en publieke commands | decisionintegratietests | `VERIFIED` |
| Agentgeheugen | 3, 4 | `agent-memory-impl` | geheugen-, audit- en AI-configuratietests | `VERIFIED` |
| AI-uitvoering en Runtime-integratie | 3, 4 | `agent-runtime-impl`, Runtime v2-adapter en outbox | runtimecontract-, herstel- en fencingtests | `VERIFIED` |
| Productontwerp API en MVP | 5 | `product-design-impl-mvp` | ontwerpcontract-, integratie- en acceptatietests | `VERIFIED` |
| Productplanning API en MVP | 6 | `product-planning-impl-mvp` | planningcontract-, integratie- en acceptatietests | `VERIFIED` |
| Kwaliteitsbewaking API en MVP | 7 | `quality-impl-mvp` | kwaliteitscontract-, integratie- en acceptatietests | `VERIFIED` |
| Software Factory-dispatcher | 8 | `software-factory-dispatcher-impl` en v2-adapter | dispatchercontract-, HTTP-stub-, herstel- en acceptatietests | `VERIFIED` |
| overzicht, entiteiten en ketenscenario's | 9 | publieke ketencommands, scheduler en operationele projecties | happy-flowtest, schedulertests en scenario's 1–19 | `VERIFIED` |
| geldende ADR's | 1–9 | code, modulecompositie, adapters en documentatie | specificatieaudit en volledige regressiesuite | `VERIFIED` |

## Auditregel

`tools/audit-mvp-specifications.sh` selecteert de volledige MVP-specificatieset, sluit uitsluitend
de drie toekomstontwerpen `uitgebreid.md` en de ADR-template uit, inventariseert alle headings en
normatieve uitspraken, koppelt ieder bronbestand aan precies één matrixrij en controleert relatieve
links. `./product-factory verify` voert die audit vóór de codebuild uit. De omgekeerde controle ligt
vast in API-/DTO-contracttests, `ImplementationManifest` en frontendtests.
