<script setup>
defineProps({
  activeResources: { type: Array, required: true },
  utilizationForm: { type: Object, required: true },
  utilizationResultRan: { type: Boolean, required: true },
  utilizationTitle: { type: String, required: true },
  utilizationRows: { type: Array, required: true },
  utilizationResourceId: { type: String, required: true }
});

defineEmits(['show-utilization']);
</script>

<template>
  <section class="view active">
    <div class="page-heading"><div><div class="eyebrow">CAPACITY / DAILY PROJECTION</div><h1>Utilization</h1><p class="page-subtitle">Review occupied hours and capacity across a date range.</p></div></div>
    <form class="utilization-form" @submit="$emit('show-utilization', $event)"><label>Resource<select v-model="utilizationForm.resourceId" required><option value="" disabled>Select an active resource</option><option v-for="resource in activeResources" :key="resource.resourceId" :value="resource.resourceId">{{ resource.name }} · {{ resource.location }} · {{ resource.resourceId.slice(0, 8) }}…</option></select></label><label>From<input v-model="utilizationForm.from" type="date" required></label><label>To<input v-model="utilizationForm.to" type="date" required></label><button class="button button-primary" type="submit">View utilization <span>→</span></button></form>
    <section class="utilization-results"><div class="section-bar"><div><span class="section-kicker">DAILY BREAKDOWN</span><h2>{{ utilizationResultRan ? utilizationTitle : 'Select a resource and date range' }}</h2></div><span class="result-count">{{ utilizationResultRan ? `${utilizationRows.length} ${utilizationRows.length === 1 ? 'day' : 'days'} · ${utilizationResourceId.slice(0, 8)}…` : '' }}</span></div>
      <div v-if="!utilizationRows.length" class="empty-state">{{ utilizationResultRan ? 'No utilization records in this range.' : 'Utilization is shown in UTC. Date ranges can include up to 366 days.' }}</div>
      <div v-else class="utilization-table-wrap"><table><thead><tr><th>UTC DATE</th><th>RESERVATIONS</th><th>OCCUPIED HOURS</th><th>CAPACITY USED</th></tr></thead><tbody><tr v-for="row in utilizationRows" :key="row.date"><td>{{ row.date }}</td><td>{{ row.reservationCount }}</td><td>{{ row.occupiedHours }} h</td><td><div class="util-bar"><span class="util-track"><span class="util-fill" :style="{ width: `${Math.min(100, Math.max(0, Number(row.utilizationPercent) || 0))}%` }"></span></span><span class="util-value">{{ Number(row.utilizationPercent || 0).toFixed(2) }}%</span></div></td></tr></tbody></table></div>
    </section>
  </section>
</template>