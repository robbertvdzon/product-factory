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
  const toast = document.querySelector(".toast");
  let returnFocus = null;
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
        <section><h3>Succes</h3><p>Gebruikers begrijpen of hun aanvraag is verzonden en kunnen zonder dubbele aanvraag herstellen.</p></section>`
    },
    "EP-016": {
      status: "Beschikbaar",
      statusClass: "available",
      reference: "Epic EP-016 · versie 1",
      title: "Documenten begrijpelijk vergelijken",
      lead: "Mensen zien zonder vakkennis welk document bij hun situatie past.",
      body: `<section><h3>Gebruikersverbetering</h3><p>De verschillen worden uitgelegd vanuit de vraag van de bezoeker, niet vanuit interne documentnamen.</p></section><section><h3>UX-ontwerp</h3><p>Een rustige vergelijking met maximaal drie relevante verschillen en een duidelijke vervolgstap.</p></section><section><h3>Status</h3><p>Productplanning heeft deze epic nog niet opgepakt. Productontwerp mag hem dus nog verbeteren.</p></section>`
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
    "US-044": {
      status: "Open",
      statusClass: "open",
      reference: "Signaal US-044 · klantgesprek",
      title: "Formulier voelt onduidelijk op mobiel",
      lead: "“Ik wist niet of mijn aanvraag al verzonden was.”",
      body: `<section><h3>Oorspronkelijke melding</h3><p>Op mijn telefoon bleef ik twijfelen of ik nog een keer moest drukken. Ik wilde mijn gegevens niet dubbel insturen.</p></section><section><h3>Verwerking</h3><p>Dit signaal is nog niet opgepakt. De oorspronkelijke tekst kan niet worden aangepast.</p></section>`
    },
    "US-039": {
      status: "In behandeling",
      statusClass: "review",
      reference: "Signaal US-039 · support",
      title: "Bevestigingsmail soms dubbel",
      lead: "Drie vergelijkbare meldingen in de afgelopen week.",
      body: `<section><h3>Verwerking</h3><p>Kwaliteitsbewaking onderzoekt dit signaal via QualityWorkItem QW-063. Het resultaat wordt hier zichtbaar zodra de controle klaar is.</p></section>`
    },
    "US-031": {
      status: "Verwerkt",
      statusClass: "processed",
      reference: "Signaal US-031 · overleg M-18",
      title: "Meer aandacht voor begrijpelijke taal",
      lead: "De Stakeholder wil vaktermen structureel vermijden.",
      body: `<section><h3>Uitkomst</h3><p>Het signaal is gekoppeld aan epic EP-014. De blijvende taalrichting is daarnaast als apart Stakeholderbesluit vastgelegd.</p></section>`
    }
  };

  function updateOverlayState() {
    const anyOpen = drawer.classList.contains("open") || !signalModal.hidden || !statsModal.hidden;
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
    navItems.forEach((item) => item.classList.toggle("active", item.dataset.view === viewName));
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
        showToast("Een nieuw stakeholderoverleg is klaargezet.");
        break;
      case "join-meeting":
        showToast("Het overleg is geopend.");
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

  document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape") return;
    if (!signalModal.hidden) closeModal(signalModal, signalBackdrop);
    else if (!statsModal.hidden) closeModal(statsModal, statsBackdrop);
    else if (drawer.classList.contains("open")) closeDrawer();
    else closeNavigation();
  });

  document.querySelectorAll(".period-button, .decision-list button, .meeting-list button").forEach((button) => {
    button.addEventListener("click", () => showToast("Dit detail hoort bij een volgende uitwerking van het UX-concept."));
  });
})();
