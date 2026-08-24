# Stap 7 — Kwaliteitsbewaking MVP

## Doel

Laat één Tester-agent gericht kwaliteitswerk uitvoeren en aantoonbaar bewijs, bugs en
kwaliteitshistorie publiceren.

## Globale scope

- Implementeer uitsluitend de MVP-provider achter de in stap 1 vastgelegde
  Kwaliteitsbewaking-API.
- Implementeer de `QualityWorkItem`-queue en geplande of handmatige `runProcessSession()`.
- Implementeer zichtbare retryhistorie met `attemptCount`, `retryAfter`, vaste begrensde back-off,
  onbeperkte domeinretries en **Retry now** zonder dubbele processessie.
- Laat de tester stories, complete epics, bugfixes en gebruikerssignalen controleren volgens hun
  bevroren context.
- Bewaar onveranderlijke `Verification`s, `Bug`s en `QualitySnapshot`s.
- Vraag ontbrekend of fout werk via publieke commands bij Productplanning aan; wijzig geen story of
  epic rechtstreeks.
- Meld iedere gepubliceerde storyverificatie of bugfixhertest via `recordStoryVerification(...)` aan
  Productplanning. Laat Productplanning in de normale route pas epicverificatie aanvragen wanneer
  alle actuele gerichte controles zijn geslaagd en geen herstelwerk resteert; documenteer daarnaast
  de expliciete feitelijke herbeoordeling na een extern geannuleerde story met status `CANCELLED`.
- Gebruik voor epicverificatie alleen `PASSED`, `NEEDS_WORK`, `BLOCKED` en `NOT_SUCCESSFUL`.
- Laat een afgekeurde bugfixhertest dezelfde bug `OPEN` houden en voor die bug opnieuw een gewone
  bugfixstory aanvragen. Behandel een `CANCELLED` bugfixstory via een complete feitelijke
  epicbeoordeling, niet als een mislukte fix.
- Sluit kwaliteitsinput vanaf deze stap ook aan op Productontwerp en Productplanning.
- Voeg kwaliteitsbeeld, historie, werkqueue, bewijs en acceptatiescenario's aan de UI toe.

## Buiten scope

De gespecialiseerde agents en parallelle werkwijze uit de uitgebreide implementatie worden niet
gebouwd. Automatische aanlevering vanuit Software Factory volgt in stap 8.

## Specificaties

- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
- [Kwaliteitsbewaking MVP](../processen/kwaliteitsbewaking/mvp.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Frontend](../stakeholder/frontend.md)

## Klaar wanneer

De MVP-tester alle ondersteunde soorten kwaliteitswerk kan verwerken, uitkomsten en retries
historisch zichtbaar zijn en vervolgwerk bij de juiste eigenaar terechtkomt. Dit werkt met
bestuurbare mocks op acceptatie en veilig op productie.
