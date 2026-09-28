<script setup>
defineProps({
  state: { type: Object, required: true },
  currentResource: { type: Object, default: null },
  activeResources: { type: Array, required: true },
  resourceListTitle: { type: String, required: true },
  availability: { type: Object, required: true },
  lookupId: { type: String, required: true },
  reservationWindow: { type: Object, required: true },
  busy: { type: Boolean, required: true },
  connected: { type: Boolean, required: true }
});

defineEmits([
  'create-resource', 'find-available', 'refresh', 'lookup-resource', 'select-resource', 'update:lookup-id',
  'go-to-utilization', 'change-resource-status', 'update-resource', 'reserve-resource'
]);
</script>

<template>
  <section class="view active">
    <div class="page-heading"><div><div class="eyebrow">INVENTORY / COMMANDS</div><h1>Resources</h1><p class="page-subtitle">Manage spaces and make reservations.</p></div><button class="button button-primary" type="button" @click="$emit('create-resource')"><span>＋</span> New resource</button></div>
    <form class="filter-bar" @submit.prevent="$emit('find-available')"><span class="filter-label">CHECK AVAILABILITY</span><label>From <input v-model="availability.start" type="datetime-local" required></label><label>To <input v-model="availability.end" type="datetime-local" required></label><button class="button button-dark" type="submit">Find available</button><button class="text-button" type="button" @click="$emit('refresh')">All active resources</button></form>
    <div class="resource-layout">
      <section class="resource-list-panel">
        <div class="section-bar"><div><span class="section-kicker">DIRECTORY</span><h2>{{ resourceListTitle }}</h2></div><button class="icon-button" type="button" title="Refresh resources" aria-label="Refresh resources" @click="$emit('refresh')">↻</button></div>
        <div class="resource-list">
          <div v-if="!state.resources.length" class="empty-state">{{ connected ? 'No resources in this result.' : 'Connect to load resources.' }}</div>
          <button v-for="resource in state.resources" :key="resource.resourceId" type="button" class="resource-item" :class="{ selected: resource.resourceId === state.selectedResourceId }" @click="$emit('select-resource', resource.resourceId)">
            <span class="resource-item-top"><span class="resource-item-name">{{ resource.name }}</span><span class="status-pill" :class="{ inactive: resource.status !== 'ACTIVE' }">{{ resource.status }}</span></span>
            <span class="resource-item-meta"><span>{{ resource.location }}</span><span>·</span><span>Capacity {{ resource.capacity }}</span></span>
          </button>
        </div>
        <form class="lookup-form" @submit.prevent="$emit('lookup-resource')"><label for="resource-lookup">OPEN ACTIVE RESOURCE</label><div class="inline-field"><select id="resource-lookup" :value="lookupId" required @input="$emit('update:lookup-id', $event.target.value)"><option value="" disabled>Select a resource</option><option v-for="resource in activeResources" :key="resource.resourceId" :value="resource.resourceId">{{ resource.name }} · {{ resource.location }} · {{ resource.resourceId.slice(0, 8) }}…</option></select><button class="icon-button" type="submit" aria-label="Open resource">→</button></div></form>
      </section>
      <section class="resource-detail-panel">
        <template v-if="currentResource">
          <div class="detail-heading"><div><div class="eyebrow">RESOURCE / {{ currentResource.resourceId }}</div><h2>{{ currentResource.name }}</h2><div class="detail-subline">{{ currentResource.location }} · capacity {{ currentResource.capacity }}</div></div>
            <div class="detail-actions"><span class="status-pill" :class="{ inactive: currentResource.status !== 'ACTIVE' }">{{ currentResource.status }}</span><button class="small-action" type="button" @click="$emit('go-to-utilization', currentResource)">Utilization</button><button class="small-action" :class="{ warn: currentResource.status === 'ACTIVE' }" type="button" :disabled="busy" @click="$emit('change-resource-status', currentResource)">{{ currentResource.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate' }}</button></div>
          </div>
          <form class="resource-fields" @submit="$emit('update-resource', $event)"><label>Name<input name="name" :value="currentResource.name" required></label><label>Description<textarea name="description" rows="2">{{ currentResource.description || '' }}</textarea></label><label>Location<input name="location" :value="currentResource.location" required></label><label>Resource ID<input :value="currentResource.resourceId" readonly></label><label>Capacity<input :value="currentResource.capacity" readonly></label><button class="small-action detail-save-button" type="submit" :disabled="busy">Save changes</button></form>
          <section class="reserve-strip"><h3>Reserve this resource</h3><form class="reserve-form-grid" @submit="$emit('reserve-resource', $event)"><label>Start<input v-model="reservationWindow.start" name="start" type="datetime-local" required></label><label>End<input v-model="reservationWindow.end" name="end" type="datetime-local" required></label><label>Booking for<input :value="state.actorId" readonly></label><button class="button button-primary" type="submit" :disabled="busy">＋ Reserve</button></form></section>
        </template>
        <div v-else class="empty-detail"><span class="empty-glyph">▦</span><h2>{{ state.resources.length ? 'Select a resource' : 'No resource selected' }}</h2><p>{{ state.resources.length ? 'Choose a resource from the directory to view details and actions.' : 'Create a resource or adjust your availability window.' }}</p></div>
      </section>
    </div>
  </section>
</template>