# Product Factory v2 — overleggen met de Stakeholder

Dit document beschrijft hoe de Stakeholder richting geeft en hoe een gesprek controleerbaar
doorwerkt in Product Factory. Er is één globale Stakeholder: de klant voor wie alle producten worden
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

StakeholderQuestionId askStakeholder(AskStakeholderCommand command);
void recordStakeholderAnswer(RecordStakeholderAnswerCommand command);
void withdrawStakeholderQuestion(WithdrawStakeholderQuestionCommand command);
StakeholderQuestionDetails getStakeholderQuestion(StakeholderQuestionId questionId);
List<StakeholderQuestionDetails> findStakeholderQuestions(StakeholderQuestionFilter filter);
```

Een proces kan een overleg aanvragen door `startMeeting(...)` met een korte agenda, reden en
gekoppelde productobjecten aan te roepen. De Stakeholder kan hetzelfde vanuit de UI doen. Deze
commands starten geen processessie van Productontwerp, Productplanning of Kwaliteitsbewaking.

Een procesagent die menselijke uitleg nodig heeft, stelt via vertrouwde procescode een gerichte
vraag met `askStakeholder(...)`. De agent levert de vraag en context; de runtime vult product,
vragende `AgentRoleKey`, processessie, bronobjecten en idempotentiesleutel in. Een vraag start geen
overleg en geen ander proces, maar verschijnt direct in de UI en op de agenda van een bestaand of
volgend overleg voor dat product.

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

Een bericht van de Stakeholder kan optioneel één `targetAgentRole` bevatten. Zonder doelrol spreekt
de Stakeholder met Product Factory als geheel. Met een doelrol antwoordt de Meeting Agent expliciet
vanuit die rol en noemt hij die rol in zijn antwoord. Het antwoord bewaart de Meeting Agent als
werkelijke afzender en de gekozen rol apart als `representedAgentRole`; audit doet dus nooit alsof
de echte procesagent aanwezig was.

## StakeholderQuestion

Een tijdelijke vraag aan de Stakeholder hoort niet in permanent agentgeheugen. Zij heeft een eigen
levenscyclus, zodat zichtbaar blijft of en waar zij is beantwoord.

`StakeholderQuestion` is een duurzame publieke entiteit van de product-/overlegmodule en bevat
minimaal:

- vraag-ID, product-ID en status `OPEN`, `ANSWERED` of `WITHDRAWN`;
- de vertrouwd vastgelegde vragende `AgentRoleKey` en bronprocessessie;
- de vraag, noodzakelijke context en eventuele gekoppelde epic, story, bug, verificatie of ander
  productobject;
- aanmaakmoment en eventuele intrekkingsreden;
- bij beantwoording: het antwoord, meeting-ID, berichtreferentie en tijdstip.

Open vragen worden automatisch aan de overlegcontext en agenda toegevoegd. De notulenagent markeert
een vraag alleen `ANSWERED` wanneer het gesprek een herkenbaar antwoord bevat. Een antwoord wordt
bij een volgende taak van de vragende rol als expliciete meetingcontext aangeboden; alleen een
blijvende, herbruikbare les wordt daarnaast in rolgeheugen vastgelegd.

De technische account-ID waarmee de Stakeholder inlogt mag voor audit worden vastgelegd, maar wordt
geen publiek productobject en geen input voor de procesmodules.

## Wie deelneemt

Ieder overleg heeft:

- de Stakeholder;
- één Meeting Agent die als super-agent het overleg begeleidt;
- de notulenagent die de afronding controleert;
- de rollen waarover wordt gesproken, zonder dat daarvoor echte procesagents worden gestart.

Een overleg hoeft niet te wachten op een actieve processessie. De daarvoor bedoelde
overlegafhandeling mag complete taken bij AI-uitvoering aanvragen; het overleg start nooit
stilletjes agents in een van de drie productprocessen. Ook overlegtaaktypes gebruiken provider en
model uit de algemene `AiJobConfiguration` en bewaren die configuratieversie op de taak.

Agentgeheugen levert een productgebonden rolcatalogus met de stabiele sleutel, weergavenaam,
capability, verantwoordelijkheid en grenzen van iedere actieve agentrol. Daardoor weet de Meeting
Agent precies welke rollen bestaan en wat iedere rol wel en niet doet.

De Meeting Agent is de enige gespreksagent. Hij krijgt via een vertrouwde `MeetingExecutionContext`
leesrechten op het actuele geheugen van alle actieve agentrollen binnen precies dit product, plus
alle open Stakeholdervragen en relevante publieke productgegevens. Hij mag daardoor antwoorden
vanuit een expliciet gekozen rol of meerdere rolperspectieven combineren. Hij doet niet alsof een
procesagent live draait, start geen productprocessessie en verandert geen productobject.

De notulenagent krijgt bij afsluiting dezelfde rolcatalogus, de exacte gelezen geheugenversies, het
volledige gesprek en alle betrokken Stakeholdervragen. Gewone procesagents blijven buiten overleg
strikt beperkt tot hun eigen rolgeheugen.

## Afsluiten en doorwerken

Bij afsluiting maakt de notulenagent een leesbare samenvatting met besproken onderwerpen,
aanwijzingen van de Stakeholder, grote besluiten, beantwoorde en open vragen en acties. Daarna
classificeert hij iedere expliciete uitkomst:

| Uitkomst uit het overleg | Vastlegging | Betekenis |
|---|---|---|
| productdoel of harde grens verandert | `updateProductAssignment(...)` | nieuwe verplichte context voor alle processen |
| feedback, correctie, wens, probleem, kans, risico of kwaliteitszorg | `submitUserSignal(...)` | onderzoekbare melding; nog geen bewezen bug of opdracht |
| grote, blijvende keuze | command op het Besluitenregister | geversioneerd Stakeholderbesluit |
| epic intrekken of actieve epic annuleren | `withdrawEpic(...)` of `cancelEpic(...)` | directe actie op Productontwerp, met meeting-ID en reden |
| epic prioriteren of andere normale procesactie | command op de eigenaarsmodule | gewone procesactie, geen besluit |
| antwoord op een open Stakeholdervraag | `recordStakeholderAnswer(...)` | vraag wordt `ANSWERED` met antwoord, meeting en bericht als bron |
| blijvende les voor een of meer agentrollen | gecontroleerde batch op Agentgeheugen | append-only `ADD`, `REPLACE` of `RETRACT` per doelrol, met het overleg als bron |

Iedere doorwerking bewaart de bron-`Meeting` en een idempotentiesleutel. Als een command mislukt,
blijft zichtbaar welke uitkomst nog niet verwerkt is; het transcript wordt niet opnieuw
geïnterpreteerd om een andere uitkomst te verzinnen.

De notulenagent mag geheugen van meerdere rollen bijwerken zonder aanvullende menselijke
goedkeuringsstap. Hij doet dat alleen voor compacte, blijvende en herbruikbare kennis die aantoonbaar
uit het gesprek volgt. De product-/overlegruntime valideert de doelrollen en biedt één idempotente
batch aan Agentgeheugen aan. Iedere geheugenversie bewaart `actorType = MEETING_MINUTES_AGENT`, het
meeting-ID, de wijzigingsreden en de gebruikte vorige versie. De Stakeholder ziet de wijzigingen in
de notulen en kan ze later via de gewone UI vervangen of intrekken.

Een antwoord, mening of losse actie wordt niet automatisch permanent geheugen. Een tegenspraak met
`ProductAssignment`, geldige besluiten of publieke productobjecten wordt evenmin in geheugen
verstopt, maar via het juiste domeincommand verwerkt of zichtbaar als open actie gelaten.

## Kwaliteitszorg uit een overleg

Wanneer de Stakeholder zegt dat iets mogelijk niet goed werkt, registreert de module een
`UserSignal` met categorie `QUALITY_CONCERN`. Dat is geen bug en schrijft het testresultaat niet
voor. Vervolgens roept de module `requestSignalInvestigation(...)` aan. Dit command zet alleen een
`QualityWorkItem` in de kwaliteitsqueue. Een latere kwaliteitsrun onderzoekt de melding en werkt de
signaalstatus via een command op de productmodule bij.

## Grote besluiten

Alleen een expliciete, blijvende keuze die meerdere toekomstige sessies begrenst gaat naar het
[Besluitenregister](../gedeelde-modules/besluitenregister.md). De notulenagent registreert een Stakeholderbesluit namens
de Stakeholder, maar is niet de beslisser. Een prioriteitswijziging, epicselectie, bugtriage of
andere normale processtap is geen besluit.

## Gerelateerde documenten

- [Overzicht](../overzicht.md)
- [Product- en overleg-API](product-en-overleg-api.md)
- [Processen en entiteiten](../processen/processen-en-entiteiten.md)
- [Besluitenregister](../gedeelde-modules/besluitenregister.md)
- [Kwaliteitsbewaking-API](../processen/kwaliteitsbewaking/api.md)
- [Agentgeheugen](../gedeelde-modules/agentgeheugen.md)
- [AI-uitvoering](../gedeelde-modules/ai-uitvoering.md)
