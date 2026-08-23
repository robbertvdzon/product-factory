# Product Factory v2 — overleggen met de Stakeholder

Dit document beschrijft hoe de Stakeholder richting geeft en hoe een gesprek controleerbaar
doorwerkt in Product Factory. Per product is er één Stakeholder: de klant voor wie het product wordt
gemaakt. De Stakeholder is een actor en geen eigen domeinentiteit.

## Verantwoordelijkheid

De product-/overlegmodule bewaart het overleg en voert de expliciete uitkomsten via publieke
commands door naar de module die het betreffende object bezit. Een transcript of samenvatting
verandert nooit rechtstreeks een epic, story, bug of verificatie.

De wil van de Stakeholder is leidend. Agents mogen adviseren, doorvragen en gevolgen uitleggen. De
notulenagent registreert wat expliciet is afgesproken, maar neemt geen besluit namens de
Stakeholder.

De Stakeholder start en voert het overleg via de gebruikersinterface. De UI gebruikt de publieke
overlegcommands; zij schrijft nooit rechtstreeks in de database of in een procesmodule.

## Publieke interface

De precieze command-DTO's kunnen technisch nog worden uitgewerkt. De betekenisvolle modulegrens is:

```java
MeetingId startMeeting(StartMeetingCommand command);
void recordMeetingMessage(RecordMeetingMessageCommand command);
void closeMeeting(CloseMeetingCommand command);
MeetingDetails getMeeting(MeetingId meetingId);
List<MeetingDetails> findMeetings(ProductId productId, MeetingStatus status);
```

Een proces kan een overleg aanvragen door `startMeeting(...)` met een korte agenda, reden en
gekoppelde productobjecten aan te roepen. De Stakeholder kan hetzelfde vanuit de UI doen. Deze
commands starten geen processessie van Productontwerp, Productplanning of Kwaliteitsbewaking.

## Meeting

`Meeting` is een duurzame publieke entiteit van de product-/overlegmodule en bevat minimaal:

- meeting-ID en product-ID;
- status `REQUESTED`, `OPEN` of `CLOSED`;
- aanleiding, agenda en gekoppelde productobjecten;
- berichten, afzenderrol en tijdstip;
- geraadpleegde bronnen;
- leesbare notulen;
- expliciete uitkomsten en de commands waarmee die zijn verwerkt;
- eventuele fout of nog openstaande actie.

De technische account-ID waarmee de Stakeholder inlogt mag voor audit worden vastgelegd, maar wordt
geen publiek productobject en geen input voor de procesmodules.

## Wie deelneemt

Ieder overleg heeft:

- de Stakeholder;
- één gespreksagent die het overleg begeleidt;
- de notulenagent die de afronding controleert;
- alleen de procesrollen die voor het onderwerp nodig zijn.

Een overleg hoeft niet te wachten op een actieve processessie. Agents worden alleen gestart binnen
de daarvoor bedoelde overlegafhandeling; het overleg start nooit stilletjes agents in een van de
drie productprocessen.

De gespreksagent, notulenagent en iedere deelnemende procesrol hebben elk een eigen stabiele
`AgentRoleKey`. Voor iedere agenttaak voegt de runtime alleen het actuele geheugen van die exacte
rol toe en legt zij de gelezen versie-ID's vast. Ook in een overleg kunnen agents dus nooit het
geheugen van een andere rol lezen. Informatie die zij tijdens het gesprek moeten delen, staat in de
expliciete meetingcontext en niet in gedeeld permanent geheugen.

## Afsluiten en doorwerken

Bij afsluiting maakt de notulenagent een leesbare samenvatting met besproken onderwerpen,
aanwijzingen van de Stakeholder, grote besluiten, open vragen en acties. Daarna classificeert hij
iedere expliciete uitkomst:

| Uitkomst uit het overleg | Vastlegging | Betekenis |
|---|---|---|
| productdoel of harde grens verandert | `updateProductAssignment(...)` | nieuwe verplichte context voor alle processen |
| feedback, correctie, wens, probleem, kans, risico of kwaliteitszorg | `submitUserSignal(...)` | onderzoekbare melding; nog geen bewezen bug of opdracht |
| grote, blijvende keuze | command op het Besluitenregister | geversioneerd Stakeholderbesluit |
| epic intrekken of actieve epic annuleren | `withdrawEpic(...)` of `cancelEpic(...)` | directe actie op Productontwerp, met meeting-ID en reden |
| epic prioriteren of andere normale procesactie | command op de eigenaarsmodule | gewone procesactie, geen besluit |
| expliciet iets voor een agentrol onthouden, corrigeren of vergeten | command op Agentgeheugen namens de Stakeholder | append-only `ADD`, `REPLACE` of `RETRACT`; de overlegagent is alleen registrator |

Iedere doorwerking bewaart de bron-`Meeting` en een idempotentiesleutel. Als een command mislukt,
blijft zichtbaar welke uitkomst nog niet verwerkt is; het transcript wordt niet opnieuw
geïnterpreteerd om een andere uitkomst te verzinnen.

## Kwaliteitszorg uit een overleg

Wanneer de Stakeholder zegt dat iets mogelijk niet goed werkt, registreert de module een
`UserSignal` met categorie `QUALITY_CONCERN`. Dat is geen bug en schrijft het testresultaat niet
voor. Vervolgens roept de module `requestSignalInvestigation(...)` aan. Dit command zet alleen een
`QualityWorkItem` in de kwaliteitsqueue. Een latere kwaliteitsrun onderzoekt de melding en werkt de
signaalstatus via een command op de productmodule bij.

## Grote besluiten

Alleen een expliciete, blijvende keuze die meerdere toekomstige sessies begrenst gaat naar het
[Besluitenregister](besluitenregister.md). De notulenagent registreert een Stakeholderbesluit namens
de Stakeholder, maar is niet de beslisser. Een prioriteitswijziging, epicselectie, bugtriage of
andere normale processtap is geen besluit.

## Gerelateerde documenten

- [Overzicht](overzicht.md)
- [Processen en entiteiten](processen-en-entiteiten.md)
- [Besluitenregister](besluitenregister.md)
- [Kwaliteitsbewaking-API](kwaliteitsbewaking.md)
- [Agentgeheugen](agentgeheugen.md)
