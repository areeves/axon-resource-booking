<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useApi } from './composables/useApi';
import { useResources } from './composables/useResources';
import { useToast } from './composables/useToast';
import type {
  AppState,
  AvailabilityTimeRange,
  AvailabilityWindow,
  DateTimeRange,
  NewResourceDraft,
  Resource,
  ResourceAvailabilityRules
} from './types';

const props = defineProps<{ connected: boolean }>();

const emit = defineEmits<{
  (event: 'navigate-to-utilization', resourceId: string): void;
  (event: 'navigate-to-reservations'): void;
}>();

const { api, actorId } = useApi();
const resourceOperations = useResources();
const { notify } = useToast();
const state = reactive<AppState>({
  credentials: null,
  actorId: actorId.value,
  resources: [],
  selectedResourceId: null,
  queryType: 'user',
  reservations: []
});
const activeResources = ref<Resource[]>([]);
const currentResource = computed<Resource | null>(() =>
  state.resources.find(
    (resource) => resource.resourceId === state.selectedResourceId
  ) ?? null
);
const resourceListTitle = ref('Active resources');
const availability = ref<DateTimeRange>({ start: '', end: '' });
const reservationWindow = ref<DateTimeRange>({ start: '', end: '' });
const availabilityRules = ref<ResourceAvailabilityRules>({
  timezone: null,
  weeklyPattern: {},
  blackouts: [],
  extras: []
});
const weeklyTimezone = ref(Intl.DateTimeFormat().resolvedOptions().timeZone);
const availabilityWindowDraft = ref<DateTimeRange>({ start: '', end: '' });
const availabilityWindowReason = ref('');
const availabilityWindowType = ref<'BLACKOUT' | 'EXTRA'>('BLACKOUT');
const availabilityLoading = ref(false);
let availabilityRequestId = 0;
const weekdays = [
  { name: 'MONDAY', label: 'Monday' },
  { name: 'TUESDAY', label: 'Tuesday' },
  { name: 'WEDNESDAY', label: 'Wednesday' },
  { name: 'THURSDAY', label: 'Thursday' },
  { name: 'FRIDAY', label: 'Friday' },
  { name: 'SATURDAY', label: 'Saturday' },
  { name: 'SUNDAY', label: 'Sunday' }
] as const;
const lookupId = ref('');
const resourceModal = ref(false);
const busy = ref(false);
const newResource = ref<NewResourceDraft>({
  name: '',
  description: '',
  capacity: 1,
  location: ''
});

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
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
}

function emptyAvailabilityRules(): ResourceAvailabilityRules {
  return { timezone: null, weeklyPattern: {}, blackouts: [], extras: [] };
}

async function loadAvailability(resourceId: string): Promise<void> {
  const requestId = ++availabilityRequestId;
  availabilityLoading.value = true;
  availabilityRules.value = emptyAvailabilityRules();
  try {
    const result = await api<ResourceAvailabilityRules>(
      `/resources/${encodeURIComponent(resourceId)}/availability`
    );
    if (requestId !== availabilityRequestId || currentResource.value?.resourceId !== resourceId) return;
    const rules = result.data ?? emptyAvailabilityRules();
    availabilityRules.value = {
      timezone: rules.timezone ?? null,
      weeklyPattern: Object.fromEntries(
        Object.entries(rules.weeklyPattern ?? {}).map(([day, ranges]) => [
          day,
          ranges.map((range) => ({
            start: range.start.slice(0, 5),
            end: range.end.slice(0, 5)
          }))
        ])
      ),
      blackouts: [...(rules.blackouts ?? [])],
      extras: [...(rules.extras ?? [])]
    };
    weeklyTimezone.value =
      rules.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch (error) {
    if (requestId === availabilityRequestId && currentResource.value?.resourceId === resourceId) {
      notify(errorMessage(error), true);
    }
  } finally {
    if (requestId === availabilityRequestId) availabilityLoading.value = false;
  }
}

function addWeeklyRange(day: string): void {
  availabilityRules.value.weeklyPattern[day] ??= [];
  availabilityRules.value.weeklyPattern[day].push({ start: '09:00', end: '17:00' });
}

function removeWeeklyRange(day: string, index: number): void {
  const ranges = availabilityRules.value.weeklyPattern[day];
  if (!ranges) return;
  ranges.splice(index, 1);
  if (!ranges.length) delete availabilityRules.value.weeklyPattern[day];
}

async function saveWeeklyAvailability(): Promise<void> {
  const resource = currentResource.value;
  if (!resource) return;
  const timezone = weeklyTimezone.value.trim();
  try {
    new Intl.DateTimeFormat(undefined, { timeZone: timezone });
  } catch {
    notify('Enter a valid IANA timezone, such as America/New_York.', true);
    return;
  }
  const weeklyPattern: Record<string, AvailabilityTimeRange[]> = {};
  for (const { name } of weekdays) {
    const ranges = availabilityRules.value.weeklyPattern[name] ?? [];
    for (const range of ranges) {
      if (!range.start || !range.end || range.start === range.end) {
        notify('Each weekly availability range must have different start and end times.', true);
        return;
      }
    }
    if (ranges.length) weeklyPattern[name] = ranges;
  }
  await runAction(
    () => api<unknown>(`/resources/${resource.resourceId}/availability/weekly`, {
      method: 'POST',
      userScoped: true,
      body: { timezone, weeklyPattern }
    }),
    'Weekly availability saved',
    () => loadAvailability(resource.resourceId)
  );
}

async function addAvailabilityWindow(): Promise<void> {
  const resource = currentResource.value;
  if (!resource) return;
  const start = new Date(availabilityWindowDraft.value.start);
  const end = new Date(availabilityWindowDraft.value.end);
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end <= start) {
    notify('End time must be after start time.', true);
    return;
  }
  const isBlackout = availabilityWindowType.value === 'BLACKOUT';
  const windowsPath = isBlackout ? 'blackouts' : 'extra';
  const body = {
    start: start.toISOString(),
    end: end.toISOString(),
    reason: availabilityWindowReason.value.trim() || null
  };
  await runAction(
    () => api<unknown>(`/resources/${resource.resourceId}/availability/${windowsPath}`, {
      method: 'POST',
      userScoped: true,
      body
    }),
    isBlackout ? 'Blackout added' : 'Extra availability added',
    async () => {
      availabilityWindowDraft.value = { start: '', end: '' };
      availabilityWindowReason.value = '';
      await loadAvailability(resource.resourceId);
    }
  );
}

async function removeAvailabilityWindow(window: AvailabilityWindow): Promise<void> {
  const resource = currentResource.value;
  if (!resource) return;
  const type = window.type === 'BLACKOUT' ? 'blackouts' : 'extra';
  await runAction(
    () => api<unknown>(
      `/resources/${resource.resourceId}/availability/${type}/${encodeURIComponent(window.windowId)}`,
      { method: 'DELETE', userScoped: true }
    ),
    window.type === 'BLACKOUT' ? 'Blackout removed' : 'Extra availability removed',
    () => loadAvailability(resource.resourceId)
  );
}

async function clearAvailability(): Promise<void> {
  const resource = currentResource.value;
  if (!resource) return;
  await runAction(
    () => api<unknown>(`/resources/${resource.resourceId}/availability`, {
      method: 'DELETE',
      userScoped: true
    }),
    'Availability cleared; resource is always available',
    () => loadAvailability(resource.resourceId)
  );
}

function formatAvailabilityWindow(value: string): string {
  return new Date(value).toLocaleString();
}

async function loadResources(): Promise<void> {
  const result = await resourceOperations.refresh();
  if (result.error) throw result.error;
  state.resources = result.data ?? [];
  activeResources.value = [...state.resources];
  resourceListTitle.value = 'Active resources';
  if (!state.resources.some((resource) => resource.resourceId === state.selectedResourceId)) {
    state.selectedResourceId = state.resources[0]?.resourceId || null;
  }
}

async function selectResource(resourceId: string): Promise<void> {
  state.selectedResourceId = resourceId;
  if (state.resources.some((resource) => resource.resourceId === resourceId)) return;
  try {
    const result = await api<Resource>(`/resources/${encodeURIComponent(resourceId)}`);
    if (!result.data) throw new Error('Resource was not found.');
    state.resources.unshift(result.data);
    state.selectedResourceId = result.data.resourceId;
  } catch (error) {
    notify(errorMessage(error), true);
  }
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
    const query = new URLSearchParams({ start: start.toISOString(), end: end.toISOString() });
    const result = await api<Resource[]>(`/resources/available?${query}`);
    state.resources = result.data ?? [];
    state.selectedResourceId = null;
    resourceListTitle.value = 'Available resources';
  } catch (error) {
    notify(errorMessage(error), true);
  }
}

async function lookupResource(): Promise<void> {
  if (lookupId.value.trim()) await selectResource(lookupId.value.trim());
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
        userId: actorId.value,
        start: start.toISOString(),
        end: end.toISOString()
      }
    }),
    'Reservation created',
    async () => {
      emit('navigate-to-reservations');
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
    () => resourceOperations.create.mutateAsync(body),
    'Resource created',
    async () => {
      newResource.value = { name: '', description: '', capacity: 1, location: '' };
      resourceModal.value = false;
      await loadResources();
    }
  );
}

function goToUtilization(resource: Resource | null): void {
  if (!resource) return;
  emit(
    'navigate-to-utilization',
    resource.status === 'ACTIVE' ? resource.resourceId : ''
  );
}

function updateLookupId(event: Event): void {
  if (event.target instanceof HTMLSelectElement) {
    lookupId.value = event.target.value;
  }
}

watch(() => props.connected, (connected) => {
  if (connected) void refreshResources();
});
watch(actorId, (id) => {
  state.actorId = id;
});
watch(
  () => currentResource.value?.resourceId,
  (resourceId) => {
    if (resourceId) {
      void loadAvailability(resourceId);
    } else {
      availabilityRules.value = emptyAvailabilityRules();
    }
  },
  { immediate: true }
);

onMounted(() => {
  initializeDates();
  if (props.connected) void refreshResources();
});
</script>

<template>
  <section class="view active">
    <div class="page-heading">
      <div>
        <div class="eyebrow">INVENTORY / COMMANDS</div>
        <h1>Resources</h1>
        <p class="page-subtitle">Manage spaces and make reservations.</p>
      </div>
      <button class="button button-primary" type="button" @click="resourceModal = true">
        <span>＋</span> New resource
      </button>
    </div>

    <form class="filter-bar" @submit.prevent="findAvailable">
      <span class="filter-label">CHECK AVAILABILITY</span>
      <label>
        From
        <input v-model="availability.start" type="datetime-local" required>
      </label>
      <label>
        To
        <input v-model="availability.end" type="datetime-local" required>
      </label>
      <button class="button button-dark" type="submit">Find available</button>
      <button class="text-button" type="button" @click="refreshResources">
        All active resources
      </button>
    </form>

    <div class="resource-layout">
      <section class="resource-list-panel">
        <div class="section-bar">
          <div>
            <span class="section-kicker">DIRECTORY</span>
            <h2>{{ resourceListTitle }}</h2>
          </div>
          <button
            class="icon-button"
            type="button"
            title="Refresh resources"
            aria-label="Refresh resources"
            @click="refreshResources"
          >
            ↻
          </button>
        </div>
        <div class="resource-list">
          <div v-if="!state.resources.length" class="empty-state">
            {{ connected ? 'No resources in this result.' : 'Connect to load resources.' }}
          </div>
          <button
            v-for="resource in state.resources"
            :key="resource.resourceId"
            type="button"
            class="resource-item"
            :class="{ selected: resource.resourceId === state.selectedResourceId }"
            @click="selectResource(resource.resourceId)"
          >
            <span class="resource-item-top">
              <span class="resource-item-name">{{ resource.name }}</span>
              <span class="status-pill" :class="{ inactive: resource.status !== 'ACTIVE' }">
                {{ resource.status }}
              </span>
            </span>
            <span class="resource-item-meta">
              <span>{{ resource.location }}</span>
              <span>·</span>
              <span>Capacity {{ resource.capacity }}</span>
            </span>
          </button>
        </div>
        <form class="lookup-form" @submit.prevent="lookupResource">
          <label for="resource-lookup">OPEN ACTIVE RESOURCE</label>
          <div class="inline-field">
            <select
              id="resource-lookup"
              :value="lookupId"
              required
              @input="updateLookupId"
            >
              <option value="" disabled>Select a resource</option>
              <option
                v-for="resource in activeResources"
                :key="resource.resourceId"
                :value="resource.resourceId"
              >
                {{ resource.name }} · {{ resource.location }} ·
                {{ resource.resourceId.slice(0, 8) }}…
              </option>
            </select>
            <button class="icon-button" type="submit" aria-label="Open resource">→</button>
          </div>
        </form>
      </section>

      <section class="resource-detail-panel">
        <template v-if="currentResource">
          <div class="detail-heading">
            <div>
              <div class="eyebrow">RESOURCE / {{ currentResource.resourceId }}</div>
              <h2>{{ currentResource.name }}</h2>
              <div class="detail-subline">
                {{ currentResource.location }} · capacity {{ currentResource.capacity }}
              </div>
            </div>
            <div class="detail-actions">
              <span
                class="status-pill"
                :class="{ inactive: currentResource.status !== 'ACTIVE' }"
              >
                {{ currentResource.status }}
              </span>
              <button
                class="small-action"
                type="button"
                @click="goToUtilization(currentResource)"
              >
                Utilization
              </button>
              <button
                class="small-action"
                :class="{ warn: currentResource.status === 'ACTIVE' }"
                type="button"
                :disabled="busy"
                @click="changeResourceStatus(currentResource)"
              >
                {{ currentResource.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate' }}
              </button>
            </div>
          </div>
          <form class="resource-fields" @submit="updateResource">
            <label>
              Name
              <input name="name" :value="currentResource.name" required>
            </label>
            <label>
              Description
              <textarea name="description" rows="2">
                {{ currentResource.description || '' }}
              </textarea>
            </label>
            <label>
              Location
              <input name="location" :value="currentResource.location" required>
            </label>
            <label>
              Resource ID
              <input :value="currentResource.resourceId" readonly>
            </label>
            <label>
              Capacity
              <input :value="currentResource.capacity" readonly>
            </label>
            <button class="small-action detail-save-button" type="submit" :disabled="busy">
              Save changes
            </button>
          </form>

          <section class="availability-section" aria-labelledby="availability-title">
            <div class="availability-heading">
              <div>
                <h3 id="availability-title">Availability rules</h3>
                <p>Set recurring hours and one-off exceptions for this resource.</p>
              </div>
              <button
                class="small-action warn"
                type="button"
                :disabled="busy || availabilityLoading"
                @click="clearAvailability"
              >
                Always available
              </button>
            </div>
            <p v-if="availabilityLoading" class="availability-note">Loading availability…</p>
            <template v-else>
              <form class="weekly-availability-form" @submit.prevent="saveWeeklyAvailability">
                <label class="timezone-field">
                  Timezone
                  <input
                    v-model="weeklyTimezone"
                    type="text"
                    placeholder="America/New_York"
                    autocomplete="off"
                    required
                  >
                </label>
                <div class="weekly-days">
                  <div v-for="day in weekdays" :key="day.name" class="weekly-day">
                    <strong>{{ day.label }}</strong>
                    <div class="weekly-ranges">
                      <div
                        v-for="(range, index) in availabilityRules.weeklyPattern[day.name] || []"
                        :key="`${day.name}-${index}`"
                        class="weekly-range"
                      >
                        <input
                          v-model="range.start"
                          type="time"
                          :aria-label="`${day.label} start time`"
                          required
                        >
                        <span>to</span>
                        <input
                          v-model="range.end"
                          type="time"
                          :aria-label="`${day.label} end time`"
                          required
                        >
                        <button
                          class="text-button"
                          type="button"
                          :aria-label="`Remove ${day.label} hours`"
                          @click="removeWeeklyRange(day.name, index)"
                        >
                          Remove
                        </button>
                      </div>
                      <button
                        class="text-button add-hours-button"
                        type="button"
                        @click="addWeeklyRange(day.name)"
                      >
                        + Add hours
                      </button>
                    </div>
                  </div>
                </div>
                <button class="small-action" type="submit" :disabled="busy">
                  Save weekly hours
                </button>
              </form>

              <div class="window-management">
                <h4>One-off availability</h4>
                <form class="window-form" @submit.prevent="addAvailabilityWindow">
                  <label>
                    Type
                    <select v-model="availabilityWindowType">
                      <option value="BLACKOUT">Blackout</option>
                      <option value="EXTRA">Extra availability</option>
                    </select>
                  </label>
                  <label>
                    Start
                    <input v-model="availabilityWindowDraft.start" type="datetime-local" required>
                  </label>
                  <label>
                    End
                    <input v-model="availabilityWindowDraft.end" type="datetime-local" required>
                  </label>
                  <label>
                    Reason <span class="label-note">optional</span>
                    <input v-model="availabilityWindowReason" type="text" maxlength="240">
                  </label>
                  <button class="small-action" type="submit" :disabled="busy">
                    Add window
                  </button>
                </form>
                <div v-if="!availabilityRules.blackouts.length && !availabilityRules.extras.length" class="availability-note">
                  No blackout or extra windows.
                </div>
                <ul v-else class="availability-windows">
                  <li
                    v-for="window in [...availabilityRules.blackouts, ...availabilityRules.extras]"
                    :key="window.windowId"
                  >
                    <div>
                      <span class="window-type" :class="{ blackout: window.type === 'BLACKOUT' }">
                        {{ window.type === 'BLACKOUT' ? 'Blackout' : 'Extra' }}
                      </span>
                      <span>{{ formatAvailabilityWindow(window.start) }} – {{ formatAvailabilityWindow(window.end) }}</span>
                      <span v-if="window.reason" class="window-reason">{{ window.reason }}</span>
                    </div>
                    <button
                      class="text-button"
                      type="button"
                      :disabled="busy"
                      @click="removeAvailabilityWindow(window)"
                    >
                      Remove
                    </button>
                  </li>
                </ul>
              </div>
            </template>
          </section>

          <section class="reserve-strip">
            <h3>Reserve this resource</h3>
            <form class="reserve-form-grid" @submit="reserveResource">
              <label>
                Start
                <input
                  v-model="reservationWindow.start"
                  name="start"
                  type="datetime-local"
                  required
                >
              </label>
              <label>
                End
                <input
                  v-model="reservationWindow.end"
                  name="end"
                  type="datetime-local"
                  required
                >
              </label>
              <label>
                Booking for
                <input :value="state.actorId" readonly>
              </label>
              <button class="button button-primary" type="submit" :disabled="busy">
                ＋ Reserve
              </button>
            </form>
          </section>
        </template>
        <div v-else class="empty-detail">
          <span class="empty-glyph">▦</span>
          <h2>{{ state.resources.length ? 'Select a resource' : 'No resource selected' }}</h2>
          <p>
            {{ state.resources.length
              ? 'Choose a resource from the directory to view details and actions.'
              : 'Create a resource or adjust your availability window.' }}
          </p>
        </div>
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
  </section>
</template>