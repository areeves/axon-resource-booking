<script setup lang="ts">
import { ref } from 'vue';
import { createActorId, useApi } from './composables/useApi';
import type { ConnectionForm, Resource } from './types';

defineProps<{ modelValue: boolean; connected: boolean }>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'connected'): void;
}>();

const { api, actorId, setCredentials, setActorId } = useApi();
const connection = ref<ConnectionForm>({
  username: 'admin',
  password: 'admin',
  actorId: actorId.value
});
const connectionError = ref('');

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

async function connect(): Promise<void> {
  connectionError.value = '';
  const credentials = {
    username: connection.value.username,
    password: connection.value.password
  };
  setCredentials(credentials);
  try {
    await api<Resource[]>('/resources');
    setActorId(connection.value.actorId.trim());
    emit('connected');
    emit('update:modelValue', false);
  } catch (error) {
    setCredentials(null);
    const message = errorMessage(error);
    const rejected = message.includes('401') || message.includes('403');
    connectionError.value = rejected ? 'Credentials were not accepted.' : message;
  }
}

function generateActor(): void {
  connection.value.actorId = createActorId();
}

</script>

<template>
  <div
    v-if="modelValue"
    class="modal-backdrop"
    @click.self="connected && emit('update:modelValue', false)"
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
</template>
