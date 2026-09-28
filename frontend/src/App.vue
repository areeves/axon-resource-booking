<script setup>
import { computed, onMounted, ref } from 'vue';
import AdministrationView from './AdministrationView.vue';
import ReservationsView from './ReservationsView.vue';
import ResourcesView from './ResourcesView.vue';
import UtilizationView from './UtilizationView.vue';

const views = [
  { id: 'resources', label: 'Resources', icon: '▦' },
  { id: 'reservations', label: 'Reservations', icon: '◷' },
  { id: 'utilization', label: 'Utilization', icon: '▤' },
  { id: 'admin', label: 'Administration', icon: '⚙' }
];
const state = ref({
  credentials: null,
  actorId: localStorage.getItem('fieldnote-actor-id') || crypto.randomUUID(),
  resources: [],
  selectedResourceId: null,
  queryType: 'user',
  reservations: []
});
const activeResources = ref([]);
const activeView = ref('resources');
const resourceListTitle = ref('Active resources');
const connectionModal = ref(true);
const resourceModal = ref(false);
const connected = ref(false);
const connectionError = ref('');
const toast = ref({ message: '', error: false, visible: false });
const toastTimer = ref(null);
const availability = ref({ start: '', end: '' });
const reservationWindow = ref({ start: '', end: '' });
const lookupId = ref('');
const connection = ref({ username: 'admin', password: 'admin', actorId: state.value.actorId });
const newResource = ref({ name: '', description: '', capacity: 1, location: '' });
const reservationQuery = ref({ user: state.value.actorId, resource: '', id: '' });
const utilizationForm = ref({ resourceId: '', from: '', to: '' });
const utilizationRows = ref([]);
const utilizationTitle = ref('Select a resource and date range');
const utilizationResourceId = ref('');
const eventData = ref(null);
const reservationResultRan = ref(false);
const utilizationResultRan = ref(false);
const busy = ref(false);

const currentResource = computed(() => state.value.resources.find((item) => item.resourceId === state.value.selectedResourceId));
const queryOptions = {
  user: { label: 'USER UUID', placeholder: 'User UUID' },
  resource: { label: 'RESOURCE UUID', placeholder: 'Resource UUID' },
  id: { label: 'RESERVATION UUID', placeholder: 'Reservation UUID' }
};
const queryLabel = computed(() => queryOptions[state.value.queryType]);
const reservationTitle = computed(() => state.value.queryType === 'user' ? 'User reservations' : state.value.queryType === 'resource' ? 'Resource reservations' : 'Reservation record');

function notify(message, error = false) {
  toast.value = { message, error, visible: true };
  clearTimeout(toastTimer.value);
  toastTimer.value = setTimeout(() => { toast.value.visible = false; }, 3600);
}

async function api(path, options = {}) {
  if (!state.value.credentials) throw new Error('Connect to the API before continuing.');
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');
  headers.set('Authorization', `Basic ${btoa(`${state.value.credentials.username}:${state.value.credentials.password}`)}`);
  if (options.userScoped) headers.set('X-User-Id', state.value.actorId);
  if (options.body !== undefined) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers, body: options.body === undefined ? undefined : JSON.stringify(options.body) });
  const text = await response.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }
  if (!response.ok) {
    const detail = typeof data === 'object' && data ? data.message || data.error || JSON.stringify(data) : data;
    throw new Error(detail || `${response.status} ${response.statusText}`);
  }
  return { data, status: response.status };
}

function toLocalInput(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

function initializeDates() {
  const now = Date.now();
  availability.value.start = toLocalInput(new Date(now + 60 * 60 * 1000));
  availability.value.end = toLocalInput(new Date(now + 2 * 60 * 60 * 1000));
  reservationWindow.value.start = availability.value.start;
  reservationWindow.value.end = availability.value.end;
  const today = new Date().toISOString().slice(0, 10);
  utilizationForm.value.from = today;
  utilizationForm.value.to = today;
}

async function loadResources() {
  const result = await api('/resources');
  state.value.resources = result.data || [];
  activeResources.value = [...state.value.resources];
  resourceListTitle.value = 'Active resources';
  if (!state.value.resources.some((item) => item.resourceId === state.value.selectedResourceId)) {
    state.value.selectedResourceId = state.value.resources[0]?.resourceId || null;
  }
}

async function selectResource(resourceId) {
  state.value.selectedResourceId = resourceId;
  if (state.value.resources.some((item) => item.resourceId === resourceId)) return;
  try {
    const result = await api(`/resources/${encodeURIComponent(resourceId)}`);
    if (!result.data) throw new Error('Resource was not found.');
    state.value.resources.unshift(result.data);
    state.value.selectedResourceId = result.data.resourceId;
  } catch (error) { notify(error.message, true); }
}

async function connect() {
  connectionError.value = '';
  state.value.actorId = connection.value.actorId.trim();
  state.value.credentials = { username: connection.value.username, password: connection.value.password };
  try {
    await api('/resources');
    localStorage.setItem('fieldnote-actor-id', state.value.actorId);
    connected.value = true;
    connectionModal.value = false;
    await loadResources();
    reservationQuery.value.user = state.value.actorId;
    notify('Connected to the resource booking API.');
  } catch (error) {
    state.value.credentials = null;
    connectionError.value = error.message.includes('401') || error.message.includes('403') ? 'Credentials were not accepted.' : error.message;
  }
}

function generateActor() {
  connection.value.actorId = crypto.randomUUID();
}

async function findAvailable() {
  const start = new Date(availability.value.start);
  const end = new Date(availability.value.end);
  if (!(end > start)) return notify('End time must be after start time.', true);
  try {
    const result = await api(`/resources/available?start=${encodeURIComponent(start.toISOString())}&end=${encodeURIComponent(end.toISOString())}`);
    state.value.resources = result.data || [];
    state.value.selectedResourceId = null;
    resourceListTitle.value = 'Available resources';
  } catch (error) { notify(error.message, true); }
}

async function lookupResource() {
  if (!lookupId.value.trim()) return;
  await selectResource(lookupId.value.trim());
}

function setQueryType(type) {
  state.value.queryType = type;
  reservationQuery.value[type] = type === 'user' ? state.value.actorId : '';
  reservationResultRan.value = false;
  state.value.reservations = [];
}

async function queryReservations() {
  const value = reservationQuery.value[state.value.queryType].trim();
  if (!value) return;
  const paths = {
    user: `/reservations/users/${encodeURIComponent(value)}`,
    resource: `/reservations/resources/${encodeURIComponent(value)}`,
    id: `/reservations/${encodeURIComponent(value)}`
  };
  try {
    const result = await api(paths[state.value.queryType]);
    state.value.reservations = state.value.queryType === 'id' ? (result.data ? [result.data] : []) : result.data || [];
    reservationResultRan.value = true;
    if (state.value.queryType === 'id' && !result.data) notify('Reservation was not found.', true);
  } catch (error) { notify(error.message, true); }
}

async function loadEvents(reservationId) {
  try {
    const result = await api(`/reservations/${reservationId}/events`);
    eventData.value = result.data;
    requestAnimationFrame(() => document.querySelector('.event-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  } catch (error) { notify(error.message, true); }
}

async function mutateReservation(operation, reservation) {
  await runAction(() => api(`/resources/${reservation.resourceId}/reservations/${reservation.reservationId}/${operation}`, { method: 'POST', userScoped: true }), `Reservation ${operation === 'confirm' ? 'confirmed' : 'cancelled'}`, queryReservations);
}

async function runAction(action, message, after) {
  busy.value = true;
  try {
    await action();
    notify(message);
    if (after) await after();
  } catch (error) { notify(error.message, true); }
  finally { busy.value = false; }
}

async function changeResourceStatus(resource) {
  const active = resource.status === 'ACTIVE';
  const operation = active ? 'deactivate' : 'reactivate';
  await runAction(() => api(`/resources/${resource.resourceId}/${operation}`, { method: 'POST', userScoped: true }), active ? 'Resource deactivated' : 'Resource reactivated', async () => {
    await loadResources();
    if (!active) await selectResource(resource.resourceId);
  });
}

async function updateResource(event) {
  event.preventDefault();
  const resource = currentResource.value;
  if (!resource) return;
  const form = new FormData(event.currentTarget);
  const body = Object.fromEntries(form);
  await runAction(() => api(`/resources/${resource.resourceId}`, { method: 'PUT', userScoped: true, body }), 'Resource updated', async () => {
    await loadResources();
    await selectResource(resource.resourceId);
  });
}

async function reserveResource(event) {
  event.preventDefault();
  const resource = currentResource.value;
  if (!resource) return;
  const form = new FormData(event.currentTarget);
  const start = new Date(form.get('start'));
  const end = new Date(form.get('end'));
  if (!(end > start)) return notify('End time must be after start time.', true);
  await runAction(() => api(`/resources/${resource.resourceId}/reservations`, {
    method: 'POST', userScoped: true,
    body: { userId: state.value.actorId, start: start.toISOString(), end: end.toISOString() }
  }), 'Reservation created', async () => {
    state.value.queryType = 'user';
    reservationQuery.value.user = state.value.actorId;
    activeView.value = 'reservations';
    await queryReservations();
  });
}

async function createResource(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const body = Object.fromEntries(form);
  body.capacity = Number(body.capacity);
  await runAction(() => api('/resources', { method: 'POST', userScoped: true, body }), 'Resource created', async () => {
    newResource.value = { name: '', description: '', capacity: 1, location: '' };
    resourceModal.value = false;
    await loadResources();
  });
}

async function showUtilization(event) {
  event.preventDefault();
  const { resourceId, from, to } = utilizationForm.value;
  try {
    const result = await api(`/resources/${encodeURIComponent(resourceId.trim())}/utilization?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
    utilizationRows.value = result.data || [];
    utilizationResourceId.value = resourceId;
    utilizationResultRan.value = true;
    const rows = utilizationRows.value;
    utilizationTitle.value = rows.length ? `${rows[0].date}${rows.length > 1 ? ` to ${rows.at(-1).date}` : ''}` : 'No dates';
  } catch (error) { notify(error.message, true); }
}

function goToUtilization(resource) {
  activeView.value = 'utilization';
  utilizationForm.value.resourceId = resource.status === 'ACTIVE' ? resource.resourceId : '';
}

async function rebuildProjections() {
  if (!window.confirm('Rebuild all projections from the event store? The current read models will be cleared while events replay.')) return;
  await runAction(() => api('/admin/projections/rebuild', { method: 'POST' }), 'Projection rebuild started. Queries may be incomplete during replay.');
}

function formatDate(value) {
  return new Date(value).toLocaleString();
}

onMounted(initializeDates);
</script>

<template>
  <div class="app-shell">
    <aside class="rail">
      <a class="brand" href="#resources" aria-label="Fieldnote home">
        <span class="brand-mark">F</span><span class="brand-name">fieldnote<span>RESOURCE OPS</span></span>
      </a>
      <div class="rail-label">WORKSPACE</div>
      <nav class="navigation" aria-label="Main navigation">
        <button v-for="view in views.slice(0, 3)" :key="view.id" class="nav-item" :class="{ active: activeView === view.id }" type="button" @click="activeView = view.id">
          <span class="nav-icon">{{ view.icon }}</span>{{ view.label }}<span v-if="view.id === 'resources'" class="nav-count">{{ state.resources.length }}</span>
        </button>
      </nav>
      <div class="rail-label rail-label-bottom">SYSTEM</div>
      <nav class="navigation"><button class="nav-item" :class="{ active: activeView === 'admin' }" type="button" @click="activeView = 'admin'"><span class="nav-icon">⚙</span>Administration</button></nav>
      <div class="rail-foot"><span class="rail-dot"></span><span>LOCAL WORKSPACE</span><span class="rail-version">v1.0</span></div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="breadcrumbs"><span>WORKSPACE</span><span class="crumb-slash">/</span><strong>{{ views.find((view) => view.id === activeView)?.label }}</strong></div>
        <div class="top-actions">
          <span class="connection-state" :class="{ connected }"><i></i>{{ connected ? 'Connected' : 'Not connected' }}</span>
          <button class="actor-chip" type="button" title="Configure API connection" @click="connectionModal = true"><span class="actor-avatar">A</span><span>{{ connected ? `${state.actorId.slice(0, 8)}…` : 'Set up connection' }}</span><span class="chevron">⌄</span></button>
        </div>
      </header>

      <ResourcesView v-if="activeView === 'resources'" :state="state" :current-resource="currentResource" :active-resources="activeResources" :resource-list-title="resourceListTitle" :availability="availability" :lookup-id="lookupId" :reservation-window="reservationWindow" :busy="busy" :connected="connected" @create-resource="resourceModal = true" @find-available="findAvailable" @refresh="loadResources().catch((error) => notify(error.message, true))" @lookup-resource="lookupResource" @update:lookup-id="lookupId = $event" @select-resource="selectResource" @go-to-utilization="goToUtilization" @change-resource-status="changeResourceStatus" @update-resource="updateResource" @reserve-resource="reserveResource" />
      <ReservationsView v-else-if="activeView === 'reservations'" :state="state" :active-resources="activeResources" :query-label="queryLabel" :reservation-title="reservationTitle" :reservation-query="reservationQuery" :reservation-result-ran="reservationResultRan" :event-data="eventData" :format-date="formatDate" @set-query-type="setQueryType" @query-reservations="queryReservations" @load-events="loadEvents" @reservation-action="mutateReservation" @close-events="eventData = null" />
      <UtilizationView v-else-if="activeView === 'utilization'" :active-resources="activeResources" :utilization-form="utilizationForm" :utilization-result-ran="utilizationResultRan" :utilization-title="utilizationTitle" :utilization-rows="utilizationRows" :utilization-resource-id="utilizationResourceId" @show-utilization="showUtilization" />
      <AdministrationView v-else :busy="busy" @rebuild-projections="rebuildProjections" />
    </main>
  </div>

  <div class="toast" :class="{ visible: toast.visible, error: toast.error }" role="status" aria-live="polite">{{ toast.message }}</div>
  <div v-if="connectionModal" class="modal-backdrop" @click.self="connected && (connectionModal = false)"><section class="connection-dialog" role="dialog" aria-modal="true" aria-labelledby="connection-title"><div class="dialog-mark">F</div><div class="eyebrow">FIELDNOTE / API ACCESS</div><h2 id="connection-title">Connect to workspace</h2><p class="dialog-copy">Sign in with your API credentials. The password stays in this browser session.</p><form class="connection-form" @submit.prevent="connect"><label>Username<input v-model="connection.username" autocomplete="username" required></label><label>Password<input v-model="connection.password" type="password" autocomplete="current-password" required></label><label>Actor UUID <span class="label-note">used for commands</span><div class="actor-field"><input v-model="connection.actorId" required><button class="text-button" type="button" @click="generateActor">Generate</button></div></label><button class="button button-primary button-wide" type="submit">Connect <span>→</span></button><div class="form-error">{{ connectionError }}</div></form><p class="dialog-footnote">Basic authentication · same-origin API</p></section></div>
  <div v-if="resourceModal" class="modal-backdrop" @click.self="resourceModal = false"><section class="form-dialog" role="dialog" aria-modal="true" aria-labelledby="resource-modal-title"><button class="modal-close" type="button" aria-label="Close" @click="resourceModal = false">×</button><div class="eyebrow">INVENTORY / NEW ENTRY</div><h2 id="resource-modal-title">Create resource</h2><form class="stack-form" @submit="createResource"><label>Name<input v-model="newResource.name" name="name" maxlength="120" required></label><label>Description<textarea v-model="newResource.description" name="description" rows="3"></textarea></label><div class="form-pair"><label>Capacity<input v-model="newResource.capacity" name="capacity" type="number" min="1" required></label><label>Location<input v-model="newResource.location" name="location" required></label></div><div class="dialog-actions"><button class="button button-quiet" type="button" @click="resourceModal = false">Cancel</button><button class="button button-primary" type="submit" :disabled="busy">Create resource</button></div></form></section></div>
</template>