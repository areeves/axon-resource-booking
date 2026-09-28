<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AdministrationView from './AdministrationView.vue';
import ReservationsView from './ReservationsView.vue';
import ResourcesView from './ResourcesView.vue';
import UtilizationView from './UtilizationView.vue';
import type {
  AppState,
  ConnectionForm,
  DateTimeRange,
  NewResourceDraft,
  QueryOption,
  QueryType,
  Reservation,
  ReservationQuery,
  Resource,
  ToastState,
  UtilizationForm,
  UtilizationRow
} from './types';

const views = [
  { id: 'resources', label: 'Resources', icon: '▦' },
  { id: 'reservations', label: 'Reservations', icon: '◷' },
  { id: 'utilization', label: 'Utilization', icon: '▤' },
  { id: 'admin', label: 'Administration', icon: '⚙' }
 ] as const;
type ViewId = (typeof views)[number]['id'];

const state = ref<AppState>({
  credentials: null,
  actorId: localStorage.getItem('fieldnote-actor-id') || crypto.randomUUID(),
  resources: [],
  selectedResourceId: null,
  queryType: 'user',
  reservations: []
});
const activeResources = ref<Resource[]>([]);
const activeView = ref<ViewId>('resources');
const resourceListTitle = ref('Active resources');
const connectionModal = ref(true);
const resourceModal = ref(false);
const connected = ref(false);
const connectionError = ref('');
const toast = ref<ToastState>({ message: '', error: false, visible: false });
let toastTimer: ReturnType<typeof setTimeout> | undefined;
const availability = ref<DateTimeRange>({ start: '', end: '' });
const reservationWindow = ref<DateTimeRange>({ start: '', end: '' });
const lookupId = ref('');
const connection = ref<ConnectionForm>({
  username: 'admin',
  password: 'admin',
  actorId: state.value.actorId
});
const newResource = ref<NewResourceDraft>({
  name: '',
  description: '',
  capacity: 1,
  location: ''
});
const reservationQuery = ref<ReservationQuery>({
  user: state.value.actorId,
  resource: '',
  id: ''
});
const utilizationForm = ref<UtilizationForm>({
  resourceId: '',
  from: '',
  to: ''
});
const utilizationRows = ref<UtilizationRow[]>([]);
const utilizationTitle = ref('Select a resource and date range');
const utilizationResourceId = ref('');
const eventData = ref<unknown>(null);
const reservationResultRan = ref(false);
const utilizationResultRan = ref(false);
const busy = ref(false);

const currentResource = computed<Resource | null>(() =>
  state.value.resources.find(
    (resource) => resource.resourceId === state.value.selectedResourceId
  ) ?? null
);
const queryOptions: Record<QueryType, QueryOption> = {
  user: { label: 'USER UUID', placeholder: 'User UUID' },
  resource: { label: 'RESOURCE UUID', placeholder: 'Resource UUID' },
  id: { label: 'RESERVATION UUID', placeholder: 'Reservation UUID' }
};
const queryLabel = computed(() => queryOptions[state.value.queryType]);
const activeViewLabel = computed(
  () => views.find((view) => view.id === activeView.value)?.label ?? ''
);
const reservationTitle = computed(() => {
  if (state.value.queryType === 'user') return 'User reservations';
  if (state.value.queryType === 'resource') return 'Resource reservations';
  return 'Reservation record';
});

interface ApiOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  userScoped?: boolean;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function notify(message: string, error = false): void {
  toast.value = { message, error, visible: true };
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toast.value.visible = false;
  }, 3600);
}

async function api<T>(path: string, options: ApiOptions = {}): Promise<{ data: T; status: number }> {
  if (!state.value.credentials) throw new Error('Connect to the API before continuing.');
  const { body, userScoped, ...requestOptions } = options;
  const headers = new Headers(requestOptions.headers);
  headers.set('Accept', 'application/json');
  const { username, password } = state.value.credentials;
  headers.set('Authorization', `Basic ${btoa(`${username}:${password}`)}`);
  if (userScoped) headers.set('X-User-Id', state.value.actorId);
  if (body !== undefined) headers.set('Content-Type', 'application/json');

  const response = await fetch(path, {
    ...requestOptions,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await response.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text) as unknown;
    } catch {
      data = text;
    }
  }

  if (!response.ok) {
    const record = typeof data === 'object' && data !== null
      ? data as Record<string, unknown>
      : null;
    const detail = record?.message ?? record?.error ?? data;
    const message = typeof detail === 'string' ? detail : JSON.stringify(detail);
    throw new Error(message || `${response.status} ${response.statusText}`);
  }

  return { data: data as T, status: response.status };
}

function toLocalInput(date: Date): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

function initializeDates(): void {
  const now = Date.now();
  availability.value.start = toLocalInput(new Date(now + 60 * 60 * 1000));
  availability.value.end = toLocalInput(new Date(now + 2 * 60 * 60 * 1000));
  reservationWindow.value.start = availability.value.start;
  reservationWindow.value.end = availability.value.end;
  const today = new Date().toISOString().slice(0, 10);
  utilizationForm.value.from = today;
  utilizationForm.value.to = today;
}

async function loadResources(): Promise<void> {
  const result = await api<Resource[]>('/resources');
  state.value.resources = result.data ?? [];
  activeResources.value = [...state.value.resources];
  resourceListTitle.value = 'Active resources';
  const selectionExists = state.value.resources.some(
    (resource) => resource.resourceId === state.value.selectedResourceId
  );
  if (!selectionExists) {
    state.value.selectedResourceId = state.value.resources[0]?.resourceId || null;
  }
}

async function selectResource(resourceId: string): Promise<void> {
  state.value.selectedResourceId = resourceId;
  const isLoaded = state.value.resources.some(
    (resource) => resource.resourceId === resourceId
  );
  if (isLoaded) return;

  try {
    const result = await api<Resource>(`/resources/${encodeURIComponent(resourceId)}`);
    if (!result.data) throw new Error('Resource was not found.');
    state.value.resources.unshift(result.data);
    state.value.selectedResourceId = result.data.resourceId;
  } catch (error) {
    notify(errorMessage(error), true);
  }
}

async function connect(): Promise<void> {
  connectionError.value = '';
  state.value.actorId = connection.value.actorId.trim();
  state.value.credentials = { username: connection.value.username, password: connection.value.password };
  try {
    await api<Resource[]>('/resources');
    localStorage.setItem('fieldnote-actor-id', state.value.actorId);
    connected.value = true;
    connectionModal.value = false;
    await loadResources();
    reservationQuery.value.user = state.value.actorId;
    notify('Connected to the resource booking API.');
  } catch (error) {
    state.value.credentials = null;
    const message = errorMessage(error);
    const rejected = message.includes('401') || message.includes('403');
    connectionError.value = rejected ? 'Credentials were not accepted.' : message;
  }
}

function generateActor(): void {
  connection.value.actorId = crypto.randomUUID();
}

function closeConnectionModal(): void {
  if (connected.value) connectionModal.value = false;
}

async function refreshResources(): Promise<void> {
  try {
    await loadResources();
  } catch (error) {
    notify(errorMessage(error), true);
  }
}

async function findAvailable(): Promise<void> {
  const start = new Date(availability.value.start);
  const end = new Date(availability.value.end);
  if (!(end > start)) {
    notify('End time must be after start time.', true);
    return;
  }

  try {
    const query = new URLSearchParams({
      start: start.toISOString(),
      end: end.toISOString()
    });
    const result = await api<Resource[]>(`/resources/available?${query}`);
    state.value.resources = result.data ?? [];
    state.value.selectedResourceId = null;
    resourceListTitle.value = 'Available resources';
  } catch (error) {
    notify(errorMessage(error), true);
  }
}

async function lookupResource(): Promise<void> {
  if (!lookupId.value.trim()) return;
  await selectResource(lookupId.value.trim());
}

function setQueryType(type: QueryType): void {
  state.value.queryType = type;
  reservationQuery.value[type] = type === 'user' ? state.value.actorId : '';
  reservationResultRan.value = false;
  state.value.reservations = [];
}

async function queryReservations(): Promise<void> {
  const value = reservationQuery.value[state.value.queryType].trim();
  if (!value) return;
  const paths: Record<QueryType, string> = {
    user: `/reservations/users/${encodeURIComponent(value)}`,
    resource: `/reservations/resources/${encodeURIComponent(value)}`,
    id: `/reservations/${encodeURIComponent(value)}`
  };

  try {
    if (state.value.queryType === 'id') {
      const result = await api<Reservation | null>(paths.id);
      state.value.reservations = result.data ? [result.data] : [];
      if (!result.data) notify('Reservation was not found.', true);
    } else {
      const result = await api<Reservation[]>(paths[state.value.queryType]);
      state.value.reservations = result.data ?? [];
    }
    reservationResultRan.value = true;
  } catch (error) {
    notify(errorMessage(error), true);
  }
}

async function loadEvents(reservationId: string): Promise<void> {
  try {
    const result = await api<unknown>(
      `/reservations/${encodeURIComponent(reservationId)}/events`
    );
    eventData.value = result.data;
    requestAnimationFrame(() => {
      document.querySelector('.event-section')?.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
      });
    });
  } catch (error) {
    notify(errorMessage(error), true);
  }
}

async function mutateReservation(
  operation: 'confirm' | 'cancel',
  reservation: Reservation
): Promise<void> {
  const message = operation === 'confirm'
    ? 'Reservation confirmed'
    : 'Reservation cancelled';
  const path = `/resources/${reservation.resourceId}/reservations/${reservation.reservationId}/${operation}`;

  await runAction(
    () => api<unknown>(path, { method: 'POST', userScoped: true }),
    message,
    queryReservations
  );
}

async function runAction(
  action: () => Promise<unknown>,
  message: string,
  after?: () => Promise<void>
): Promise<void> {
  busy.value = true;
  try {
    await action();
    notify(message);
    if (after) await after();
  } catch (error) {
    notify(errorMessage(error), true);
  } finally {
    busy.value = false;
  }
}

async function changeResourceStatus(resource: Resource): Promise<void> {
  const active = resource.status === 'ACTIVE';
  const operation = active ? 'deactivate' : 'reactivate';
  await runAction(
    () => api<unknown>(`/resources/${resource.resourceId}/${operation}`, {
      method: 'POST',
      userScoped: true
    }),
    active ? 'Resource deactivated' : 'Resource reactivated',
    async () => {
      await loadResources();
      if (!active) await selectResource(resource.resourceId);
    }
  );
}

async function updateResource(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const resource = currentResource.value;
  if (!resource) return;
  const form = new FormData(event.currentTarget as HTMLFormElement);
  const body = {
    name: String(form.get('name') ?? ''),
    description: String(form.get('description') ?? ''),
    location: String(form.get('location') ?? '')
  };

  await runAction(
    () => api<unknown>(`/resources/${resource.resourceId}`, {
      method: 'PUT',
      userScoped: true,
      body
    }),
    'Resource updated',
    async () => {
      await loadResources();
      await selectResource(resource.resourceId);
    }
  );
}

async function reserveResource(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const resource = currentResource.value;
  if (!resource) return;
  const form = new FormData(event.currentTarget as HTMLFormElement);
  const start = new Date(String(form.get('start') ?? ''));
  const end = new Date(String(form.get('end') ?? ''));
  if (!(end > start)) {
    notify('End time must be after start time.', true);
    return;
  }

  await runAction(
    () => api<unknown>(`/resources/${resource.resourceId}/reservations`, {
      method: 'POST',
      userScoped: true,
      body: {
        userId: state.value.actorId,
        start: start.toISOString(),
        end: end.toISOString()
      }
    }),
    'Reservation created',
    async () => {
      state.value.queryType = 'user';
      reservationQuery.value.user = state.value.actorId;
      activeView.value = 'reservations';
      await queryReservations();
    }
  );
}

async function createResource(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const form = new FormData(event.currentTarget as HTMLFormElement);
  const body = {
    name: String(form.get('name') ?? ''),
    description: String(form.get('description') ?? ''),
    capacity: Number(form.get('capacity')),
    location: String(form.get('location') ?? '')
  };

  await runAction(
    () => api<unknown>('/resources', {
      method: 'POST',
      userScoped: true,
      body
    }),
    'Resource created',
    async () => {
      newResource.value = {
        name: '',
        description: '',
        capacity: 1,
        location: ''
      };
      resourceModal.value = false;
      await loadResources();
    }
  );
}

async function showUtilization(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const { resourceId, from, to } = utilizationForm.value;
  try {
    const query = new URLSearchParams({ from, to });
    const path = `/resources/${encodeURIComponent(resourceId.trim())}/utilization?${query}`;
    const result = await api<UtilizationRow[]>(path);
    utilizationRows.value = result.data ?? [];
    utilizationResourceId.value = resourceId;
    utilizationResultRan.value = true;
    const rows = utilizationRows.value;
    if (rows.length === 0) {
      utilizationTitle.value = 'No dates';
      return;
    }

    const lastDate = rows.at(-1)?.date;
    utilizationTitle.value = rows.length > 1
      ? `${rows[0].date} to ${lastDate}`
      : rows[0].date;
  } catch (error) {
    notify(errorMessage(error), true);
  }
}

function goToUtilization(resource: Resource): void {
  activeView.value = 'utilization';
  utilizationForm.value.resourceId = resource.status === 'ACTIVE' ? resource.resourceId : '';
}

async function rebuildProjections(): Promise<void> {
  const confirmed = window.confirm(
    'Rebuild all projections from the event store? The current read models will be cleared while events replay.'
  );
  if (!confirmed) return;

  await runAction(
    () => api<unknown>('/admin/projections/rebuild', { method: 'POST' }),
    'Projection rebuild started. Queries may be incomplete during replay.'
  );
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString();
}

onMounted(initializeDates);
</script>

<template>
  <div class="app-shell">
    <aside class="rail">
      <a class="brand" href="#resources" aria-label="Fieldnote home">
        <span class="brand-mark">F</span>
        <span class="brand-name">fieldnote<span>RESOURCE OPS</span></span>
      </a>
      <div class="rail-label">WORKSPACE</div>
      <nav class="navigation" aria-label="Main navigation">
        <button
          v-for="view in views.slice(0, 3)"
          :key="view.id"
          class="nav-item"
          :class="{ active: activeView === view.id }"
          type="button"
          @click="activeView = view.id"
        >
          <span class="nav-icon">{{ view.icon }}</span>
          {{ view.label }}
          <span v-if="view.id === 'resources'" class="nav-count">
            {{ state.resources.length }}
          </span>
        </button>
      </nav>
      <div class="rail-label rail-label-bottom">SYSTEM</div>
      <nav class="navigation">
        <button
          class="nav-item"
          :class="{ active: activeView === 'admin' }"
          type="button"
          @click="activeView = 'admin'"
        >
          <span class="nav-icon">⚙</span>
          Administration
        </button>
      </nav>
      <div class="rail-foot">
        <span class="rail-dot"></span>
        <span>LOCAL WORKSPACE</span>
        <span class="rail-version">v1.0</span>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="breadcrumbs">
          <span>WORKSPACE</span>
          <span class="crumb-slash">/</span>
          <strong>{{ activeViewLabel }}</strong>
        </div>
        <div class="top-actions">
          <span class="connection-state" :class="{ connected }">
            <i></i>{{ connected ? 'Connected' : 'Not connected' }}
          </span>
          <button
            class="actor-chip"
            type="button"
            title="Configure API connection"
            @click="connectionModal = true"
          >
            <span class="actor-avatar">A</span>
            <span>
              {{ connected ? `${state.actorId.slice(0, 8)}…` : 'Set up connection' }}
            </span>
            <span class="chevron">⌄</span>
          </button>
        </div>
      </header>

      <ResourcesView
        v-if="activeView === 'resources'"
        :state="state"
        :current-resource="currentResource"
        :active-resources="activeResources"
        :resource-list-title="resourceListTitle"
        :availability="availability"
        :lookup-id="lookupId"
        :reservation-window="reservationWindow"
        :busy="busy"
        :connected="connected"
        @create-resource="resourceModal = true"
        @find-available="findAvailable"
        @refresh="refreshResources"
        @lookup-resource="lookupResource"
        @update:lookup-id="lookupId = $event"
        @select-resource="selectResource"
        @go-to-utilization="goToUtilization"
        @change-resource-status="changeResourceStatus"
        @update-resource="updateResource"
        @reserve-resource="reserveResource"
      />
      <ReservationsView
        v-else-if="activeView === 'reservations'"
        :state="state"
        :active-resources="activeResources"
        :query-label="queryLabel"
        :reservation-title="reservationTitle"
        :reservation-query="reservationQuery"
        :reservation-result-ran="reservationResultRan"
        :event-data="eventData"
        :format-date="formatDate"
        @set-query-type="setQueryType"
        @query-reservations="queryReservations"
        @load-events="loadEvents"
        @reservation-action="mutateReservation"
        @close-events="eventData = null"
      />
      <UtilizationView
        v-else-if="activeView === 'utilization'"
        :active-resources="activeResources"
        :utilization-form="utilizationForm"
        :utilization-result-ran="utilizationResultRan"
        :utilization-title="utilizationTitle"
        :utilization-rows="utilizationRows"
        :utilization-resource-id="utilizationResourceId"
        @show-utilization="showUtilization"
      />
      <AdministrationView
        v-else
        :busy="busy"
        @rebuild-projections="rebuildProjections"
      />
    </main>
  </div>

  <div
    class="toast"
    :class="{ visible: toast.visible, error: toast.error }"
    role="status"
    aria-live="polite"
  >
    {{ toast.message }}
  </div>

  <div
    v-if="connectionModal"
    class="modal-backdrop"
    @click.self="closeConnectionModal"
  >
    <section
      class="connection-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="connection-title"
    >
      <div class="dialog-mark">F</div>
      <div class="eyebrow">FIELDNOTE / API ACCESS</div>
      <h2 id="connection-title">Connect to workspace</h2>
      <p class="dialog-copy">
        Sign in with your API credentials. The password stays in this browser session.
      </p>
      <form class="connection-form" @submit.prevent="connect">
        <label>
          Username
          <input v-model="connection.username" autocomplete="username" required>
        </label>
        <label>
          Password
          <input
            v-model="connection.password"
            type="password"
            autocomplete="current-password"
            required
          >
        </label>
        <label>
          Actor UUID <span class="label-note">used for commands</span>
          <div class="actor-field">
            <input v-model="connection.actorId" required>
            <button class="text-button" type="button" @click="generateActor">
              Generate
            </button>
          </div>
        </label>
        <button class="button button-primary button-wide" type="submit">
          Connect <span>→</span>
        </button>
        <div class="form-error">{{ connectionError }}</div>
      </form>
      <p class="dialog-footnote">Basic authentication · same-origin API</p>
    </section>
  </div>

  <div
    v-if="resourceModal"
    class="modal-backdrop"
    @click.self="resourceModal = false"
  >
    <section
      class="form-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="resource-modal-title"
    >
      <button
        class="modal-close"
        type="button"
        aria-label="Close"
        @click="resourceModal = false"
      >
        ×
      </button>
      <div class="eyebrow">INVENTORY / NEW ENTRY</div>
      <h2 id="resource-modal-title">Create resource</h2>
      <form class="stack-form" @submit="createResource">
        <label>
          Name
          <input v-model="newResource.name" name="name" maxlength="120" required>
        </label>
        <label>
          Description
          <textarea v-model="newResource.description" name="description" rows="3"></textarea>
        </label>
        <div class="form-pair">
          <label>
            Capacity
            <input
              v-model="newResource.capacity"
              name="capacity"
              type="number"
              min="1"
              required
            >
          </label>
          <label>
            Location
            <input v-model="newResource.location" name="location" required>
          </label>
        </div>
        <div class="dialog-actions">
          <button
            class="button button-quiet"
            type="button"
            @click="resourceModal = false"
          >
            Cancel
          </button>
          <button class="button button-primary" type="submit" :disabled="busy">
            Create resource
          </button>
        </div>
      </form>
    </section>
  </div>
</template>