/* Administrator pages: overview analytics, pickups, users, partners and the audit log. */

const CHART_COLORS = ['#1f5138', '#2b7a78', '#6aa391', '#9bc3b3', '#8a5a00', '#a13a2c', '#66726c', '#c4ccc5', '#3d6b8a', '#7b6a9b', '#b08a4a', '#4a5650'];

function renderChart(id, type, obj, extra) {
  const el = document.getElementById(id);
  if (!el || !window.Chart) return;
  const labels = Object.keys(obj), values = Object.values(obj);
  State.charts.push(new Chart(el, {
    type: type === 'hbar' ? 'bar' : type,
    data: { labels, datasets: [{ data: values, backgroundColor: type === 'line' ? '#2b7a78' : CHART_COLORS, borderColor: '#2b7a78', borderWidth: type === 'line' ? 2 : 0, tension: 0.25 }] },
    options: Object.assign({
      maintainAspectRatio: false, indexAxis: type === 'hbar' ? 'y' : 'x',
      plugins: { legend: { display: false } },
      scales: { [type === 'hbar' ? 'x' : 'y']: { beginAtZero: true, ticks: { precision: 0 } } }
    }, extra || {})
  }));
}

const chartBox = (id, title, data) => `<div class="card"><h3>${esc(title)}</h3>${window.Chart ? `<div class="chart-box"><canvas id="${id}" role="img" aria-label="${esc(title)}"></canvas></div>` : ''}
  <details${window.Chart ? '' : ' open'}><summary class="small muted">Data table</summary><div style="margin-top:8px">${Object.keys(data).length ? Object.entries(data).map(([k, v]) =>
    `<div class="bar-line"><span>${esc(k)}</span><i style="width:${Math.max(4, v / Math.max(...Object.values(data)) * 100)}%"></i><b>${v}</b></div>`).join('') : '<span class="muted small">No data for this period.</span>'}</div></details></div>`;

Views.admin = async () => {
  const r = State.range;
  const m = await API.admin.metrics({ from: r.from, to: r.to, includeDemo: r.includeDemo });
  const cards = [
    [m.totalBookings, 'Total bookings'], [m.pendingPickups, 'Pending pickups'], [m.completedCollections, 'Collections completed'],
    [m.awaitingProcessing, 'Items awaiting processing'], [m.recyclingConfirmed, 'Processing outcomes confirmed'],
    [m.failedPickups, 'Failed pickups'], [m.rejected, 'Rejected by partners'], [m.overdue, 'Overdue pickups'],
    [m.avgCollectionHours + ' h', 'Average time from booking to collection']];
  const html = `
    <div class="page-head"><div><h1>Overview</h1><p class="muted">Calculated from stored records each time this page loads.</p></div></div>
    <form id="rangeForm" class="card tight row" style="align-items:flex-end" novalidate>
      <div><label for="rFrom">Booked from</label><input id="rFrom" type="date" value="${esc(r.from)}"></div>
      <div><label for="rTo">Booked to</label><input id="rTo" type="date" value="${esc(r.to)}"></div>
      ${State.config.demoMode ? `<label class="check" style="margin:0 8px 8px"><input id="rDemo" type="checkbox" ${r.includeDemo ? 'checked' : ''}>Include demonstration data</label>` : ''}
      <button class="btn" type="submit">Apply</button>
    </form>
    ${m.includesDemoData ? '<div class="notice warn">These figures include demonstration data. Uncheck "Include demonstration data" to see real activity only.</div>' : ''}
    ${m.totalBookings === 0 ? '<div class="card empty" style="margin-top:16px">No bookings in this period yet.</div>' : `
    <div class="grid cols-4" style="margin-top:16px">${cards.map(([n, l]) => stat(n, l)).join('')}</div>
    <div class="grid cols-2 section">
      ${chartBox('chStatus', 'Pickups by status (select a bar to filter the pickup list)', m.byStatus)}
      ${chartBox('chMonth', 'Bookings per month', m.byMonth)}
      ${chartBox('chCat', 'Items by category (quantity)', m.byCategory)}
      ${chartBox('chLoc', 'Pickups by locality', m.byLocality)}
    </div>
    <div class="card section"><h3>Weights</h3>
      <dl class="facts"><dt>Reported at booking</dt><dd>${m.estimatedWeightKg} kg (approximate, entered by customers, cancelled bookings excluded)</dd>
        <dt>Measured by partners</dt><dd>${m.verifiedWeightKg} kg (recorded when a recycling partner confirms the outcome; not independently audited)</dd></dl>
      <p class="muted small" style="margin:10px 0 0">Environmental savings such as CO2 are not shown, because no sourced emission factors are configured.</p></div>`}
    ${State.config.demoMode ? `<div class="card section"><h3>Demonstration data</h3>
      <p class="muted">Resets only the sample pickups and their history to their starting state. Real accounts and real bookings are not changed.</p>
      <button class="btn secondary" data-act="reset-demo">Reset demonstration data</button></div>` : ''}`;
  const reverse = Object.fromEntries(Object.entries(State.meta.statuses).map(([k, l]) => [l, k]));
  const mount = () => {
    destroyCharts();
    renderChart('chStatus', 'hbar', m.byStatus, { onClick: (e, els, chart) => {
      if (els.length) { State.lists.adminPickups = { status: reverse[chart.data.labels[els[0].index]] || 'ALL', q: '', page: 0 }; location.hash = '#/admin/pickups'; }
    } });
    renderChart('chMonth', 'line', m.byMonth);
    renderChart('chCat', 'bar', m.byCategory);
    renderChart('chLoc', 'bar', m.byLocality);
  };
  return { html, mount };
};

Views['admin/pickups'] = async () => {
  const st = State.lists.adminPickups;
  const page = await API.pickups({ status: st.status, q: st.q, page: st.page, size: 10 });
  const options = [['ALL', 'All statuses'], ['ACTIVE', 'Active'], ['CLOSED', 'Closed'], ...Object.entries(State.meta.statuses)];
  return { html: `
    <div class="page-head"><div><h1>Pickups</h1><p class="muted">Open a pickup to confirm it, assign a collector, or close the case.</p></div></div>
    <form class="card tight row" data-form="admin-pickup-filter" style="align-items:flex-end" novalidate>
      <div><label for="apStatus">Status</label><select id="apStatus">${options.map(([v, l]) => `<option value="${esc(v)}" ${v === st.status ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select></div>
      <div><label for="apQ">Reference</label><input id="apQ" value="${esc(st.q)}" placeholder="Search by reference"></div>
      <button class="btn" type="submit">Filter</button></form>
    <div style="margin-top:14px">${pickupTable(page.content, { collector: true, empty: 'No pickups match these filters.' })}</div>
    ${pagerHtml(page, 'adminPickups')}` };
};

/* ---------- users ---------- */
Views['admin/users'] = async () => {
  const st = State.lists.users;
  const page = await API.admin.users({ role: st.role, q: st.q, page: st.page, size: 10 });
  const roles = [['', 'All roles'], ['CUSTOMER', 'Customer'], ['COLLECTOR', 'Collector'], ['RECYCLER', 'Recycler'], ['ADMIN', 'Administrator']];
  return { html: `
    <div class="page-head"><div><h1>Users</h1><p class="muted">Customers register themselves. Create collector, recycler and administrator accounts here.</p></div>
      <button class="btn" data-act="add-user">${icon('plus')}Add account</button></div>
    <form class="card tight row" data-form="user-filter" style="align-items:flex-end" novalidate>
      <div><label for="uRole">Role</label><select id="uRole">${roles.map(([v, l]) => `<option value="${v}" ${v === st.role ? 'selected' : ''}>${l}</option>`).join('')}</select></div>
      <div><label for="uQ">Name or email</label><input id="uQ" value="${esc(st.q)}"></div>
      <button class="btn" type="submit">Filter</button></form>
    <div class="table-wrap" style="margin-top:14px"><table class="responsive"><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Action</th></tr></thead><tbody>
      ${page.content.length ? page.content.map((u) => `<tr>
        <td data-label="Name">${esc(u.name)} ${sampleBadge(u.demo)}</td>
        <td data-label="Email">${esc(u.email)}</td>
        <td data-label="Role">${esc(u.roleLabel)}${u.partnerName ? '<br><span class="muted small">' + esc(u.partnerName) + '</span>' : ''}</td>
        <td data-label="Status"><span class="badge ${u.active ? 'success' : 'warn'}">${icon(u.active ? 'check' : 'alert')}${u.active ? 'Active' : 'Deactivated'}</span></td>
        <td data-label="Action">${u.id === State.user.id ? '<span class="muted small">This is you</span>' : `<button class="btn secondary small" data-act="toggle-user" data-id="${u.id}" data-active="${!u.active}">${u.active ? 'Deactivate' : 'Reactivate'}</button>`}</td></tr>`).join('') : emptyRow(5, 'No users match these filters.')}
    </tbody></table></div>${pagerHtml(page, 'users')}` };
};

async function openUserForm() {
  let recyclers = [];
  try { recyclers = (await API.admin.partners()).filter((p) => p.type === 'RECYCLER' && p.active); } catch (e) { recyclers = []; }
  openModal(`<h2>Add account</h2>
    <form id="userForm" novalidate>
      <div class="error" data-error="form" role="alert"></div>
      ${field('name', 'Full name', input('name', 'text', 'maxlength="80"'))}
      ${field('email', 'Email', input('email', 'email'))}
      ${field('phone', 'Mobile number (optional)', input('phone', 'tel', 'inputmode="numeric" maxlength="10"'))}
      ${field('role', 'Role', `<select id="f-role" name="role"><option value="COLLECTOR">Collector</option><option value="RECYCLER">Recycler</option><option value="ADMIN">Administrator</option><option value="CUSTOMER">Customer</option></select>`)}
      ${field('partnerId', 'Recycling partner (recycler accounts only)', `<select id="f-partnerId" name="partnerId"><option value="">None</option>${recyclers.map((p) => `<option value="${p.id}">${esc(p.name)}</option>`).join('')}</select>`)}
      ${field('password', 'Temporary password', input('password', 'password', 'autocomplete="new-password"'), 'At least 8 characters, with a letter and a number. Share it securely.')}
      <div class="row" style="margin-top:14px"><button class="btn" type="submit">Create account</button><button type="button" class="btn secondary" data-act="close-modal">Cancel</button></div>
    </form>`);
}

async function submitUserForm(form) {
  clearErrors(form);
  const v = (n) => $(`[name="${n}"]`, form).value.trim();
  const body = { name: v('name'), email: v('email'), phone: v('phone') || null, password: v('password'), role: v('role'), partnerId: v('partnerId') ? Number(v('partnerId')) : null };
  const btn = $('button[type=submit]', form); btn.disabled = true;
  try { await API.admin.createUser(body); closeModal(); toast('Account created.'); render(true); }
  catch (e) { showErrors(form, Object.assign({ form: e.message }, e.fieldErrors)); btn.disabled = false; }
}

/* ---------- partners ---------- */
State.adminPartners = [];

Views['admin/partners'] = async () => {
  State.adminPartners = await API.admin.partners();
  return { html: `
    <div class="page-head"><div><h1>Partners</h1><p class="muted">Manage collection facilities and recycling partners. Only mark a partner as verified after you have confirmed its authorisation, and record how.</p></div>
      <button class="btn" data-act="add-partner">${icon('plus')}Add partner</button></div>
    <div class="table-wrap"><table class="responsive"><thead><tr><th>Name</th><th>Type</th><th>Locality</th><th>Verification</th><th>Listing</th><th>Action</th></tr></thead><tbody>
    ${State.adminPartners.length ? State.adminPartners.map((p) => `<tr>
      <td data-label="Name">${esc(p.name)} ${sampleBadge(p.demo)}</td><td data-label="Type">${esc(p.typeLabel)}</td><td data-label="Locality">${esc(p.locality)}</td>
      <td data-label="Verification"><span class="badge ${p.verification === 'VERIFIED' ? 'success' : 'warn'}">${icon(p.verification === 'VERIFIED' ? 'check' : 'alert')}${esc(p.verificationLabel)}</span></td>
      <td data-label="Listing">${p.active ? 'Listed' : 'Hidden'}</td>
      <td data-label="Action"><button class="btn secondary small" data-act="edit-partner" data-id="${p.id}">Edit</button>
        <button class="btn secondary small" data-act="toggle-partner" data-id="${p.id}" data-active="${!p.active}">${p.active ? 'Hide' : 'List'}</button></td></tr>`).join('') : emptyRow(6, 'No partners yet.')}
    </tbody></table></div>` };
};

function openPartnerForm(p) {
  const checks = (name, options, selected) => options.map(([v, l]) =>
    `<label class="check"><input type="checkbox" name="${name}" value="${esc(v)}" ${selected.includes(v) ? 'checked' : ''}>${esc(l)}</label>`).join('');
  const v = p || { name: '', type: 'RECYCLER', address: '', city: 'Mumbai', locality: '', serviceArea: '', acceptedCategories: [], contactEmail: '', contactPhone: '',
    services: [], verification: 'PENDING', verificationNote: '', lat: '', lng: '', active: true };
  openModal(`<h2>${p ? 'Edit partner' : 'Add partner'}</h2>
    <form id="partnerForm" data-id="${p ? p.id : ''}" novalidate>
      <div class="error" data-error="form" role="alert"></div>
      <div class="form-grid">
        ${field('name', 'Name', input('name', 'text', `maxlength="120" value="${esc(v.name)}"`))}
        ${field('type', 'Type', select('type', [['RECYCLER', 'Recycling partner'], ['COLLECTION_FACILITY', 'Collection facility']], v.type))}
        ${field('address', 'Address', input('address', 'text', `maxlength="200" value="${esc(v.address)}"`))}
        ${field('city', 'City', input('city', 'text', `value="${esc(v.city)}"`))}
        ${field('locality', 'Locality', input('locality', 'text', `value="${esc(v.locality)}"`))}
        ${field('serviceArea', 'Service area', input('serviceArea', 'text', `maxlength="300" value="${esc(v.serviceArea || '')}"`))}
        ${field('contactEmail', 'Contact email', input('contactEmail', 'email', `value="${esc(v.contactEmail || '')}"`))}
        ${field('contactPhone', 'Contact phone', input('contactPhone', 'tel', `value="${esc(v.contactPhone || '')}"`))}
        ${field('lat', 'Latitude (optional)', input('lat', 'number', `step="any" value="${esc(v.lat == null ? '' : v.lat)}"`))}
        ${field('lng', 'Longitude (optional)', input('lng', 'number', `step="any" value="${esc(v.lng == null ? '' : v.lng)}"`))}
      </div>
      <fieldset><legend>Accepted categories</legend>${checks('acceptedCategories', State.meta.categories.map((c) => [c, c]), v.acceptedCategories)}<div class="error" data-error="acceptedCategories" role="alert"></div></fieldset>
      <fieldset><legend>Services</legend>${checks('services', [['DOORSTEP', 'Doorstep collection'], ['DROP_OFF', 'Drop-off']], v.services)}<div class="error" data-error="services" role="alert"></div></fieldset>
      <fieldset><legend>Verification</legend>
        ${field('verification', 'Status', select('verification', [['SAMPLE', 'Sample entry, not verified'], ['PENDING', 'Verification pending'], ['VERIFIED', 'Verified by administrator']], v.verification))}
        ${field('verificationNote', 'How was this verified?', input('verificationNote', 'text', `maxlength="300" value="${esc(v.verificationNote || '')}"`), 'Required when marking a partner as verified.')}</fieldset>
      <div class="row" style="margin-top:14px"><button class="btn" type="submit">${p ? 'Save changes' : 'Add partner'}</button><button type="button" class="btn secondary" data-act="close-modal">Cancel</button></div>
    </form>`);
}

async function submitPartnerForm(form) {
  clearErrors(form);
  const v = (n) => $(`[name="${n}"]`, form).value.trim();
  const many = (n) => $$(`[name="${n}"]:checked`, form).map((e) => e.value);
  const num = (n) => (v(n) === '' ? null : Number(v(n)));
  const body = { name: v('name'), type: v('type'), address: v('address'), city: v('city'), locality: v('locality'), serviceArea: v('serviceArea') || null,
    acceptedCategories: many('acceptedCategories'), contactEmail: v('contactEmail') || null, contactPhone: v('contactPhone') || null,
    services: many('services'), verification: v('verification'), verificationNote: v('verificationNote') || null, lat: num('lat'), lng: num('lng'), active: true };
  const id = form.dataset.id;
  if (id) { const existing = State.adminPartners.find((p) => String(p.id) === id); if (existing) body.active = existing.active; }
  const btn = $('button[type=submit]', form); btn.disabled = true;
  try {
    if (id) await API.admin.updatePartner(id, body); else await API.admin.createPartner(body);
    closeModal(); toast('Partner saved.'); render(true);
  } catch (e) { showErrors(form, Object.assign({ form: e.message }, e.fieldErrors)); btn.disabled = false; }
}

/* ---------- verified handovers ---------- */
Views['admin/handovers'] = async () => {
  const st = State.lists.handovers;
  const page = await API.admin.handovers({ page: st.page, size: 15 });
  const rows = page.content.map((h) => `<tr>
      <td data-label="Handover"><strong>${esc(h.handoverReference)}</strong> ${sampleBadge(h.demo)}</td>
      <td data-label="Pickup"><a href="#/pickup/${esc(h.pickupReference)}">${esc(h.pickupReference)}</a><br><span class="muted small">${esc(h.locality)}</span></td>
      <td data-label="Customer">${esc(h.customerName)}</td>
      <td data-label="Collector">${esc(h.collectorName)}</td>
      <td data-label="Verified">${fmtDateTime(h.verifiedAt)}</td>
      <td data-label="Collected">${h.collectedAt ? fmtDateTime(h.collectedAt) : '<span class="muted">Not yet</span>'}</td>
      <td data-label="Photo">${h.hasPhoto ? 'Yes' : 'No'}</td></tr>`).join('');
  return { html: `
    <div class="page-head"><div><h1>Verified handovers</h1><p class="muted">A record exists only after a collector entered the customer's one-time code. Codes themselves are never stored or shown.</p></div></div>
    <div class="table-wrap"><table class="responsive">
      <thead><tr><th>Handover</th><th>Pickup</th><th>Customer</th><th>Collector</th><th>Verified</th><th>Collected</th><th>Photo</th></tr></thead>
      <tbody>${rows || emptyRow(7, 'No handovers have been verified yet.')}</tbody>
    </table></div>${pagerHtml(page, 'handovers')}` };
};

/* ---------- audit ---------- */
Views['admin/audit'] = async () => {
  const st = State.lists.audit;
  const page = await API.admin.audit({ failedOnly: st.failedOnly, page: st.page, size: 15 });
  return { html: `
    <div class="page-head"><div><h1>Audit log</h1><p class="muted">Every attempted status change, including attempts that were blocked.</p></div></div>
    <label class="check"><input type="checkbox" id="auditBlocked" ${st.failedOnly ? 'checked' : ''}>Show blocked attempts only</label>
    <div class="table-wrap"><table class="responsive"><thead><tr><th>When</th><th>Who</th><th>Pickup</th><th>Result</th><th>Detail</th></tr></thead><tbody>
    ${page.content.length ? page.content.map((a) => `<tr>
      <td data-label="When">${fmtDateTime(a.occurredAt)}</td><td data-label="Who">${esc(a.actorName || '')}<br><span class="muted small">${esc(a.actorRole || '')}</span></td>
      <td data-label="Pickup">${a.pickupReference ? `<a href="#/pickup/${esc(a.pickupReference)}">${esc(a.pickupReference)}</a>` : ''}</td>
      <td data-label="Result"><span class="badge ${a.success ? 'success' : 'warn'}">${icon(a.success ? 'check' : 'alert')}${a.success ? 'Recorded' : 'Blocked'}</span></td>
      <td data-label="Detail">${esc(a.message)}</td></tr>`).join('') : emptyRow(5, 'No audit entries yet.')}
    </tbody></table></div>${pagerHtml(page, 'audit')}` };
};
