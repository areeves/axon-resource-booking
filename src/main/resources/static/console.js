const state = {
  credentials: null,
  actorId: localStorage.getItem('fieldnote-actor-id') || crypto.randomUUID(),
  resources: [],
  selectedResourceId: null,
  queryType: 'user',
  reservations: []
};

const byId = (id) => document.getElementById(id);
const escapeHtml = (value = '') => String(value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
let toastTimer;

function showToast(message, isError = false) {
  const toast = byId('toast');
  toast.textContent = message;
  toast.classList.toggle('error', isError);
  toast.classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('visible'), 3600);
}

function setConnection(connected) {
  byId('connection-state').classList.toggle('connected', connected);
  byId('connection-state').innerHTML = `<i></i>${connected ? 'Connected' : 'Not connected'}`;
  byId('actor-label').textContent = connected ? `${state.actorId.slice(0, 8)}…` : 'Set up connection';
  byId('actor-id-input').value = state.actorId;
}

async function api(path, options = {}) {
  if (!state.credentials) throw new Error('Connect to the API before continuing.');
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');
  headers.set('Authorization', `Basic ${btoa(`${state.credentials.username}:${state.credentials.password}`)}`);
  if (options.userScoped) headers.set('X-User-Id', state.actorId);
  if (options.body !== undefined) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers, body: options.body === undefined ? undefined : JSON.stringify(options.body) });
  const text = await response.text();
  let result = null;
  if (text) {
    try { result = JSON.parse(text); } catch { result = text; }
  }
  if (!response.ok) {
    const detail = typeof result === 'object' && result ? result.message || result.error || JSON.stringify(result) : result;
    throw new Error(detail || `${response.status} ${response.statusText}`);
  }
  return { data: result, status: response.status, headers: response.headers };
}

function toLocalInput(date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function initializeDates() {
  const now = new Date();
  const later = new Date(now.getTime() + 60 * 60 * 1000);
  const after = new Date(now.getTime() + 2 * 60 * 60 * 1000);
  document.querySelectorAll('#availability-form [name="start"]').forEach((input) => input.value = toLocalInput(later));
  document.querySelectorAll('#availability-form [name="end"]').forEach((input) => input.value = toLocalInput(after));
  const today = new Date().toISOString().slice(0, 10);
  byId('utilization-form').elements.from.value = today;
  byId('utilization-form').elements.to.value = today;
}

function displayView(name) {
  document.querySelectorAll('.view').forEach((view) => view.classList.toggle('active', view.id === `view-${name}`));
  document.querySelectorAll('.nav-item[data-view]').forEach((item) => item.classList.toggle('active', item.dataset.view === name));
  byId('page-crumb').textContent = name[0].toUpperCase() + name.slice(1);
}

function renderResources() {
  byId('resource-count').textContent = state.resources.length;
  const list = byId('resource-list');
  if (!state.resources.length) {
    list.innerHTML = '<div class="empty-state">No resources in this result.</div>';
    byId('resource-detail').innerHTML = '<div class="empty-detail"><span class="empty-glyph">▦</span><h2>No resource selected</h2><p>Create a resource or adjust your availability window.</p></div>';
    return;
  }
  list.innerHTML = state.resources.map((resource) => `<button type="button" class="resource-item ${resource.resourceId === state.selectedResourceId ? 'selected' : ''}" data-resource-id="${escapeHtml(resource.resourceId)}">
    <span class="resource-item-top"><span class="resource-item-name">${escapeHtml(resource.name)}</span><span class="status-pill ${resource.status === 'ACTIVE' ? '' : 'inactive'}">${escapeHtml(resource.status)}</span></span>
    <span class="resource-item-meta"><span>${escapeHtml(resource.location)}</span><span>·</span><span>Capacity ${escapeHtml(resource.capacity)}</span></span>
  </button>`).join('');
  list.querySelectorAll('[data-resource-id]').forEach((button) => button.addEventListener('click', () => selectResource(button.dataset.resourceId)));
  if (!state.resources.some((resource) => resource.resourceId === state.selectedResourceId)) selectResource(state.resources[0].resourceId);
  else renderSelectedResource();
}

async function loadResources() {
  const result = await api('/resources');
  state.resources = result.data || [];
  byId('resource-list-title').textContent = 'Active resources';
  renderResources();
}

function selectResource(resourceId) {
  state.selectedResourceId = resourceId;
  const resource = state.resources.find((item) => item.resourceId === resourceId);
  if (resource) renderSelectedResource();
  else loadResourceById(resourceId).catch((error) => showToast(error.message, true));
}

async function loadResourceById(resourceId) {
  const result = await api(`/resources/${encodeURIComponent(resourceId)}`);
  if (!result.data) throw new Error('Resource was not found.');
  if (!state.resources.some((resource) => resource.resourceId === result.data.resourceId)) state.resources.unshift(result.data);
  state.selectedResourceId = result.data.resourceId;
  renderResources();
}

function renderSelectedResource() {
  const resource = state.resources.find((item) => item.resourceId === state.selectedResourceId);
  if (!resource) return;
  byId('resource-list').querySelectorAll('.resource-item').forEach((item) => item.classList.toggle('selected', item.dataset.resourceId === resource.resourceId));
  byId('resource-detail').innerHTML = `
    <div class="detail-heading"><div><div class="eyebrow">RESOURCE / ${escapeHtml(resource.resourceId)}</div><h2>${escapeHtml(resource.name)}</h2><div class="detail-subline">${escapeHtml(resource.location)} · capacity ${escapeHtml(resource.capacity)}</div></div>
      <div class="detail-actions"><span class="status-pill ${resource.status === 'ACTIVE' ? '' : 'inactive'}">${escapeHtml(resource.status)}</span>
      <button class="small-action" id="resource-utilization" type="button">Utilization</button>
      <button class="small-action ${resource.status === 'ACTIVE' ? 'warn' : ''}" id="resource-status-action" type="button">${resource.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}</button></div></div>
    <form class="resource-fields" id="update-resource-form">
      <label>Name<input name="name" value="${escapeHtml(resource.name)}" required></label>
      <label>Description<textarea name="description" rows="2">${escapeHtml(resource.description || '')}</textarea></label>
      <label>Location<input name="location" value="${escapeHtml(resource.location)}" required></label>
      <label>Resource ID<input value="${escapeHtml(resource.resourceId)}" readonly></label>
      <label>Capacity<input value="${escapeHtml(resource.capacity)}" readonly></label>
    </form>
    <div class="detail-save"><button class="small-action" id="save-resource" type="button">Save changes</button></div>
    <section class="reserve-strip"><h3>Reserve this resource</h3><form class="reserve-form-grid" id="reserve-form">
      <label>Start<input name="start" type="datetime-local" required></label><label>End<input name="end" type="datetime-local" required></label>
      <label>Booking for<input value="${escapeHtml(state.actorId)}" readonly></label><button class="button button-primary" type="submit">＋ Reserve</button>
    </form></section>`;
  const reserveStart = new Date(Date.now() + 60 * 60 * 1000);
  const reserveEnd = new Date(Date.now() + 2 * 60 * 60 * 1000);
  byId('reserve-form').elements.start.value = toLocalInput(reserveStart);
  byId('reserve-form').elements.end.value = toLocalInput(reserveEnd);
  byId('resource-status-action').addEventListener('click', () => changeResourceStatus(resource));
  byId('resource-utilization').addEventListener('click', () => {
    displayView('utilization');
    byId('utilization-form').elements.resourceId.value = resource.resourceId;
  });
  byId('save-resource').addEventListener('click', () => updateResource(resource.resourceId));
  byId('reserve-form').addEventListener('submit', (event) => reserveResource(event, resource.resourceId));
}

async function changeResourceStatus(resource) {
  const operation = resource.status === 'ACTIVE' ? 'deactivate' : 'reactivate';
  await runAction(() => api(`/resources/${resource.resourceId}/${operation}`, { method: 'POST', userScoped: true }), `${operation === 'deactivate' ? 'Resource deactivated' : 'Resource reactivated'}`, async () => {
    await loadResources();
    if (resource.status !== 'ACTIVE') await loadResourceById(resource.resourceId);
  });
}

async function updateResource(resourceId) {
  const form = byId('update-resource-form');
  const body = Object.fromEntries(new FormData(form));
  await runAction(() => api(`/resources/${resourceId}`, { method: 'PUT', userScoped: true, body }), 'Resource updated', async () => {
    await loadResources();
    await loadResourceById(resourceId);
  });
}

async function reserveResource(event, resourceId) {
  event.preventDefault();
  const form = event.currentTarget;
  const start = new Date(form.elements.start.value);
  const end = new Date(form.elements.end.value);
  if (!(end > start)) return showToast('End time must be after start time.', true);
  await runAction(() => api(`/resources/${resourceId}/reservations`, { method: 'POST', userScoped: true, body: {
    userId: state.actorId, start: start.toISOString(), end: end.toISOString()
  } }), 'Reservation created', async () => {
    form.reset();
    byId('reservation-query-input').value = state.actorId;
    displayView('reservations');
    await queryReservations();
  });
}

async function runAction(action, successMessage, after) {
  try {
    await action();
    showToast(successMessage);
    if (after) await after();
  } catch (error) { showToast(error.message, true); }
}

function openResourceModal() { byId('resource-modal').classList.remove('hidden'); }
function closeResourceModal() { byId('resource-modal').classList.add('hidden'); }

async function createResource(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const values = Object.fromEntries(new FormData(form));
  values.capacity = Number(values.capacity);
  await runAction(() => api('/resources', { method: 'POST', userScoped: true, body: values }), 'Resource created', async () => {
    form.reset();
    closeResourceModal();
    await loadResources();
  });
}

function setQueryType(type) {
  state.queryType = type;
  document.querySelectorAll('.query-tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.query === type));
  const labels = { user: ['USER UUID', 'User UUID'], resource: ['RESOURCE UUID', 'Resource UUID'], id: ['RESERVATION UUID', 'Reservation UUID'] };
  byId('reservation-query-form').querySelector('label').textContent = labels[type][0];
  byId('reservation-query-input').placeholder = labels[type][1];
  if (type === 'user') byId('reservation-query-input').value = state.actorId;
  else byId('reservation-query-input').value = '';
}

async function queryReservations() {
  const value = byId('reservation-query-input').value.trim();
  if (!value) return;
  const paths = { user: `/reservations/users/${encodeURIComponent(value)}`, resource: `/reservations/resources/${encodeURIComponent(value)}`, id: `/reservations/${encodeURIComponent(value)}` };
  try {
    const result = await api(paths[state.queryType]);
    state.reservations = state.queryType === 'id' ? (result.data ? [result.data] : []) : result.data || [];
    renderReservations();
    if (state.queryType === 'id' && !result.data) showToast('Reservation was not found.', true);
  } catch (error) { showToast(error.message, true); }
}

function renderReservations() {
  const container = byId('reservation-results');
  byId('reservation-results-title').textContent = state.queryType === 'user' ? 'User reservations' : state.queryType === 'resource' ? 'Resource reservations' : 'Reservation record';
  byId('reservation-result-count').textContent = `${state.reservations.length} ${state.reservations.length === 1 ? 'record' : 'records'}`;
  if (!state.reservations.length) {
    container.innerHTML = '<div class="empty-state">No reservations found for this query.</div>';
    return;
  }
  container.innerHTML = `<div class="reservation-table-wrap"><table><thead><tr><th>RESERVATION</th><th>RESOURCE</th><th>USER</th><th>START</th><th>END</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody>${state.reservations.map((reservation) => `<tr>
    <td><code title="${escapeHtml(reservation.reservationId)}">${escapeHtml(reservation.reservationId)}</code></td><td><code title="${escapeHtml(reservation.resourceId)}">${escapeHtml(reservation.resourceId)}</code></td><td><code title="${escapeHtml(reservation.userId)}">${escapeHtml(reservation.userId)}</code></td>
    <td>${escapeHtml(new Date(reservation.start).toLocaleString())}</td><td>${escapeHtml(new Date(reservation.end).toLocaleString())}</td><td><span class="status-pill ${reservation.status === 'CANCELLED' ? 'inactive' : ''}">${escapeHtml(reservation.status)}</span></td>
    <td><div class="row-actions"><button data-reservation-action="events" data-resource="${escapeHtml(reservation.resourceId)}" data-id="${escapeHtml(reservation.reservationId)}">Events</button>${reservation.status === 'PENDING' ? `<button data-reservation-action="confirm" data-resource="${escapeHtml(reservation.resourceId)}" data-id="${escapeHtml(reservation.reservationId)}">Confirm</button>` : ''}${['PENDING', 'CONFIRMED'].includes(reservation.status) ? `<button data-reservation-action="cancel" data-resource="${escapeHtml(reservation.resourceId)}" data-id="${escapeHtml(reservation.reservationId)}">Cancel</button>` : ''}</div></td>
  </tr>`).join('')}</tbody></table></div>`;
  container.querySelectorAll('[data-reservation-action]').forEach((button) => button.addEventListener('click', () => {
    const { reservationAction: action, resource, id } = button.dataset;
    if (action === 'events') loadEvents(id);
    else mutateReservation(action, resource, id);
  }));
}

async function mutateReservation(operation, resourceId, reservationId) {
  await runAction(() => api(`/resources/${resourceId}/reservations/${reservationId}/${operation}`, { method: 'POST', userScoped: true }), `Reservation ${operation === 'confirm' ? 'confirmed' : 'cancelled'}`, queryReservations);
}

async function loadEvents(reservationId) {
  try {
    const result = await api(`/reservations/${reservationId}/events`);
    byId('event-output').textContent = JSON.stringify(result.data, null, 2);
    byId('event-section').classList.remove('hidden');
    byId('event-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) { showToast(error.message, true); }
}

async function showUtilization(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const resourceId = form.elements.resourceId.value.trim();
  const from = form.elements.from.value;
  const to = form.elements.to.value;
  try {
    const result = await api(`/resources/${encodeURIComponent(resourceId)}/utilization?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
    renderUtilization(result.data || [], resourceId);
  } catch (error) { showToast(error.message, true); }
}

function renderUtilization(rows, resourceId) {
  byId('utilization-title').textContent = `${rows[0]?.date || 'No dates'}${rows.length > 1 ? ` to ${rows.at(-1).date}` : ''}`;
  byId('utilization-count').textContent = `${rows.length} ${rows.length === 1 ? 'day' : 'days'} · ${resourceId.slice(0, 8)}…`;
  if (!rows.length) {
    byId('utilization-table').innerHTML = '<div class="empty-state">No utilization records in this range.</div>';
    return;
  }
  byId('utilization-table').innerHTML = `<div class="utilization-table-wrap"><table><thead><tr><th>UTC DATE</th><th>RESERVATIONS</th><th>OCCUPIED HOURS</th><th>CAPACITY USED</th></tr></thead><tbody>${rows.map((row) => {
    const percent = Number(row.utilizationPercent) || 0;
    return `<tr><td>${escapeHtml(row.date)}</td><td>${escapeHtml(row.reservationCount)}</td><td>${escapeHtml(row.occupiedHours)} h</td><td><div class="util-bar"><span class="util-track"><span class="util-fill" style="display:block;width:${Math.min(100, Math.max(0, percent))}%"></span></span><span class="util-value">${escapeHtml(percent.toFixed(2))}%</span></div></td></tr>`;
  }).join('')}</tbody></table></div>`;
}

async function rebuildProjections() {
  if (!window.confirm('Rebuild all projections from the event store? The current read models will be cleared while events replay.')) return;
  await runAction(() => api('/admin/projections/rebuild', { method: 'POST' }), 'Projection rebuild started. Queries may be incomplete during replay.');
}

async function connect(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const values = Object.fromEntries(new FormData(form));
  const error = byId('connection-error');
  error.textContent = '';
  state.credentials = { username: values.username, password: values.password };
  state.actorId = values.actorId.trim();
  try {
    await api('/resources');
    localStorage.setItem('fieldnote-actor-id', state.actorId);
    byId('connection-modal').classList.add('hidden');
    setConnection(true);
    await loadResources();
    showToast('Connected to the resource booking API.');
  } catch (connectionError) {
    state.credentials = null;
    error.textContent = connectionError.message.includes('401') || connectionError.message.includes('403') ? 'Credentials were not accepted.' : connectionError.message;
  }
}

function bindEvents() {
  document.querySelectorAll('.nav-item[data-view]').forEach((item) => item.addEventListener('click', () => displayView(item.dataset.view)));
  byId('open-connection').addEventListener('click', () => {
    byId('connection-error').textContent = '';
    byId('connection-modal').classList.remove('hidden');
  });
  byId('new-actor').addEventListener('click', () => byId('actor-id-input').value = crypto.randomUUID());
  byId('connection-form').addEventListener('submit', connect);
  byId('new-resource-button').addEventListener('click', openResourceModal);
  document.querySelectorAll('[data-close-resource]').forEach((button) => button.addEventListener('click', closeResourceModal));
  byId('create-resource-form').addEventListener('submit', createResource);
  byId('refresh-resources').addEventListener('click', () => loadResources().catch((error) => showToast(error.message, true)));
  byId('show-active').addEventListener('click', () => loadResources().catch((error) => showToast(error.message, true)));
  byId('availability-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const start = new Date(form.elements.start.value);
    const end = new Date(form.elements.end.value);
    if (!(end > start)) return showToast('End time must be after start time.', true);
    try {
      const result = await api(`/resources/available?start=${encodeURIComponent(start.toISOString())}&end=${encodeURIComponent(end.toISOString())}`);
      state.resources = result.data || [];
      state.selectedResourceId = null;
      byId('resource-list-title').textContent = 'Available resources';
      renderResources();
    } catch (error) { showToast(error.message, true); }
  });
  byId('resource-lookup-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    try { await loadResourceById(event.currentTarget.elements.resourceId.value.trim()); }
    catch (error) { showToast(error.message, true); }
  });
  document.querySelectorAll('.query-tab').forEach((tab) => tab.addEventListener('click', () => setQueryType(tab.dataset.query)));
  byId('reservation-query-form').addEventListener('submit', (event) => { event.preventDefault(); queryReservations(); });
  byId('close-events').addEventListener('click', () => byId('event-section').classList.add('hidden'));
  byId('utilization-form').addEventListener('submit', showUtilization);
  byId('rebuild-button').addEventListener('click', rebuildProjections);
  byId('connection-modal').addEventListener('click', (event) => {
    if (event.target === byId('connection-modal') && state.credentials) byId('connection-modal').classList.add('hidden');
  });
  byId('resource-modal').addEventListener('click', (event) => {
    if (event.target === byId('resource-modal')) closeResourceModal();
  });
}

bindEvents();
initializeDates();
setConnection(false);
byId('reservation-query-input').value = state.actorId;