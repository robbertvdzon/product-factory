# Stap 7 — Kwaliteitsbewaking MVP

## Doel

Laat één Tester-agent gericht kwaliteitswerk uitvoeren en aantoonbaar bewijs, bugs en
kwaliteitshistorie publiceren.

## Globale scope

- Implementeer de publieke Kwaliteitsbewaking-API en uitsluitend de MVP-implementatiemodule.
- Implementeer de `QualityWorkItem`-queue en geplande of handmatige `runProcessSession()`.
- Laat de tester stories, complete epics, bugfixes en gebruikerssignalen controleren volgens hun
  bevroren context.
- Bewaar onveranderlijke `Verification`s, `Bug`s en `QualitySnapshot`s.
- Vraag ontbrekend of fout werk via publieke commands bij Productplanning aan; wijzig geen story of
  epic rechtstreeks.
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

De MVP-tester alle ondersteunde soorten kwaliteitswerk kan verwerken, uitkomsten historisch
zichtbaar zijn en vervolgwerk bij de juiste eigenaar terechtkomt. Dit werkt met bestuurbare mocks op
acceptatie en veilig op productie.
