<script setup>
defineProps({ busy: { type: Boolean, required: true } });
defineEmits(['rebuild-projections']);
</script>

<template>
  <section class="view active">
    <div class="page-heading"><div><div class="eyebrow">SYSTEM / MAINTENANCE</div><h1>Administration</h1><p class="page-subtitle">Projection controls for the event-sourced read models.</p></div></div>
    <div class="admin-row"><div><span class="section-kicker">READ MODEL</span><h2>Rebuild projections</h2><p>Clears the resource, reservation, and utilization projections, then replays events from the event store. Queries may return incomplete results while replay runs.</p></div><button class="button button-danger" type="button" :disabled="busy" @click="$emit('rebuild-projections')">↻ &nbsp; Rebuild projections</button></div>
    <div class="admin-note"><span class="note-mark">i</span><p>This operation requires an administrator account and returns as soon as event replay begins.</p></div>
  </section>
</template>