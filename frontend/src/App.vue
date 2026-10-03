<script setup lang="ts">
import { computed, ref } from 'vue';
import AdministrationView from './AdministrationView.vue';
import ConnectionDialog from './ConnectionDialog.vue';
import ReservationsView from './ReservationsView.vue';
import ResourcesView from './ResourcesView.vue';
import UtilizationView from './UtilizationView.vue';
import { useApi } from './composables/useApi';
import { useResources } from './composables/useResources';
import { useToast } from './composables/useToast';

const views = [
  { id: 'resources', label: 'Resources', icon: '▦' },
  { id: 'reservations', label: 'Reservations', icon: '◷' },
  { id: 'utilization', label: 'Utilization', icon: '▤' },
  { id: 'admin', label: 'Administration', icon: '⚙' }
] as const;
type ViewId = (typeof views)[number]['id'];

const { actorId } = useApi();
const { resources } = useResources();
const { toast } = useToast();
const activeView = ref<ViewId>('resources');
const connected = ref(false);
const connectionModal = ref(true);
const utilizationResourceId = ref('');
const utilizationSelectionVersion = ref(0);
const reservationRefreshKey = ref(0);
const activeViewLabel = computed(
  () => views.find((view) => view.id === activeView.value)?.label ?? ''
);
const resourceCount = computed(() => resources.value?.length ?? 0);

function goToUtilization(resourceId: string): void {
  utilizationResourceId.value = resourceId;
  utilizationSelectionVersion.value += 1;
  activeView.value = 'utilization';
}

function openReservations(): void {
  reservationRefreshKey.value += 1;
  activeView.value = 'reservations';
}
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
            {{ resourceCount }}
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
              {{ connected ? `${actorId.slice(0, 8)}…` : 'Set up connection' }}
            </span>
            <span class="chevron">⌄</span>
          </button>
        </div>
      </header>

      <ResourcesView
        v-show="activeView === 'resources'"
        :connected="connected"
        @navigate-to-utilization="goToUtilization"
        @navigate-to-reservations="openReservations"
      />
      <ReservationsView
        v-show="activeView === 'reservations'"
        :refresh-key="reservationRefreshKey"
      />
      <UtilizationView
        v-show="activeView === 'utilization'"
        :initial-resource-id="utilizationResourceId"
        :selection-version="utilizationSelectionVersion"
      />
      <AdministrationView v-show="activeView === 'admin'" />
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

  <ConnectionDialog
    v-model="connectionModal"
    :connected="connected"
    @connected="connected = true"
  />
</template>
