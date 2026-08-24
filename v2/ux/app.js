(() => {
  const body = document.body;
  const views = [...document.querySelectorAll(".view")];
  const navItems = [...document.querySelectorAll(".nav-item[data-view]")];
  const menuButton = document.querySelector(".menu-button");
  const drawer = document.querySelector(".story-drawer");
  const drawerBackdrop = document.querySelector(".drawer-backdrop");
  const drawerClose = document.querySelector(".drawer-close");
  const modal = document.querySelector(".modal");
  const modalBackdrop = document.querySelector(".modal-backdrop");
  const modalClose = document.querySelector(".modal-close");
  const modalCancel = document.querySelector(".modal-cancel");
  const modalConfirm = document.querySelector(".modal-confirm");
  const modalTextarea = modal.querySelector("textarea");
  const toast = document.querySelector(".toast");
  let returnFocus = null;
  let toastTimeout;

  const storyContent = {
    "ST-087": {
      state: "In uitvoering",
      stateClass: "progress",
      title: "Bevestiging na verzenden verbeteren",
      lead: "Na verzenden weet de bezoeker direct dat de aanvraag veilig is ontvangen.",
      result: "De bezoeker hoeft niet te twijfelen of opnieuw te verzenden en ziet wat de volgende stap is."
    },
    "ST-088": {
      state: "Te doen",
      stateClass: "todo",
      title: "Foutmelding met herstelactie",
      lead: "Als verzenden niet lukt, ziet de bezoeker wat er gebeurde en kan die veilig opnieuw proberen.",
      result: "De bezoeker verliest geen ingevulde gegevens en weet dat de aanvraag nog niet is verzonden."
    },
    "ST-091": {
      state: "Wordt verstuurd",
      stateClass: "reserved",
      title: "Focus behouden na terugnavigeren",
      lead: "Na terugnavigeren komt de bezoeker terug op de logische plek in het formulier.",
      result: "De mobiele route blijft rustig en voorspelbaar, ook bij gebruik met toetsenbord of schermlezer."
    },
    "ST-096": {
      state: "Te doen",
      stateClass: "todo",
      title: "Herstel dubbele bevestigingsmail",
      lead: "Eén geslaagde aanvraag veroorzaakt precies één bevestigingsmail.",
      result: "De bezoeker ontvangt geen verwarrende dubbele bevestiging en de aanvraag zelf blijft ongewijzigd."
    },
    "ST-102": {
      state: "Wacht",
      stateClass: "waiting",
      title: "Voortgang veilig bewaren",
      lead: "Een bezoeker kan later verdergaan met een nog niet afgeronde aanvraag.",
      result: "De voortgang is veilig terug te vinden zonder dat onvolledige gegevens worden verwerkt."
    }
  };

  function setOverlayState() {
    const overlayOpen = drawer.classList.contains("open") || !modal.hidden;
    body.classList.toggle("overlay-open", overlayOpen);
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

  function openDrawer(storyId, trigger) {
    const content = storyContent[storyId] || storyContent["ST-088"];
    const chip = drawer.querySelector(".status-chip");
    chip.className = `status-chip ${content.stateClass}`;
    chip.textContent = content.state;
    drawer.querySelector(".drawer-header p").textContent = `Story ${storyId} · EP-014`;
    drawer.querySelector("#story-drawer-title").textContent = content.title;
    drawer.querySelector(".lead").textContent = content.lead;
    drawer.querySelector(".drawer-content section p").textContent = content.result;

    returnFocus = trigger;
    drawerBackdrop.hidden = false;
    drawer.classList.add("open");
    drawer.setAttribute("aria-hidden", "false");
    setOverlayState();
    drawerClose.focus();
  }

  function closeDrawer() {
    if (!drawer.classList.contains("open")) return;
    drawer.classList.remove("open");
    drawer.setAttribute("aria-hidden", "true");
    window.setTimeout(() => {
      drawerBackdrop.hidden = true;
      setOverlayState();
    }, 230);
    if (returnFocus) returnFocus.focus();
  }

  function openModal(trigger) {
    returnFocus = trigger;
    modal.hidden = false;
    modalBackdrop.hidden = false;
    setOverlayState();
    modalTextarea.focus();
  }

  function closeModal() {
    if (modal.hidden) return;
    modal.hidden = true;
    modalBackdrop.hidden = true;
    setOverlayState();
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

    const storyRow = event.target.closest("[data-story]");
    if (storyRow) {
      openDrawer(storyRow.dataset.story, storyRow);
      return;
    }

    const actionButton = event.target.closest("[data-action]");
    if (!actionButton) return;

    switch (actionButton.dataset.action) {
      case "new-signal":
        openModal(actionButton);
        break;
      case "run-planning":
        startButton(actionButton, "Planning gestart", "De planningssessie is gestart.");
        break;
      case "run-quality":
        startButton(actionButton, "Kwaliteit gestart", "De kwaliteitssessie is gestart.");
        break;
      case "prioritize": {
        document.querySelectorAll('[data-action="prioritize"]').forEach((button) => {
          button.textContent = "Voorrang geven";
          button.classList.remove("primary");
        });
        actionButton.textContent = "Heeft voorrang";
        actionButton.classList.add("primary");
        showToast("De epic staat bovenaan. De lopende story blijft ongewijzigd.");
        break;
      }
      case "start-meeting":
        showToast("Een nieuw stakeholderoverleg is klaargezet.");
        break;
      case "join-meeting":
        showToast("Het overleg is geopend. Notulen en besluiten verschijnen hierna.");
        break;
      case "investigate":
        startButton(actionButton, "Aangevraagd", "Kwaliteitsonderzoek is aan de inbox gekoppeld.");
        break;
      case "open-epic":
        showToast("In de echte applicatie opent hier het epicdetail.");
        break;
      default:
        break;
    }
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

  document.querySelectorAll(".retry-button").forEach((button) => {
    button.addEventListener("click", () => {
      const item = button.closest(".retry-item");
      if (item.classList.contains("retrying")) return;
      item.classList.add("retrying");
      const statusText = item.querySelector("small");
      statusText.textContent = "Klaargezet · kwaliteitssessie wordt gestart";
      button.textContent = "Klaargezet";
      button.disabled = true;

      const remaining = document.querySelectorAll(".retry-item:not(.retrying)").length;
      const qualityCount = document.querySelector('.nav-item[data-view="quality"] .nav-count');
      if (qualityCount) qualityCount.textContent = String(remaining);
      showToast("Retry is direct klaargezet en de kwaliteitssessie wordt gestart.");
    });
  });

  document.querySelectorAll(".process-run").forEach((button) => {
    button.addEventListener("click", () => {
      const processName = button.closest(".process-card").querySelector("h2").textContent;
      startButton(button, "Gestart", `${processName} is handmatig gestart.`);
    });
  });

  document.querySelectorAll(".signal-row").forEach((row) => {
    row.addEventListener("click", () => {
      document.querySelectorAll(".signal-row").forEach((item) => item.classList.remove("selected"));
      row.classList.add("selected");
      if (!row.matches(":first-child")) {
        showToast("In de echte applicatie wordt rechts het gekozen signaal getoond.");
      }
    });
  });

  drawerClose.addEventListener("click", closeDrawer);
  drawerBackdrop.addEventListener("click", closeDrawer);
  modalClose.addEventListener("click", closeModal);
  modalCancel.addEventListener("click", closeModal);
  modalBackdrop.addEventListener("click", closeModal);
  modalConfirm.addEventListener("click", () => {
    const hasText = modalTextarea.value.trim().length > 0;
    closeModal();
    showToast(hasText ? "Het signaal is aan de inbox toegevoegd." : "UX-demo: het signaalformulier is gesloten.");
    modalTextarea.value = "";
  });

  document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape") return;
    if (!modal.hidden) closeModal();
    else if (drawer.classList.contains("open")) closeDrawer();
    else closeNavigation();
  });

  document.querySelectorAll(".period-button, .decision-list button, .simple-list button, .meeting-list button, .factory-status + .text-button").forEach((button) => {
    button.addEventListener("click", () => showToast("Dit detail hoort bij een volgende uitwerking van het UX-concept."));
  });
})();
