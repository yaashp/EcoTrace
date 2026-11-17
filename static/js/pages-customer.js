/* Booking, pickup lists, role dashboards, notifications and the account page. */

/* ---------- booking ---------- */
const emptyItem = () => ({ category: '', quantity: 1, condition: '', details: '' });
function newBooking() {
  return { items: [emptyItem()], address: '', city: 'Mumbai', locality: '', contactPhone: (State.user && State.user.phone) || '',
           pickupDate: '', timeSlot: '', instructions: '', estimatedWeightKg: '' };
}
State.booking = null;

function itemRow(it, i, count) {
  const n = (k) => `items[${i}].${k}`;
  const id = (k) => `f-items-${i}-${k}`;
  const ph = (k, label, control) => `<div><label for="${id(k)}">${label}</label>${control}<div class="error" data-error="${n(k)}" role="alert"></div></div>`;
  return `<fieldset data-item="${i}"><legend>Item ${i + 1}</legend>
    <div class="form-grid">
      ${ph('category', 'Category', `<select id="${id('category')}" name="${n('category')}"><option value="">Choose</option>${State.meta.categories.map((c) => `<option ${c === it.category ? 'selected' : ''}>${esc(c)}</option>`).join('')}</select>`)}
      ${ph('quantity', 'Quantity', `<input id="${id('quantity')}" name="${n('quantity')}" type="number" min="1" max="500" value="${esc(it.quantity)}">`)}
      ${ph('condition', 'Condition', `<select id="${id('condition')}" name="${n('condition')}"><option value="">Choose</option>${State.meta.conditions.map((c) => `<option ${c === it.condition ? 'selected' : ''}>${esc(c)}</option>`).join('')}</select>`)}
      ${ph('details', 'Details (optional)', `<input id="${id('details')}" name="${n('details')}" maxlength="200" value="${esc(it.details)}" placeholder="Brand, model or damage">`)}
    </div>
    ${count > 1 ? `<button type="button" class="btn secondary small" data-act="remove-item" data-i="${i}">${icon('trash')}Remove item</button>` : ''}
  </fieldset>`;
}

function slotsHtml(slots, selected) {
  if (!slots) return '<p class="muted small">Choose a date to see available time slots.</p>';
  return slots.map((s) => `<label class="check"><input type="radio" name="timeSlot" value="${esc(s.slot)}" ${s.available ? '' : 'disabled'} ${s.slot === selected ? 'checked' : ''}>
    ${esc(s.slot.replace('-', ' to '))} ${s.available ? `<span class="muted small">&nbsp;(${s.remaining} available)</span>` : '<span class="badge warn">Full</span>'}</label>`).join('');
}

function bookingFormHtml(b) {
  return `
  <div class="page-head"><div><h1>Book a pickup</h1><p class="muted">Add the items, say where and when, then review before confirming.</p></div></div>
  <form id="bookingForm" novalidate>
    <div class="card"><h2>Items</h2>
      <div id="itemRows">${b.items.map((it, i) => itemRow(it, i, b.items.length)).join('')}</div>
      <div class="error" data-error="items" role="alert"></div>
      ${b.items.length < 10 ? `<button type="button" class="btn secondary small" data-act="add-item">${icon('plus')}Add another item</button>` : ''}
      <div class="notice" style="margin-top:14px"><strong>Not sure which category?</strong> Describe the item for a keyword-based suggestion (not an AI model).
        <div class="row" style="margin-top:8px"><input id="assistText" style="flex:1;min-width:200px" placeholder="For example, old notebook with a swollen battery" maxlength="300" aria-label="Describe the item">
        <button type="button" class="btn secondary small" data-act="suggest">Suggest</button></div><div id="assistOut" class="small" style="margin-top:8px" aria-live="polite"></div></div>
      <div class="notice warn">Back up your files and wipe personal data from devices before handover. Tell the collector about any damaged or swollen battery.</div>
    </div>

    <div class="card section"><h2>Address and contact</h2>
      <div class="form-grid">
        ${field('address', 'Pickup address', input('address', 'text', `maxlength="200" value="${esc(b.address)}" autocomplete="street-address"`))}
        ${field('locality', 'Locality', input('locality', 'text', `maxlength="60" value="${esc(b.locality)}" placeholder="For example, Andheri"`))}
        ${field('city', 'City', input('city', 'text', `maxlength="60" value="${esc(b.city)}"`))}
        ${field('contactPhone', 'Contact number', input('contactPhone', 'tel', `inputmode="numeric" maxlength="10" value="${esc(b.contactPhone)}"`), 'The collector uses this number for the pickup.')}
      </div>
    </div>

    <div class="card section"><h2>Date and time</h2>
      <div class="form-grid">
        ${field('pickupDate', 'Preferred date', input('pickupDate', 'date', `min="${new Date().toISOString().slice(0, 10)}" value="${esc(b.pickupDate)}"`))}
        ${field('estimatedWeightKg', 'Approximate total weight in kg (optional)', input('estimatedWeightKg', 'number', `step="0.1" min="0.1" value="${esc(b.estimatedWeightKg)}"`))}
      </div>
      <div class="label">Time slot</div>
      <div id="slotBox">${slotsHtml(null, b.timeSlot)}</div>
      <div class="error" data-error="timeSlot" role="alert"></div>
      ${field('instructions', 'Instructions for the collector (optional)', `<textarea id="f-instructions" name="instructions" maxlength="500">${esc(b.instructions)}</textarea>`)}
    </div>
    <p class="section"><button type="button" class="btn" data-act="review-booking">Review booking</button></p>
  </form>`;
}

function readBookingForm() {
  const f = $('#bookingForm');
  const b = State.booking;
  b.items = $$('[data-item]', f).map((fs, i) => ({
    category: $(`[name="items[${i}].category"]`, f).value, quantity: parseInt($(`[name="items[${i}].quantity"]`, f).value, 10) || 0,
    condition: $(`[name="items[${i}].condition"]`, f).value, details: $(`[name="items[${i}].details"]`, f).value.trim() }));
  ['address', 'locality', 'city', 'contactPhone', 'pickupDate', 'instructions', 'estimatedWeightKg'].forEach((k) => { b[k] = $(`[name="${k}"]`, f).value.trim(); });
  const slot = $('[name="timeSlot"]:checked', f);
  b.timeSlot = slot ? slot.value : '';
}

async function loadSlots() {
  const date = $('#f-pickupDate') && $('#f-pickupDate').value;
  if (!date) return;
  try {
    const slots = await API.slots(date);
    const current = ($('[name="timeSlot"]:checked') || {}).value || State.booking.timeSlot;
    $('#slotBox').innerHTML = slotsHtml(slots, current);
  } catch (e) { $('#slotBox').innerHTML = `<p class="error">${esc(e.message)}</p>`; }
}

function validateBooking(b) {
  const e = {};
  b.items.forEach((it, i) => {
    if (!it.category) e[`items[${i}].category`] = 'Choose a category';
    if (!it.condition) e[`items[${i}].condition`] = 'Choose a condition';
    if (!(it.quantity >= 1)) e[`items[${i}].quantity`] = 'Quantity must be at least 1';
  });
  if (!b.address) e.address = 'Enter the pickup address';
  if (!b.locality) e.locality = 'Enter the locality';
  if (!b.city) e.city = 'Enter the city';
  if (!/^[6-9][0-9]{9}$/.test(b.contactPhone)) e.contactPhone = 'Enter a 10-digit Indian mobile number';
  if (!b.pickupDate) e.pickupDate = 'Choose a pickup date';
  else if (b.pickupDate < new Date().toISOString().slice(0, 10)) e.pickupDate = 'Choose today or a later date';
  if (!b.timeSlot) e.timeSlot = 'Choose a time slot';
  if (b.estimatedWeightKg && !(parseFloat(b.estimatedWeightKg) > 0)) e.estimatedWeightKg = 'Weight must be greater than zero';
  return e;
}

function bookingPayload(b) {
  return { items: b.items.map((i) => ({ category: i.category, quantity: i.quantity, condition: i.condition, details: i.details || null })),
    address: b.address, city: b.city, locality: b.locality, contactPhone: b.contactPhone, pickupDate: b.pickupDate,
    timeSlot: b.timeSlot, instructions: b.instructions || null, estimatedWeightKg: b.estimatedWeightKg ? parseFloat(b.estimatedWeightKg) : null };
}

function bookingReviewHtml(b) {
  return `<div class="page-head"><div><h1>Review your booking</h1><p class="muted">Check the details, then confirm. You can edit anything first.</p></div></div>
  <div class="card"><dl class="facts">
    <dt>Items</dt><dd>${b.items.map((i) => `${i.quantity} x ${esc(i.category)} (${esc(i.condition)})${i.details ? ', ' + esc(i.details) : ''}`).join('<br>')}</dd>
    <dt>Address</dt><dd>${esc(b.address)}, ${esc(b.locality)}, ${esc(b.city)}</dd>
    <dt>Contact</dt><dd>${esc(b.contactPhone)}</dd>
    <dt>Pickup</dt><dd>${fmtDate(b.pickupDate)}, ${esc(b.timeSlot.replace('-', ' to '))}</dd>
    ${b.estimatedWeightKg ? `<dt>Approximate weight</dt><dd>${esc(b.estimatedWeightKg)} kg</dd>` : ''}
    ${b.instructions ? `<dt>Instructions</dt><dd>${esc(b.instructions)}</dd>` : ''}
  </dl>
  <div class="error" id="reviewError" role="alert"></div>
  <div class="row" style="margin-top:16px"><button class="btn" data-act="confirm-booking">Confirm booking</button><button class="btn secondary" data-act="edit-booking">Edit</button></div></div>`;
}

Views.book = async () => {
  if (!State.booking) State.booking = newBooking();
  return { html: bookingFormHtml(State.booking), mount: () => { if (State.booking.pickupDate) loadSlots(); } };
};

/* ---------- lists ---------- */
const LIST_TITLES = { CUSTOMER: 'My pickups', COLLECTOR: 'Assigned pickups', RECYCLER: 'Consignments', ADMIN: 'Pickups' };

Views.pickups = async () => {
  const st = State.lists.pickups;
  const page = await API.pickups({ status: st.status, q: st.q, page: st.page, size: 10 });
  const role = State.user.role;
  const html = `
    <div class="page-head"><div><h1>${LIST_TITLES[role]}</h1></div>${role === 'CUSTOMER' ? '<a class="btn" href="#/book">Book a pickup</a>' : ''}</div>
    ${chipsHtml(STATUS_CHIPS, st.status, 'pickups')}
    <form class="row" data-form="list-search" data-key="pickups" style="margin-bottom:12px" novalidate>
      <input id="listQ" style="max-width:280px" value="${esc(st.q)}" placeholder="Search by reference" aria-label="Search by reference">
      <button class="btn secondary" type="submit">${icon('search')}Search</button></form>
    ${pickupTable(page.content, { collector: role !== 'COLLECTOR' && role !== 'RECYCLER', empty: role === 'CUSTOMER' ? 'No pickups yet. Book your first pickup to get started.' : 'No pickups match this filter.' })}
    ${pagerHtml(page, 'pickups')}`;
  return { html };
};

/* ---------- dashboards ---------- */
const countOf = async (status) => (await API.pickups({ status, size: 1 })).totalElements;
const stat = (n, label) => `<div class="card stat"><div class="num">${n}</div><div class="lbl">${esc(label)}</div></div>`;

Views.dashboard = async () => {
  const role = State.user.role;
  if (role === 'ADMIN') { location.hash = '#/admin'; return { html: skeleton() }; }
  let html = '';
  if (role === 'CUSTOMER') {
    const [active, completed, waiting, activeList, doneList] = await Promise.all([
      countOf('ACTIVE'), countOf('COMPLETED'), countOf('REQUESTED'), API.pickups({ status: 'ACTIVE', size: 5 }), API.pickups({ status: 'COMPLETED', size: 5 })]);
    html = `<div class="page-head"><div><h1>Dashboard</h1><p class="muted">Signed in as ${esc(State.user.name)}.</p></div><a class="btn" href="#/book">Book a pickup</a></div>
      <div class="grid cols-3">${stat(active, 'Active pickups')}${stat(completed, 'Completed recycling records')}${stat(waiting, 'Awaiting confirmation')}</div>
      <section class="section"><div class="row between"><h2>Active pickups</h2><a href="#/pickups">View all</a></div>
        ${pickupTable(activeList.content, { collector: true, empty: 'No active pickups. Book a pickup to get started.' })}</section>
      <section class="section"><h2>Completed records</h2>${pickupTable(doneList.content, { empty: 'Completed recycling records appear here once a case is closed.' })}</section>`;
  } else if (role === 'COLLECTOR') {
    const [toCollect, toHand, issues, list] = await Promise.all([countOf('CONFIRMED'), countOf('COLLECTED'), countOf('FAILED_PICKUP'), API.pickups({ status: 'ACTIVE', size: 10 })]);
    html = `<div class="page-head"><div><h1>Dashboard</h1><p class="muted">Pickups assigned to you.</p></div></div>
      <div class="grid cols-3">${stat(toCollect, 'Waiting to be collected')}${stat(toHand, 'Collected, to hand to a facility')}${stat(issues, 'Failed pickups')}</div>
      <section class="section"><div class="row between"><h2>Active assignments</h2><a href="#/pickups">View all</a></div>${pickupTable(list.content, { empty: 'No active assignments right now.' })}</section>`;
  } else {
    const [incoming, received, processing, confirmed, list] = await Promise.all([
      countOf('TRANSFERRED'), countOf('RECEIVED_BY_PARTNER'), countOf('PROCESSING'), countOf('RECYCLING_CONFIRMED'), API.pickups({ status: 'ACTIVE', size: 10 })]);
    html = `<div class="page-head"><div><h1>Dashboard</h1><p class="muted">${esc(State.user.partnerName || 'No recycling partner is linked to this account.')}</p></div></div>
      <div class="grid cols-4">${stat(incoming, 'Incoming, awaiting receipt')}${stat(received, 'Received')}${stat(processing, 'In processing')}${stat(confirmed, 'Outcome confirmed, awaiting closure')}</div>
      <section class="section"><div class="row between"><h2>Active consignments</h2><a href="#/pickups">View all</a></div>${pickupTable(list.content, { empty: 'No active consignments.' })}</section>`;
  }
  return { html };
};

/* ---------- notifications ---------- */
Views.notifications = async () => {
  const st = State.lists.notifications;
  const page = await API.notifications({ page: st.page, size: 10 });
  const html = `
    <div class="page-head"><div><h1>Notifications</h1><p class="muted">Updates appear here inside EcoTrace. Email and SMS delivery are not available yet.</p></div>
      <button class="btn secondary" data-act="read-all">Mark all as read</button></div>
    ${page.content.length ? `<div class="stack">${page.content.map((n) => `
      <article class="card tight">
        <div class="row between"><strong>${esc(n.title)}</strong><span class="muted small">${fmtDateTime(n.createdAt)}</span></div>
        <p style="margin:4px 0 6px">${esc(n.message)}</p>
        <div class="row">${n.read ? '<span class="muted small">Read</span>' : '<span class="badge progress unread-dot">Unread</span>'}
          ${n.pickupReference ? `<a href="#/pickup/${esc(n.pickupReference)}" data-act="open-notification" data-id="${n.id}">View pickup</a>` : ''}
          ${!n.read ? `<button class="link-btn small" data-act="read-one" data-id="${n.id}">Mark as read</button>` : ''}</div>
      </article>`).join('')}</div>` : '<div class="card empty">No notifications yet. Updates about your pickups will appear here.</div>'}
    ${pagerHtml(page, 'notifications')}`;
  return { html };
};

/* ---------- account ---------- */
Views.account = async () => {
  const u = State.user;
  return { html: `
    <div class="page-head"><h1>Account</h1></div>
    <div class="grid cols-2" style="align-items:start">
      <form id="profileForm" class="card" novalidate>
        <h2>Profile</h2>
        <dl class="facts" style="margin-bottom:8px"><dt>Email</dt><dd>${esc(u.email)}</dd><dt>Role</dt><dd>${esc(u.roleLabel)}${u.partnerName ? ', ' + esc(u.partnerName) : ''}</dd></dl>
        ${field('name', 'Full name', input('name', 'text', `maxlength="80" value="${esc(u.name)}"`))}
        ${field('phone', 'Mobile number (optional)', input('phone', 'tel', `inputmode="numeric" maxlength="10" value="${esc(u.phone || '')}"`))}
        <p style="margin-top:14px"><button class="btn" type="submit">Save profile</button></p>
      </form>
      <form id="passwordForm" class="card" novalidate>
        <h2>Change password</h2>
        ${field('currentPassword', 'Current password', input('currentPassword', 'password', 'autocomplete="current-password"'))}
        ${field('newPassword', 'New password', input('newPassword', 'password', 'autocomplete="new-password"'), 'At least 8 characters, with a letter and a number.')}
        <p style="margin-top:14px"><button class="btn" type="submit">Change password</button></p>
      </form>
    </div>` };
};
