/* Router, navigation, access guards, event handling and start-up. */
const appEl = document.getElementById('app');
const ANY = ['CUSTOMER', 'COLLECTOR', 'RECYCLER', 'ADMIN'];
const ROUTES = {
  '': { view: 'home', title: 'EcoTrace' },
  login: { view: 'login', guest: true, title: 'Log in' },
  register: { view: 'register', guest: true, title: 'Create account' },
  track: { view: 'track', title: 'Track e-waste' },
  partners: { view: 'partners', title: 'Recycling partners' },
  about: { view: 'about', title: 'How records work' },
  policies: { view: 'policies', title: 'Privacy and terms' },
  book: { view: 'book', roles: ['CUSTOMER'], title: 'Book a pickup' },
  dashboard: { view: 'dashboard', roles: ANY, title: 'Dashboard' },
  pickups: { view: 'pickups', roles: ANY, title: 'Pickups' },
  pickup: { view: 'pickup', roles: ANY, title: 'Pickup' },
  notifications: { view: 'notifications', roles: ANY, title: 'Notifications' },
  account: { view: 'account', roles: ANY, title: 'Account' },
  admin: { view: 'admin', roles: ['ADMIN'], title: 'Overview' },
  'admin/pickups': { view: 'admin/pickups', roles: ['ADMIN'], title: 'Pickups' },
  'admin/handovers': { view: 'admin/handovers', roles: ['ADMIN'], title: 'Handovers' },
  'admin/users': { view: 'admin/users', roles: ['ADMIN'], title: 'Users' },
  'admin/partners': { view: 'admin/partners', roles: ['ADMIN'], title: 'Partners' },
  'admin/audit': { view: 'admin/audit', roles: ['ADMIN'], title: 'Audit log' }
};
let renderToken = 0;

function parseRoute() {
  const path = decodeURIComponent(location.hash.replace(/^#\/?/, '').split('?')[0]);
  if (ROUTES[path] !== undefined) return { key: path, arg: undefined };
  const [first, ...rest] = path.split('/');
  return { key: first, arg: rest.join('/') || undefined };
}

/* ----- navigation ----- */
function navLinks() {
  const u = State.user;
  if (!u) return [['book', 'Book a pickup'], ['track', 'Track e-waste'], ['partners', 'Recycling partners'], ['about', 'How it works']];
  switch (u.role) {
    case 'CUSTOMER': return [['dashboard', 'Dashboard'], ['book', 'Book a pickup'], ['pickups', 'My pickups'], ['track', 'Track'], ['partners', 'Partners']];
    case 'COLLECTOR': return [['dashboard', 'Dashboard'], ['pickups', 'Assigned pickups'], ['track', 'Track'], ['partners', 'Partners']];
    case 'RECYCLER': return [['dashboard', 'Dashboard'], ['pickups', 'Consignments'], ['track', 'Track'], ['partners', 'Partners']];
    default: return [['admin', 'Overview'], ['admin/pickups', 'Pickups'], ['admin/handovers', 'Handovers'], ['admin/users', 'Users'], ['admin/partners', 'Partners'], ['admin/audit', 'Audit log'], ['track', 'Track']];
  }
}

function buildNav(activeKey) {
  $('#nav').innerHTML = navLinks().map(([k, label]) =>
    `<a href="#/${k}" ${k === activeKey || (k === 'pickups' && activeKey === 'pickup') ? 'aria-current="page"' : ''}>${esc(label)}</a>`).join('');
  const u = State.user;
  $('#barEnd').innerHTML = u ? `
    <a class="icon-btn" href="#/notifications" aria-label="Notifications${State.unread ? ', ' + State.unread + ' unread' : ''}">${icon('bell')}${State.unread ? `<span class="count" aria-hidden="true">${State.unread > 99 ? '99+' : State.unread}</span>` : ''}</a>
    <details class="menu"><summary>${icon('user')}<span>${esc(u.name.split(' ')[0])}</span></summary>
      <div class="menu-list"><div class="who"><strong>${esc(u.name)}</strong><br><span class="muted small">${esc(u.roleLabel)}</span></div>
        <a href="#/account">${icon('user')}Account</a><button type="button" data-act="logout">${icon('logout')}Log out</button></div></details>`
    : '<a class="btn secondary small" href="#/login">Log in</a><a class="btn small" href="#/register">Create account</a>';
  $('#nav').classList.remove('open');
  $('#navToggle').setAttribute('aria-expanded', 'false');
}

async function refreshUnread() {
  if (!State.user) { State.unread = 0; return; }
  try { State.unread = (await API.unreadCount()).count; } catch (e) { /* keep the old value */ }
}

/* ----- rendering ----- */
async function render(keepScroll) {
  const mine = ++renderToken;
  const { key, arg } = parseRoute();
  const route = ROUTES[key];
  const activeKey = key === 'pickup' ? 'pickups' : key;
  if (!route) { buildNav(''); appEl.innerHTML = '<div class="page-head"><h1>Page not found</h1></div><p><a href="#/">Go to the home page</a></p>'; return; }
  if (route.roles && !State.user) { State.next = location.hash; location.hash = '#/login'; return; }
  if (route.guest && State.user) { location.hash = '#/dashboard'; return; }
  buildNav(activeKey);
  document.title = route.title === 'EcoTrace' ? 'EcoTrace' : route.title + ' | EcoTrace';
  if (route.roles && !route.roles.includes(State.user.role)) {
    appEl.innerHTML = '<div class="page-head"><h1>Not available</h1></div><div class="notice warn">This page is not available for your account type.</div><p><a href="#/dashboard">Go to your dashboard</a></p>';
    return;
  }
  destroyCharts();
  if (!keepScroll) appEl.innerHTML = skeleton();
  try {
    const out = await Views[route.view](arg);
    if (mine !== renderToken) return;
    appEl.innerHTML = out.html;
    if (out.mount) out.mount();
  } catch (e) {
    if (mine !== renderToken) return;
    appEl.innerHTML = `<div class="page-head"><h1>Something went wrong</h1></div>${errorBox(e)}<p><button class="btn secondary" data-act="retry">Try again</button></p>`;
  }
  if (!keepScroll) { window.scrollTo(0, 0); appEl.focus({ preventScroll: true }); }
}

function afterSignIn() {
  const target = State.next || '#/dashboard';
  State.next = null;
  refreshUnread().then(() => { if (location.hash === target) render(); else location.hash = target; });
}

async function logout() {
  try { await API.logout(); } catch (e) { /* the cookie is cleared by the server when possible */ }
  State.user = null; State.booking = null; State.unread = 0;
  toast('You have been logged out.');
  if (location.hash === '#/') render(); else location.hash = '#/';
}

API.handlers.unauthorized = () => {
  if (!State.user) return;
  State.user = null; State.unread = 0;
  State.next = location.hash;
  toast('Your session has expired. Log in again to continue.', true);
  location.hash = '#/login';
};

/* ----- booking interactions ----- */
function showBookingForm() {
  appEl.innerHTML = bookingFormHtml(State.booking);
  if (State.booking.pickupDate) loadSlots();
}

async function confirmBooking() {
  const btn = $('[data-act="confirm-booking"]'); btn.disabled = true;
  try {
    const d = await API.book(bookingPayload(State.booking));
    State.justBooked = d.reference; State.booking = null;
    toast('Booking received: ' + d.reference);
    await refreshUnread();
    location.hash = '#/pickup/' + d.reference;
  } catch (e) {
    if (e.fieldErrors && Object.keys(e.fieldErrors).length) {
      showBookingForm();
      showErrors($('#bookingForm'), e.fieldErrors);
      toast(e.message, true);
    } else { $('#reviewError').textContent = e.message; btn.disabled = false; }
  }
}

function reviewBooking() {
  readBookingForm();
  const form = $('#bookingForm');
  clearErrors(form);
  const errors = validateBooking(State.booking);
  if (Object.keys(errors).length) { showErrors(form, errors); toast('Please correct the highlighted fields.', true); return; }
  appEl.innerHTML = bookingReviewHtml(State.booking);
  window.scrollTo(0, 0);
}

async function suggestCategory() {
  const text = ($('#assistText').value || '').trim();
  if (!text) { toast('Describe the item first.', true); return; }
  try {
    const r = await API.suggest(text);
    const target = $$('[name$=".category"]').find((s) => !s.value);
    if (r.suggestedCategory && target) target.value = r.suggestedCategory;
    $('#assistOut').innerHTML = `${r.suggestedCategory ? `Suggested category: <strong>${esc(r.suggestedCategory)}</strong>${target ? ' (applied to the first item without a category).' : '.'}<br>` : ''}${esc(r.guidance)}<br><span class="muted">${esc(r.notice)}</span>`;
  } catch (e) { toast(e.message, true); }
}

/* ----- events ----- */
document.addEventListener('click', async (ev) => {
  const el = ev.target.closest('[data-act]');
  if (!el) return;
  const d = el.dataset;
  switch (d.act) {
    case 'logout': logout(); break;
    case 'retry': render(); break;
    case 'generate-handover-code': generateHandoverCode(d.ref); break;
    case 'close-modal': closeModal(); break;
    case 'chip': State.lists[d.key].status = d.value; State.lists[d.key].page = 0; render(true); break;
    case 'page': State.lists[d.key].page = Number(d.page); render(true); window.scrollTo(0, 0); break;
    case 'fill-demo': $('#f-email').value = d.email; $('#f-password').value = State.config.demoPassword; $('#f-password').focus(); break;
    case 'add-item': readBookingForm(); State.booking.items.push(emptyItem()); showBookingForm(); break;
    case 'remove-item': readBookingForm(); State.booking.items.splice(Number(d.i), 1); showBookingForm(); break;
    case 'suggest': suggestCategory(); break;
    case 'review-booking': reviewBooking(); break;
    case 'edit-booking': showBookingForm(); break;
    case 'confirm-booking': confirmBooking(); break;
    case 'read-all': try { await API.markAllRead(); await refreshUnread(); render(true); } catch (e) { toast(e.message, true); } break;
    case 'read-one': try { await API.markRead(d.id); await refreshUnread(); render(true); } catch (e) { toast(e.message, true); } break;
    case 'open-notification': API.markRead(d.id).then(refreshUnread).catch(() => {}); break;
    case 'add-user': openUserForm(); break;
    case 'toggle-user': try { await API.admin.setUserActive(d.id, d.active === 'true'); render(true); } catch (e) { toast(e.message, true); } break;
    case 'add-partner': openPartnerForm(null); break;
    case 'edit-partner': openPartnerForm(State.adminPartners.find((p) => String(p.id) === d.id)); break;
    case 'toggle-partner': try { await API.admin.setPartnerActive(d.id, d.active === 'true'); render(true); } catch (e) { toast(e.message, true); } break;
    case 'reset-demo':
      if (!confirm('Reset the demonstration data? Sample pickups return to their starting state. Real accounts and bookings are not changed.')) break;
      try { await API.admin.resetDemo(); toast('Demonstration data was reset.'); render(true); } catch (e) { toast(e.message, true); }
      break;
  }
});

document.addEventListener('submit', (ev) => {
  ev.preventDefault();
  const f = ev.target;
  const id = f.getAttribute('id');
  if (id === 'loginForm') submitLogin(f);
  else if (id === 'registerForm') submitRegister(f);
  else if (id === 'userForm') submitUserForm(f);
  else if (id === 'partnerForm') submitPartnerForm(f);
  else if (id === 'lookupForm') {
    const ref = ($('#lookupRef').value || '').trim().toUpperCase();
    if (ref) location.hash = '#/track/' + encodeURIComponent(ref); else toast('Enter a tracking reference.', true);
  } else if (id === 'profileForm') {
    clearErrors(f);
    const phone = $('#f-phone', f).value.trim();
    API.updateProfile({ name: $('#f-name', f).value.trim(), phone: phone || null })
      .then((u) => { State.user = u; toast('Profile saved.'); render(true); })
      .catch((e) => { showErrors(f, e.fieldErrors); toast(e.message, true); });
  } else if (id === 'passwordForm') {
    clearErrors(f);
    API.changePassword({ currentPassword: $('#f-currentPassword', f).value, newPassword: $('#f-newPassword', f).value })
      .then(() => { f.reset(); toast('Password changed.'); })
      .catch((e) => { showErrors(f, e.fieldErrors); toast(e.message, true); });
  } else if (id === 'rangeForm') {
    State.range = { from: $('#rFrom').value, to: $('#rTo').value, includeDemo: $('#rDemo') ? $('#rDemo').checked : false };
    render(true);
  } else if (id === 'handoverVerifyForm') submitHandoverVerify(f);
  else if (id === 'handoverPhotoForm') submitHandoverPhoto(f);
  else if (f.hasAttribute('data-action-form')) submitAction(f);
  else if (f.dataset.form === 'list-search') { State.lists[f.dataset.key].q = $('#listQ').value.trim(); State.lists[f.dataset.key].page = 0; render(true); }
  else if (f.dataset.form === 'admin-pickup-filter') { State.lists.adminPickups = { status: $('#apStatus').value, q: $('#apQ').value.trim(), page: 0 }; render(true); }
  else if (f.dataset.form === 'user-filter') { State.lists.users = { role: $('#uRole').value, q: $('#uQ').value.trim(), page: 0 }; render(true); }
});

let partnerTimer;
document.addEventListener('input', (ev) => {
  if (ev.target.id === 'pq') { clearTimeout(partnerTimer); partnerTimer = setTimeout(refreshPartners, 250); }
});
document.addEventListener('change', (ev) => {
  const id = ev.target.id;
  if (id === 'pc' || id === 'pt' || id === 'pv') refreshPartners();
  if (id === 'f-pickupDate') { State.booking.pickupDate = ev.target.value; loadSlots(); }
  if (id === 'auditBlocked') { State.lists.audit.failedOnly = ev.target.checked; State.lists.audit.page = 0; render(true); }
});
document.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && !$('#modal').hidden) closeModal(); });
$('#navToggle').addEventListener('click', () => {
  const open = $('#nav').classList.toggle('open');
  $('#navToggle').setAttribute('aria-expanded', String(open));
});
window.addEventListener('hashchange', () => render());

/* ----- start-up ----- */
(async function init() {
  try { State.config = await API.config(); } catch (e) { /* offline: keep defaults */ }
  try { State.meta = await API.meta(); } catch (e) { appEl.innerHTML = `<div class="page-head"><h1>EcoTrace is not reachable</h1></div>${errorBox(e)}<p><button class="btn secondary" data-act="retry">Try again</button></p>`; return; }
  try { State.user = await API.me(); } catch (e) { State.user = null; }
  $('#demoBanner').hidden = !State.config.demoMode;
  await refreshUnread();
  render();
})();
