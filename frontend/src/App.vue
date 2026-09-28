<script setup>
import { computed, onMounted, ref } from 'vue';

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

      <section v-if="activeView === 'resources'" class="view active">
        <div class="page-heading"><div><div class="eyebrow">INVENTORY / COMMANDS</div><h1>Resources</h1><p class="page-subtitle">Manage spaces and make reservations.</p></div><button class="button button-primary" type="button" @click="resourceModal = true"><span>＋</span> New resource</button></div>
        <form class="filter-bar" @submit.prevent="findAvailable"><span class="filter-label">CHECK AVAILABILITY</span><label>From <input v-model="availability.start" type="datetime-local" required></label><label>To <input v-model="availability.end" type="datetime-local" required></label><button class="button button-dark" type="submit">Find available</button><button class="text-button" type="button" @click="loadResources().catch((error) => notify(error.message, true))">All active resources</button></form>
        <div class="resource-layout">
          <section class="resource-list-panel">
            <div class="section-bar"><div><span class="section-kicker">DIRECTORY</span><h2>{{ resourceListTitle }}</h2></div><button class="icon-button" type="button" title="Refresh resources" aria-label="Refresh resources" @click="loadResources().catch((error) => notify(error.message, true))">↻</button></div>
            <div class="resource-list">
              <div v-if="!state.resources.length" class="empty-state">{{ connected ? 'No resources in this result.' : 'Connect to load resources.' }}</div>
              <button v-for="resource in state.resources" :key="resource.resourceId" type="button" class="resource-item" :class="{ selected: resource.resourceId === state.selectedResourceId }" @click="selectResource(resource.resourceId)">
                <span class="resource-item-top"><span class="resource-item-name">{{ resource.name }}</span><span class="status-pill" :class="{ inactive: resource.status !== 'ACTIVE' }">{{ resource.status }}</span></span>
                <span class="resource-item-meta"><span>{{ resource.location }}</span><span>·</span><span>Capacity {{ resource.capacity }}</span></span>
              </button>
            </div>
            <form class="lookup-form" @submit.prevent="lookupResource"><label for="resource-lookup">OPEN ACTIVE RESOURCE</label><div class="inline-field"><select id="resource-lookup" v-model="lookupId" required><option value="" disabled>Select a resource</option><option v-for="resource in activeResources" :key="resource.resourceId" :value="resource.resourceId">{{ resource.name }} · {{ resource.location }} · {{ resource.resourceId.slice(0, 8) }}…</option></select><button class="icon-button" type="submit" aria-label="Open resource">→</button></div></form>
          </section>
          <section class="resource-detail-panel">
            <template v-if="currentResource">
              <div class="detail-heading"><div><div class="eyebrow">RESOURCE / {{ currentResource.resourceId }}</div><h2>{{ currentResource.name }}</h2><div class="detail-subline">{{ currentResource.location }} · capacity {{ currentResource.capacity }}</div></div>
                <div class="detail-actions"><span class="status-pill" :class="{ inactive: currentResource.status !== 'ACTIVE' }">{{ currentResource.status }}</span><button class="small-action" type="button" @click="goToUtilization(currentResource)">Utilization</button><button class="small-action" :class="{ warn: currentResource.status === 'ACTIVE' }" type="button" :disabled="busy" @click="changeResourceStatus(currentResource)">{{ currentResource.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate' }}</button></div>
              </div>
              <form class="resource-fields" @submit="updateResource"><label>Name<input name="name" :value="currentResource.name" required></label><label>Description<textarea name="description" rows="2">{{ currentResource.description || '' }}</textarea></label><label>Location<input name="location" :value="currentResource.location" required></label><label>Resource ID<input :value="currentResource.resourceId" readonly></label><label>Capacity<input :value="currentResource.capacity" readonly></label><button class="small-action detail-save-button" type="submit" :disabled="busy">Save changes</button></form>
              <section class="reserve-strip"><h3>Reserve this resource</h3><form class="reserve-form-grid" @submit="reserveResource"><label>Start<input v-model="reservationWindow.start" name="start" type="datetime-local" required></label><label>End<input v-model="reservationWindow.end" name="end" type="datetime-local" required></label><label>Booking for<input :value="state.actorId" readonly></label><button class="button button-primary" type="submit" :disabled="busy">＋ Reserve</button></form></section>
            </template>
            <div v-else class="empty-detail"><span class="empty-glyph">▦</span><h2>{{ state.resources.length ? 'Select a resource' : 'No resource selected' }}</h2><p>{{ state.resources.length ? 'Choose a resource from the directory to view details and actions.' : 'Create a resource or adjust your availability window.' }}</p></div>
          </section>
        </div>
      </section>

      <section v-else-if="activeView === 'reservations'" class="view active">
        <div class="page-heading"><div><div class="eyebrow">BOOKING / LOOKUP</div><h1>Reservations</h1><p class="page-subtitle">Find bookings by user, resource, or reservation ID.</p></div></div>
        <div class="query-tabs" role="tablist" aria-label="Reservation lookup type"><button v-for="item in [{ id: 'user', label: 'By user' }, { id: 'resource', label: 'By resource' }, { id: 'id', label: 'By reservation ID' }]" :key="item.id" class="query-tab" :class="{ active: state.queryType === item.id }" type="button" @click="setQueryType(item.id)">{{ item.label }}</button></div>
        <form class="query-form" @submit.prevent="queryReservations"><label class="query-input-label">{{ queryLabel.label }}</label><div class="query-controls"><select v-if="state.queryType === 'resource'" v-model="reservationQuery.resource" required><option value="" disabled>Select an active resource</option><option v-for="resource in activeResources" :key="resource.resourceId" :value="resource.resourceId">{{ resource.name }} · {{ resource.location }} · {{ resource.resourceId.slice(0, 8) }}…</option></select><input v-else v-model="reservationQuery[state.queryType]" :placeholder="queryLabel.placeholder" required><button class="button button-primary" type="submit">Search reservations <span>→</span></button></div></form>
        <section class="results-section"><div class="section-bar"><div><span class="section-kicker">QUERY RESULTS</span><h2>{{ reservationTitle }}</h2></div><span class="result-count">{{ reservationResultRan ? `${state.reservations.length} ${state.reservations.length === 1 ? 'record' : 'records'}` : '' }}</span></div>
          <div v-if="!state.reservations.length" class="empty-state">{{ reservationResultRan ? 'No reservations found for this query.' : 'Run a lookup to see reservation records.' }}</div>
          <div v-else class="reservation-table-wrap"><table><thead><tr><th>RESERVATION</th><th>RESOURCE</th><th>USER</th><th>START</th><th>END</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody><tr v-for="reservation in state.reservations" :key="reservation.reservationId"><td><code :title="reservation.reservationId">{{ reservation.reservationId }}</code></td><td><code :title="reservation.resourceId">{{ reservation.resourceId }}</code></td><td><code :title="reservation.userId">{{ reservation.userId }}</code></td><td>{{ formatDate(reservation.start) }}</td><td>{{ formatDate(reservation.end) }}</td><td><span class="status-pill" :class="{ inactive: reservation.status === 'CANCELLED' }">{{ reservation.status }}</span></td><td><div class="row-actions"><button type="button" @click="loadEvents(reservation.reservationId)">Events</button><button v-if="reservation.status === 'PENDING'" type="button" @click="mutateReservation('confirm', reservation)">Confirm</button><button v-if="['PENDING', 'CONFIRMED'].includes(reservation.status)" type="button" @click="mutateReservation('cancel', reservation)">Cancel</button></div></td></tr></tbody></table></div>
        </section>
        <section v-if="eventData" class="event-section"><div class="section-bar"><div><span class="section-kicker">EVENT STORE</span><h2>Reservation history</h2></div><button class="icon-button" type="button" aria-label="Close event history" @click="eventData = null">×</button></div><pre class="json-output">{{ JSON.stringify(eventData, null, 2) }}</pre></section>
      </section>

      <section v-else-if="activeView === 'utilization'" class="view active">
        <div class="page-heading"><div><div class="eyebrow">CAPACITY / DAILY PROJECTION</div><h1>Utilization</h1><p class="page-subtitle">Review occupied hours and capacity across a date range.</p></div></div>
        <form class="utilization-form" @submit="showUtilization"><label>Resource<select v-model="utilizationForm.resourceId" required><option value="" disabled>Select an active resource</option><option v-for="resource in activeResources" :key="resource.resourceId" :value="resource.resourceId">{{ resource.name }} · {{ resource.location }} · {{ resource.resourceId.slice(0, 8) }}…</option></select></label><label>From<input v-model="utilizationForm.from" type="date" required></label><label>To<input v-model="utilizationForm.to" type="date" required></label><button class="button button-primary" type="submit">View utilization <span>→</span></button></form>
        <section class="utilization-results"><div class="section-bar"><div><span class="section-kicker">DAILY BREAKDOWN</span><h2>{{ utilizationResultRan ? utilizationTitle : 'Select a resource and date range' }}</h2></div><span class="result-count">{{ utilizationResultRan ? `${utilizationRows.length} ${utilizationRows.length === 1 ? 'day' : 'days'} · ${utilizationResourceId.slice(0, 8)}…` : '' }}</span></div>
          <div v-if="!utilizationRows.length" class="empty-state">{{ utilizationResultRan ? 'No utilization records in this range.' : 'Utilization is shown in UTC. Date ranges can include up to 366 days.' }}</div>
          <div v-else class="utilization-table-wrap"><table><thead><tr><th>UTC DATE</th><th>RESERVATIONS</th><th>OCCUPIED HOURS</th><th>CAPACITY USED</th></tr></thead><tbody><tr v-for="row in utilizationRows" :key="row.date"><td>{{ row.date }}</td><td>{{ row.reservationCount }}</td><td>{{ row.occupiedHours }} h</td><td><div class="util-bar"><span class="util-track"><span class="util-fill" :style="{ width: `${Math.min(100, Math.max(0, Number(row.utilizationPercent) || 0))}%` }"></span></span><span class="util-value">{{ Number(row.utilizationPercent || 0).toFixed(2) }}%</span></div></td></tr></tbody></table></div>
        </section>
      </section>

      <section v-else class="view active">
        <div class="page-heading"><div><div class="eyebrow">SYSTEM / MAINTENANCE</div><h1>Administration</h1><p class="page-subtitle">Projection controls for the event-sourced read models.</p></div></div>
        <div class="admin-row"><div><span class="section-kicker">READ MODEL</span><h2>Rebuild projections</h2><p>Clears the resource, reservation, and utilization projections, then replays events from the event store. Queries may return incomplete results while replay runs.</p></div><button class="button button-danger" type="button" :disabled="busy" @click="rebuildProjections">↻ &nbsp; Rebuild projections</button></div>
        <div class="admin-note"><span class="note-mark">i</span><p>This operation requires an administrator account and returns as soon as event replay begins.</p></div>
      </section>
    </main>
  </div>

  <div class="toast" :class="{ visible: toast.visible, error: toast.error }" role="status" aria-live="polite">{{ toast.message }}</div>
  <div v-if="connectionModal" class="modal-backdrop" @click.self="connected && (connectionModal = false)"><section class="connection-dialog" role="dialog" aria-modal="true" aria-labelledby="connection-title"><div class="dialog-mark">F</div><div class="eyebrow">FIELDNOTE / API ACCESS</div><h2 id="connection-title">Connect to workspace</h2><p class="dialog-copy">Sign in with your API credentials. The password stays in this browser session.</p><form class="connection-form" @submit.prevent="connect"><label>Username<input v-model="connection.username" autocomplete="username" required></label><label>Password<input v-model="connection.password" type="password" autocomplete="current-password" required></label><label>Actor UUID <span class="label-note">used for commands</span><div class="actor-field"><input v-model="connection.actorId" required><button class="text-button" type="button" @click="generateActor">Generate</button></div></label><button class="button button-primary button-wide" type="submit">Connect <span>→</span></button><div class="form-error">{{ connectionError }}</div></form><p class="dialog-footnote">Basic authentication · same-origin API</p></section></div>
  <div v-if="resourceModal" class="modal-backdrop" @click.self="resourceModal = false"><section class="form-dialog" role="dialog" aria-modal="true" aria-labelledby="resource-modal-title"><button class="modal-close" type="button" aria-label="Close" @click="resourceModal = false">×</button><div class="eyebrow">INVENTORY / NEW ENTRY</div><h2 id="resource-modal-title">Create resource</h2><form class="stack-form" @submit="createResource"><label>Name<input v-model="newResource.name" name="name" maxlength="120" required></label><label>Description<textarea v-model="newResource.description" name="description" rows="3"></textarea></label><div class="form-pair"><label>Capacity<input v-model="newResource.capacity" name="capacity" type="number" min="1" required></label><label>Location<input v-model="newResource.location" name="location" required></label></div><div class="dialog-actions"><button class="button button-quiet" type="button" @click="resourceModal = false">Cancel</button><button class="button button-primary" type="submit" :disabled="busy">Create resource</button></div></form></section></div>
</template>