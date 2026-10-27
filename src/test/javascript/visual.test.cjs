const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');
const path = require('node:path');

class Element {
  constructor() { this.children = []; this.textContent = ''; this.style = {}; this.classList = {toggle() {}, add() {}, remove() {}}; }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  addEventListener() {}
}

function loadVisual() {
  const elements = new Map();
  const document = {
    getElementById(id) { if (!elements.has(id)) elements.set(id, new Element()); return elements.get(id); },
    createElement() { return new Element(); },
    querySelectorAll() { return []; }
  };
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/visual/visual.js'), 'utf8');
  const context = { document, Node: Element, crypto: webcrypto, TextEncoder, URL,
    fetch() { throw new Error('The synthetic demo made a live request'); } };
  vm.runInNewContext(`${source}\nglobalThis.visualTest = {scenarios, renderScenario, verifySynthetic};`, context);
  return { elements, visual: context.visualTest };
}

test('seeded journeys cover partial search, unknown write and guarded cancellation replay', () => {
  const { elements, visual } = loadVisual();
  assert.deepEqual(Object.keys(visual.scenarios), ['partial', 'unknown', 'cancellation']);
  for (const key of Object.keys(visual.scenarios)) {
    visual.renderScenario(key);
    assert.equal(elements.get('journey').children.length, 4);
    assert.equal(elements.get('supplier-responses').children.length, 3);
    assert.ok(elements.get('decision').textContent.startsWith('Decision:'));
  }
  assert.match(elements.get('decision').textContent, /verified idempotent/);
  visual.renderScenario('unknown');
  assert.match(elements.get('scenario-summary').textContent, /idempotency key/);
  visual.renderScenario('partial');
  assert.match(elements.get('decision').textContent, /partial = true/);
});

test('synthetic HMAC accepts original and rejects changed payload', async () => {
  const { elements, visual } = loadVisual();
  await visual.verifySynthetic();
  assert.match(elements.get('verification-result').textContent, /Original: valid · altered payload: invalid/);
  assert.match(elements.get('verification-result').textContent, /Synthetic signature: [0-9a-f]{64}/);
});

test('console API calls and rendered containers match the published contract', () => {
  const root = path.resolve(__dirname, '../../main/resources');
  const html = fs.readFileSync(path.join(root, 'static/visual/index.html'), 'utf8');
  const script = fs.readFileSync(path.join(root, 'static/visual/visual.js'), 'utf8');
  const openapi = fs.readFileSync(path.join(root, 'openapi.yaml'), 'utf8');
  for (const endpoint of [
    '/api/v1/visual/session', '/api/v1/operations/{operationId}',
    '/api/v1/operations/{operationId}/timeline', '/api/v1/admin/operations/{operationId}',
    '/api/v1/admin/operations/{operationId}/timeline', '/api/v1/admin/operations/review',
    '/api/v1/admin/cancellations/queue', '/api/v1/admin/cancellations/history',
    '/api/v1/admin/cancellations/dlq', '/api/v1/admin/cancellations/dlq/{jobId}/replay',
    '/api/v1/admin/cancellations/dlq/{jobId}/resolve',
    '/api/v1/admin/operations/{operationId}/review', '/api/v1/admin/visual/metrics'
  ]) assert.ok(openapi.includes(`  ${endpoint}:`), endpoint);
  for (const id of ['demo', 'console', 'operation-detail', 'timeline', 'queue', 'dlq', 'history', 'review', 'metrics-chart']) {
    assert.ok(html.includes(`id="${id}"`), id);
    assert.ok(script.includes(`'${id}'`), id);
  }
});
