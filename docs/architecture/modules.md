# Modulith-architectuur

`productfactory` bevat één `@Modulithic` composition root. Iedere top-level bedrijfsmodule heeft
expliciete `@ApplicationModule`-metadata. `ApplicationModules.verify()` en een aanvullende
conventietest draaien bij iedere Maven-verificatie.

De modules `product`, `story` en `workspace` bevatten de eerste werkende use-cases. De overige
fase-2-modules zijn als gesloten grenzen gereserveerd en krijgen pas gedrag wanneer een volgende
fase dat nodig heeft. Zo ontstaan geen generieke lagen die latere domeinen ongemerkt koppelen.

De contracts-module bevat uitsluitend wire-DTO's voor runtime, dashboard en agentworker. Common
bevat alleen de zelfstandig geïmplementeerde configuratielader. Geen van beide verwijst naar
Software Factory-artifacts.
