/* Shared helpers: state, formatting, small components. */
const State = {
  user: null, config: { demoMode: false, demoAccounts: [] }, meta: null,
  next: null, charts: [], maps: {}, stream: null, unread: 0, handoverReveal: null,
  lists: { pickups: { status: 'ALL', q: '', page: 0 }, adminPickups: { status: 'ALL', q: '', page: 0 },
           users: { role: '', q: '', page: 0 }, audit: { failedOnly: false, page: 0 }, notifications: { page: 0 },
           handovers: { page: 0 } },
  range: { from: '', to: '', includeDemo: true }
};

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const icon = (name) => `<svg class="icon" aria-hidden="true"><use href="#i-${name}"/></svg>`;
const fmtDateTime = (t) => t ? new Date(t).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '';
const fmtDate = (d) => d ? new Date(d.length === 10 ? d + 'T00:00:00' : d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
const kg = (n) => (n == null ? '' : n + ' kg');

let toastTimer;
function toast(message, isError) {
  const t = $('#toast');
  t.textContent = message;
  t.className = 'toast' + (isError ? ' error' : '');
  t.style.display = 'block';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.style.display = 'none'; }, 4500);
}

/* Status badge: text label plus a shape marker, so meaning never depends on colour alone. */
function badge(label, status) {
  let cls = '', mark = 'dot';
  if (status === 'REQUESTED') cls = '';
  else if (['CONFIRMED', 'COLLECTED', 'AT_FACILITY', 'TRANSFERRED', 'RECEIVED_BY_PARTNER', 'PROCESSING'].includes(status)) cls = 'progress';
  else if (['RECYCLING_CONFIRMED', 'COMPLETED'].includes(status)) { cls = 'success'; mark = 'check'; }
  else if (['FAILED_PICKUP', 'CANCELLED'].includes(status)) { cls = 'warn'; mark = 'alert'; }
  else if (status === 'REJECTED') { cls = 'danger'; mark = 'alert'; }
  return `<span class="badge ${cls}">${icon(mark)}${esc(label)}</span>`;
}

const sampleBadge = (demo) => demo ? '<span class="badge warn" title="Demonstration record">Demo data</span>' : '';

function timelineHtml(events, nextExpected, exceptionStatuses) {
  const bad = exceptionStatuses || ['FAILED_PICKUP', 'REJECTED', 'CANCELLED'];
  const items = events.map((e) => `
    <li class="${bad.includes(e.status) ? 'exception' : ''}">
      <strong>${esc(e.statusLabel)}</strong> <span class="when">${fmtDateTime(e.occurredAt)}</span>
      <div class="by">${esc(e.actorName ? e.actorName + ' (' + e.roleLabel + ')' : e.roleLabel)}</div>
      ${e.detail ? `<div class="note">${esc(e.detail)}</div>` : ''}
      ${e.note ? `<div class="note">Note: ${esc(e.note)}</div>` : ''}
    </li>`).join('');
  const next = nextExpected ? `<li class="next"><strong>Next:</strong> ${esc(nextExpected)}</li>` : '';
  return `<ol class="timeline">${items}${next}</ol>`;
}

function pagerHtml(page, key) {
  if (!page || page.totalPages <= 1) return '';
  return `<nav class="pager" aria-label="Pagination">
    <button class="btn secondary small" data-act="page" data-key="${key}" data-page="${page.page - 1}" ${page.page <= 0 ? 'disabled' : ''}>${icon('chev-l')}Previous</button>
    <span>Page ${page.page + 1} of ${page.totalPages} (${page.totalElements} records)</span>
    <button class="btn secondary small" data-act="page" data-key="${key}" data-page="${page.page + 1}" ${page.page + 1 >= page.totalPages ? 'disabled' : ''}>Next${icon('chev-r')}</button>
  </nav>`;
}

/* Form field helpers. Errors are matched by data-error="<field name>". */
const field = (name, label, control, hint) =>
  `<div><label for="f-${name}">${label}</label>${control}${hint ? `<div class="hint">${hint}</div>` : ''}<div class="error" data-error="${name}" role="alert"></div></div>`;
const labelled = (id, label, control) => `<div><label for="${id}">${label}</label>${control}</div>`;
const input = (name, type = 'text', extra = '') => `<input id="f-${name}" name="${name}" type="${type}" ${extra}>`;
const select = (name, options, selected, extra = '') =>
  `<select id="f-${name}" name="${name}" ${extra}>${options.map((o) => {
    const [v, l] = Array.isArray(o) ? o : [o, o];
    return `<option value="${esc(v)}" ${String(v) === String(selected) ? 'selected' : ''}>${esc(l)}</option>`;
  }).join('')}</select>`;

function clearErrors(root) {
  $$('[data-error]', root).forEach((e) => { e.textContent = ''; });
  $$('[aria-invalid]', root).forEach((e) => e.removeAttribute('aria-invalid'));
}
function showErrors(root, errors) {
  Object.entries(errors || {}).forEach(([k, v]) => {
    const el = $(`[data-error="${k}"]`, root);
    if (el) el.textContent = v;
    const ctl = $(`[name="${k}"]`, root);
    if (ctl) ctl.setAttribute('aria-invalid', 'true');
  });
}

const trackUrl = (ref) => location.origin + location.pathname + '#/track/' + ref;

function mountQr() {
  $$('.qr[data-ref]').forEach((el) => {
    el.innerHTML = '';
    try { new QRCode(el, { text: trackUrl(el.dataset.ref), width: 112, height: 112 }); }
    catch (e) { el.textContent = 'QR code unavailable'; }
  });
}

function mountMap(id, partners) {
  const el = document.getElementById(id);
  if (!el) return;
  if (State.maps[id]) { State.maps[id].remove(); delete State.maps[id]; }
  const located = partners.filter((p) => p.lat != null && p.lng != null);
  if (!window.L) {
    el.innerHTML = '<div class="empty">The map could not be loaded. The directory below lists the same partners with their addresses.</div>';
    return;
  }
  const map = L.map(id).setView([19.12, 72.88], 11);
  State.maps[id] = map;
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: '&copy; OpenStreetMap contributors' }).addTo(map);
  located.forEach((p) => {
    L.circleMarker([p.lat, p.lng], { radius: 8, color: '#1f5138', weight: 2, fillOpacity: 0.85, fillColor: p.type === 'RECYCLER' ? '#2b7a78' : '#ffffff' })
      .addTo(map)
      .bindPopup(`<strong>${esc(p.name)}</strong><br>${esc(p.typeLabel)}<br>${esc(p.verificationLabel)}`);
  });
  if (located.length > 1) map.fitBounds(located.map((p) => [p.lat, p.lng]), { padding: [30, 30] });
}

function destroyCharts() { State.charts.forEach((c) => c.destroy()); State.charts = []; }

function openModal(html) {
  $('#modalBody').innerHTML = html;
  $('#modal').hidden = false;
  const first = $('#modalBody input, #modalBody select, #modalBody button');
  if (first) first.focus();
}
function closeModal() {
  $('#modal').hidden = true;
  $('#modalBody').innerHTML = '';
  if (State.stream) { State.stream.getTracks().forEach((t) => t.stop()); State.stream = null; }
}

function skeleton() {
  return '<div aria-busy="true" aria-label="Loading"><div class="skeleton" style="width:40%;height:28px"></div><div class="skeleton"></div><div class="skeleton" style="width:85%"></div><div class="skeleton" style="height:120px"></div></div>';
}

function errorBox(e) {
  return `<div class="notice error-box" role="alert">${esc(e && e.message ? e.message : 'Something went wrong.')}</div>`;
}

function emptyRow(cols, text) {
  return `<tr><td colspan="${cols}" class="empty">${esc(text)}</td></tr>`;
}

/* A list of pickups as a responsive table. */
function pickupTable(list, opts = {}) {
  if (!list.length) return `<div class="table-wrap"><div class="empty">${esc(opts.empty || 'No pickups to show.')}</div></div>`;
  return `<div class="table-wrap"><table class="responsive">
    <thead><tr><th>Reference</th><th>Items</th><th>Pickup</th>${opts.collector ? '<th>Collector</th>' : ''}<th>Status</th></tr></thead>
    <tbody>${list.map((p) => `<tr>
      <td data-label="Reference"><a href="#/pickup/${esc(p.reference)}">${esc(p.reference)}</a> ${sampleBadge(p.demo)}</td>
      <td data-label="Items">${esc(p.itemsSummary)}</td>
      <td data-label="Pickup">${fmtDate(p.pickupDate)}, ${esc(p.timeSlot)}<br><span class="muted small">${esc(p.locality)}</span>${p.overdue ? ' <span class="badge warn">' + icon('alert') + 'Overdue</span>' : ''}</td>
      ${opts.collector ? `<td data-label="Collector">${esc(p.collectorName || 'Not assigned')}</td>` : ''}
      <td data-label="Status">${badge(p.statusLabel, p.status)}</td></tr>`).join('')}</tbody></table></div>`;
}

const STATUS_CHIPS = [['ALL', 'All'], ['ACTIVE', 'Active'], ['CLOSED', 'Closed']];
function chipsHtml(options, selected, key) {
  return `<div class="chips" role="group" aria-label="Filter">${options.map(([v, l]) =>
    `<button class="chip" data-act="chip" data-key="${key}" data-value="${esc(v)}" aria-pressed="${v === selected}">${esc(l)}</button>`).join('')}</div>`;
}

