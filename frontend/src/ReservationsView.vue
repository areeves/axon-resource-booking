<script setup>
defineProps({
  state: { type: Object, required: true },
  activeResources: { type: Array, required: true },
  queryLabel: { type: Object, required: true },
  reservationTitle: { type: String, required: true },
  reservationQuery: { type: Object, required: true },
  reservationResultRan: { type: Boolean, required: true },
  eventData: { type: [Object, Array, String, Number, Boolean], default: null },
  formatDate: { type: Function, required: true }
});

defineEmits(['set-query-type', 'query-reservations', 'load-events', 'reservation-action', 'close-events']);
</script>

<template>
  <section class="view active">
    <div class="page-heading"><div><div class="eyebrow">BOOKING / LOOKUP</div><h1>Reservations</h1><p class="page-subtitle">Find bookings by user, resource, or reservation ID.</p></div></div>
    <div class="query-tabs" role="tablist" aria-label="Reservation lookup type"><button v-for="item in [{ id: 'user', label: 'By user' }, { id: 'resource', label: 'By resource' }, { id: 'id', label: 'By reservation ID' }]" :key="item.id" class="query-tab" :class="{ active: state.queryType === item.id }" type="button" @click="$emit('set-query-type', item.id)">{{ item.label }}</button></div>
    <form class="query-form" @submit.prevent="$emit('query-reservations')"><label class="query-input-label">{{ queryLabel.label }}</label><div class="query-controls"><select v-if="state.queryType === 'resource'" v-model="reservationQuery.resource" required><option value="" disabled>Select an active resource</option><option v-for="resource in activeResources" :key="resource.resourceId" :value="resource.resourceId">{{ resource.name }} · {{ resource.location }} · {{ resource.resourceId.slice(0, 8) }}…</option></select><input v-else v-model="reservationQuery[state.queryType]" :placeholder="queryLabel.placeholder" required><button class="button button-primary" type="submit">Search reservations <span>→</span></button></div></form>
    <section class="results-section"><div class="section-bar"><div><span class="section-kicker">QUERY RESULTS</span><h2>{{ reservationTitle }}</h2></div><span class="result-count">{{ reservationResultRan ? `${state.reservations.length} ${state.reservations.length === 1 ? 'record' : 'records'}` : '' }}</span></div>
      <div v-if="!state.reservations.length" class="empty-state">{{ reservationResultRan ? 'No reservations found for this query.' : 'Run a lookup to see reservation records.' }}</div>
      <div v-else class="reservation-table-wrap"><table><thead><tr><th>RESERVATION</th><th>RESOURCE</th><th>USER</th><th>START</th><th>END</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody><tr v-for="reservation in state.reservations" :key="reservation.reservationId"><td><code :title="reservation.reservationId">{{ reservation.reservationId }}</code></td><td><code :title="reservation.resourceId">{{ reservation.resourceId }}</code></td><td><code :title="reservation.userId">{{ reservation.userId }}</code></td><td>{{ formatDate(reservation.start) }}</td><td>{{ formatDate(reservation.end) }}</td><td><span class="status-pill" :class="{ inactive: reservation.status === 'CANCELLED' }">{{ reservation.status }}</span></td><td><div class="row-actions"><button type="button" @click="$emit('load-events', reservation.reservationId)">Events</button><button v-if="reservation.status === 'PENDING'" type="button" @click="$emit('reservation-action', 'confirm', reservation)">Confirm</button><button v-if="['PENDING', 'CONFIRMED'].includes(reservation.status)" type="button" @click="$emit('reservation-action', 'cancel', reservation)">Cancel</button></div></td></tr></tbody></table></div>
    </section>
    <section v-if="eventData" class="event-section"><div class="section-bar"><div><span class="section-kicker">EVENT STORE</span><h2>Reservation history</h2></div><button class="icon-button" type="button" aria-label="Close event history" @click="$emit('close-events')">×</button></div><pre class="json-output">{{ JSON.stringify(eventData, null, 2) }}</pre></section>
  </section>
</template>