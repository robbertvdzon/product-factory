(() => {
  const body = document.body;
  const views = [...document.querySelectorAll(".view")];
  const navItems = [...document.querySelectorAll(".nav-item[data-view]")];
  const menuButton = document.querySelector(".menu-button");
  const drawer = document.querySelector(".entity-drawer");
  const drawerBackdrop = document.querySelector(".drawer-backdrop");
  const drawerClose = document.querySelector(".drawer-close");
  const signalModal = document.querySelector(".signal-modal");
  const signalBackdrop = document.querySelector(".signal-backdrop");
  const statsModal = document.querySelector(".statistics-modal");
  const statsBackdrop = document.querySelector(".stats-backdrop");
  const authModal = document.querySelector(".auth-modal");
  const authBackdrop = document.querySelector(".auth-backdrop");
  const scheduleModal = document.querySelector(".schedule-modal");
  const scheduleBackdrop = document.querySelector(".schedule-backdrop");
  const toast = document.querySelector(".toast");
  let returnFocus = null;
  let editingScheduleRow = null;
  let toastTimeout;

  const entityData = {
    "EP-014": {
      status: "Actief",
      statusClass: "active",
      reference: "Epic EP-014 · versie 3",
      title: "Een rustiger aanvraagproces op mobiel",
      lead: "De bezoeker rondt de aanvraag op een klein scherm zonder twijfel en zonder gegevensverlies af.",
      body: `
        <section><h3>Gebruikersverbetering</h3><p>De belangrijkste aanvraagroute voelt voorspelbaar, ook wanneer verzenden niet direct lukt of iemand terugnavigeert.</p></section>
        <section><h3>Scope</h3><ul><li>Bevestiging en fouttoestand na verzenden.</li><li>Focus en terugnavigeren op mobiel.</li><li>Behoud van ingevulde gegevens.</li></ul></section>
        <section><h3>UX-ontwerp</h3><div class="ux-wireframe"><div class="phone"><span></span><div class="fake-title"></div><div class="fake-line"></div><div class="fake-line short"></div><div class="fake-error">Verzenden lukte niet<br><b>Probeer opnieuw</b></div><div class="fake-button"></div></div><div><strong>Mobiele aanvraag · herstelbare fout</strong><p>Behoud de formuliercontext en plaats de herstelactie direct bij de fout.</p><small>Het complete ontwerp en alle toestanden horen bij deze epic.</small></div></div></section>
        <section><h3>Succes</h3><p>Gebruikers begrijpen of hun aanvraag is verzonden en kunnen zonder dubbele aanvraag herstellen.</p></section>
        <section class="drawer-actions"><h3>Stakeholderacties</h3><p>Deze epic is actief. Je kunt hem voorrang geven of met een verplichte reden annuleren.</p><div><button class="secondary-action" data-action="prioritize-epic">Epic voorrang geven</button><button class="danger-action" data-action="cancel-epic">Epic annuleren</button></div></section>`
    },
    "EP-016": {
      status: "Beschikbaar",
      statusClass: "available",
      reference: "Epic EP-016 · versie 1",
      title: "Documenten begrijpelijk vergelijken",
      lead: "Mensen zien zonder vakkennis welk document bij hun situatie past.",
      body: `<section><h3>Gebruikersverbetering</h3><p>De verschillen worden uitgelegd vanuit de vraag van de bezoeker, niet vanuit interne documentnamen.</p></section><section><h3>UX-ontwerp</h3><p>Een rustige vergelijking met maximaal drie relevante verschillen en een duidelijke vervolgstap.</p></section><section><h3>Status</h3><p>Productplanning heeft deze epic nog niet opgepakt. Productontwerp mag hem dus nog verbeteren.</p></section><section class="drawer-actions"><h3>Stakeholderacties</h3><p>Deze epic is nog beschikbaar en kan daarom worden geprioriteerd of ingetrokken.</p><div><button class="secondary-action" data-action="prioritize-epic">Epic voorrang geven</button><button class="danger-action" data-action="withdraw-epic">Epic intrekken</button></div></section>`
    },
    "EP-009": {
      status: "Controle",
      statusClass: "verifying",
      reference: "Epic EP-009 · versie 2",
      title: "Zoeken zonder vaktaal",
      lead: "Bezoekers vinden passende hulp met woorden die zij zelf gebruiken.",
      body: `<section><h3>Huidige fase</h3><p>Alle stories zijn opgeleverd en gericht gecontroleerd. Kwaliteitsbewaking test nu de complete gebruikersverbetering.</p></section><section><h3>Bevroren ontwerp</h3><p>De gekozen epicversie en UX worden niet meer aangepast tijdens de controle.</p></section>`
    },
    "EP-005": {
      status: "Afgerond",
      statusClass: "processed",
      reference: "Epic EP-005 · versie 2",
      title: "Direct zien waar hulp beschikbaar is",
      lead: "De verbetering is gebouwd, gecontroleerd en aantoonbaar geslaagd.",
      body: `<section><h3>Uitkomst</h3><p>De epiccontrole is op 12 juli geslaagd. De bevroren verificatie en het bewijs blijven beschikbaar.</p></section>`
    },
    "EP-003": {
      status: "Niet succesvol",
      statusClass: "warning",
      reference: "Epic EP-003 · versie 1",
      title: "Persoonlijk advies na drie vragen",
      lead: "Alles werkte zoals ontworpen, maar de verbetering hielp gebruikers onvoldoende.",
      body: `<section><h3>Uitkomst</h3><p>De epic is niet alsnog gerepareerd. De bevinding is als productsignaal bewaard voor een mogelijke nieuwe richting.</p></section>`
    },
    "ST-087": {
      status: "In uitvoering",
      statusClass: "progress",
      reference: "Story ST-087 · EP-014",
      title: "Bevestiging na verzenden verbeteren",
      lead: "Na verzenden weet de bezoeker direct dat de aanvraag veilig is ontvangen.",
      body: `<section><h3>Gebruikersresultaat</h3><p>De bezoeker hoeft niet te twijfelen of opnieuw te verzenden en ziet wat de volgende stap is.</p></section><section><h3>Acceptatiecriteria</h3><ul><li>Bevestiging staat direct in de bestaande route.</li><li>De vervolgstap is in gewone taal beschreven.</li><li>Verversen veroorzaakt geen dubbele aanvraag.</li></ul></section>`
    },
    "ST-088": {
      status: "Te doen",
      statusClass: "todo",
      reference: "Story ST-088 · EP-014",
      title: "Foutmelding met herstelactie",
      lead: "Als verzenden niet lukt, ziet de bezoeker wat er gebeurde en kan die veilig opnieuw proberen.",
      body: `<section><h3>Gebruikersresultaat</h3><p>De bezoeker verliest geen ingevulde gegevens en weet dat de aanvraag nog niet is verzonden.</p></section><section><h3>Acceptatiecriteria</h3><ul><li>De foutmelding staat direct bij de hoofdactie.</li><li>Ingevulde gegevens blijven ongewijzigd.</li><li>Opnieuw proberen maakt nooit een dubbele aanvraag.</li><li>Toetsenbordfocus gaat naar de foutmelding.</li></ul></section><section><h3>UX-overdracht</h3><div class="ux-wireframe"><div class="phone"><span></span><div class="fake-title"></div><div class="fake-line"></div><div class="fake-line short"></div><div class="fake-error">Verzenden lukte niet<br><b>Probeer opnieuw</b></div><div class="fake-button"></div></div><div><strong>Mobiel · fouttoestand</strong><p>Behoud de formuliercontext. Gebruik geen los foutscherm.</p><small>SVG-ontwerp en tokens staan volledig in de story.</small></div></div></section>`
    },
    "ST-091": {
      status: "Wordt verstuurd",
      statusClass: "reserved",
      reference: "Story ST-091 · EP-014",
      title: "Focus behouden na terugnavigeren",
      lead: "Na terugnavigeren komt de bezoeker terug op de logische plek in het formulier.",
      body: `<section><h3>Gebruikersresultaat</h3><p>De mobiele route blijft rustig en voorspelbaar, ook met toetsenbord of schermlezer.</p></section>`
    },
    "ST-096": {
      status: "Te doen",
      statusClass: "todo",
      reference: "Bugfixstory ST-096 · EP-011",
      title: "Herstel dubbele bevestigingsmail",
      lead: "Eén geslaagde aanvraag veroorzaakt precies één bevestigingsmail.",
      body: `<section><h3>Bugfixresultaat</h3><p>De bezoeker ontvangt geen verwarrende dubbele bevestiging en de aanvraag zelf blijft ongewijzigd.</p></section>`
    },
    "ST-102": {
      status: "Wacht",
      statusClass: "waiting",
      reference: "Story ST-102 · EP-016",
      title: "Voortgang veilig bewaren",
      lead: "Een bezoeker kan later verdergaan met een nog niet afgeronde aanvraag.",
      body: `<section><h3>Afhankelijkheid</h3><p>Deze story wordt uitvoerbaar nadat ST-101 is opgeleverd.</p></section>`
    },
    "ST-073": {status:"Geannuleerd",statusClass:"warning",reference:"Story ST-073 · EP-003",title:"Los bevestigingsscherm na verzenden",lead:"De Stakeholder annuleerde de actieve epic en gaf een reden.",body:`<section><h3>Annulering</h3><p>Bron: overleg M-009. Reden: een los scherm maakte de route langer zonder aantoonbare gebruikersverbetering.</p></section>`},
    "ST-074": {status:"Geannuleerd",statusClass:"warning",reference:"Story ST-074 · EP-009",title:"Oude zoekfilter uitbreiden",lead:"Software Factory heeft dit externe werk geannuleerd.",body:`<section><h3>Doorwerking</h3><p>Product Factory legt geen mislukstatus vast. Na het overige werk beoordeelt Kwaliteitsbewaking de feitelijke complete epic; Productplanning herplant alleen wanneer die controle een dekkingsgat bewijst.</p></section>`},
    "US-044": {
      status: "Open",
      statusClass: "open",
      reference: "Signaal US-044 · klantgesprek",
      title: "Formulier voelt onduidelijk op mobiel",
      lead: "“Ik wist niet of mijn aanvraag al verzonden was.”",
      body: `<section><h3>Oorspronkelijke melding</h3><p>Op mijn telefoon bleef ik twijfelen of ik nog een keer moest drukken. Ik wilde mijn gegevens niet dubbel insturen.</p><dl class="detail-grid"><div><dt>Categorie</dt><dd>Feedback</dd></div><div><dt>Urgentie</dt><dd>Hoog</dd></div><div><dt>Bron</dt><dd>Klantgesprek</dd></div><div><dt>Bijlagen</dt><dd>schermopname-mobiel.mp4</dd></div></dl></section><section><h3>Verwerking</h3><p>Dit signaal is nog niet opgepakt. De oorspronkelijke tekst kan niet worden aangepast.</p></section>`
    },
    "US-039": {
      status: "In behandeling",
      statusClass: "review",
      reference: "Signaal US-039 · support",
      title: "Bevestigingsmail soms dubbel",
      lead: "Drie vergelijkbare meldingen in de afgelopen week.",
      body: `<section><h3>Melding</h3><dl class="detail-grid"><div><dt>Categorie</dt><dd>Kwaliteitszorg</dd></div><div><dt>Urgentie</dt><dd>Hoog</dd></div><div><dt>Bron</dt><dd>Support</dd></div><div><dt>Bijlagen</dt><dd>3 supporttickets</dd></div></dl></section><section><h3>Verwerking</h3><p>Kwaliteitsbewaking onderzoekt dit signaal via QualityWorkItem QW-063. Het resultaat wordt hier zichtbaar zodra de controle klaar is.</p><p><b>Gekoppeld:</b> QW-063 · BUG-021 zodra reproductie bevestigd is.</p></section>`
    },
    "US-031": {
      status: "Verwerkt",
      statusClass: "processed",
      reference: "Signaal US-031 · overleg M-18",
      title: "Meer aandacht voor begrijpelijke taal",
      lead: "De Stakeholder wil vaktermen structureel vermijden.",
      body: `<section><h3>Uitkomst</h3><p>Het signaal is gekoppeld aan epic EP-014. De blijvende taalrichting is daarnaast als apart Stakeholderbesluit vastgelegd.</p><p><b>Gekoppeld:</b> overleg M-018 · epic EP-014 · besluit DEC-031.</p></section>`
    },
    "BUG-021": {
      status: "Open · P1", statusClass: "warning", reference: "Bug BUG-021 · versie 2", title: "Bevestigingsmail wordt soms dubbel verstuurd", lead: "Eén aanvraag kan twee identieke bevestigingsmails veroorzaken.",
      body: `<section><h3>Verwacht en werkelijk</h3><p><b>Verwacht:</b> precies één bevestigingsmail per geslaagde aanvraag.<br><b>Werkelijk:</b> bij een trage mailresponse wordt dezelfde mail soms tweemaal verstuurd.</p></section><section><h3>Reproductie en bewijs</h3><ol><li>Open acceptatie op mobiel.</li><li>Verstuur één aanvraag tijdens het trage-mailprofiel.</li><li>Controleer de mailbox: twee berichten met hetzelfde aanvraag-ID.</li></ol><p>Omgeving: acceptatie · commit 7f3b9d1 · bewijs VER-146.</p></section><section><h3>Herstel</h3><p>Bugfixstory ST-096 staat in de backlog. Na oplevering maakt Kwaliteitsbewaking een nieuwe hertest.</p></section>`
    },
    "BUG-018": {status:"Open · P2",statusClass:"open",reference:"Bug BUG-018 · versie 1",title:"Focus verdwijnt na een mislukte verzending",lead:"Toetsenbord- en schermlezergebruikers komen niet bij de foutmelding uit.",body:`<section><h3>Bewijs</h3><p>Gereproduceerd in mobiele Safari en VoiceOver op acceptatie. De fout verschijnt visueel, maar focus blijft op de verzendknop.</p></section><section><h3>Herstel</h3><p>Bugfixstory ST-104 staat klaar. Er is maximaal één actieve bugfixstory aan deze bug gekoppeld.</p></section>`},
    "BUG-012": {status:"Opgelost",statusClass:"processed",reference:"Bug BUG-012 · herstelhistorie",title:"Adresgegevens verdwenen bij terugnavigeren",lead:"De bugfix is op de werkelijk gedeployde commit hertest en geslaagd.",body:`<section><h3>Herstelhistorie</h3><p>ST-082 · opgeleverd commit 7f3b9d1 · verificatie VER-144 geslaagd op acceptatie.</p></section>`},
    "VER-144": {status:"Geslaagd",statusClass:"processed",reference:"Verificatie VER-144 · onveranderlijk",title:"Adresgegevens blijven bewaard",lead:"Story ST-082 is op de gedeployde commit aantoonbaar geslaagd.",body:`<section><h3>Controle</h3><dl class="detail-grid"><div><dt>Omgeving</dt><dd>Acceptatie</dd></div><div><dt>Storycommit</dt><dd>7f3b9d1</dd></div><div><dt>Deployment</dt><dd>7f3b9d1</dd></div><div><dt>Uitkomst</dt><dd>Geslaagd</dd></div></dl><p>Alle acht acceptatiecontroles slaagden. Screenshot- en browserlogbewijs zijn bewaard.</p></section>`},
    "VER-141": {status:"Geslaagd",statusClass:"processed",reference:"Verificatie VER-141 · onveranderlijk",title:"Zoeken zonder vaktaal",lead:"De volledige gebruikersverbetering van EP-009 is aantoonbaar bereikt.",body:`<section><h3>Epiccontrole</h3><p>Twaalf primaire en alternatieve routes zijn gecontroleerd op acceptatie en read-only in productie. Geen open bug blokkeert de succescriteria.</p></section>`},
    "VER-139": {status:"Geblokkeerd",statusClass:"warning",reference:"Verificatiepoging VER-139",title:"Mobiele Safari-route",lead:"Het oordeel over de story is niet vervalst door een onbereikbaar testaccount.",body:`<section><h3>Blokkade</h3><p>Het veilige Safari-testaccount was niet bereikbaar. QualityWorkItem QW-031 blijft retrybaar en staat zichtbaar bovenaan.</p></section>`},
    "SNAP-051": {status:"Actueel",statusClass:"active",reference:"Kwaliteitssnapshot SNAP-051 · 24 augustus",title:"Kwaliteitsbeeld van HKH",lead:"Onveranderlijke momentopname na de laatste afgeronde niet-lege kwaliteitssessie.",body:`<section><h3>Dimensies</h3><dl class="detail-grid"><div><dt>Kritieke bugs</dt><dd>0</dd></div><div><dt>Open bugs</dt><dd>2</dd></div><div><dt>Recent geteste kernroutes</dt><dd>13 van 15</dd></div><div><dt>Geblokkeerd testwerk</dt><dd>2</dd></div></dl></section><section><h3>Bronnen</h3><p>Gebaseerd op verificaties VER-139 t/m VER-146 en bugs BUG-012, BUG-018 en BUG-021. Er is geen verborgen totaalscore.</p></section>`},
    "DEC-031": {status:"Actief",statusClass:"active",reference:"Besluit DEC-031 · versie 3",title:"We schrijven voor mensen zonder vakkennis",lead:"Geldig sinds 12 mei 2026.",body:`<section><h3>Actuele beslissing</h3><p>Alle primaire routes gebruiken gewone taal. Een vakterm krijgt uitleg op de plek waar die nodig is.</p></section><section><h3>Historie</h3><p>Versie 3 verving versie 2 na overleg M-018. Alle eerdere teksten en geldigheidsperioden blijven beschikbaar.</p><button class="secondary-action" data-action="start-meeting">Besluit in overleg aanpassen</button></section>`},
    "DEC-028": {status:"Actief",statusClass:"active",reference:"Besluit DEC-028 · versie 1",title:"Persoonlijke gegevens blijven in PostgreSQL",lead:"Factorybesluit, zichtbaar en corrigeerbaar via een stakeholderoverleg.",body:`<section><h3>Actuele beslissing</h3><p>Nieuwe productdata wordt relationeel opgeslagen en volgt de vastgelegde bewaartermijnen.</p></section><section><h3>Vervangt</h3><p>DEC-014 over MongoDB is hierdoor SUPERSEDED. De opvolgingslink is in beide richtingen zichtbaar.</p></section>`},
    "DEC-036": {status:"Actief",statusClass:"active",reference:"Besluit DEC-036 · versie 1",title:"Productietesten wijzigen nooit echte klantdata",lead:"Geldig sinds 21 juni 2026.",body:`<section><h3>Actuele beslissing</h3><p>Processen gebruiken alleen veilige read-only routes of expliciete testaccounts.</p></section>`},
    "DEC-014": {status:"Vervangen",statusClass:"waiting",reference:"Besluit DEC-014 · alle versies",title:"Productdata opslaan in MongoDB",lead:"Niet meer geldig; opgevolgd door DEC-028.",body:`<section><h3>Vervangingsrelatie</h3><p>Dit besluit werd op 3 juni 2026 ongeldig toen DEC-028 de relationele opslagrichting overnam.</p></section>`},
    "DEC-019": {status:"Ingetrokken",statusClass:"warning",reference:"Besluit DEC-019 · alle versies",title:"Alle formulieren openen in een losse stap",lead:"Ingetrokken op 9 april 2026.",body:`<section><h3>Reden</h3><p>Gebruikersonderzoek liet zien dat de losse stap juist extra twijfel veroorzaakte. Er is geen opvolgend besluit.</p></section>`},
    "M-024": {status:"Open",statusClass:"open",reference:"Overleg M-024 · vandaag 14:30",title:"Mobiele aanvraagroute bespreken",lead:"Agenda: drie signalen en de terugkerende Safari-testblokkade.",body:`<section><h3>Gesprek</h3><div class="conversation"><p><b>Stakeholder · 14:31</b><br>De foutmelding voelt nog te technisch. Ik wil dat duidelijker wordt wat iemand zelf kan doen.</p><p><b>Gespreksagent · 14:32</b><br>Ik leg dit vast als richting voor de huidige epic. Wilt u ook dat de Safari-blokkade apart onderzocht blijft?</p></div><label class="meeting-message">Bericht<textarea rows="3" placeholder="Geef richting of stel een vraag..."></textarea></label><button class="secondary-action" data-action="meeting-message">Bericht versturen</button></section><section><h3>Gekoppeld</h3><p>EP-014 · US-044 · US-039 · QW-031</p></section><section class="drawer-actions"><h3>Overleg afronden</h3><p>De notulenagent maakt daarna de notulen en laat per uitkomst zien welk command is uitgevoerd.</p><button class="primary-action" data-action="close-meeting">Afsluiten en notulen maken</button></section>`},
    "M-018": {status:"Afgerond",statusClass:"processed",reference:"Overleg M-018 · 18 augustus",title:"Begrijpelijke taal als vaste richting",lead:"Alle notulen en expliciete doorwerkingen zijn verwerkt.",body:`<section><h3>Notulen</h3><p>De Stakeholder wil dat primaire routes ook zonder vakkennis begrijpelijk zijn. Technische precisie blijft, maar wordt op de plek zelf uitgelegd.</p></section><section><h3>Doorwerking</h3><ul><li>✓ UserSignal US-031 gemaakt.</li><li>✓ Besluit DEC-031 herzien naar versie 3.</li><li>✓ Geheugen van PRODUCT_DESIGNER_MVP vervangen.</li></ul></section>`},
    "M-015": {status:"Afgerond",statusClass:"processed",reference:"Overleg M-015 · 4 augustus",title:"Prioriteit mobiele aanvraag",lead:"EP-014 kreeg aantoonbaar voorrang via gewoon planningswerk.",body:`<section><h3>Doorwerking</h3><p>PlanningWorkItem PW-028 is uitgevoerd. Dit was een prioriteitsactie en geen blijvend besluit.</p></section>`},
    "M-011": {status:"Afgerond",statusClass:"processed",reference:"Overleg M-011 · 21 juli",title:"Productietesten en privacy",lead:"Eén blijvende privacykeuze is als besluit vastgelegd.",body:`<section><h3>Doorwerking</h3><p>Besluit DEC-036 is aangemaakt. TestableProductConfiguration werd aangepast met read-only productiegrenzen.</p></section>`},
    "PS-188": {status:"Geslaagd",statusClass:"processed",reference:"Processessie PS-188 · Productplanning",title:"Stories voor EP-016 gepland",lead:"De run publiceerde acht zelfstandige stories en ordende de productbacklog.",body:`<section><h3>Run</h3><dl class="detail-grid"><div><dt>Start</dt><dd>10:17:04</dd></div><div><dt>Einde</dt><dd>10:20:31</dd></div><div><dt>Implementatie</dt><dd>planning-mvp 1.2.0</dd></div><div><dt>Hervat</dt><dd>2 keer na WAITING_FOR_AI</dd></div></dl></section><section><h3>Input en output</h3><p>Input: EP-016 v1, opdracht v8, besluitenpeildatum 10:17, geheugenversies 11 en 14.<br>Output: ST-101 t/m ST-108 en backlogvolgorde 14–21.</p></section>`},
    "PS-187": {status:"Geslaagd",statusClass:"processed",reference:"Processessie PS-187 · Productontwerp",title:"Nieuwe epic gepubliceerd",lead:"EP-016 versie 1 is als complete beschikbare epic gepubliceerd.",body:`<section><h3>Uitkomst</h3><p>1 epic gepubliceerd · 0 signalen verwerkt · AI-taak AI-401 geslaagd.</p></section>`},
    "PS-186": {status:"Geblokkeerd",statusClass:"warning",reference:"Processessie PS-186 · Kwaliteitsbewaking",title:"Twee controles wachten op een testvoorwaarde",lead:"De productuitkomst is niet als mislukt geregistreerd.",body:`<section><h3>Uitkomst</h3><p>QW-031 wacht op een Safari-testaccount. QW-052 wacht tot commit 91c0ae2 op acceptatie staat. Beide retries blijven zichtbaar.</p></section>`},
    "PS-185": {status:"No-op",statusClass:"idle",reference:"Dispatchersessie PS-185",title:"Geen nieuw werk verstuurd",lead:"De dispatcher synchroniseerde ST-087 en vond daarna terecht geen nieuwe uitvoerbare story.",body:`<section><h3>Uitkomst</h3><p>Software Factory meldde ST-087 nog als OPEN. Daarom is geen tweede story aangemaakt.</p></section>`},
    "PS-184": {status:"Overgeslagen",statusClass:"waiting",reference:"Processessie PS-184 · Productontwerp",title:"Schedulerbotsing overgeslagen",lead:"Een handmatige run voor hetzelfde product voerde al een call uit.",body:`<section><h3>Uitkomst</h3><p>Geen input geclaimd, geen AI-taak gestart en geen productdata gewijzigd.</p></section>`}
  };

  function updateOverlayState() {
    const anyOpen = drawer.classList.contains("open") || !signalModal.hidden || !statsModal.hidden || !authModal.hidden || !scheduleModal.hidden;
    body.classList.toggle("overlay-open", anyOpen);
  }

  function showToast(message) {
    window.clearTimeout(toastTimeout);
    toast.textContent = message;
    toast.classList.add("show");
    toastTimeout = window.setTimeout(() => toast.classList.remove("show"), 3200);
  }

  function closeNavigation() {
    body.classList.remove("nav-open");
    menuButton.setAttribute("aria-expanded", "false");
    menuButton.setAttribute("aria-label", "Menu openen");
  }

  function openView(viewName) {
    const nextView = document.querySelector(`#view-${viewName}`);
    if (!nextView) return;
    views.forEach((view) => view.classList.toggle("active", view === nextView));
    const managementViews = ["management", "product-settings", "decisions", "memory", "operations", "acceptance"];
    navItems.forEach((item) => item.classList.toggle("active", item.dataset.view === viewName || (item.dataset.view === "management" && managementViews.includes(viewName))));
    closeNavigation();
    window.scrollTo({ top: 0, behavior: "smooth" });
    const heading = nextView.querySelector("h1");
    if (heading) document.title = `${heading.textContent} — Product Factory`;
  }

  function openDrawer(entityId, trigger) {
    const content = entityData[entityId];
    if (!content) return;
    const status = drawer.querySelector(".drawer-status");
    status.className = `status-chip drawer-status ${content.statusClass}`;
    status.textContent = content.status;
    drawer.querySelector(".drawer-reference").textContent = content.reference;
    drawer.querySelector("#entity-drawer-title").textContent = content.title;
    drawer.querySelector(".drawer-lead").textContent = content.lead;
    drawer.querySelector(".drawer-body").innerHTML = content.body;
    returnFocus = trigger;
    drawerBackdrop.hidden = false;
    drawer.classList.add("open");
    drawer.setAttribute("aria-hidden", "false");
    updateOverlayState();
    drawerClose.focus();
  }

  function closeDrawer() {
    if (!drawer.classList.contains("open")) return;
    drawer.classList.remove("open");
    drawer.setAttribute("aria-hidden", "true");
    window.setTimeout(() => {
      drawerBackdrop.hidden = true;
      updateOverlayState();
    }, 230);
    if (returnFocus) returnFocus.focus();
  }

  function openModal(modal, backdrop, trigger, focusTarget) {
    returnFocus = trigger;
    modal.hidden = false;
    backdrop.hidden = false;
    updateOverlayState();
    (focusTarget || modal.querySelector("button")).focus();
  }

  function closeModal(modal, backdrop) {
    if (modal.hidden) return;
    modal.hidden = true;
    backdrop.hidden = true;
    updateOverlayState();
    if (returnFocus) returnFocus.focus();
  }

  function startButton(button, label, message) {
    if (button.disabled) return;
    button.disabled = true;
    button.dataset.originalLabel = button.textContent;
    button.textContent = label;
    showToast(message);
    window.setTimeout(() => {
      button.disabled = false;
      button.textContent = button.dataset.originalLabel;
    }, 4200);
  }

  function setScheduleMode(mode) {
    scheduleModal.querySelector(".schedule-mode").value = mode;
    scheduleModal.querySelector(".weekly-schedule-fields").hidden = mode !== "weekly";
    scheduleModal.querySelector(".interval-schedule-fields").hidden = mode !== "interval";
  }

  const scheduleDayNames = [
    ["1", "Ma", "maandag"],
    ["2", "Di", "dinsdag"],
    ["3", "Wo", "woensdag"],
    ["4", "Do", "donderdag"],
    ["5", "Vr", "vrijdag"],
    ["6", "Za", "zaterdag"],
    ["0", "Zo", "zondag"]
  ];

  function addScheduleTimeInput(container, value = "12:00") {
    const input = document.createElement("input");
    input.type = "time";
    input.value = value;
    container.querySelector("button").before(input);
    return input;
  }

  function renumberScheduleRules() {
    scheduleModal.querySelectorAll(".schedule-rule").forEach((rule, index) => {
      rule.querySelector("header strong").textContent = `Regel ${index + 1}`;
      rule.querySelector('[data-action="remove-schedule-rule"]').hidden = scheduleModal.querySelectorAll(".schedule-rule").length === 1;
    });
  }

  function createScheduleRule(days = ["1"], times = ["09:00"]) {
    const rule = document.createElement("article");
    rule.className = "schedule-rule";

    const header = document.createElement("header");
    header.innerHTML = '<strong>Regel</strong><button type="button" data-action="remove-schedule-rule">Verwijderen</button>';
    rule.append(header);

    const fieldset = document.createElement("fieldset");
    const legend = document.createElement("legend");
    legend.textContent = "Dagen";
    const picker = document.createElement("div");
    picker.className = "weekday-picker";
    scheduleDayNames.forEach(([value, shortName]) => {
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = "checkbox";
      input.value = value;
      input.checked = days.includes(value);
      const text = document.createElement("span");
      text.textContent = shortName;
      label.append(input, text);
      picker.append(label);
    });
    fieldset.append(legend, picker);
    rule.append(fieldset);

    const timesLabel = document.createElement("label");
    const timesTitle = document.createElement("span");
    timesTitle.textContent = "Tijden";
    const timesContainer = document.createElement("div");
    timesContainer.className = "schedule-times";
    const addButton = document.createElement("button");
    addButton.type = "button";
    addButton.dataset.action = "add-schedule-time";
    addButton.setAttribute("aria-label", "Tijd aan deze regel toevoegen");
    addButton.textContent = "+";
    timesContainer.append(addButton);
    times.forEach((time) => addScheduleTimeInput(timesContainer, time));
    timesLabel.append(timesTitle, timesContainer);
    rule.append(timesLabel);
    return rule;
  }

  function collectScheduleRules() {
    return [...scheduleModal.querySelectorAll(".schedule-rule")].map((rule) => ({
      days: [...rule.querySelectorAll(".weekday-picker input:checked")].map((input) => input.value),
      times: [...rule.querySelectorAll(".schedule-times input")].map((input) => input.value).filter(Boolean)
    }));
  }

  function parseScheduleRules(serializedRules) {
    return (serializedRules || "1@09:00").split(";").filter(Boolean).map((serializedRule) => {
      const [days = "", times = ""] = serializedRule.split("@");
      return { days: days.split(",").filter(Boolean), times: times.split(",").filter(Boolean) };
    });
  }

  function serializeScheduleRules(rules) {
    return rules.map((rule) => `${rule.days.join(",")}@${rule.times.join(",")}`).join(";");
  }

  function formatScheduleRule(rule) {
    const fullNames = Object.fromEntries(scheduleDayNames.map(([value, , fullName]) => [value, fullName]));
    const shortNames = Object.fromEntries(scheduleDayNames.map(([value, shortName]) => [value, shortName.toLowerCase()]));
    const timeText = rule.times.join(" en ");
    if (rule.days.length === 7) return `Dagelijks om ${timeText}`;
    if (rule.days.length === 1) return `${fullNames[rule.days[0]][0].toUpperCase()}${fullNames[rule.days[0]].slice(1)} ${timeText}`;
    return `${rule.days.map((day) => shortNames[day]).join(", ")} om ${timeText}`;
  }

  function formatScheduleFromModal() {
    const mode = scheduleModal.querySelector(".schedule-mode").value;
    if (mode === "interval") {
      const minutes = Math.max(5, Number(scheduleModal.querySelector(".interval-input input").value) || 60);
      if (minutes === 60) return "Ieder uur";
      if (minutes % 60 === 0) return `Iedere ${minutes / 60} uur`;
      return `Iedere ${minutes} minuten`;
    }

    const rules = collectScheduleRules().filter((rule) => rule.days.length && rule.times.length);
    return rules.length ? rules.map(formatScheduleRule).join(" · ") : "Maak minimaal één complete regel";
  }

  function updateSchedulePreview() {
    scheduleModal.querySelector(".schedule-preview-text").textContent = formatScheduleFromModal();
    const enabled = scheduleModal.querySelector(".schedule-enabled input").checked;
    scheduleModal.querySelector(".schedule-enabled b").textContent = enabled ? "Ingeschakeld" : "Uitgeschakeld";
  }

  function configureScheduleModal(button) {
    const mode = button.dataset.scheduleMode;
    editingScheduleRow = button.closest("article");
    scheduleModal.querySelector("#schedule-modal-title").textContent = `${button.dataset.scheduleProcess} plannen`;
    setScheduleMode(mode);
    scheduleModal.querySelector(".schedule-enabled input").checked = editingScheduleRow.querySelector("[data-schedule-toggle]").checked;
    const ruleList = scheduleModal.querySelector(".schedule-rule-list");
    ruleList.replaceChildren(...parseScheduleRules(button.dataset.scheduleRules).map((rule) => createScheduleRule(rule.days, rule.times)));
    renumberScheduleRules();
    scheduleModal.querySelector(".interval-input input").value = button.dataset.scheduleInterval || "60";
    updateSchedulePreview();
  }

  document.addEventListener("click", (event) => {
    const viewTrigger = event.target.closest("[data-view]");
    if (viewTrigger) {
      openView(viewTrigger.dataset.view);
      return;
    }

    const entityTrigger = event.target.closest("[data-entity-id]");
    if (entityTrigger) {
      openDrawer(entityTrigger.dataset.entityId, entityTrigger);
      return;
    }

    const retryButton = event.target.closest(".retry-button");
    if (retryButton) {
      const item = retryButton.closest(".quality-work-row");
      if (item.classList.contains("retrying")) return;
      item.classList.add("retrying");
      item.querySelector(".work-copy small").textContent = "Klaargezet · kwaliteitssessie wordt gestart";
      item.querySelector(".status-chip").textContent = "Open";
      item.querySelector(".status-chip").className = "status-chip todo";
      retryButton.textContent = "Klaargezet";
      retryButton.disabled = true;
      showToast("Retry is klaargezet en de kwaliteitssessie wordt gestart.");
      return;
    }

    const actionButton = event.target.closest("[data-action]");
    if (!actionButton) return;
    switch (actionButton.dataset.action) {
      case "new-signal":
        openModal(signalModal, signalBackdrop, actionButton, signalModal.querySelector("textarea"));
        break;
      case "close-navigation":
        closeNavigation();
        break;
      case "show-quality-stats":
        openModal(statsModal, statsBackdrop, actionButton, statsModal.querySelector(".stats-modal-close"));
        break;
      case "run-design":
        startButton(actionButton, "Ontwerp gestart", "De Productontwerp-sessie is gestart.");
        break;
      case "run-planning":
        startButton(actionButton, "Planning gestart", "De Productplanning-sessie is gestart.");
        break;
      case "run-quality":
        startButton(actionButton, "Kwaliteit gestart", "De Kwaliteitsbewaking-sessie is gestart.");
        break;
      case "start-meeting":
        openView("meetings");
        showToast("Een nieuw stakeholderoverleg is klaargezet. Voeg een agenda toe om te beginnen.");
        break;
      case "join-meeting":
        showToast("Het overleg is geopend.");
        break;
      case "prioritize-epic":
        showToast("De prioriteitsreden wordt gevraagd en daarna als PlanningWorkItem klaargezet.");
        break;
      case "withdraw-epic":
        showToast("Na bevestiging en een verplichte reden wordt deze beschikbare epic ingetrokken.");
        break;
      case "cancel-epic":
        showToast("Na bevestiging en een verplichte reden wordt deze actieve epic geannuleerd.");
        break;
      case "meeting-message":
        showToast("Het bericht is aan het overleg toegevoegd.");
        break;
      case "close-meeting":
        showToast("Het overleg wordt afgesloten; de notulenagent verwerkt daarna iedere expliciete uitkomst.");
        break;
      case "memory-add":
        showToast("Nieuw geheugen vraagt inhoud en een verplichte wijzigingsreden.");
        break;
      case "memory-replace":
        showToast("De nieuwe versie wordt toegevoegd; de vorige versie blijft in de historie.");
        break;
      case "memory-retract":
        showToast("Intrekken vraagt een reden en verwijdert de eerdere versies niet.");
        break;
      case "memory-history":
        showToast("De versiegeschiedenis toont ook welke processessies iedere versie gebruikten.");
        break;
      case "memory-date":
        showToast("Kies een peildatum om de toen actieve geheugenset te reconstrueren.");
        break;
      case "save-ai-config":
        showToast("AI-instelling bewaard als configuratie v19; alleen nieuwe taken gebruiken haar.");
        break;
      case "save-product":
        showToast("De productopdracht is als nieuwe versie bewaard.");
        break;
      case "save-dispatch":
        showToast("De dispatchinstelling voor HKH is bewaard.");
        break;
      case "create-product":
        showToast("Een nieuw product krijgt eerst een naam, opdracht en testconfiguratie.");
        break;
      case "edit-test-config":
        showToast("De veilige routes en omgevingsgrenzen kunnen nu worden aangepast.");
        break;
      case "jump-settings": {
        const section = document.getElementById(actionButton.dataset.settingsTarget);
        if (section) section.scrollIntoView({ behavior: "smooth", block: "start" });
        break;
      }
      case "edit-schedule":
        configureScheduleModal(actionButton);
        openModal(scheduleModal, scheduleBackdrop, actionButton, scheduleModal.querySelector(".schedule-mode"));
        break;
      case "save-schedule": {
        const editButton = editingScheduleRow.querySelector('[data-action="edit-schedule"]');
        const enabled = scheduleModal.querySelector(".schedule-enabled input").checked;
        const mode = scheduleModal.querySelector(".schedule-mode").value;
        const rules = collectScheduleRules();
        const interval = Number(scheduleModal.querySelector(".interval-input input").value);
        if ((mode === "weekly" && (!rules.length || rules.some((rule) => !rule.days.length || !rule.times.length))) || (mode === "interval" && interval < 5)) {
          showToast(mode === "weekly" ? "Iedere regel heeft minimaal één dag en één tijd nodig." : "Het interval is minimaal 5 minuten.");
          break;
        }
        const moments = rules.flatMap((rule) => rule.days.flatMap((day) => rule.times.map((time) => `${day}@${time}`)));
        if (mode === "weekly" && new Set(moments).size !== moments.length) {
          showToast("Dezelfde dag en tijd staat meer dan één keer in het schema.");
          break;
        }
        editButton.dataset.scheduleMode = mode;
        editButton.dataset.scheduleRules = serializeScheduleRules(rules);
        editButton.dataset.scheduleInterval = interval;
        editingScheduleRow.querySelector("[data-schedule-toggle]").checked = enabled;
        editingScheduleRow.querySelector(".schedule-description strong").textContent = formatScheduleFromModal();
        editingScheduleRow.querySelector(".schedule-description small").textContent = `${mode === "interval" ? "Vast interval" : `${rules.length} ${rules.length === 1 ? "regel" : "regels"}`} · ${scheduleModal.querySelector(".schedule-timezone").value}`;
        editingScheduleRow.querySelector("time").textContent = enabled ? "Wordt berekend…" : "Niet gepland";
        closeModal(scheduleModal, scheduleBackdrop);
        showToast("Het schema is als nieuwe versie bewaard; de server berekent nu de volgende run.");
        break;
      }
      case "add-schedule-time": {
        const input = addScheduleTimeInput(actionButton.closest(".schedule-times"));
        updateSchedulePreview();
        input.focus();
        break;
      }
      case "add-schedule-rule": {
        const ruleList = scheduleModal.querySelector(".schedule-rule-list");
        const newRule = createScheduleRule();
        ruleList.append(newRule);
        renumberScheduleRules();
        updateSchedulePreview();
        newRule.querySelector(".weekday-picker input").focus();
        break;
      }
      case "remove-schedule-rule":
        actionButton.closest(".schedule-rule").remove();
        renumberScheduleRules();
        updateSchedulePreview();
        break;
      case "run-scheduled-process":
        startButton(actionButton, "Gestart", `${actionButton.dataset.processLabel} is handmatig gestart voor HKH.`);
        break;
      case "logout":
        closeDrawer();
        openModal(authModal, authBackdrop, actionButton, authModal.querySelector(".google-login"));
        break;
      case "login-google":
        closeModal(authModal, authBackdrop);
        showToast("Je Product Factory-sessie is actief.");
        break;
      case "check-version":
        showToast("Dit is de nieuwste beschikbare productiebuild.");
        break;
      case "reset-acceptance":
        showToast("Acceptatiedata wordt na bevestiging teruggezet naar HKH-MVP-04.");
        break;
      case "choose-scenario":
        showToast("Kies een vast, versieerbaar mockscenario.");
        break;
      case "mock-sleep":
      case "mock-resume":
      case "mock-complete":
      case "mock-cancel":
      case "mock-fail":
        showToast("De testbedactie is uitgevoerd; controleer nu de normale flow en historie.");
        break;
      default:
        break;
    }
  });

  document.querySelectorAll("[data-epic-filter]").forEach((button) => {
    button.addEventListener("click", () => {
      const filter = button.dataset.epicFilter;
      document.querySelectorAll("[data-epic-filter]").forEach((tab) => {
        const selected = tab === button;
        tab.classList.toggle("active", selected);
        tab.setAttribute("aria-pressed", String(selected));
      });
      document.querySelectorAll("[data-entity-group]").forEach((row) => {
        row.hidden = row.dataset.entityGroup !== filter;
      });
    });
  });

  document.querySelectorAll("[data-quality-filter]").forEach((button) => {
    button.addEventListener("click", () => {
      const filter = button.dataset.qualityFilter;
      document.querySelectorAll("[data-quality-filter]").forEach((tab) => {
        const selected = tab === button;
        tab.classList.toggle("active", selected);
        tab.setAttribute("aria-pressed", String(selected));
      });
      document.querySelectorAll("[data-work-group]").forEach((row) => {
        row.hidden = row.dataset.workGroup !== filter;
      });
    });
  });

  document.querySelectorAll("[data-planning-filter]").forEach((button) => {
    button.addEventListener("click", () => {
      const value = button.dataset.planningFilter;
      document.querySelectorAll("[data-planning-filter]").forEach((tab) => {
        const selected = tab.dataset.planningFilter === value;
        tab.classList.toggle("active", selected);
        tab.setAttribute("aria-pressed", String(selected));
      });
      document.querySelectorAll("[data-planning-panel]").forEach((panel) => {
        panel.hidden = panel.dataset.planningPanel !== value;
      });
    });
  });

  document.querySelectorAll("[data-quality-section]").forEach((button) => {
    button.addEventListener("click", () => {
      const value = button.dataset.qualitySection;
      document.querySelectorAll("[data-quality-section]").forEach((tab) => {
        const selected = tab.dataset.qualitySection === value;
        tab.classList.toggle("active", selected);
        tab.setAttribute("aria-pressed", String(selected));
      });
      document.querySelectorAll("[data-quality-panel]").forEach((panel) => {
        panel.hidden = panel.dataset.qualityPanel !== value;
      });
    });
  });

  document.querySelectorAll("[data-operation-section]").forEach((button) => {
    button.addEventListener("click", () => {
      const value = button.dataset.operationSection;
      document.querySelectorAll("[data-operation-section]").forEach((tab) => {
        const selected = tab.dataset.operationSection === value;
        tab.classList.toggle("active", selected);
        tab.setAttribute("aria-pressed", String(selected));
      });
      document.querySelectorAll("[data-operation-panel]").forEach((panel) => {
        panel.hidden = panel.dataset.operationPanel !== value;
      });
    });
  });

  document.querySelectorAll("[data-decision-filter]").forEach((button) => {
    button.addEventListener("click", () => {
      const value = button.dataset.decisionFilter;
      document.querySelectorAll("[data-decision-filter]").forEach((tab) => {
        const selected = tab.dataset.decisionFilter === value;
        tab.classList.toggle("active", selected);
        tab.setAttribute("aria-pressed", String(selected));
      });
      document.querySelectorAll("[data-decision-panel]").forEach((panel) => {
        panel.hidden = panel.dataset.decisionPanel !== value;
      });
    });
  });

  document.querySelectorAll("[data-memory-role]").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll("[data-memory-role]").forEach((role) => role.classList.toggle("active", role === button));
      if (button.dataset.memoryRole !== "designer") showToast("UX-demo: deze rol krijgt dezelfde actuele, historische en wijzigingsweergave.");
    });
  });

  document.querySelectorAll(".process-run").forEach((button) => {
    button.addEventListener("click", () => {
      const processName = button.closest(".process-card").querySelector("h2").textContent;
      startButton(button, "Gestart", `${processName} is handmatig gestart.`);
    });
  });

  menuButton.addEventListener("click", () => {
    const open = body.classList.toggle("nav-open");
    menuButton.setAttribute("aria-expanded", String(open));
    menuButton.setAttribute("aria-label", open ? "Menu sluiten" : "Menu openen");
  });

  document.addEventListener("click", (event) => {
    if (body.classList.contains("nav-open") && !event.target.closest(".sidebar") && !event.target.closest(".menu-button")) {
      closeNavigation();
    }
  });

  drawerClose.addEventListener("click", closeDrawer);
  drawerBackdrop.addEventListener("click", closeDrawer);

  signalModal.querySelector(".signal-modal-close").addEventListener("click", () => closeModal(signalModal, signalBackdrop));
  signalModal.querySelector(".signal-modal-cancel").addEventListener("click", () => closeModal(signalModal, signalBackdrop));
  signalBackdrop.addEventListener("click", () => closeModal(signalModal, signalBackdrop));
  signalModal.querySelector(".signal-modal-confirm").addEventListener("click", () => {
    const textarea = signalModal.querySelector("textarea");
    const hasText = textarea.value.trim().length > 0;
    closeModal(signalModal, signalBackdrop);
    showToast(hasText ? "Het signaal is toegevoegd." : "UX-demo: het signaalformulier is gesloten.");
    textarea.value = "";
  });

  statsModal.querySelector(".stats-modal-close").addEventListener("click", () => closeModal(statsModal, statsBackdrop));
  statsBackdrop.addEventListener("click", () => closeModal(statsModal, statsBackdrop));

  scheduleModal.querySelector(".schedule-modal-close").addEventListener("click", () => closeModal(scheduleModal, scheduleBackdrop));
  scheduleModal.querySelector(".schedule-modal-cancel").addEventListener("click", () => closeModal(scheduleModal, scheduleBackdrop));
  scheduleBackdrop.addEventListener("click", () => closeModal(scheduleModal, scheduleBackdrop));
  scheduleModal.querySelector(".schedule-mode").addEventListener("change", (event) => {
    setScheduleMode(event.target.value);
    updateSchedulePreview();
  });
  scheduleModal.addEventListener("input", updateSchedulePreview);
  scheduleModal.addEventListener("change", updateSchedulePreview);

  document.querySelectorAll("[data-schedule-toggle]").forEach((toggle) => {
    toggle.addEventListener("change", () => {
      const scheduleRow = toggle.closest("article");
      const processName = scheduleRow.querySelector(".schedule-process strong").textContent;
      scheduleRow.querySelector("time").textContent = toggle.checked ? "Wordt berekend…" : "Niet gepland";
      showToast(`${processName}: automatische starts zijn ${toggle.checked ? "ingeschakeld" : "uitgeschakeld"}. Handmatig starten blijft mogelijk.`);
    });
  });

  document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape") return;
    if (!signalModal.hidden) closeModal(signalModal, signalBackdrop);
    else if (!statsModal.hidden) closeModal(statsModal, statsBackdrop);
    else if (!scheduleModal.hidden) closeModal(scheduleModal, scheduleBackdrop);
    else if (!authModal.hidden) return;
    else if (drawer.classList.contains("open")) closeDrawer();
    else closeNavigation();
  });

  document.querySelectorAll(".period-button, .decision-list button, .meeting-list button").forEach((button) => {
    button.addEventListener("click", () => showToast("Dit detail hoort bij een volgende uitwerking van het UX-concept."));
  });
})();
