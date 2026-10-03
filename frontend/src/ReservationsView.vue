<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useApi } from './composables/useApi';
import { useResources } from './composables/useResources';
import { useToast } from './composables/useToast';
import type {
  AppState,
  QueryOption,
  QueryType,
  Reservation
} from './types';

const props = defineProps<{ refreshKey: number }>();
const queryTabs = [
  { id: 'user', label: 'By user' },
  { id: 'resource', label: 'By resource' },
  { id: 'id', label: 'By reservation ID' }
] as const;

const { api, actorId } = useApi();
const { resources } = useResources();
const { notify } = useToast();
const state = reactive<AppState>({
  credentials: null,
  actorId: actorId.value,
  resources: [],
  selectedResourceId: null,
  queryType: 'user',
  reservations: []
});
const activeResources = computed(() =>
  (resources.value ?? []).filter((resource) => resource.status === 'ACTIVE')
);
const reservationQuery = reactive({ user: actorId.value, resource: '', id: '' });
const reservationResultRan = ref(false);
const eventData = ref<unknown>(null);
const queryOptions: Record<QueryType, QueryOption> = {
  user: { label: 'USER UUID', placeholder: 'User UUID' },
  resource: { label: 'RESOURCE UUID', placeholder: 'Resource UUID' },
  id: { label: 'RESERVATION UUID', placeholder: 'Reservation UUID' }
};
const queryLabel = computed(() => queryOptions[state.queryType]);
const reservationTitle = computed(() => {
  if (state.queryType === 'user') return 'User reservations';
  if (state.queryType === 'resource') return 'Resource reservations';
  return 'Reservation record';
});

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function setQueryType(type: QueryType): void {
  state.queryType = type;
  reservationQuery[type] = type === 'user' ? actorId.value : '';
  reservationResultRan.value = false;
  state.reservations = [];
}

async function queryReservations(): Promise<void> {
  const value = reservationQuery[state.queryType].trim();
  if (!value) return;
  const paths: Record<QueryType, string> = {
    user: `/reservations/users/${encodeURIComponent(value)}`,
    resource: `/reservations/resources/${encodeURIComponent(value)}`,
    id: `/reservations/${encodeURIComponent(value)}`
  };
  try {
    if (state.queryType === 'id') {
      const result = await api<Reservation | null>(paths.id);
      state.reservations = result.data ? [result.data] : [];
      if (!result.data) notify('Reservation was not found.', true);
    } else {
      const result = await api<Reservation[]>(paths[state.queryType]);
      state.reservations = result.data ?? [];
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
  await runAction(
    () => api<unknown>(
      `/resources/${reservation.resourceId}/reservations/${reservation.reservationId}/${operation}`,
      { method: 'POST', userScoped: true }
    ),
    message,
    queryReservations
  );
}

async function runAction(
  action: () => Promise<unknown>,
  message: string,
  after?: () => Promise<void>
): Promise<void> {
  try {
    await action();
    notify(message);
    if (after) await after();
  } catch (error) {
    notify(errorMessage(error), true);
  }
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString();
}

watch(actorId, (id) => {
  state.actorId = id;
  reservationQuery.user = id;
});
watch(() => props.refreshKey, (key, previousKey) => {
  if (key !== previousKey) {
    setQueryType('user');
    void queryReservations();
  }
});
</script>

<template>
  <section class="view active">
    <div class="page-heading">
      <div>
        <div class="eyebrow">BOOKING / LOOKUP</div>
        <h1>Reservations</h1>
        <p class="page-subtitle">Find bookings by user, resource, or reservation ID.</p>
      </div>
    </div>

    <div class="query-tabs" role="tablist" aria-label="Reservation lookup type">
      <button
        v-for="item in queryTabs"
        :key="item.id"
        class="query-tab"
        :class="{ active: state.queryType === item.id }"
        type="button"
        @click="setQueryType(item.id)"
      >
        {{ item.label }}
      </button>
    </div>

    <form class="query-form" @submit.prevent="queryReservations">
      <label class="query-input-label">{{ queryLabel.label }}</label>
      <div class="query-controls">
        <select
          v-if="state.queryType === 'resource'"
          v-model="reservationQuery.resource"
          required
        >
          <option value="" disabled>Select an active resource</option>
          <option
            v-for="resource in activeResources"
            :key="resource.resourceId"
            :value="resource.resourceId"
          >
            {{ resource.name }} · {{ resource.location }} ·
            {{ resource.resourceId.slice(0, 8) }}…
          </option>
        </select>
        <input
          v-else
          v-model="reservationQuery[state.queryType]"
          :placeholder="queryLabel.placeholder"
          required
        >
        <button class="button button-primary" type="submit">
          Search reservations <span>→</span>
        </button>
      </div>
    </form>

    <section class="results-section">
      <div class="section-bar">
        <div>
          <span class="section-kicker">QUERY RESULTS</span>
          <h2>{{ reservationTitle }}</h2>
        </div>
        <span class="result-count">
          {{ reservationResultRan
            ? `${state.reservations.length} ${state.reservations.length === 1 ? 'record' : 'records'}`
            : '' }}
        </span>
      </div>

      <div v-if="!state.reservations.length" class="empty-state">
        {{ reservationResultRan
          ? 'No reservations found for this query.'
          : 'Run a lookup to see reservation records.' }}
      </div>
      <div v-else class="reservation-table-wrap">
        <table>
          <thead>
            <tr>
              <th>RESERVATION</th>
              <th>RESOURCE</th>
              <th>USER</th>
              <th>START</th>
              <th>END</th>
              <th>STATUS</th>
              <th>ACTIONS</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="reservation in state.reservations"
              :key="reservation.reservationId"
            >
              <td><code :title="reservation.reservationId">{{ reservation.reservationId }}</code></td>
              <td><code :title="reservation.resourceId">{{ reservation.resourceId }}</code></td>
              <td><code :title="reservation.userId">{{ reservation.userId }}</code></td>
              <td>{{ formatDate(reservation.start) }}</td>
              <td>{{ formatDate(reservation.end) }}</td>
              <td>
                <span
                  class="status-pill"
                  :class="{ inactive: reservation.status === 'CANCELLED' }"
                >
                  {{ reservation.status }}
                </span>
              </td>
              <td>
                <div class="row-actions">
                  <button
                    type="button"
                    @click="loadEvents(reservation.reservationId)"
                  >
                    Events
                  </button>
                  <button
                    v-if="reservation.status === 'PENDING'"
                    type="button"
                    @click="mutateReservation('confirm', reservation)"
                  >
                    Confirm
                  </button>
                  <button
                    v-if="['PENDING', 'CONFIRMED'].includes(reservation.status)"
                    type="button"
                    @click="mutateReservation('cancel', reservation)"
                  >
                    Cancel
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="eventData" class="event-section">
      <div class="section-bar">
        <div>
          <span class="section-kicker">EVENT STORE</span>
          <h2>Reservation history</h2>
        </div>
        <button
          class="icon-button"
          type="button"
          aria-label="Close event history"
          @click="eventData = null"
        >
          ×
        </button>
      </div>
      <pre class="json-output">{{ JSON.stringify(eventData, null, 2) }}</pre>
    </section>
  </section>
</template>