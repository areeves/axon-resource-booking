// composables/useApi.ts
import { computed, ref } from 'vue';

export interface Credentials {
  username: string;
  password: string;
}

export interface ApiOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  userScoped?: boolean;
}

export interface ApiResult<T> {
  data: T;
  status: number;
}

// Module-level: shared by every component that calls useApi()
const credentials = ref<Credentials | null>(null);
const actorId = ref(
  localStorage.getItem('fieldnote-actor-id') || createActorId()
);

export function createActorId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20)
  ].join('-');
}

export function useApi() {
  const isAuthenticated = computed(() => credentials.value !== null);

  function setCredentials(next: Credentials | null): void {
    credentials.value = next;
  }

  function setActorId(id: string): void {
    actorId.value = id;
    localStorage.setItem('fieldnote-actor-id', id);
  }

  async function api<T>(
    path: string,
    options: ApiOptions = {}
  ): Promise<ApiResult<T>> {
    if (!credentials.value) {
      throw new Error('Connect to the API before continuing.');
    }

    const { body, userScoped, ...requestOptions } = options;
    const headers = new Headers(requestOptions.headers);
    const { username, password } = credentials.value;

    headers.set('Accept', 'application/json');
    headers.set('Authorization', `Basic ${btoa(`${username}:${password}`)}`);
    if (userScoped) headers.set('X-User-Id', actorId.value);
    if (body !== undefined) headers.set('Content-Type', 'application/json');

    const response = await fetch(path, {
      ...requestOptions,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });

    const text = await response.text();
    let data: unknown = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        data = text;
      }
    }

    if (!response.ok) {
      const record =
        typeof data === 'object' && data !== null
          ? (data as Record<string, unknown>)
          : null;
      const detail = record?.message ?? record?.error ?? data;
      const message = typeof detail === 'string' ? detail : JSON.stringify(detail);
      throw new Error(message || `${response.status} ${response.statusText}`);
    }

    return { data: data as T, status: response.status };
  }

  return {
    api,
    credentials,
    actorId,
    isAuthenticated,
    setCredentials,
    setActorId
  };
}
