/* No dependencies, storage, or live calls in the public demonstration. */
const $ = (id) => document.getElementById(id);
const scenarios = Object.freeze({
  partial: {
    kicker: 'Search · partial result', title: 'Return useful offers within the deadline.',
    summary: 'Three synthetic suppliers receive the same search. One misses its time budget. The client still receives the completed offers, clearly marked partial.',
    steps: [['Accept request', 'Validate tenant and request; start one bounded search deadline.'], ['Fan out', 'Call enabled suppliers concurrently under the shared bulkhead.'], ['Contain failure', 'A timeout or open circuit contributes no offers; it does not erase healthy responses.'], ['Return partial', 'Include available offers and a partial flag so the client can decide whether to poll.']],
    responses: [['STUB', '2 offers · 240 ms'], ['SKYRIDE', '1 offer · 510 ms'], ['SLOW_STUB', 'timeout · no offer body retained']],
    decision: 'Decision: return 3 offers, partial = true. The timed-out supplier is isolated.'
  },
  unknown: {
    kicker: 'Booking · ambiguous supplier write', title: 'Stop before a second write.',
    summary: 'A booking request has an idempotency key. The supplier call may have succeeded just as the connection failed; retrying blindly could create a duplicate booking.',
    steps: [['Claim key', 'Hash the client key and fingerprint; create one durable operation.'], ['Attempt write', 'Persist IN_PROGRESS before calling the supplier.'], ['Lose certainty', 'Transport breaks after dispatch; mark UNKNOWN_OUTCOME.'], ['Review evidence', 'An operator checks supplier evidence and records a decision. The same key maps to the original operation.']],
    responses: [['STUB', 'write sent · response unavailable'], ['Repeat request', 'same key · existing operation returned'], ['Different payload', 'same key · conflict']],
    decision: 'Decision: no automatic second supplier write while the outcome is unknown.'
  },
  cancellation: {
    kicker: 'Cancellation · durable queue', title: 'Keep recovery explicit.',
    summary: 'Cancellation is queued durably and claimed by a worker. Exhausted safe retries move the job to a dead-letter queue with a sanitized reason code.',
    steps: [['Request', 'Create the cancellation operation and queue job.'], ['Claim lease', 'A worker leases the job; restarts can recover expired leases.'], ['Fail safely', 'Record bounded attempts and a reason code; move exhausted work to DLQ.'], ['Review and replay', 'Only an operator can replay, with evidence, when the supplier declares cancellation idempotent. Otherwise resolve after external reconciliation.']],
    responses: [['STUB', 'idempotent cancel · timeout'], ['Queue', 'retry scheduled, then DLQ'], ['Operator', 'evidence reference audited']],
    decision: 'Decision: replay only if the supplier is verified idempotent; every transition remains in the operation timeline.'
  }
});
const node = (tag, content, className) => { const element = document.createElement(tag); if (content !== undefined) element.textContent = String(content); if (className) element.className = className; return element; };
const clear = (element) => element.replaceChildren();
const stamp = (value) => value ? new Date(value).toLocaleString() : '—';
const short = (value) => value == null ? '—' : String(value);
let token = null;
let roles = [];
let dashboardUrl = null;

function renderScenario(key) {
  const selected = scenarios[key];
  if (!selected) return;
  document.querySelectorAll('[data-scenario]').forEach(button => button.classList.toggle('selected', button.dataset.scenario === key));
  $('scenario-kicker').textContent = selected.kicker;
  $('scenario-title').textContent = selected.title;
  $('scenario-summary').textContent = selected.summary;
  clear($('journey'));
  selected.steps.forEach(([title, detail]) => { const item = node('li'); item.append(node('strong', title), node('span', detail)); $('journey').append(item); });
  clear($('supplier-responses'));
  selected.responses.forEach(([name, response]) => { const row = node('div', undefined, 'response'); row.append(node('strong', name), node('span', response)); $('supplier-responses').append(row); });
  $('decision').textContent = selected.decision;
}

function bars(container, values) {
  clear(container);
  const max = Math.max(1, ...values.map(item => item[1]));
  values.forEach(([label, value, warn]) => {
    const row = node('div', undefined, `bar-row${warn ? ' warn' : ''}`);
    const track = node('div', undefined, 'bar-track');
    const fill = node('div', undefined, 'bar-fill');
    fill.style.width = `${Math.max(0, Math.min(100, value / max * 100))}%`;
    track.append(fill);
    row.append(node('span', label), track, node('strong', value));
    container.append(row);
  });
}

async function verifySynthetic() {
  const payload = $('synthetic-payload').textContent;
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode('synthetic-demo-key-public-never-deploy'),
    {name: 'HMAC', hash: 'SHA-256'}, false, ['sign', 'verify']);
  const bytes = new TextEncoder().encode(payload);
  const signature = await crypto.subtle.sign('HMAC', key, bytes);
  const signatureHex = [...new Uint8Array(signature)].map(value => value.toString(16).padStart(2, '0')).join('');
  const valid = await crypto.subtle.verify('HMAC', key, signature, bytes);
  const tampered = await crypto.subtle.verify('HMAC', key, signature, new TextEncoder().encode(payload + '-changed'));
  $('verification-result').textContent = `Synthetic signature: ${signatureHex}. Original: ${valid ? 'valid' : 'invalid'} · altered payload: ${tampered ? 'valid' : 'invalid'}. Verification uses only the public synthetic key in this page.`;
}

async function api(path, options = {}) {
  if (!token) throw new Error('Connect with an OIDC access token first.');
  const response = await fetch(path, { ...options, headers: {Authorization: `Bearer ${token}`, ...(options.body ? {'Content-Type': 'application/json'} : {})}, cache: 'no-store' });
  if (response.status === 401) { disconnect(); throw new Error('The access token expired or was rejected.'); }
  if (response.status === 403) throw new Error('This role or tenant cannot access that record.');
  if (response.status === 404) throw new Error('No operation is visible for this tenant and ID.');
  if (response.status === 409) throw new Error('State changed; refresh before acting again.');
  if (!response.ok) throw new Error(`Request failed (${response.status}).`);
  return response.status === 204 || !response.headers.get('content-type')?.includes('json') ? null : response.json();
}

function disconnect() {
  token = null; roles = []; dashboardUrl = null;
  $('access-token').value = '';
  $('live-content').classList.add('hidden');
  $('disconnect').disabled = true;
  $('auth-status').textContent = 'Disconnected. Use a short-lived token from your approved OIDC flow.';
  ['operation-detail','timeline','queue','dlq','history','review','metrics-chart','metrics-detail','dashboard-link'].forEach(id => clear($(id)));
}

async function connect() {
  const candidate = $('access-token').value.trim();
  $('access-token').value = '';
  if (!candidate) { $('auth-status').textContent = 'Enter an access token.'; return; }
  token = candidate;
  try {
    const session = await api('/api/v1/visual/session');
    roles = session.roles;
    $('auth-status').textContent = `Connected · tenant ${session.tenantId} · ${roles.join(', ')}`;
    $('disconnect').disabled = false;
    $('live-content').classList.remove('hidden');
    if (roles.includes('SUPPORT') || roles.includes('OPERATOR')) await refreshQueues();
    if (roles.includes('OPERATOR')) await refreshMetrics();
    else $('metrics-detail').textContent = 'Operator role is required for the metrics snapshot.';
  } catch (error) { disconnect(); $('auth-status').textContent = error.message; }
}

function table(container, columns, rows) {
  clear(container);
  if (!rows.length) { container.append(node('p', 'No records in this bounded view.')); return; }
  const tableNode = node('table', undefined, 'data-table');
  const head = node('thead'); const header = node('tr');
  columns.forEach(([title]) => header.append(node('th', title)));
  head.append(header); tableNode.append(head);
  const body = node('tbody');
  rows.forEach(item => {
    const row = node('tr');
    columns.forEach(([, render]) => {
      const cell = node('td'); const value = render(item);
      if (value instanceof Node) cell.append(value); else cell.textContent = short(value);
      row.append(cell);
    });
    body.append(row);
  });
  tableNode.append(body); container.append(tableNode);
}

function operationLink(id) {
  const button = node('button', 'Open timeline', 'small-button'); button.type = 'button';
  button.addEventListener('click', () => { $('operation-id').value = id; loadOperation(); });
  return button;
}

function traceLink(id) {
  if (!/^[0-9a-f]{32}$/.test(id || '') || !dashboardUrl) return node('span', short(id));
  const base = new URL(dashboardUrl).origin;
  const left = JSON.stringify({datasource:'tempo',queries:[{refId:'A',queryType:'traceId',query:id}]});
  const link = node('a', `Trace ${id.slice(0, 8)}…`);
  link.href = `${base}/explore?left=${encodeURIComponent(left)}`;
  link.target = '_blank'; link.rel = 'noopener noreferrer';
  return link;
}

async function loadOperation() {
  const id = $('operation-id').value.trim();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) { $('operation-status').textContent = 'Enter a valid operation UUID.'; return; }
  try {
    const admin = roles.includes('SUPPORT') || roles.includes('OPERATOR');
    const base = admin ? '/api/v1/admin/operations' : '/api/v1/operations';
    const [operation, events] = await Promise.all([api(`${base}/${id}`), api(`${base}/${id}/timeline`)]);
    $('operation-status').textContent = `Showing ${operation.kind} · ${operation.state} · tenant ${operation.tenantId}`;
    table($('operation-detail'), [['Operation', x => x.operationId], ['Supplier', x => x.supplierCode], ['Created', x => stamp(x.createdAt)], ['Updated', x => stamp(x.updatedAt)]], [operation]);
    clear($('timeline'));
    const list = node('ol', undefined, 'event-list');
    events.forEach(event => { const item = node('li'); item.append(node('time', stamp(event.createdAt)), node('strong', `${event.action} · ${short(event.detailCode)}`), event.traceId ? traceLink(event.traceId) : node('span', `Request ${short(event.requestId)}`)); list.append(item); });
    $('timeline').append(list);
  } catch (error) { $('operation-status').textContent = error.message; clear($('operation-detail')); clear($('timeline')); }
}

async function act(path, decision, evidenceReference, localStatus) {
  if (!/^[A-Za-z0-9._:/-]{4,128}$/.test(evidenceReference)) {
    localStatus.textContent = 'Use 4–128 letters, digits, dots, underscores, colons, slashes, or hyphens.';
    return;
  }
  try {
    await api(path, {method:'POST', body:JSON.stringify({evidenceReference, decision})});
    const refreshed = await refreshQueues();
    $('action-status').textContent = `${decision} recorded. ${refreshed ? 'Queues refreshed.' : 'Refresh queues to inspect the new state.'}`;
  } catch (error) { localStatus.textContent = error.message; $('action-status').textContent = error.message; }
}

function actionButton(label, path, decision) {
  const control = node('div', undefined, 'operator-action');
  const button = node('button', label, 'small-button'); button.type = 'button';
  const entry = node('div', undefined, 'evidence-entry hidden');
  const guidance = label === 'Resolve'
    ? 'Verify the supplier outcome externally before resolving. Enter its safe evidence reference.'
    : 'Enter a safe external evidence reference before recording this action.';
  const fieldLabel = node('label', guidance);
  const input = node('input'); input.type = 'text'; input.maxLength = 128; input.autocomplete = 'off'; input.spellcheck = false;
  fieldLabel.append(input);
  const submit = node('button', `Record ${label}`, 'small-button'); submit.type = 'button';
  const cancel = node('button', 'Cancel', 'small-button'); cancel.type = 'button';
  const status = node('p'); status.setAttribute('role', 'status');
  button.addEventListener('click', () => { entry.classList.remove('hidden'); status.textContent = ''; input.focus(); });
  cancel.addEventListener('click', () => { input.value = ''; status.textContent = ''; entry.classList.add('hidden'); });
  submit.addEventListener('click', async () => {
    submit.disabled = true;
    try { await act(path, decision, input.value.trim(), status); }
    finally { submit.disabled = false; }
  });
  entry.append(fieldLabel, submit, cancel, status);
  control.append(button, entry);
  return control;
}

async function refreshQueues() {
  if (!roles.includes('SUPPORT') && !roles.includes('OPERATOR')) {
    $('action-status').textContent = 'Support or operator role is required for queues.'; return;
  }
  try {
    const [queue, dlq, history, review] = await Promise.all([
      api('/api/v1/admin/cancellations/queue?limit=50'),
      api('/api/v1/admin/cancellations/dlq?limit=50'),
      api('/api/v1/admin/cancellations/history?limit=50'),
      api('/api/v1/admin/operations/review?limit=50')]);
    table($('queue'), [['Operation', x => operationLink(x.operationId)], ['Tenant', x => x.tenantId], ['Supplier', x => x.supplierCode], ['State', x => x.state], ['Attempts', x => x.attempts], ['Next attempt', x => stamp(x.nextAttemptAt)]], queue);
    table($('dlq'), [['Operation', x => operationLink(x.operationId)], ['Tenant', x => x.tenantId], ['Reason', x => x.reasonCode], ['Attempts', x => x.attempts], ['Replay count', x => x.replayCount], ['Action', x => {
      if (!roles.includes('OPERATOR')) return 'Operator only';
      const actions = node('div');
      if (x.replayPermitted) actions.append(actionButton('Replay', `/api/v1/admin/cancellations/dlq/${x.jobId}/replay`, 'Replay'));
      else actions.append(node('span', 'Replay not permitted · '));
      actions.append(actionButton('Resolve', `/api/v1/admin/cancellations/dlq/${x.jobId}/resolve`, 'Resolve'));
      return actions;
    }]], dlq);
    table($('history'), [['Operation', x => operationLink(x.operationId)], ['Tenant', x => x.tenantId], ['Supplier', x => x.supplierCode], ['State', x => x.state], ['Attempts', x => x.attempts], ['Last change', x => stamp(x.updatedAt)]], history);
    table($('review'), [['Operation', x => operationLink(x.operationId)], ['Tenant', x => x.tenantId], ['Kind', x => x.kind], ['State', x => x.state], ['Action', x => roles.includes('OPERATOR') ? actionButton('Mark manual review', `/api/v1/admin/operations/${x.operationId}/review`, 'MANUAL_REVIEW') : 'Operator only']], review);
    $('action-status').textContent = `Loaded ${queue.length} queued, ${dlq.length} dead-letter, ${history.length} history, and ${review.length} review records.`;
    return true;
  } catch (error) { $('action-status').textContent = error.message; return false; }
}

async function refreshMetrics() {
  if (!roles.includes('OPERATOR')) { $('metrics-detail').textContent = 'Operator role is required.'; return; }
  try {
    const metrics = await api('/api/v1/admin/visual/metrics');
    dashboardUrl = metrics.dashboardUrl;
    $('sampled-at').textContent = `Sampled ${stamp(metrics.sampledAt)}`;
    bars($('metrics-chart'), [['Complete searches', metrics.searchComplete], ['Partial searches', metrics.searchPartial, true], ['Supplier timeouts', metrics.supplierTimeout, true], ['Circuit-open skips', metrics.circuitOpen, true], ['Idempotency replays', metrics.claimsReplay], ['Unknown writes for review', metrics.reviewDepth, true], ['Pending cancellations', metrics.pendingCancellations], ['Dead letters', metrics.deadLetters, true]]);
    $('metrics-detail').textContent = `Idempotency claims: ${metrics.claimsAcquired} acquired, ${metrics.claimsReplay} replayed, ${metrics.claimsConflict} conflicted. Bulkhead saturation: ${(metrics.bulkheadSaturation * 100).toFixed(0)}%.`;
    clear($('dashboard-link'));
    if (dashboardUrl) { const link = node('a', 'Open Prometheus charts in Grafana'); link.href = dashboardUrl; link.target = '_blank'; link.rel = 'noopener noreferrer'; $('dashboard-link').append(link); }
  } catch (error) { $('metrics-detail').textContent = error.message; }
}

document.querySelectorAll('[data-scenario]').forEach(button => button.addEventListener('click', () => renderScenario(button.dataset.scenario)));
$('demo-tab').addEventListener('click', () => { $('demo').classList.remove('hidden'); $('console').classList.add('hidden'); $('demo-tab').classList.add('active'); $('console-tab').classList.remove('active'); });
$('console-tab').addEventListener('click', () => { $('console').classList.remove('hidden'); $('demo').classList.add('hidden'); $('console-tab').classList.add('active'); $('demo-tab').classList.remove('active'); });
$('verify-button').addEventListener('click', () => verifySynthetic().catch(() => { $('verification-result').textContent = 'Web Crypto is unavailable in this browser context.'; }));
$('connect').addEventListener('click', connect);
$('disconnect').addEventListener('click', disconnect);
$('load-operation').addEventListener('click', loadOperation);
$('refresh-queues').addEventListener('click', refreshQueues);
$('refresh-metrics').addEventListener('click', refreshMetrics);
$('synthetic-payload').textContent = 'demo-offer|STUB|USD 42.00|expires=2030-01-01T00:00:00Z';
renderScenario('partial');
bars($('demo-chart'), [['Completed searches', 18], ['Partial searches', 4, true], ['Safe replays', 3], ['Unknown outcomes', 1, true], ['Cancellation DLQ', 1, true]]);
