<script setup lang="ts">
import { ref } from 'vue';
import { useApi } from './composables/useApi';
import { useToast } from './composables/useToast';

const { api } = useApi();
const { notify } = useToast();
const busy = ref(false);

async function rebuildProjections(): Promise<void> {
  const confirmed = window.confirm(
    'Rebuild all projections from the event store? The current read models will be cleared while events replay.'
  );
  if (!confirmed) return;

  busy.value = true;
  try {
    await api<unknown>('/admin/projections/rebuild', { method: 'POST' });
    notify('Projection rebuild started. Queries may be incomplete during replay.');
  } catch (error) {
    notify(error instanceof Error ? error.message : String(error), true);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <section class="view active">
    <div class="page-heading">
      <div>
        <div class="eyebrow">SYSTEM / MAINTENANCE</div>
        <h1>Administration</h1>
        <p class="page-subtitle">Projection controls for the event-sourced read models.</p>
      </div>
    </div>

    <div class="admin-row">
      <div>
        <span class="section-kicker">READ MODEL</span>
        <h2>Rebuild projections</h2>
        <p>
          Clears the resource, reservation, and utilization projections, then replays events from
          the event store. Queries may return incomplete results while replay runs.
        </p>
      </div>
      <button
        class="button button-danger"
        type="button"
        :disabled="busy"
        @click="rebuildProjections"
      >
        ↻ &nbsp; Rebuild projections
      </button>
    </div>

    <div class="admin-note">
      <span class="note-mark">i</span>
      <p>
        This operation requires an administrator account and returns as soon as event replay begins.
      </p>
    </div>
  </section>
</template>