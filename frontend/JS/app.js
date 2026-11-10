"use strict";

function loadSavedUser() {
  const savedUser = localStorage.getItem("bagorithm-user");
  if (!savedUser) return null;
  try {
    return JSON.parse(savedUser);
  } catch (error) {
    console.error("Could not read the saved BAGORITHM user:", error);
    localStorage.removeItem("bagorithm-user");
    localStorage.removeItem("bagorithm-token");
    return null;
  }
}

const state = {
  token: localStorage.getItem("bagorithm-token") || "",
  user: loadSavedUser(),
  trips: [],
  items: [],
  selectedTripId: localStorage.getItem("bagorithm-trip") || "",
  weightLimit: null,
  recommendations: [],
  recommendationGenerated: false,
  recommendationInputs: { destination: "", weather: "", activity: "" },
  recommendationSort: { field: "source", direction: "asc" },
  recommendationLoading: false,
  recommendationRequestId: 0,
  recommendationError: "",
  pendingRecommendations: new Set(),
  selectedSmartPicks: new Set(),
  smartPicksAdded: false,
  outfitPlans: [],
  outfitPlan: null,
  outfitPlanSaved: false,
  outfitPlanLoading: false,
  outfitPlanError: "",
  outfitPlannerTripId: "",
  outfitPlannerInputs: { destination: "", weather: "", days: 1, activities: ["Sightseeing"] },
  selectedOutfitItemIds: new Set(),
  page: "dashboard",
  search: "",
  authMode: "login",
  editingTripId: "",
  editingItemId: "",
  toastTimer: null
};

const pages = {
  dashboard: "Overview",
  trips: "My trips",
  previous: "Previous trips",
  recommendations: "Smart Picks",
  outfits: "Day-wise Outfit Planner",
  checklist: "Packing checklist",
  shopping: "Shopping list",
  budget: "Budget & weight"
};

const categoryLabels = {
  PACKING: "Packing item",
  CLOTHING: "Clothing",
  SHOPPING: "Shopping"
};

const itemIcons = {
  PACKING: "☷",
  CLOTHING: "◇",
  SHOPPING: "▢"
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`/api/${path}`, { ...options, headers });
  let result = {};
  try {
    result = await response.json();
  } catch {
    if (response.ok) throw new Error("The server returned an unreadable response.");
  }
  if (!response.ok) {
    if (response.status === 401 && path !== "logout") {
      state.token = "";
      state.user = null;
      state.trips = [];
      state.items = [];
      state.selectedTripId = "";
      localStorage.removeItem("bagorithm-token");
      localStorage.removeItem("bagorithm-user");
      localStorage.removeItem("bagorithm-trip");
      showAuth();
      setFormError("#auth-message", result.error || "Please sign in again.");
    }
    throw new Error(result.error || "Something went wrong. Please try again.");
  }
  return result;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[character]);
}

function amount(value) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(Number(value) || 0);
}

function formatDate(value) {
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "numeric" })
    .format(new Date(year, month - 1, day));
}

function todayDateString() {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
}

function isPastTrip(trip) {
  const lastDay = new Date(`${trip.startDate}T00:00:00`);
  lastDay.setDate(lastDay.getDate() + trip.days - 1);
  return lastDay < new Date(`${todayDateString()}T00:00:00`);
}

function tripDateLabel(trip) {
  return `${formatDate(trip.startDate)} · ${trip.days} ${trip.days === 1 ? "day" : "days"}`;
}

function currentTrip() {
  return state.trips.find((trip) => trip.id === state.selectedTripId) || null;
}

function setToast(message, isError = false) {
  const toast = $("#toast");
  toast.textContent = message;
  toast.classList.toggle("error", isError);
  toast.classList.remove("hidden");
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => toast.classList.add("hidden"), 3500);
}

function setFormError(selector, message = "") {
  const element = $(selector);
  element.textContent = message;
  element.classList.toggle("hidden", !message);
}

function showAuth() {
  $("#auth-screen").classList.remove("hidden");
  $("#app-shell").classList.add("hidden");
  updateAuthForm();
}

function updateAuthForm() {
  const registering = state.authMode === "register";
  $("#auth-eyebrow").textContent = registering ? "YOUR NEXT ADVENTURE AWAITS" : "WELCOME ABOARD";
  $("#auth-title").textContent = registering ? "Create your account" : "Sign in to your trips";
  $("#auth-subtitle").textContent = registering
    ? "Start planning trips worth remembering."
    : "A little planning goes a long way.";
  $("#name-field").classList.toggle("hidden", !registering);
  $("#auth-name").required = registering;
  $("#auth-password").autocomplete = registering ? "new-password" : "current-password";
  $("#auth-submit").innerHTML = registering ? "Create account <span>→</span>" : "Sign in <span>→</span>";
  $("#auth-switch-copy").textContent = registering ? "Already have an account?" : "New around here?";
  $("#auth-switch").textContent = registering ? "Sign in" : "Create an account";
  setFormError("#auth-message");
}

function showApplication() {
  $("#auth-screen").classList.add("hidden");
  $("#app-shell").classList.remove("hidden");
  $("#user-name").textContent = state.user?.name || "Traveler";
  $("#user-email").textContent = state.user?.email || "";
  $("#user-avatar").textContent = (state.user?.name || "A").trim().charAt(0).toUpperCase();
  $("#welcome-heading").textContent = `Welcome back, ${(state.user?.name || "traveler").split(/\s+/)[0]}`;
  render();
}

async function loadTrips() {
  state.trips = await api("trips");
  if (!state.trips.some((trip) => trip.id === state.selectedTripId)) {
    const nextTrip = [...state.trips].filter((trip) => !isPastTrip(trip))
      .sort((a, b) => a.startDate.localeCompare(b.startDate))[0];
    state.selectedTripId = (nextTrip || state.trips[0])?.id || "";
  }
  if (state.selectedTripId) {
    localStorage.setItem("bagorithm-trip", state.selectedTripId);
    const tripId = encodeURIComponent(state.selectedTripId);
    const [items, settings, plans] = await Promise.all([
      loadSelectedTripItems(state.selectedTripId),
      api(`packing-limit?tripId=${tripId}`),
      api(`outfit-plans?tripId=${tripId}`)
    ]);
    state.items = items;
    state.weightLimit = settings.weightLimit;
    state.outfitPlans = plans;
    if (state.outfitPlannerTripId !== state.selectedTripId) resetOutfitPlanner(currentTrip());
  } else {
    localStorage.removeItem("bagorithm-trip");
    state.items = [];
    state.weightLimit = null;
    state.outfitPlans = [];
    state.outfitPlan = null;
    resetOutfitPlanner(null);
  }
}

async function loadSelectedTripItems(tripId = state.selectedTripId) {
  if (!tripId) return [];
  const items = await api(`items?tripId=${encodeURIComponent(tripId)}`);
  if (!Array.isArray(items)) throw new Error("The packing-items endpoint returned an invalid list.");
  return items;
}

async function refresh() {
  const previousTripId = state.selectedTripId;
  await loadTrips();
  if (state.selectedTripId !== previousTripId) {
    state.recommendationRequestId++;
    state.recommendationLoading = false;
    state.recommendationError = "";
    state.recommendations = [];
    state.selectedSmartPicks = new Set();
    state.smartPicksAdded = false;
    state.recommendationGenerated = false;
    state.recommendationInputs = { destination: "", weather: "", activity: "" };
  }
  render();
  if (state.page === "recommendations") await renderRecommendations(currentTrip());
}

function render() {
  const trip = currentTrip();
  $("#trip-select").innerHTML = state.trips.length
    ? state.trips.map((entry) => `<option value="${escapeHtml(entry.id)}">${escapeHtml(entry.name || entry.destination)} · ${escapeHtml(entry.destination)} · ${formatDate(entry.startDate)}</option>`).join("")
    : '<option value="">No trips yet</option>';
  $("#trip-select").value = state.selectedTripId;
  $("#trip-select").disabled = state.trips.length === 0;
  $("#breadcrumb-current").textContent = pages[state.page] || "Overview";
  $$("[data-page-link]").forEach((link) => link.classList.toggle("active", link.dataset.pageLink === state.page));
  $$(".page-view").forEach((view) => view.classList.toggle("active", view.dataset.page === state.page));
  renderDashboard(trip);
  renderTrips();
  renderPreviousTrips();
  renderItemsPage("checklist", state.items.filter((item) => item.category !== "SHOPPING"));
  renderItemsPage("shopping", state.items.filter((item) => item.category === "SHOPPING"));
  renderBudget(trip);
  renderDayPlanner(trip);
}

function renderDashboard(trip) {
  const host = $("#dashboard-content");
  const upcoming = [...state.trips].filter((entry) => !isPastTrip(entry))
    .sort((a, b) => a.startDate.localeCompare(b.startDate)).slice(0, 4);
  if (!trip) {
    host.innerHTML = `${emptyState("◷", "Your next trip starts here", "Add a destination and we’ll get your packing plan started.", "Plan a trip", "new-trip")}
      <div class="dashboard-feature-grid">${renderPackingScore(null)}${renderOutfitPlannerCard(null)}</div>
      ${state.trips.length ? `<div class="card section-card"><div class="card-heading"><h3>Your saved trips</h3><button class="text-link" data-page-link="trips">View all →</button></div>${tripRows(state.trips.slice(0, 4))}</div>` : ""}`;
    return;
  }

  const packingItems = state.items.filter((item) => item.category !== "SHOPPING");
  const packedCount = packingItems.filter((item) => item.packed).length;
  const progress = packingItems.length ? Math.round((packedCount / packingItems.length) * 100) : 0;
  const packedWeight = packingItems.filter((item) => item.packed)
    .reduce((total, item) => total + Number(item.totalWeight || 0), 0);
  const purchasedCost = state.items.filter((item) => item.category === "SHOPPING" && item.purchased)
    .reduce((total, item) => total + Number(item.totalPrice || 0), 0);
  const totalWeight = packingItems.reduce((total, item) => total + item.totalWeight, 0);
  const remaining = trip.budget - purchasedCost;
  const luggageRemaining = state.weightLimit === null ? null : state.weightLimit - packedWeight;
  host.innerHTML = `
    <div class="hero-card"><div class="hero-copy"><span class="eyebrow">${escapeHtml(trip.name || trip.destination)}</span><h2>${escapeHtml(trip.destination)} is on the horizon.</h2><p>${tripDateLabel(trip)} · ${amount(packedWeight)} g packed of ${state.weightLimit === null ? "no limit set" : `${amount(state.weightLimit)} g`}</p></div><button class="button" data-page-link="checklist">Open checklist <span>→</span></button></div>
    ${luggageRemaining !== null && luggageRemaining < 0 ? `<div class="weight-warning" role="alert">Packed weight exceeds the luggage limit by ${amount(Math.abs(luggageRemaining))} g.</div>` : ""}
    <div class="dashboard-feature-grid">${renderPackingScore(trip)}${renderOutfitPlannerCard(trip)}</div>
    <div class="metric-grid">
      ${metricCard("Packing progress", `${progress}%`, `${packedCount} of ${packingItems.length} items packed`, "☷")}
      ${metricCard("Amount spent", amount(purchasedCost), `of ${amount(trip.budget)} budget`, "＄")}
      ${metricCard("Packed weight", `${amount(packedWeight)} g`, state.weightLimit === null ? "luggage limit not set" : `${amount(Math.max(0, luggageRemaining))} g capacity remaining`, "◇")}
      ${metricCard("Budget left", amount(remaining), remaining < 0 ? "over your planned budget" : "after listed items", "↗")}
    </div>
    <div class="content-grid">
      <div class="card"><div class="card-heading"><h3>Upcoming trips</h3><button class="text-link" data-page-link="trips">All trips →</button></div>
        ${upcoming.length ? tripRows(upcoming) : '<div class="muted" style="font-size:11px;padding:12px 0">No upcoming trips. You can still update this packing list.</div>'}
      </div>
      <div class="card"><div class="card-heading"><h3>Packing progress</h3><span class="muted">${packedCount}/${packingItems.length} packed</span></div>
        <div class="progress-track"><div class="progress-fill" style="width:${progress}%"></div></div><div class="progress-label"><span>Ready for take-off</span><strong>${progress}%</strong></div>
        <div class="quick-list">
          <button class="quick-action" data-page-link="recommendations"><span>✳</span>Get a packing head start</button>
          <button class="quick-action" data-page-link="shopping"><span>▢</span>Check your shopping list</button>
          <button class="quick-action" data-page-link="budget"><span>↗</span>Review budget and weight</button>
        </div>
      </div>
    </div>`;
}

function renderPackingScore(trip) {
  const items = state.items.filter((item) => item.category !== "SHOPPING");
  if (!trip || !items.length) {
    return `<article class="card score-card score-empty"><div class="card-heading"><div><span class="eyebrow">SMART PACKING SCORE</span><h3>No packing items yet</h3></div><span class="metric-icon">◉</span></div>
      <p class="muted">Add items to your packing checklist to calculate a score from your saved list.</p>
      ${trip ? renderWeightLimitForm("dashboard-weight-limit-input") : ""}<button class="button button-primary button-small" data-page-link="checklist">Open packing checklist →</button></article>`;
  }
  const packedItems = items.filter((item) => item.packed);
  const completionPoints = Math.round((packedItems.length / items.length) * 80);
  const packedWeight = packedItems.reduce((total, item) => total + Number(item.totalWeight || 0), 0);
  const weightPoints = state.weightLimit !== null && packedWeight <= state.weightLimit ? 20 : 0;
  const score = Math.max(0, Math.min(100, completionPoints + weightPoints));
  const status = score >= 90 ? "Excellent packing!" : score >= 70 ? "Almost ready!"
    : score >= 40 ? "Keep packing!" : "More preparation needed!";
  return `<article class="card score-card"><div class="score-card-main">
      <div class="score-ring" style="--score:${score}" role="img" aria-label="Smart Packing Score ${score} out of 100"><span><strong>${score}</strong><small>/ 100</small></span></div>
      <div class="score-summary"><span class="eyebrow">SMART PACKING SCORE</span><h3>${status}</h3><p class="muted">${score}% prepared for ${escapeHtml(trip.destination)}.</p>
        <div class="score-progress-track"><span style="width:${score}%"></span></div>
        <small>${packedItems.length} items packed · ${items.length - packedItems.length} remaining</small>
      </div>
    </div>
    <div class="score-details"><div><span>Packing checklist</span><strong>${packedItems.length} of ${items.length} packed</strong></div><div><span>Packed bag weight</span><strong>${amount(packedWeight)} g <small>/ ${state.weightLimit === null ? "Not set" : `${amount(state.weightLimit)} g`}</small></strong></div></div>
    ${state.weightLimit !== null && packedWeight > state.weightLimit ? `<div class="weight-warning" role="alert">Your packed weight is ${amount(packedWeight - state.weightLimit)} g over the limit.</div>` : ""}
    ${renderWeightLimitForm("dashboard-weight-limit-input")}</article>`;
}

function renderWeightLimitForm(inputId) {
  return `<form class="weight-limit-form" data-weight-limit-form><label class="form-field">Maximum luggage weight (grams) · ${state.weightLimit === null ? "Not set" : `${amount(state.weightLimit)} g saved`}
    <input id="${inputId}" name="weightLimit" type="number" min="1" max="100000" step="1" value="${state.weightLimit === null ? "" : escapeHtml(state.weightLimit)}" placeholder="Enter limit" required></label>
    <button class="button button-quiet button-small" type="submit">Save limit</button></form>`;
}

function renderOutfitPlannerCard(trip) {
  return `<article class="card dashboard-planner-card"><div class="planner-card-icon">✧</div><span class="eyebrow">DAY-WISE OUTFIT PLANNER</span>
    <h3>${trip ? `${trip.days}-day outfit plan` : "Plan an outfit for every day"}</h3>
    <p class="muted">${trip ? `Create a reusable clothing plan for ${escapeHtml(trip.destination)} and estimate its weight.` : "Choose a trip, activities and weather to build outfits that work together."}</p>
    <button class="button button-primary button-small" data-page-link="outfits">Plan daily outfits →</button></article>`;
}

function metricCard(label, value, hint, icon) {
  return `<div class="metric-card"><div class="metric-top"><span>${label}</span><span class="metric-icon">${icon}</span></div><strong class="metric-value">${escapeHtml(value)}</strong><small class="metric-hint">${escapeHtml(hint)}</small></div>`;
}

function tripRows(trips) {
  return trips.map((trip) => `<div class="trip-row"><span class="trip-initial">${escapeHtml((trip.name || trip.destination).trim().charAt(0).toUpperCase())}</span><div class="trip-row-copy"><strong>${escapeHtml(trip.name || trip.destination)}</strong><small>${escapeHtml(trip.destination)} · ${tripDateLabel(trip)}</small></div><span class="trip-row-side">${trip.weightLimit == null ? "Limit not set" : `${amount(trip.weightLimit)} g limit`}</span></div>`).join("");
}

function renderTrips() {
  const host = $("#trips-content");
  if (!state.trips.length) {
    host.innerHTML = emptyState("◷", "No trips planned just yet", "Create a trip to start building your packing list.", "Plan a trip", "new-trip");
    return;
  }
  host.innerHTML = `<div class="trip-card-grid">${state.trips.map((trip) => {
    const selected = trip.id === state.selectedTripId;
    const itemCount = selected ? state.items.filter((item) => item.tripId === trip.id).length : "—";
    return `<article class="trip-card"><div class="trip-card-top"><div><h3>${escapeHtml(trip.name || trip.destination)}</h3><small>${escapeHtml(trip.destination)} · ${tripDateLabel(trip)}</small></div>${selected ? '<span class="item-category">SELECTED</span>' : ""}</div>
      <div class="trip-card-stats"><span>${trip.days}<small>trip length</small></span><span>${amount(trip.budget)}<small>trip budget</small></span><span>${trip.weightLimit == null ? "Not set" : `${amount(trip.weightLimit)} g`}<small>luggage limit</small></span></div>
      <div class="trip-card-footer"><button class="button ${selected ? "button-quiet" : "button-primary"}" data-action="select-trip" data-id="${escapeHtml(trip.id)}">${selected ? "Selected ✓" : "Open trip →"}</button><span class="trip-card-tools"><button class="icon-button" data-action="edit-trip" data-id="${escapeHtml(trip.id)}" aria-label="Edit trip">✎</button><button class="icon-button danger" data-action="delete-trip" data-id="${escapeHtml(trip.id)}" aria-label="Delete trip">⌫</button></span></div></article>`;
  }).join("")}</div>`;
}

function renderPreviousTrips() {
  const previous = [...state.trips].filter(isPastTrip)
    .sort((a, b) => b.startDate.localeCompare(a.startDate));
  $("#previous-content").innerHTML = previous.length
    ? `<div class="trip-card-grid">${previous.map((trip) => `<article class="trip-card"><div class="trip-card-top"><div><h3>${escapeHtml(trip.name || trip.destination)}</h3><small>${escapeHtml(trip.destination)} · ${tripDateLabel(trip)}</small></div><span class="item-category">PAST TRIP</span></div><div class="trip-card-stats"><span>${trip.days}<small>trip length</small></span><span>${amount(trip.budget)}<small>trip budget</small></span><span>${trip.weightLimit == null ? "Not set" : `${amount(trip.weightLimit)} g`}<small>luggage limit</small></span></div><div class="trip-card-footer"><button class="button button-primary" data-action="select-trip" data-id="${escapeHtml(trip.id)}">Open packing list →</button><span class="muted" style="font-size:9px">Saved for next time</span></div></article>`).join("")}</div>`
    : emptyState("↶", "Your travel memories will live here", "Trips with a departure date in the past appear here.", "Plan a trip", "new-trip");
}

function renderItemsPage(page, items) {
  const host = $(`#${page}-content`);
  const titles = {
    checklist: ["Your packing list", "Mark items as packed as they go into your bag."],
    shopping: ["Things to pick up", "Mark items as purchased when you find them."]
  };
  const listItems = items.filter((item) => !state.search
    || item.name.toLowerCase().includes(state.search.toLowerCase()));
  const trip = currentTrip();
  if (!trip) {
    host.innerHTML = emptyState("◷", "Choose a trip to get started", "Create or select a trip before adding packing items.", "Plan a trip", "new-trip");
    return;
  }
  if (!items.length && !state.search) {
    const empty = emptyState(page === "shopping" ? "▢" : "☷",
      page === "shopping" ? "Your shopping list is clear" : "Nothing on your list yet",
      page === "shopping" ? "Add something you want to pick up before your trip."
        : "Add an item, or let Smart Picks suggest outfit ideas.",
      page === "shopping" ? "Add to list" : "Add an item", "new-item", page === "shopping" ? "SHOPPING" : "PACKING");
    host.innerHTML = page === "checklist" ? `${empty}${packingFlowActions(items)}` : empty;
    return;
  }
  const checked = page === "shopping" ? items.filter((item) => item.purchased).length : items.filter((item) => item.packed).length;
  const itemName = page === "shopping" ? "Purchased" : "Packed";
  host.innerHTML = `<div class="card section-card">
    <div class="card-heading"><div><h3>${titles[page][0]}</h3><span class="muted">${titles[page][1]}</span></div><span class="item-category">${checked} / ${items.length} ${itemName.toLowerCase()}</span></div>
    <div class="toolbar"><label class="search-box"><span>⌕</span><input data-search type="search" value="${escapeHtml(state.search)}" placeholder="Search these items..." aria-label="Search items"></label><span class="muted" style="font-size:10px">${items.length} ${items.length === 1 ? "item" : "items"}</span></div>
    ${listItems.length ? itemsTable(listItems, page === "shopping") : '<div class="empty-state"><h3>No matching items</h3><p>Try a different search.</p></div>'}
  </div>${page === "checklist" ? packingFlowActions(items) : ""}`;
}

function packingFlowActions(items) {
  const packed = items.filter((item) => item.packed).length;
  const progress = items.length ? Math.round((packed / items.length) * 100) : 0;
  return `<div class="card packing-flow-card"><div><span class="eyebrow">PACKING PROGRESS</span><h3>${packed} of ${items.length} items packed · ${progress}%</h3><p class="muted">Continue planning with your current trip and saved checklist.</p></div>
    <div class="packing-flow-actions"><button class="button button-quiet" data-page-link="recommendations">← Previous: Smart Picks</button><button class="button button-primary" data-page-link="outfits">Continue to trip planning →</button></div></div>`;
}

function itemsTable(items, shopping) {
  const totalWeight = items.filter((item) => item.category !== "SHOPPING").reduce((sum, item) => sum + item.totalWeight, 0);
  const totalCost = items.reduce((sum, item) => sum + Number(item.totalPrice || 0), 0);
  return `<div class="table-wrap"><table class="data-table"><thead><tr><th class="check-cell">${shopping ? "BUY" : "PACK"}</th><th>ITEM</th><th>CATEGORY</th><th>QTY</th><th>WEIGHT</th><th>PRICE</th><th></th></tr></thead><tbody>
    ${items.map((item) => `<tr><td class="check-cell"><input type="checkbox" data-status="${shopping ? "purchased" : "packed"}" data-id="${escapeHtml(item.id)}" ${item[shopping ? "purchased" : "packed"] ? "checked" : ""} aria-label="${shopping ? "Purchased" : "Packed"}: ${escapeHtml(item.name)}"></td>
      <td class="item-name">${escapeHtml(item.name)}</td><td><span class="item-category">${escapeHtml(categoryLabels[item.category] || item.category)}</span></td><td>${item.quantity}</td><td>${amount(item.totalWeight)} g</td><td>₹${amount(item.totalPrice)}</td>
      <td><span class="item-actions"><button class="icon-button" data-action="edit-item" data-id="${escapeHtml(item.id)}" aria-label="Edit ${escapeHtml(item.name)}">✎</button><button class="icon-button danger" data-action="delete-item" data-id="${escapeHtml(item.id)}" aria-label="Delete ${escapeHtml(item.name)}">⌫</button></span></td></tr>`).join("")}
    </tbody></table></div><div class="table-foot"><span>List weight: <strong>${amount(totalWeight)} g</strong></span><span>Estimated list cost: <strong>₹${amount(totalCost)}</strong></span></div>`;
}

function renderBudget(trip) {
  const host = $("#budget-content");
  if (!trip) {
    host.innerHTML = emptyState("＄", "Your trip budget starts here", "Choose a trip to plan your spending and bag weight.", "Plan a trip", "new-trip");
    return;
  }
  const packingItems = state.items.filter((item) => item.category !== "SHOPPING");
  const shoppingItems = state.items.filter((item) => item.category === "SHOPPING");
  const purchasedItems = shoppingItems.filter((item) => item.purchased);
  const spent = purchasedItems.reduce((sum, item) => sum + Number(item.totalPrice || 0), 0);
  const packedWeight = packingItems.filter((item) => item.packed).reduce((sum, item) => sum + item.totalWeight, 0);
  const totalWeight = packingItems.reduce((sum, item) => sum + item.totalWeight, 0);
  const luggageRemaining = state.weightLimit === null ? null : state.weightLimit - packedWeight;
  const remaining = trip.budget - spent;
  const spendingProgress = trip.budget > 0 ? Math.min(100, Math.round((spent / trip.budget) * 100)) : 0;
  const categories = ["PACKING", "CLOTHING"];
  const categoryWeights = categories.map((category) => ({
    name: categoryLabels[category],
    weight: packingItems.filter((item) => item.category === category).reduce((sum, item) => sum + item.totalWeight, 0)
  }));
  host.innerHTML = `<div class="budget-layout">
    <div class="card"><div class="card-heading"><h3>Trip spending</h3><button class="text-link" data-action="edit-trip">Edit trip budget →</button></div>
      <div class="muted">Spent on purchased shopping items</div><div class="budget-big">${amount(spent)}</div><div class="budget-caption">of ${amount(trip.budget)} budgeted for ${escapeHtml(trip.destination)}</div>
      <div class="progress-track" style="margin-top:17px"><div class="progress-fill" style="width:${spendingProgress}%;${remaining < 0 ? "background:#c65361" : ""}"></div></div>
      <div class="progress-label"><span>Budget used</span><strong>${spendingProgress}%</strong></div>
      <div class="budget-breakdown"><div class="breakdown-row"><span>Total budget</span><strong>${amount(trip.budget)}</strong></div><div class="breakdown-row"><span>Amount remaining</span><strong>${amount(remaining)}</strong></div><div class="breakdown-row"><span>Items on shopping list</span><strong>${shoppingItems.length}</strong></div><div class="breakdown-row"><span>Packing list items</span><strong>${packingItems.length}</strong></div></div>
    </div>
    <div class="card"><div class="card-heading"><h3>Bag weight</h3><span class="muted">Items to pack</span></div>
      <div class="weight-callout"><span class="weight-icon">◇</span><div><strong>${amount(totalWeight)} g</strong><small>Total estimated packing weight</small></div></div>
      <div class="budget-breakdown"><div class="breakdown-row"><span>Luggage limit</span><strong>${state.weightLimit === null ? "Not set" : `${amount(state.weightLimit)} g`}</strong></div><div class="breakdown-row"><span>Already packed</span><strong>${amount(packedWeight)} g</strong></div><div class="breakdown-row"><span>Capacity remaining</span><strong>${luggageRemaining === null ? "Not set" : `${amount(luggageRemaining)} g`}</strong></div><div class="breakdown-row"><span>Still to pack</span><strong>${amount(Math.max(0, totalWeight - packedWeight))} g</strong></div></div>
      ${luggageRemaining !== null && luggageRemaining < 0 ? `<div class="weight-warning" role="alert">Packed weight exceeds the luggage limit by ${amount(Math.abs(luggageRemaining))} g.</div>` : ""}
      ${renderWeightLimitForm("budget-weight-limit-input")}
      <div class="weight-legend">${categoryWeights.map((entry) => `<div class="legend-row"><span class="legend-dot"></span><span>${escapeHtml(entry.name)}</span><strong style="margin-left:auto;color:#414458">${amount(entry.weight)} g</strong></div>`).join("")}</div>
    </div></div>
    <div class="card section-card purchased-items-card"><div class="card-heading"><div><h3>Shopping purchases</h3><span class="muted">Only items marked purchased count toward spending.</span></div><strong>${amount(spent)} spent</strong></div>
      ${shoppingItems.length ? `<div class="table-wrap"><table class="data-table"><thead><tr><th>ITEM</th><th>PRICE</th><th>STATUS</th></tr></thead><tbody>${shoppingItems.map((item) => `<tr><td class="item-name">${escapeHtml(item.name)}</td><td>${amount(item.totalPrice)}</td><td><span class="purchase-status ${item.purchased ? "is-purchased" : ""}">${item.purchased ? "Purchased" : "Not purchased"}</span></td></tr>`).join("")}</tbody></table></div>` : '<p class="muted">Add items to your Shopping list to track purchases here.</p>'}
    </div>`;
}

function resetOutfitPlanner(trip) {
  state.outfitPlannerTripId = trip?.id || "";
  state.outfitPlan = null;
  state.outfitPlanSaved = false;
  state.outfitPlanLoading = false;
  state.outfitPlanError = "";
  state.selectedOutfitItemIds = new Set();
  state.outfitPlannerInputs = {
    destination: trip?.destination || "",
    weather: "",
    days: trip?.days || 1,
    activities: Array.from({ length: trip?.days || 1 }, () => "Sightseeing")
  };
}

function renderDayPlanner(trip) {
  const host = $("#outfits-content");
  if (!trip) {
    host.innerHTML = emptyState("✧", "Choose a trip to plan outfits", "Create or select a trip before saving a day-wise outfit plan.", "Plan a trip", "new-trip");
    return;
  }
  if (state.outfitPlannerTripId !== trip.id) resetOutfitPlanner(trip);
  const inputs = state.outfitPlannerInputs;
  const clothingItems = state.items.filter((item) => item.category === "CLOTHING");
  const weatherOptions = ["Sunny", "Hot", "Moderate", "Cold", "Rainy", "Humid", "Snowy"];
  const activityOptions = ["Sightseeing", "Trekking", "Beach", "Swimming", "Camping", "Business", "Casual"];
  host.innerHTML = `<div class="card section-card outfit-form-card">
    <div class="card-heading"><div><h3>Build your daily outfits</h3><span class="muted">Pick one weather condition and an activity for each trip day.</span></div><span class="item-category">NO FORECAST NEEDED</span></div>
    <form id="outfit-planner-form">
      <div class="outfit-input-grid">
        <label class="form-field">Destination<input id="outfit-destination" maxlength="100" value="${escapeHtml(inputs.destination)}" placeholder="Enter your destination" required></label>
        <label class="form-field">Weather<select id="outfit-weather" required><option value="" disabled ${inputs.weather ? "" : "selected"}>Select weather</option>${weatherOptions.map((option) => `<option value="${option}" ${inputs.weather === option ? "selected" : ""}>${option}</option>`).join("")}</select></label>
        <label class="form-field">Number of days<input id="outfit-days" type="number" min="1" max="365" value="${inputs.days}" required></label>
      </div>
      <div class="day-activity-grid">${inputs.activities.map((activity, index) => `<label class="form-field">Day ${index + 1} activity<select data-outfit-activity="${index}" required>${activityOptions.map((option) => `<option value="${option}" ${activity === option ? "selected" : ""}>${option}</option>`).join("")}</select></label>`).join("")}</div>
      <fieldset class="planner-clothing-picker"><legend>Select clothing from your packing checklist</legend>
        <p class="muted">Choose one or more saved clothing items. They will be included in each day's plan.</p>
        ${clothingItems.length ? `<div class="planner-clothing-list">${clothingItems.map((item) => `<label class="planner-clothing-option ${state.selectedOutfitItemIds.has(item.id) ? "selected" : ""}"><input type="checkbox" data-outfit-item-id="${escapeHtml(item.id)}" ${state.selectedOutfitItemIds.has(item.id) ? "checked" : ""}><span><strong>${escapeHtml(item.name)}</strong><small>${amount(item.totalWeight)} g · Qty ${item.quantity}</small></span></label>`).join("")}</div>` : '<div class="planner-clothing-empty">No clothing items are saved for this trip yet. <button class="text-link" type="button" data-action="new-item" data-category="CLOTHING">Add clothing →</button></div>'}
      </fieldset>
      <div class="outfit-form-actions"><button class="button button-primary" type="submit" ${state.outfitPlanLoading ? "disabled" : ""}>${state.outfitPlanLoading ? "Building outfits…" : "Generate Outfit Plan →"}</button>${state.outfitPlan ? `<button class="button button-quiet" type="button" data-action="regenerate-outfits" ${state.outfitPlanLoading ? "disabled" : ""}>Regenerate Plan</button>` : ""}</div>
      <div id="outfit-plan-error" class="form-error ${state.outfitPlanError ? "" : "hidden"}" role="alert">${escapeHtml(state.outfitPlanError)}</div>
    </form>
  </div>
  ${state.outfitPlanLoading ? '<div class="card recommendation-loading" role="status">Creating a day-by-day outfit plan…</div>' : ""}
  ${state.outfitPlan ? renderOutfitPlan(state.outfitPlan, !state.outfitPlanSaved) : ""}
  <div class="card section-card saved-outfits"><div class="card-heading"><div><h3>Saved outfit plans</h3><span class="muted">Plans are saved separately from your trips and packing checklist.</span></div><span class="item-category">${state.outfitPlans.length} saved</span></div>
    ${state.outfitPlans.length ? `<div class="saved-outfit-list">${[...state.outfitPlans].reverse().map((plan) => `<article class="saved-outfit-row"><span class="saved-outfit-icon">✧</span><div><strong>${escapeHtml(plan.destination)}</strong><small>${escapeHtml(plan.weather)} · ${plan.days.length} ${plan.days.length === 1 ? "day" : "days"}</small></div><button class="button button-quiet button-small" data-action="view-outfit-plan" data-id="${escapeHtml(plan.id)}">View plan</button></article>`).join("")}</div>` : '<p class="muted saved-outfit-empty">Your saved plans will appear here after you save one.</p>'}
  </div>`;
}

function renderOutfitPlan(plan, allowSave) {
  const uniqueItems = new Map();
  for (const day of plan.days) {
    for (const item of day.items) {
      const key = `${String(item.category).toLowerCase()}:${String(item.name).toLocaleLowerCase()}`;
      if (!uniqueItems.has(key)) uniqueItems.set(key, Number(item.weight));
    }
  }
  const totalWeight = [...uniqueItems.values()].reduce((sum, weight) => sum + weight, 0);
  return `<section class="card section-card outfit-plan-results">
    <div class="card-heading"><div><span class="eyebrow">YOUR TRIP, THOUGHTFULLY PACKED</span><h3>Outfits for ${escapeHtml(plan.destination)}</h3><span class="muted">${escapeHtml(plan.weather)} weather · ${plan.days.length} ${plan.days.length === 1 ? "day" : "days"}</span></div>${allowSave ? '<span class="item-category">NEW PLAN</span>' : '<span class="item-category">SAVED PLAN</span>'}</div>
    <div class="outfit-day-list">${plan.days.map((day) => `<article class="outfit-day-card"><div class="outfit-day-heading"><div><span class="day-number">DAY ${day.day}</span><h4>${escapeHtml(day.activity)}</h4></div><span class="outfit-weight">${amount(day.totalWeight ?? day.items.reduce((sum, item) => sum + Number(item.weight), 0))} g</span></div>
      <ul>${day.items.map((item) => `<li><span class="outfit-item-type">${escapeHtml(item.category)}</span><strong>${escapeHtml(item.name)}</strong><small>${amount(item.weight)} g</small></li>`).join("")}</ul>
    </article>`).join("")}</div>
    <div class="outfit-plan-total"><div><span>Estimated trip clothing weight</span><strong>${amount(totalWeight)} g</strong><small>Unique clothing items only; repeated pieces are counted once.</small></div>${allowSave ? '<button class="button button-primary" data-action="save-outfit-plan">Save Outfit Plan</button>' : ""}</div>
  </section>`;
}

function updateOutfitPlannerInputs() {
  const destination = $("#outfit-destination");
  if (!destination) return;
  const dayCount = Number($("#outfit-days").value);
  const previousActivities = state.outfitPlannerInputs.activities;
  state.outfitPlannerInputs = {
    destination: destination.value.trim(),
    weather: $("#outfit-weather").value,
    days: dayCount,
    activities: Array.from({ length: dayCount }, (_, index) => previousActivities[index] || "Sightseeing")
  };
}

async function generateOutfitPlan() {
  const form = $("#outfit-planner-form");
  if (!form || state.outfitPlanLoading) return;
  if (!form.reportValidity()) return;
  const selectedItemIds = state.items.filter((item) => item.category === "CLOTHING"
    && state.selectedOutfitItemIds.has(item.id)).map((item) => item.id);
  if (!selectedItemIds.length) {
    state.outfitPlanError = state.items.some((item) => item.category === "CLOTHING")
      ? "Select at least one clothing item from your packing checklist."
      : "Add clothing to this trip, then select at least one item to build your plan.";
    renderDayPlanner(currentTrip());
    return;
  }
  updateOutfitPlannerInputs();
  state.outfitPlan = null;
  state.outfitPlanSaved = false;
  state.outfitPlanLoading = true;
  state.outfitPlanError = "";
  const tripId = state.selectedTripId;
  renderDayPlanner(currentTrip());
  try {
    const plan = await api("outfit-plans/generate", {
      method: "POST",
      body: JSON.stringify({ tripId, ...state.outfitPlannerInputs, selectedItemIds })
    });
    if (tripId !== state.selectedTripId) return;
    state.outfitPlan = plan;
    state.outfitPlanSaved = false;
    setToast(`Created outfits for ${plan.days.length} days in ${plan.destination}.`);
  } catch (error) {
    if (tripId === state.selectedTripId) {
      state.outfitPlanError = error.message || "The outfit plan could not be generated.";
    }
  } finally {
    state.outfitPlanLoading = false;
    if (state.page === "outfits" && tripId === state.selectedTripId) renderDayPlanner(currentTrip());
  }
}

function renderRecommendations(trip) {
  const host = $("#recommendations-content");
  if (!trip) {
    host.innerHTML = emptyState("✳", "Choose or create a trip first", "Create a trip to build a packing list for your recommendations.", "Plan a trip", "new-trip");
    return;
  }
  if (!state.recommendationInputs || typeof state.recommendationInputs !== "object"
      || Array.isArray(state.recommendationInputs)) {
    state.recommendationInputs = { destination: "", weather: "", activity: "" };
  }
  const inputs = state.recommendationInputs || {};
  const destination = typeof inputs.destination === "string" ? inputs.destination : "";
  const weather = typeof inputs.weather === "string" ? inputs.weather : "";
  const activity = typeof inputs.activity === "string" ? inputs.activity : "";
  const recommendations = sortedRecommendations();
  host.innerHTML = `<div class="card section-card">
    <div class="card-heading"><div><h3>Smart Picks for this trip</h3><span class="muted">Choose your destination, weather and activity to get outfit and packing suggestions.</span></div><span class="item-category">NO FORECAST NEEDED</span></div>
    <form id="recommendation-form" class="recommendation-form" ${state.recommendationLoading ? 'aria-busy="true"' : ""}>
      <label class="form-field">Destination
        <input id="recommendation-destination" name="destination" maxlength="100" value="${escapeHtml(destination)}" placeholder="Enter your destination" required ${state.recommendationLoading ? "disabled" : ""}>
      </label>
      <label class="form-field">Expected weather
        <select id="recommendation-weather" name="weather" required ${state.recommendationLoading ? "disabled" : ""}>
          <option value="" disabled ${weather ? "" : "selected"}>Select weather</option>
          ${["Sunny", "Hot", "Moderate", "Cold", "Rainy", "Humid", "Snowy"].map((option) => `<option value="${option}" ${weather === option ? "selected" : ""}>${option}</option>`).join("")}
        </select>
      </label>
      <label class="form-field">Planned activity
        <select id="recommendation-activity" name="activity" required ${state.recommendationLoading ? "disabled" : ""}>
          <option value="" disabled ${activity ? "" : "selected"}>Select activity</option>
          ${["Sightseeing", "Trekking", "Beach", "Swimming", "Camping", "Business", "Casual"].map((option) => `<option value="${option}" ${activity === option ? "selected" : ""}>${option}</option>`).join("")}
        </select>
      </label>
      <button class="button button-primary recommendation-generate" type="submit" ${state.recommendationLoading ? "disabled" : ""}>${state.recommendationLoading ? "Generating recommendations…" : "Generate recommendations →"}</button>
    </form>
    <div id="recommendation-error" class="form-error hidden" role="alert"></div>
  </div>
  ${state.recommendationLoading ? '<div class="card recommendation-loading" role="status">Creating your picks…</div>' : ""}
  ${state.recommendationGenerated ? `<div id="recommendation-results" class="card section-card">
    <div class="card-heading"><div><h3>Smart Picks for ${escapeHtml(destination)}</h3><span class="muted">${escapeHtml(weather)} weather · ${escapeHtml(activity)} · ${trip.days} ${trip.days === 1 ? "day" : "days"}</span></div><span class="item-category">SMART PICKS</span></div>
    <div class="recommendation-sort">
      <span class="muted">Sort recommendations</span>
      <label>By
        <select id="recommendation-sort-field" aria-label="Sort by source or weight">
          <option value="source" ${state.recommendationSort.field === "source" ? "selected" : ""}>Source / category</option>
          <option value="weight" ${state.recommendationSort.field === "weight" ? "selected" : ""}>Weight</option>
        </select>
      </label>
      <label>Order
        <select id="recommendation-sort-direction" aria-label="Sort direction">
          <option value="asc" ${state.recommendationSort.direction === "asc" ? "selected" : ""}>Ascending</option>
          <option value="desc" ${state.recommendationSort.direction === "desc" ? "selected" : ""}>Descending</option>
        </select>
      </label>
    </div>
    <div class="recommendation-grid">${recommendations.map((item) => {
      const alreadyAdded = state.items.some((saved) => sameRecommendation(saved, item));
      const key = recommendationKey(item);
      const isPending = state.pendingRecommendations.has(key);
      const selected = state.selectedSmartPicks.has(key);
      return `<article class="recommendation-card ${selected ? "smart-pick-selected" : ""}"><label class="smart-pick-select" aria-label="Select ${escapeHtml(item.name)}"><input type="checkbox" data-smart-pick-id="${escapeHtml(key)}" ${selected ? "checked" : ""} ${alreadyAdded ? "disabled" : ""}><span class="sr-only">Select</span></label><span class="recommendation-emoji">${itemIcons[item.category] || "◇"}</span><div class="recommendation-copy"><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(categoryLabels[item.category] || item.category)} · ${escapeHtml(item.source)} · ${item.quantity} ${item.quantity === 1 ? "item" : "items"} · ${amount(item.weight * item.quantity)} g</small><span class="priority-label priority-${item.priority.toLowerCase()}">${escapeHtml(item.priority)} PRIORITY</span></div><span class="recommendation-state">${alreadyAdded ? "Added ✓" : selected ? "Selected" : ""}</span></article>`;
    }).join("")}</div>
    <div class="smart-picks-actions"><span class="muted">${state.selectedSmartPicks.size} selected · select items to add them to your packing checklist.</span><div><button class="button button-quiet" data-action="add-selected-smart-picks" ${state.selectedSmartPicks.size === 0 || state.smartPicksAdded ? "disabled" : ""}>Add selected to checklist</button><button class="button button-primary" data-action="next-smart-picks" ${state.smartPicksAdded ? "" : "disabled"}>Next: Packing checklist →</button></div></div>
    <p class="muted recommendation-note">Suggestions are general guidance. Adjust them for local conditions and your personal needs.</p>
  </div>` : ""}`;
}

function normalizeRecommendations(response) {
  if (!Array.isArray(response)) {
    throw new Error("The recommendation service returned an invalid list. Please try again.");
  }
  const uniqueItems = new Map();
  for (const entry of response) {
    if (!entry || typeof entry !== "object" || typeof entry.name !== "string" || !entry.name.trim()) {
      throw new Error("A recommendation was missing its item name. Please try again.");
    }
    const category = typeof entry.category === "string" ? entry.category.trim().toUpperCase() : "";
    if (!Object.prototype.hasOwnProperty.call(categoryLabels, category)) {
      throw new Error("A recommendation had an unknown category. Please try again.");
    }
    const priority = typeof entry.priority === "string" ? entry.priority.trim().toUpperCase() : "";
    if (!["HIGH", "MEDIUM", "LOW"].includes(priority)) {
      throw new Error("A recommendation had an unknown priority. Please try again.");
    }
    const quantity = entry.quantity;
    const weight = entry.weight;
    if (typeof quantity !== "number" || !Number.isInteger(quantity) || quantity < 1
        || typeof weight !== "number" || !Number.isFinite(weight) || weight < 0) {
      throw new Error("A recommendation had invalid quantity or weight. Please try again.");
    }
    const name = entry.name.trim();
    const key = `${category}:${name.toLocaleLowerCase()}`;
    if (uniqueItems.has(key)) continue;
    uniqueItems.set(key, {
      name,
      category,
      source: typeof entry.source === "string" && entry.source.trim() ? entry.source.trim() : "Other",
      quantity,
      weight,
      price: typeof entry.price === "number" && Number.isFinite(entry.price) ? entry.price : 0,
      priority
    });
  }
  return [...uniqueItems.values()];
}

function recommendationKey(item) {
  return `${item.category}:${item.name.toLocaleLowerCase()}`;
}

function sameRecommendation(saved, recommendation) {
  return saved && typeof saved.name === "string"
    && saved.name.toLocaleLowerCase() === recommendation.name.toLocaleLowerCase()
    && saved.category === recommendation.category;
}

function compareText(first, second) {
  return String(first ?? "").localeCompare(String(second ?? ""));
}

function sortedRecommendations() {
  const direction = state.recommendationSort.direction === "desc" ? -1 : 1;
  return [...state.recommendations].sort((first, second) => {
    let comparison;
    if (state.recommendationSort.field === "weight") {
      const firstWeight = Number(first?.weight) * Number(first?.quantity);
      const secondWeight = Number(second?.weight) * Number(second?.quantity);
      comparison = (Number.isFinite(firstWeight) ? firstWeight : 0)
        - (Number.isFinite(secondWeight) ? secondWeight : 0);
    } else {
      comparison = compareText(first?.source, second?.source)
        || compareText(first?.category, second?.category);
    }
    return (comparison || compareText(first?.name, second?.name)) * direction;
  });
}

function emptyState(icon, title, description, buttonLabel, action, category = "") {
  return `<div class="empty-state"><span class="empty-icon">${icon}</span><h3>${escapeHtml(title)}</h3><p>${escapeHtml(description)}</p><button class="button button-primary" data-action="${action}" ${category ? `data-category="${category}"` : ""}>＋ ${escapeHtml(buttonLabel)}</button></div>`;
}

function navigate(page) {
  if (!(page in pages)) return;
  state.page = page;
  state.search = "";
  render();
  if (page === "recommendations") renderRecommendations(currentTrip());
}

function openTripDialog(trip = null) {
  state.editingTripId = trip?.id || "";
  $("#trip-dialog-title").textContent = trip ? "Edit trip" : "Plan a trip";
  $("#trip-save").textContent = trip ? "Save changes" : "Save trip";
  $("#trip-name").value = trip?.name || trip?.destination || "";
  $("#trip-destination").value = trip?.destination || "";
  $("#trip-date").value = trip?.startDate || todayDateString();
  $("#trip-days").value = trip?.days || 5;
  $("#trip-budget").value = trip?.budget ?? 0;
  $("#trip-weight-limit").value = trip?.weightLimit ?? "";
  setFormError("#trip-form-error");
  $("#trip-dialog").showModal();
  $("#trip-destination").focus();
}

function openItemDialog(category = "PACKING", item = null) {
  if (!currentTrip()) {
    setToast("Create or select a trip before adding packing items.", true);
    navigate("trips");
    return;
  }
  state.editingItemId = item?.id || "";
  $("#item-dialog-title").textContent = item ? "Edit item" : "Add an item";
  $("#item-category").value = item?.category || category;
  $("#item-name").value = item?.name || "";
  $("#item-quantity").value = item?.quantity || 1;
  $("#item-weight").value = item?.weight ?? "";
  $("#item-price").value = item?.price ?? "";
  setFormError("#item-form-error");
  $("#item-dialog").showModal();
  $("#item-name").focus();
}

async function signOut(notify = true) {
  const hadSession = Boolean(state.token);
  let logoutError = "";
  if (hadSession) {
    try {
      await api("logout", { method: "POST" });
    } catch (error) {
      logoutError = error.message;
    }
  }
  state.token = "";
  state.user = null;
  state.trips = [];
  state.items = [];
  state.selectedTripId = "";
  localStorage.removeItem("bagorithm-token");
  localStorage.removeItem("bagorithm-user");
  localStorage.removeItem("bagorithm-trip");
  showAuth();
  if (notify && hadSession) {
    setFormError("#auth-message", logoutError ? `Signed out locally. ${logoutError}` : "You have signed out.");
  }
}

$("#auth-switch").addEventListener("click", () => {
  state.authMode = state.authMode === "login" ? "register" : "login";
  $("#auth-form").reset();
  updateAuthForm();
});

$("#auth-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const submitButton = $("#auth-submit");
  submitButton.disabled = true;
  setFormError("#auth-message");
  try {
    const registering = state.authMode === "register";
    const body = {
      email: $("#auth-email").value,
      password: $("#auth-password").value
    };
    if (registering) body.name = $("#auth-name").value;
    const result = await api(registering ? "register" : "login", {
      method: "POST",
      body: JSON.stringify(body)
    });
    state.token = result.token;
    state.user = result.user;
    localStorage.setItem("bagorithm-token", state.token);
    localStorage.setItem("bagorithm-user", JSON.stringify(state.user));
    await loadTrips();
    showApplication();
  } catch (error) {
    setFormError("#auth-message", error.message);
  } finally {
    submitButton.disabled = false;
  }
});

$("#trip-select").addEventListener("change", async (event) => {
  state.recommendationRequestId++;
  state.recommendationLoading = false;
  state.recommendationError = "";
  state.selectedTripId = event.target.value;
  localStorage.setItem("bagorithm-trip", state.selectedTripId);
  state.search = "";
  try {
    if (state.selectedTripId) {
      const tripId = encodeURIComponent(state.selectedTripId);
      const [items, settings, plans] = await Promise.all([
        api(`items?tripId=${tripId}`),
        api(`packing-limit?tripId=${tripId}`),
        api(`outfit-plans?tripId=${tripId}`)
      ]);
      state.items = items;
      state.weightLimit = settings.weightLimit;
      state.outfitPlans = plans;
    } else {
      state.items = [];
      state.weightLimit = null;
      state.outfitPlans = [];
    }
    resetOutfitPlanner(currentTrip());
    state.recommendations = [];
    state.selectedSmartPicks = new Set();
    state.smartPicksAdded = false;
    state.recommendationGenerated = false;
    state.recommendationInputs = { destination: "", weather: "", activity: "" };
    render();
    if (state.page === "recommendations") renderRecommendations(currentTrip());
  } catch (error) {
    setToast(error.message, true);
  }
});

$("#sign-out").addEventListener("click", () => signOut());

document.addEventListener("submit", async (event) => {
  if (event.target.id === "outfit-planner-form") {
    event.preventDefault();
    await generateOutfitPlan();
    return;
  }
  if (event.target.matches("[data-weight-limit-form]")) {
    event.preventDefault();
    const form = event.target;
    if (!form.reportValidity() || !state.selectedTripId) return;
    try {
      const result = await api("packing-limit", {
        method: "PUT",
        body: JSON.stringify({
          tripId: state.selectedTripId,
          weightLimit: Number(form.querySelector("[name='weightLimit']").value)
        })
      });
      state.weightLimit = result.weightLimit;
      state.trips = state.trips.map((trip) => trip.id === state.selectedTripId
        ? { ...trip, weightLimit: result.weightLimit } : trip);
      render();
      setToast(`Luggage limit updated to ${amount(state.weightLimit)} g.`);
    } catch (error) {
      setToast(error.message, true);
    }
    return;
  }
  if (event.target.id !== "recommendation-form") return;
  event.preventDefault();
  if (state.recommendationLoading) return;
  const tripId = state.selectedTripId;
  state.recommendationInputs = {
    destination: $("#recommendation-destination").value.trim(),
    weather: $("#recommendation-weather").value,
    activity: $("#recommendation-activity").value
  };
  state.recommendationGenerated = false;
  state.recommendations = [];
  state.selectedSmartPicks = new Set();
  state.smartPicksAdded = false;
  state.recommendationLoading = true;
  state.recommendationError = "";
  const requestId = ++state.recommendationRequestId;
  renderRecommendations(currentTrip());
  try {
    const query = new URLSearchParams({
      tripId,
      ...state.recommendationInputs
    });
    const response = await api(`recommendations?${query.toString()}`);
    const suggestions = normalizeRecommendations(response);
    if (requestId !== state.recommendationRequestId || tripId !== state.selectedTripId) return;
    state.recommendations = suggestions;
    state.recommendationGenerated = true;
    setToast(`Generated ${suggestions.length} clothing recommendations for ${state.recommendationInputs.destination}.`);
  } catch (error) {
    if (requestId === state.recommendationRequestId && tripId === state.selectedTripId) {
      state.recommendations = [];
      state.recommendationGenerated = false;
      state.recommendationError = error.message || "We couldn't create recommendations. Check your choices and try again.";
      setToast(state.recommendationError, true);
    }
  } finally {
    if (requestId === state.recommendationRequestId) {
      state.recommendationLoading = false;
      if (state.page === "recommendations" && tripId === state.selectedTripId) {
        renderRecommendations(currentTrip());
        if (state.recommendationError) {
          setFormError("#recommendation-error", state.recommendationError);
          state.recommendationError = "";
        }
      }
    }
  }
});

document.addEventListener("change", (event) => {
  if (event.target.matches("[data-smart-pick-id]")) {
    const key = event.target.dataset.smartPickId;
    if (event.target.checked) state.selectedSmartPicks.add(key);
    else state.selectedSmartPicks.delete(key);
    state.smartPicksAdded = false;
    renderRecommendations(currentTrip());
    return;
  }
  if (event.target.matches("[data-outfit-item-id]")) {
    const itemId = event.target.dataset.outfitItemId;
    if (event.target.checked) state.selectedOutfitItemIds.add(itemId);
    else state.selectedOutfitItemIds.delete(itemId);
    state.outfitPlan = null;
    state.outfitPlanSaved = false;
    state.outfitPlanError = "";
    renderDayPlanner(currentTrip());
    return;
  }
  if (event.target.id === "outfit-days") {
    updateOutfitPlannerInputs();
    state.outfitPlan = null;
    state.outfitPlanSaved = false;
    renderDayPlanner(currentTrip());
    return;
  }
  if (event.target.id === "outfit-weather" || event.target.matches("[data-outfit-activity]")) {
    updateOutfitPlannerInputs();
    if (event.target.matches("[data-outfit-activity]")) {
      const activityIndex = Number(event.target.dataset.outfitActivity);
      state.outfitPlannerInputs.activities[activityIndex] = event.target.value;
    }
    state.outfitPlan = null;
    state.outfitPlanSaved = false;
    state.outfitPlanError = "";
    renderDayPlanner(currentTrip());
    return;
  }
  if (["recommendation-weather", "recommendation-activity"].includes(event.target.id)) {
    saveRecommendationFormInputs();
    return;
  }
  if (event.target.id === "recommendation-sort-field") {
    state.recommendationSort.field = event.target.value;
    renderRecommendations(currentTrip());
    return;
  }
  if (event.target.id === "recommendation-sort-direction") {
    state.recommendationSort.direction = event.target.value;
    renderRecommendations(currentTrip());
  }
});

$("#trip-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const trip = {
    name: $("#trip-name").value.trim(),
    destination: $("#trip-destination").value,
    startDate: $("#trip-date").value,
    days: Number($("#trip-days").value),
    budget: Number($("#trip-budget").value),
    weightLimit: Number($("#trip-weight-limit").value)
  };
  if (!trip.name || !trip.destination.trim() || !Number.isFinite(trip.weightLimit)
      || trip.weightLimit < 1 || trip.weightLimit > 100000) {
    setFormError("#trip-form-error", "Enter a trip name, destination and luggage limit from 1 to 100,000 grams.");
    return;
  }
  try {
    const saved = state.editingTripId
      ? await api(`trips/${encodeURIComponent(state.editingTripId)}`, { method: "PUT", body: JSON.stringify(trip) })
      : await api("trips", { method: "POST", body: JSON.stringify(trip) });
    state.selectedTripId = saved.id;
    $("#trip-dialog").close();
    await refresh();
    setToast(state.editingTripId ? "Trip details updated." : "Your trip is ready to plan.");
  } catch (error) {
    setFormError("#trip-form-error", error.message);
  }
});

$("#item-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  const item = {
    tripId: state.selectedTripId,
    name: $("#item-name").value,
    category: $("#item-category").value,
    quantity: Number($("#item-quantity").value),
    weight: Number($("#item-weight").value),
    price: Number($("#item-price").value),
    packed: false,
    purchased: false
  };
  if (!item.tripId || !item.name.trim() || !Number.isFinite(item.quantity) || item.quantity < 1
      || !Number.isFinite(item.weight) || item.weight < 0
      || !Number.isFinite(item.price) || item.price < 0) {
    setFormError("#item-form-error", "Enter an item name, a valid quantity, weight and price.");
    return;
  }
  if (state.editingItemId) {
    const existing = state.items.find((entry) => entry.id === state.editingItemId);
    item.packed = existing?.packed || false;
    item.purchased = existing?.purchased || false;
  }
  try {
    let savedItem;
    if (state.editingItemId) {
      savedItem = await api(`items/${encodeURIComponent(state.editingItemId)}`, { method: "PUT", body: JSON.stringify(item) });
      state.items = state.items.map((entry) => entry.id === savedItem.id ? savedItem : entry);
    } else {
      savedItem = await api(`items?tripId=${encodeURIComponent(state.selectedTripId)}`, { method: "POST", body: JSON.stringify(item) });
      state.items = [...state.items, savedItem];
    }
    $("#item-dialog").close();
    render();
    setToast(state.editingItemId ? "Packing item updated." : "Item added to your trip.");
    try {
      const tripId = state.selectedTripId;
      const latestItems = await loadSelectedTripItems(tripId);
      if (tripId === state.selectedTripId) {
        state.items = latestItems;
        render();
      }
    } catch (error) {
      setToast(`Item saved, but the latest list could not be refreshed: ${error.message}`, true);
    }
  } catch (error) {
    setFormError("#item-form-error", error.message);
  }
});

document.addEventListener("click", async (event) => {
  const pageLink = event.target.closest("[data-page-link]");
  if (pageLink) {
    event.preventDefault();
    navigate(pageLink.dataset.pageLink);
    return;
  }

  const button = event.target.closest("[data-action]");
  if (!button) return;
  const { action, id } = button.dataset;
  try {
    if (action === "new-trip") openTripDialog();
    if (action === "edit-trip") {
      const trip = state.trips.find((entry) => entry.id === (id || state.selectedTripId));
      if (trip) openTripDialog(trip);
      else setToast("Choose a trip before editing its budget.", true);
    }
    if (action === "close-trip-dialog") $("#trip-dialog").close();
    if (action === "close-item-dialog") $("#item-dialog").close();
    if (action === "new-item") openItemDialog(button.dataset.category || "PACKING");
    if (action === "select-trip") {
      state.recommendationRequestId++;
      state.recommendationLoading = false;
      state.recommendationError = "";
      $("#trip-select").value = id;
      state.selectedTripId = id;
      localStorage.setItem("bagorithm-trip", id);
      state.search = "";
      const tripId = encodeURIComponent(id);
      const [items, settings, plans] = await Promise.all([
        api(`items?tripId=${tripId}`),
        api(`packing-limit?tripId=${tripId}`),
        api(`outfit-plans?tripId=${tripId}`)
      ]);
      state.items = items;
      state.weightLimit = settings.weightLimit;
      state.outfitPlans = plans;
      resetOutfitPlanner(currentTrip());
      state.recommendations = [];
      state.selectedSmartPicks = new Set();
      state.smartPicksAdded = false;
      state.recommendationGenerated = false;
      state.recommendationInputs = { destination: "", weather: "", activity: "" };
      render();
      if (state.page === "recommendations") renderRecommendations(currentTrip());
      setToast(`Now planning for ${currentTrip()?.destination || "your trip"}.`);
    }
    if (action === "delete-trip") {
      const trip = state.trips.find((entry) => entry.id === id);
      if (trip && window.confirm(`Delete the trip to ${trip.destination} and its packing list? This cannot be undone.`)) {
        await api(`trips/${encodeURIComponent(id)}`, { method: "DELETE" });
        if (state.selectedTripId === id) state.selectedTripId = "";
        await refresh();
        setToast("Trip and its packing list deleted.");
      }
    }
    if (action === "edit-item") {
      const item = state.items.find((entry) => entry.id === id);
      if (item) openItemDialog(item.category, item);
    }
    if (action === "delete-item") {
      const item = state.items.find((entry) => entry.id === id);
      if (item && window.confirm(`Remove "${item.name}" from this trip?`)) {
        await api(`items/${encodeURIComponent(id)}`, {
          method: "DELETE",
          body: JSON.stringify({ tripId: state.selectedTripId })
        });
        await refresh();
        setToast("Item removed from your trip.");
      }
    }
    if (action === "add-recommendation") {
      const key = button.dataset.id;
      const suggestion = state.recommendations.find((item) => recommendationKey(item) === key);
      if (!suggestion) return;
      if (state.pendingRecommendations.has(key)) return;
      state.pendingRecommendations.add(key);
      const tripId = state.selectedTripId;
      button.disabled = true;
      button.textContent = "Adding…";
      try {
        const savedItem = await api(`items?tripId=${encodeURIComponent(tripId)}`, {
          method: "POST",
          body: JSON.stringify({ ...suggestion, tripId, packed: false, purchased: false })
        });
        if (savedItem.tripId === tripId) {
          state.items = [...state.items.filter((item) => item.id !== savedItem.id), savedItem];
        }
        state.pendingRecommendations.delete(key);
        render();
        if (state.page === "recommendations") renderRecommendations(currentTrip());
        setToast(`${suggestion.name} added to your packing list.`);
        try {
          const latestItems = await loadSelectedTripItems(tripId);
          if (tripId === state.selectedTripId) {
            state.items = latestItems;
            render();
            if (state.page === "recommendations") renderRecommendations(currentTrip());
          }
        } catch (error) {
          setToast(`Item added, but the latest list could not be refreshed: ${error.message}`, true);
        }
      } catch (error) {
        state.pendingRecommendations.delete(key);
        if (state.page === "recommendations") renderRecommendations(currentTrip());
        throw error;
      }
    }
    if (action === "add-selected-smart-picks") {
      const selected = state.recommendations.filter((item) => state.selectedSmartPicks.has(recommendationKey(item)));
      if (!selected.length) {
        setToast("Select at least one suggestion before adding it.", true);
        return;
      }
      const tripId = state.selectedTripId;
      let addedCount = 0;
      for (const suggestion of selected) {
        if (state.items.some((saved) => sameRecommendation(saved, suggestion))) continue;
        const savedItem = await api(`items?tripId=${encodeURIComponent(tripId)}`, {
          method: "POST",
          body: JSON.stringify({
            tripId,
            name: suggestion.name,
            category: suggestion.category,
            quantity: suggestion.quantity,
            weight: suggestion.weight,
            price: suggestion.price,
            packed: false,
            purchased: false
          })
        });
        if (tripId !== state.selectedTripId) return;
        state.items = [...state.items, savedItem];
        addedCount++;
      }
      state.smartPicksAdded = true;
      render();
      if (state.page === "recommendations") renderRecommendations(currentTrip());
      setToast(addedCount
        ? `${addedCount} selected ${addedCount === 1 ? "item was" : "items were"} added to your packing checklist.`
        : "Those items are already on this trip's checklist.");
    }
    if (action === "next-smart-picks") {
      if (!state.smartPicksAdded) {
        setToast("Add your selected Smart Picks before continuing to the checklist.", true);
        return;
      }
      navigate("checklist");
    }
    if (action === "regenerate-outfits") await generateOutfitPlan();
    if (action === "save-outfit-plan") {
      if (!state.outfitPlan) throw new Error("Generate an outfit plan before saving it.");
      const saved = await api(`outfit-plans?tripId=${encodeURIComponent(state.selectedTripId)}`, {
        method: "POST",
        body: JSON.stringify({
          tripId: state.selectedTripId,
          destination: state.outfitPlan.destination,
          weather: state.outfitPlan.weather,
          days: state.outfitPlan.days
        })
      });
      state.outfitPlans = [...state.outfitPlans, saved];
      state.outfitPlan = saved;
      state.outfitPlanSaved = true;
      renderDayPlanner(currentTrip());
      setToast("Your outfit plan was saved separately from the packing checklist.");
    }
    if (action === "view-outfit-plan") {
      const saved = state.outfitPlans.find((plan) => plan.id === id);
      if (!saved) throw new Error("Saved outfit plan not found.");
      state.outfitPlan = saved;
      state.outfitPlanSaved = true;
      state.outfitPlannerInputs = {
        destination: saved.destination,
        weather: saved.weather,
        days: saved.days.length,
        activities: saved.days.map((day) => day.activity)
      };
      renderDayPlanner(currentTrip());
      setToast(`Showing the saved ${saved.destination} outfit plan.`);
    }
  } catch (error) {
    setToast(error.message, true);
  }
});

document.addEventListener("change", async (event) => {
  const checkbox = event.target.closest("[data-status]");
  if (!checkbox) return;
  const item = state.items.find((entry) => entry.id === checkbox.dataset.id);
  if (!item) return;
  const statusKey = checkbox.dataset.status;
  const previousStatus = item[statusKey];
  const nextStatus = checkbox.checked;
  if (previousStatus === nextStatus) return;
  const updated = {
    tripId: item.tripId,
    name: item.name,
    category: item.category,
    quantity: item.quantity,
    weight: item.weight,
    price: item.price,
    packed: statusKey === "packed" ? nextStatus : item.packed,
    purchased: statusKey === "purchased" ? nextStatus : item.purchased
  };
  state.items = state.items.map((entry) => entry.id === item.id ? { ...entry, [statusKey]: nextStatus } : entry);
  render();
  try {
    const savedItem = await api(`items/${encodeURIComponent(item.id)}`, {
      method: "PUT",
      body: JSON.stringify(updated)
    });
    state.items = state.items.map((entry) => entry.id === savedItem.id ? savedItem : entry);
    render();
  } catch (error) {
    state.items = state.items.map((entry) => entry.id === item.id
      ? { ...entry, [statusKey]: previousStatus } : entry);
    render();
    setToast(error.message, true);
  }
});

document.addEventListener("input", (event) => {
  if (event.target.id === "outfit-destination") {
    state.outfitPlannerInputs.destination = event.target.value.trim();
    if (state.outfitPlan) {
      state.outfitPlan = null;
      state.outfitPlanSaved = false;
      $(".outfit-plan-results")?.remove();
    }
    return;
  }
  if (event.target.id === "recommendation-destination") {
    saveRecommendationFormInputs();
    return;
  }
  if (!event.target.matches("[data-search]")) return;
  state.search = event.target.value;
  const cursor = event.target.selectionStart;
  render();
  const replacement = $("[data-search]");
  if (replacement) {
    replacement.focus();
    replacement.setSelectionRange(cursor, cursor);
  }
});

function saveRecommendationFormInputs() {
  const form = $("#recommendation-form");
  if (!form) return;
  state.recommendationInputs = {
    destination: $("#recommendation-destination").value.trim(),
    weather: $("#recommendation-weather").value,
    activity: $("#recommendation-activity").value
  };
  if (state.recommendationGenerated) {
    state.recommendationGenerated = false;
    state.recommendations = [];
    $("#recommendation-results")?.remove();
  }
}

async function startApplication() {
  showAuth();
  updateAuthForm();
  if (!state.token || !state.user) {
    return;
  }
  try {
    const session = await api("session");
    if (!session.user || session.user.id !== state.user.id) {
      throw new Error("The saved session does not match the signed-in user.");
    }
    state.user = session.user;
    localStorage.setItem("bagorithm-user", JSON.stringify(state.user));
    await loadTrips();
    showApplication();
  } catch (error) {
    state.token = "";
    state.user = null;
    state.trips = [];
    state.items = [];
    state.selectedTripId = "";
    localStorage.removeItem("bagorithm-token");
    localStorage.removeItem("bagorithm-user");
    localStorage.removeItem("bagorithm-trip");
    showAuth();
    const message = error.message === "Please sign in again."
      ? "Your saved session has expired. Please sign in again."
      : `Could not restore your saved sign-in: ${error.message}. Please sign in again.`;
    setFormError("#auth-message", message);
  }
}

window.addEventListener("pageshow", (event) => {
  if (event.persisted) void startApplication();
});

startApplication();
