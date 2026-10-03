<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useApi } from './composables/useApi';
import { useResources } from './composables/useResources';
import { useToast } from './composables/useToast';
import type { Resource, UtilizationForm, UtilizationRow } from './types';

const props = defineProps<{
  initialResourceId: string;
  selectionVersion: number;
}>();
const { api } = useApi();
const { resources } = useResources();
const { notify } = useToast();
const activeResources = computed<Resource[]>(() =>
  (resources.value ?? []).filter((resource) => resource.status === 'ACTIVE')
);
const utilizationForm = reactive<UtilizationForm>({
  resourceId: props.initialResourceId,
  from: '',
  to: ''
});
const utilizationResultRan = ref(false);
const utilizationTitle = ref('Select a resource and date range');
const utilizationRows = ref<UtilizationRow[]>([]);
const utilizationResourceId = ref('');

async function showUtilization(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const { resourceId, from, to } = utilizationForm;
  try {
    const query = new URLSearchParams({ from, to });
    const path = `/resources/${encodeURIComponent(resourceId.trim())}/utilization?${query}`;
    const result = await api<UtilizationRow[]>(path);
    utilizationRows.value = result.data ?? [];
    utilizationResourceId.value = resourceId;
    utilizationResultRan.value = true;
    if (utilizationRows.value.length === 0) {
      utilizationTitle.value = 'No dates';
      return;
    }
    const lastDate = utilizationRows.value.at(-1)?.date;
    utilizationTitle.value = utilizationRows.value.length > 1
      ? `${utilizationRows.value[0].date} to ${lastDate}`
      : utilizationRows.value[0].date;
  } catch (error) {
    notify(error instanceof Error ? error.message : String(error), true);
  }
}

function initializeDates(): void {
  const today = new Date().toISOString().slice(0, 10);
  utilizationForm.from = today;
  utilizationForm.to = today;
}

watch(
  () => [props.initialResourceId, props.selectionVersion] as const,
  ([resourceId]) => {
    utilizationForm.resourceId = resourceId;
  }
);

onMounted(initializeDates);

function utilizationWidth(percent: number): string {
  const boundedPercent = Math.min(100, Math.max(0, Number(percent) || 0));
  return `${boundedPercent}%`;
}

function formatPercent(percent: number): string {
  return `${Number(percent || 0).toFixed(2)}%`;
}

function formatResultCount(rowCount: number, resourceId: string): string {
  const unit = rowCount === 1 ? 'day' : 'days';
  return `${rowCount} ${unit} · ${resourceId.slice(0, 8)}…`;
}
</script>

<template>
  <section class="view active">
    <div class="page-heading">
      <div>
        <div class="eyebrow">CAPACITY / DAILY PROJECTION</div>
        <h1>Utilization</h1>
        <p class="page-subtitle">Review occupied hours and capacity across a date range.</p>
      </div>
    </div>

    <form class="utilization-form" @submit="showUtilization">
      <label>
        Resource
        <select v-model="utilizationForm.resourceId" required>
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
      </label>
      <label>
        From
        <input v-model="utilizationForm.from" type="date" required>
      </label>
      <label>
        To
        <input v-model="utilizationForm.to" type="date" required>
      </label>
      <button class="button button-primary" type="submit">
        View utilization <span>→</span>
      </button>
    </form>

    <section class="utilization-results">
      <div class="section-bar">
        <div>
          <span class="section-kicker">DAILY BREAKDOWN</span>
          <h2>
            {{ utilizationResultRan
              ? utilizationTitle
              : 'Select a resource and date range' }}
          </h2>
        </div>
        <span class="result-count">
          {{ utilizationResultRan
            ? formatResultCount(utilizationRows.length, utilizationResourceId)
            : '' }}
        </span>
      </div>

      <div v-if="!utilizationRows.length" class="empty-state">
        {{ utilizationResultRan
          ? 'No utilization records in this range.'
          : 'Utilization is shown in UTC. Date ranges can include up to 366 days.' }}
      </div>
      <div v-else class="utilization-table-wrap">
        <table>
          <thead>
            <tr>
              <th>UTC DATE</th>
              <th>RESERVATIONS</th>
              <th>OCCUPIED HOURS</th>
              <th>CAPACITY USED</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in utilizationRows" :key="row.date">
              <td>{{ row.date }}</td>
              <td>{{ row.reservationCount }}</td>
              <td>{{ row.occupiedHours }} h</td>
              <td>
                <div class="util-bar">
                  <span class="util-track">
                    <span
                      class="util-fill"
                      :style="{ width: utilizationWidth(row.utilizationPercent) }"
                    ></span>
                  </span>
                  <span class="util-value">{{ formatPercent(row.utilizationPercent) }}</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>