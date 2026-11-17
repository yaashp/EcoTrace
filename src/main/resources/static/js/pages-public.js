/* Pages anyone can open: home, sign in, register, public tracking, partner directory, about, policies. */
const Views = {};

Views.home = async () => {
  let sample = null;
  if (State.config.demoMode) { try { sample = await API.track('EW-DEMO0007'); } catch (e) { sample = null; } }
  const preview = sample ? `
    <div class="card">
      <div class="row between"><h3>Example tracking record</h3><span class="badge warn">Sample data</span></div>
      <p class="muted small">Public tracking pages show status and handovers only. They never show names, phone numbers or addresses.</p>
      <div class="row between"><strong>${esc(sample.reference)}</strong>${badge(sample.statusLabel, sample.status)}</div>
      ${timelineHtml(sample.timeline.map((e) => ({ ...e, actorName: null })), sample.nextExpected)}
    </div>` : `
    <div class="card"><h3>What gets recorded</h3>
      <p class="muted">Each handover is saved with a time, the role that made the change, and the details that step requires, such as the facility, the recycling partner or a processing reference.</p></div>`;

  const html = `
    <section class="hero">
      <div>
        <h1>Book e-waste pickups and follow each handover to recycling</h1>
        <p class="lead">EcoTrace lets households book a collection and see every step afterwards: pickup, facility, recycling partner and the outcome the partner reports.</p>
        <div class="row">
          <a class="btn" href="#/book">Book a pickup</a>
          <a class="btn secondary" href="#/track">Track a submission</a>
        </div>
        <p class="muted small" style="margin-top:14px">An item is marked as processed only after a recycling partner confirms it. Collection alone never counts as recycling.</p>
      </div>
      ${preview}
    </section>

    <section class="section">
      <h2>How it works</h2>
      <ol class="steps">
        <li><strong>Book</strong><span>Choose items, an address and a time slot.</span></li>
        <li><strong>Confirm</strong><span>An administrator confirms the booking and assigns a collector.</span></li>
        <li><strong>Collect</strong><span>The collector picks up the items and hands them to a collection facility.</span></li>
        <li><strong>Transfer</strong><span>The consignment moves to a recycling partner, which confirms receipt.</span></li>
        <li><strong>Outcome</strong><span>The partner records what happened to the items, with a processing reference.</span></li>
      </ol>
    </section>

    <section class="section">
      <div class="card planned">
        <div class="row between"><h2 style="margin:0">EcoRewards</h2><span class="badge outline">Planned feature, not yet available</span></div>
        <p style="margin-top:10px">EcoRewards is a planned addition that would reward verified recycling. The proposed rate is <strong>100 points = &#8377;5</strong>. Points would be earned only after collection and recycling have been verified, and could later be redeemed for money.</p>
        <p class="muted small" style="margin:0">This is not available today. No points can be earned or redeemed.</p>
      </div>
    </section>`;
  return { html, mount: mountQr };
};

/* ---------- sign in and register ---------- */
Views.login = async () => {
  const demo = State.config.demoMode ? `
    <div class="card" style="margin-top:16px">
      <h3>Demonstration accounts</h3>
      <p class="muted small">Demo mode only. These are sample accounts so each role can be tried. All use the password <code>${esc(State.config.demoPassword)}</code>.</p>
      <div class="row">${State.config.demoAccounts.map((a) =>
        `<button type="button" class="btn secondary small" data-act="fill-demo" data-email="${esc(a.email)}">${esc(a.roleLabel)}</button>`).join('')}</div>
    </div>` : '';
  return { html: `
    <div style="max-width:440px;margin:0 auto">
      <h1>Log in</h1>
      <form id="loginForm" class="card" novalidate>
        <div class="error" data-error="form" role="alert"></div>
        ${field('email', 'Email', input('email', 'email', 'autocomplete="username"'))}
        ${field('password', 'Password', input('password', 'password', 'autocomplete="current-password"'))}
        <p style="margin-top:14px"><button class="btn" type="submit">Log in</button></p>
        <p class="muted small" style="margin:0">No account? <a href="#/register">Create one</a>.</p>
      </form>
      ${demo}
    </div>` };
};

Views.register = async () => ({ html: `
  <div style="max-width:480px;margin:0 auto">
    <h1>Create an account</h1>
    <p class="muted">Accounts created here are customer accounts. Collector, recycler and administrator accounts are created by an administrator.</p>
    <form id="registerForm" class="card" novalidate>
      <div class="error" data-error="form" role="alert"></div>
      ${field('name', 'Full name', input('name', 'text', 'autocomplete="name" maxlength="80"'))}
      ${field('email', 'Email', input('email', 'email', 'autocomplete="username"'))}
      ${field('phone', 'Mobile number (optional)', input('phone', 'tel', 'inputmode="numeric" maxlength="10" autocomplete="tel"'), 'Ten digits. Used to pre-fill pickup bookings.')}
      ${field('password', 'Password', input('password', 'password', 'autocomplete="new-password"'), 'At least 8 characters, with a letter and a number.')}
      ${field('confirm', 'Confirm password', input('confirm', 'password', 'autocomplete="new-password"'))}
      <p style="margin-top:14px"><button class="btn" type="submit">Create account</button></p>
      <p class="muted small" style="margin:0">Already registered? <a href="#/login">Log in</a>.</p>
    </form>
  </div>` });

async function submitLogin(form) {
  clearErrors(form);
  const body = { email: $('#f-email', form).value.trim(), password: $('#f-password', form).value };
  const local = {};
  if (!body.email) local.email = 'Enter your email address';
  if (!body.password) local.password = 'Enter your password';
  if (Object.keys(local).length) { showErrors(form, local); return; }
  const btn = $('button[type=submit]', form); btn.disabled = true;
  try {
    State.user = await API.login(body);
    afterSignIn();
  } catch (e) {
    showErrors(form, Object.assign({ form: e.message }, e.fieldErrors));
  } finally { btn.disabled = false; }
}

async function submitRegister(form) {
  clearErrors(form);
  const v = { name: $('#f-name', form).value.trim(), email: $('#f-email', form).value.trim(),
    phone: $('#f-phone', form).value.trim(), password: $('#f-password', form).value, confirm: $('#f-confirm', form).value };
  const local = {};
  if (!v.name) local.name = 'Enter your name';
  if (!v.email) local.email = 'Enter your email address';
  if (v.phone && !/^[6-9][0-9]{9}$/.test(v.phone)) local.phone = 'Enter a 10-digit Indian mobile number';
  if (v.password.length < 8) local.password = 'Use at least 8 characters';
  if (v.password !== v.confirm) local.confirm = 'The passwords do not match';
  if (Object.keys(local).length) { showErrors(form, local); return; }
  const btn = $('button[type=submit]', form); btn.disabled = true;
  try {
    State.user = await API.register({ name: v.name, email: v.email, phone: v.phone || null, password: v.password });
    toast('Account created.');
    afterSignIn();
  } catch (e) {
    showErrors(form, Object.assign({ form: e.message }, e.fieldErrors));
  } finally { btn.disabled = false; }
}

/* ---------- public tracking ---------- */
const lookupForm = (value = '') => `
  <form id="lookupForm" class="card tight row" novalidate style="align-items:flex-end">
    <div style="flex:1;min-width:220px"><label for="lookupRef">Tracking reference</label>
      <input id="lookupRef" name="reference" value="${esc(value)}" placeholder="EW-XXXXXXXX" autocomplete="off"></div>
    <button class="btn" type="submit">${icon('search')}Track</button>
  </form>`;

Views.track = async (ref) => {
  if (!ref) {
    return { html: `<div class="page-head"><div><h1>Track e-waste</h1><p class="muted">Enter the reference from your booking confirmation or scan its QR code.</p></div></div>${lookupForm()}
      <p class="muted small" style="margin-top:14px">Tracking pages are public so a QR code can be scanned by anyone. They show status and handovers only, never names, phone numbers or addresses.</p>` };
  }
  let t;
  try { t = await API.track(ref); }
  catch (e) {
    return { html: `<div class="page-head"><h1>Track e-waste</h1></div>${lookupForm(ref)}<div class="notice error-box" role="alert">${esc(e.message)} Check the reference and try again.</div>` };
  }
  let canOpen = false;
  if (State.user) { try { await API.pickup(t.reference); canOpen = true; } catch (e) { canOpen = false; } }
  const items = t.items.map((i) => `${i.quantity} x ${esc(i.category)}`).join(', ');
  const outcome = t.outcomeLabel ? `
    <dt>Outcome</dt><dd>${esc(t.outcomeLabel)}${t.verifiedWeightKg ? ', ' + kg(t.verifiedWeightKg) + ' measured' : ''}<br>
      <span class="muted small">Reported by the recycling partner. Processing reference: ${esc(t.processingReference)}. Not independently verified.</span></dd>` : '';
  const html = `
    <div class="page-head"><div><h1>Track e-waste</h1></div></div>
    ${lookupForm(t.reference)}
    <div class="grid cols-2" style="margin-top:16px;align-items:start">
      <div class="card">
        <div class="row between"><h2 style="margin:0">${esc(t.reference)}</h2>${badge(t.statusLabel, t.status)}</div>
        <dl class="facts" style="margin-top:14px">
          <dt>Items</dt><dd>${items}</dd>
          <dt>City</dt><dd>${esc(t.city)}</dd>
          <dt>Booked</dt><dd>${fmtDateTime(t.createdAt)}</dd>
          ${t.facilityName ? `<dt>Collection facility</dt><dd>${esc(t.facilityName)}</dd>` : ''}
          ${t.partnerName ? `<dt>Recycling partner</dt><dd>${esc(t.partnerName)}</dd>` : ''}
          ${t.handoverReference ? `<dt>Verified handover</dt><dd>${esc(t.handoverReference)}, verified ${fmtDateTime(t.handoverVerifiedAt)}<br><span class="muted small">Confirmed with a one-time code at collection. No personal details are shown here.</span></dd>` : ''}
          ${outcome}
        </dl>
        ${t.demo ? '<p class="muted small" style="margin-top:12px">This is a demonstration record.</p>' : ''}
        ${canOpen ? `<p style="margin:14px 0 0"><a class="btn secondary small" href="#/pickup/${esc(t.reference)}">Open full record</a></p>` : ''}
        <div style="margin-top:14px"><div class="qr" data-ref="${esc(t.reference)}"></div><div class="hint">QR code for this page. It links to this record only and does not by itself prove that recycling took place.</div></div>
      </div>
      <div class="card"><h3>Chain of custody</h3>${timelineHtml(t.timeline, t.nextExpected)}</div>
    </div>`;
  return { html, mount: mountQr };
};

/* ---------- partner directory ---------- */
function partnerCards(list) {
  if (!list.length) return '<div class="card empty">No partners match these filters. Clear a filter or search a different locality.</div>';
  const svc = { DOORSTEP: 'Doorstep collection', DROP_OFF: 'Drop-off' };
  return `<div class="grid cols-2">${list.map((p) => `
    <article class="card">
      <div class="row between"><h3 style="margin:0">${esc(p.name)}</h3>
        <span class="badge ${p.verification === 'VERIFIED' ? 'success' : 'warn'}">${icon(p.verification === 'VERIFIED' ? 'check' : 'alert')}${esc(p.verificationLabel)}</span></div>
      <p class="muted small" style="margin:4px 0 10px">${esc(p.typeLabel)}</p>
      <dl class="facts">
        <dt>Address</dt><dd>${esc(p.address)}</dd>
        ${p.serviceArea ? `<dt>Service area</dt><dd>${esc(p.serviceArea)}</dd>` : ''}
        <dt>Accepts</dt><dd>${p.acceptedCategories.map(esc).join(', ')}</dd>
        ${p.services.length ? `<dt>Services</dt><dd>${p.services.map((s) => esc(svc[s] || s)).join(', ')}</dd>` : ''}
        <dt>Contact</dt><dd>${esc(p.contactEmail || 'Not listed')}${p.contactPhone ? '<br>' + esc(p.contactPhone) : ''}</dd>
      </dl>
      ${p.verificationNote ? `<p class="muted small" style="margin:10px 0 0">${esc(p.verificationNote)}</p>` : ''}
    </article>`).join('')}</div>`;
}

async function refreshPartners() {
  const params = { q: $('#pq').value, category: $('#pc').value, type: $('#pt').value, verification: $('#pv').value };
  try {
    const list = await API.partners(params);
    $('#partnerList').innerHTML = partnerCards(list);
    mountMap('partnerMap', list);
  } catch (e) { toast(e.message, true); }
}

Views.partners = async () => {
  const list = await API.partners({});
  const hasSample = list.some((p) => p.verification !== 'VERIFIED');
  const html = `
    <div class="page-head"><div><h1>Recycling partners</h1><p class="muted">Collection facilities and recycling partners, with the materials they accept.</p></div></div>
    ${hasSample ? '<div class="notice warn">Entries marked as sample are demonstration data. They are not real organisations, addresses or certifications. Only entries marked verified have been confirmed by an administrator.</div>' : ''}
    <div class="card tight"><div class="form-grid">
      ${labelled('pq', 'Search by name or locality', '<input id="pq" placeholder="For example, Thane">')}
      ${labelled('pc', 'Accepted material', `<select id="pc"><option value="">Any</option>${State.meta.categories.map((c) => `<option>${esc(c)}</option>`).join('')}</select>`)}
      ${labelled('pt', 'Type', '<select id="pt"><option value="">Any</option><option value="COLLECTION_FACILITY">Collection facility</option><option value="RECYCLER">Recycling partner</option></select>')}
      ${labelled('pv', 'Verification', '<select id="pv"><option value="">Any</option><option value="VERIFIED">Verified</option><option value="SAMPLE">Sample</option><option value="PENDING">Pending</option></select>')}
    </div></div>
    <div id="partnerMap" class="map" style="margin:16px 0" role="region" aria-label="Map of partners"></div>
    <div id="partnerList">${partnerCards(list)}</div>`;
  return { html, mount: () => mountMap('partnerMap', list) };
};

/* ---------- about and policies ---------- */
Views.about = async () => ({ html: `
  <div class="page-head"><h1>How records work</h1></div>
  <div class="grid cols-2">
    <div class="card"><h3>Chain of custody</h3>
      <p>Every status change is saved with the time, the role that made it and the details that step needs. Changes must follow the allowed order, and each role can only record the steps it is responsible for.</p>
      <p style="margin:0">Only a recycling partner can record receipt, processing and the final outcome. Administrators can confirm bookings and close cases but cannot record those steps.</p></div>
    <div class="card"><h3>What the record does and does not prove</h3>
      <p>Outcomes are reported by the recycling partner with a processing reference. EcoTrace does not independently audit them, and a QR code only links to a record. It is not proof of recycling on its own.</p></div>
    <div class="card"><h3>Live, sample and planned</h3>
      <p><strong>Live:</strong> accounts, bookings, tracking, notifications inside the app, role permissions, analytics from stored records.</p>
      <p><strong>Sample:</strong> demonstration accounts, partners and pickups, always labelled.</p>
      <p style="margin:0"><strong>Planned:</strong> email and SMS delivery, photo evidence, live location, EcoRewards.</p></div>
    <div class="card"><h3>Architecture</h3>
      <p style="margin:0">Browser pages (HTML, CSS, JavaScript) call a Spring Boot REST API. The API enforces roles and workflow rules, and stores data in a relational database through JPA.</p></div>
  </div>` });

Views.policies = async () => ({ html: `
  <div class="page-head"><h1>Privacy and terms</h1></div>
  <div class="stack" style="max-width:760px">
    <div class="card"><h2 id="privacy">Privacy and data use</h2>
      <p>EcoTrace stores your name, email address, optional phone number, and the pickup details you submit, such as address and items.</p>
      <p>Your address and phone number are visible only to you, the collector assigned to your pickup, and administrators. Recycling partners see items and weights but not contact details. Public tracking pages show status and handovers only.</p>
      <p>Passwords are stored as one-way hashes. Sessions use a secure cookie and expire automatically. This prototype does not send data to third parties, and email or SMS are not used.</p>
      <p class="muted small" style="margin:0">This text describes the prototype. A live service would need a full privacy policy reviewed against applicable Indian data protection law.</p></div>
    <div class="card"><h2 id="terms">Terms of use</h2>
      <p>Wipe personal data from devices before handover. Do not hand over damaged or swollen batteries without telling the collector.</p>
      <p>Status information comes from the people and organisations who record it. EcoTrace does not guarantee outcomes reported by recycling partners.</p>
      <p class="muted small" style="margin:0">Prototype terms. A live service would need formal terms.</p></div>
  </div>` });
