/* A single pickup for signed-in users: full details, chain of custody, and the actions the server allows. */

const HANDOVER_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const HANDOVER_PHOTO_MAX = 3 * 1024 * 1024;
const COLLECTED_OR_LATER = ['COLLECTED', 'AT_FACILITY', 'TRANSFERRED', 'RECEIVED_BY_PARTNER', 'PROCESSING',
  'RECYCLING_CONFIRMED', 'COMPLETED', 'REJECTED'];

/* ----- verified handover ----- */

function handoverVerifyForm(ref) {
  return `<form id="handoverVerifyForm" class="card tight" data-ref="${esc(ref)}" novalidate>
    <h3>Enter the collection code</h3>
    <p class="muted small">Ask the customer for the six-digit code shown when they generated it. Five incorrect attempts stop that code.</p>
    <label for="handoverCode">Collection code</label>
    <input id="handoverCode" name="code" class="otp-input" inputmode="numeric" autocomplete="one-time-code" maxlength="12" aria-describedby="handoverCodeHint">
    <div id="handoverCodeHint" class="hint">Six digits.</div>
    <div class="error" data-error="code" role="alert"></div>
    <p style="margin:12px 0 0"><button class="btn" type="submit">Verify handover</button></p>
  </form>`;
}

function handoverPhotoForm(ref, hasPhoto) {
  return `<form id="handoverPhotoForm" class="card tight" data-ref="${esc(ref)}" novalidate style="margin-top:14px">
    <h3>Mandatory geotagged handover photo</h3>
    <p class="muted small">${hasPhoto ? 'Uploading again replaces the current photo.' : 'Required before collection can be completed. Allow location access; date, time and GPS coordinates are stamped onto the photo automatically. JPEG, PNG or WebP, up to 3 MB.'}</p>
    <label for="handoverPhoto">Photo</label>
    <input id="handoverPhoto" name="photo" type="file" accept="image/jpeg,image/png,image/webp" capture="environment" required>
    <p class="hint">Take the photo at the collection point. Precise location permission is required.</p>
    <div class="error" data-error="photo" role="alert"></div>
    <p style="margin:12px 0 0"><button class="btn secondary" type="submit">Upload photo</button></p>
  </form>`;
}

/* The code is shown once, straight after it is generated. The server never returns it again. */
function handoverRevealHtml(reveal) {
  return `<div class="notice" role="status">
    <strong>Collection code for your collector</strong>
    <div style="margin:10px 0"><span class="code-reveal" aria-label="Collection code, digits ${esc(reveal.code.split('').join(' '))}">${esc(reveal.code)}</span></div>
    Valid until ${fmtDateTime(reveal.expiresAt)}, with ${reveal.maxAttempts} attempts. It is shown only once, so write it down or share it now.
    Generating a new code stops this one working.
  </div>`;
}

function handoverDetailsHtml(h, viewerRole, ref) {
  const ids = viewerRole === 'ADMIN'
    ? `<dt>Collector ID</dt><dd>${esc(h.collectorId)}</dd><dt>Customer ID</dt><dd>${esc(h.customerId)}</dd>` : '';
  return `<dl class="facts">
      <dt>Handover reference</dt><dd><strong>${esc(h.handoverReference)}</strong></dd>
      <dt>Verified</dt><dd>${fmtDateTime(h.verifiedAt)}</dd>
      <dt>Collected</dt><dd>${h.collectedAt ? fmtDateTime(h.collectedAt) : 'Not yet recorded'}</dd>
      <dt>Collector</dt><dd>${esc(h.collectorName)}</dd>
      ${ids}
    </dl>
    ${h.hasPhoto ? `<img class="handover-photo" src="${API.handover.photoUrl(ref)}" alt="Photo taken at the handover of pickup ${esc(ref)}">` : ''}`;
}

/* One card for the handover, with the parts each role may use. Recycling partners see nothing here. */
function handoverPanel(d, reveal) {
  const role = State.user ? State.user.role : '';
  if (role === 'RECYCLER') return '';
  const h = d.handover;
  const parts = [];

  if (reveal) parts.push(handoverRevealHtml(reveal));

  if (role === 'CUSTOMER') {
    if (h) {
      parts.push(handoverDetailsHtml(h, role, d.reference));
    } else if (d.handoverCode) {
      parts.push(`<p>A collection code is active until ${fmtDateTime(d.handoverCode.expiresAt)}, with ${d.handoverCode.attemptsLeft} attempts left.
        The code is shown only when it is generated, so generate a new one if you have lost it.</p>`);
    }
    if (d.canGenerateHandoverCode) {
      parts.push(`<p class="muted">When the collector arrives, generate a code and tell it to them. It works once and only for a limited time.</p>
        <button class="btn" type="button" data-act="generate-handover-code" data-ref="${esc(d.reference)}">${d.handoverCode ? 'Generate a new code' : 'Generate collection code'}</button>`);
    } else if (!h && !d.handoverCode) {
      parts.push(`<p class="muted" style="margin:0">A collection code can be generated once a collector is assigned and the pickup is confirmed.</p>`);
    }
  }

  if (role === 'COLLECTOR') {
    if (d.canVerifyHandover) parts.push(handoverVerifyForm(d.reference));
    if (h) {
      parts.push(handoverDetailsHtml(h, role, d.reference));
      if (d.canAttachPhoto) parts.push(handoverPhotoForm(d.reference, h.hasPhoto));
    }
  }

  if (role === 'ADMIN') {
    if (h) parts.push(handoverDetailsHtml(h, role, d.reference));
    else if (d.status === 'CONFIRMED') parts.push(`<p class="muted" style="margin:0">Waiting for the customer's collection code.</p>`);
  }

  if (!h && COLLECTED_OR_LATER.includes(d.status)) {
    parts.push(`<div class="notice warn" role="note">No verified handover is recorded for this pickup. It was collected before handover verification was introduced.</div>`);
  }

  if (!parts.length) return '';
  return `<section class="card section" id="handover" aria-labelledby="handover-title">
    <h2 id="handover-title">Verified handover</h2>
    ${parts.join('\n')}
  </section>`;
}

async function generateHandoverCode(ref) {
  if (!confirm('Generate a new collection code? Any earlier code for this pickup stops working.')) return;
  try {
    const r = await API.handover.generateCode(ref);
    State.handoverReveal = { ref, code: r.code, expiresAt: r.expiresAt, maxAttempts: r.maxAttempts };
    toast('Collection code generated.');
    render(true);
  } catch (e) { toast(e.message, true); }
}

async function submitHandoverVerify(form) {
  clearErrors(form);
  const code = ($('input[name="code"]', form).value || '').trim();
  const btn = $('button[type=submit]', form); btn.disabled = true;
  try {
    await API.handover.verify(form.dataset.ref, code);
    toast('Handover verified. You can now mark the pickup as collected.');
    render(true);
  } catch (e) {
    showErrors(form, e.fieldErrors);
    toast(e.message, true);
    btn.disabled = false;
  }
}

async function loadHandoverImage(file) {
  // Prefer the browser's native decoder (also applies EXIF orientation on modern browsers).
  if (typeof createImageBitmap === 'function') {
    try { return await createImageBitmap(file); } catch (_) { /* Try the FileReader fallback below. */ }
  }

  // A data URL fallback is more reliable than object URLs in some browser/file-provider combinations.
  const dataUrl = await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('The selected file could not be read. Please choose the photo again.'));
    reader.readAsDataURL(file);
  });
  const image = new Image();
  image.src = dataUrl;
  try {
    if (typeof image.decode === 'function') await image.decode();
    else await new Promise((resolve, reject) => {
      image.onload = resolve;
      image.onerror = () => reject(new Error('This image format could not be decoded. Please choose a JPEG, PNG or WebP photo.'));
    });
  } catch (_) {
    throw new Error('This photo could not be opened by the browser. Please re-save it as JPEG or PNG and select it again.');
  }
  return image;
}

async function stampHandoverPhoto(file, latitude, longitude, capturedAt) {
  const image = await loadHandoverImage(file);
  const imageWidth = image.width || image.naturalWidth;
  const imageHeight = image.height || image.naturalHeight;
  if (!imageWidth || !imageHeight) {
    if (typeof image.close === 'function') image.close();
    throw new Error('The selected photo has invalid image dimensions. Please choose another photo.');
  }
  const canvas = document.createElement('canvas');
  const scale = Math.min(1, 2000 / Math.max(imageWidth, imageHeight));
  canvas.width = Math.round(imageWidth * scale); canvas.height = Math.round(imageHeight * scale);
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Your browser could not prepare the geotagged photo. Try another browser.');
  ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
  if (typeof image.close === 'function') image.close();
  const pad = Math.max(12, Math.round(canvas.width * 0.018));
  const font = Math.max(14, Math.round(canvas.width * 0.022));
  const lines = ['ECOTRACE • VERIFIED COLLECTION EVIDENCE',
    `Date & time: ${capturedAt.toLocaleString()}`,
    `GPS: ${latitude.toFixed(6)}, ${longitude.toFixed(6)}`,
    `Map: https://maps.google.com/?q=${latitude},${longitude}`];
  const lineHeight = Math.round(font * 1.55), boxHeight = pad * 2 + lineHeight * lines.length;
  ctx.fillStyle = 'rgba(0,0,0,0.76)'; ctx.fillRect(0, canvas.height - boxHeight, canvas.width, boxHeight);
  ctx.fillStyle = '#fff'; ctx.font = `600 ${font}px Arial`; ctx.textBaseline = 'top';
  lines.forEach((line, i) => ctx.fillText(line, pad, canvas.height - boxHeight + pad + i * lineHeight, canvas.width - pad * 2));
  const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.88));
  if (!blob) throw new Error('Could not create the geotagged photo. Try another image.');
  if (blob.size > HANDOVER_PHOTO_MAX) throw new Error('The stamped photo is larger than 3 MB. Choose a smaller image.');
  return new File([blob], 'ecotrace-geotagged-handover.jpg', { type: 'image/jpeg', lastModified: capturedAt.getTime() });
}

async function submitHandoverPhoto(form) {
  clearErrors(form);
  const file = $('input[type=file]', form).files[0];
  let problem = '';
  if (!file) problem = 'Choose a photo to upload';
  else if (file.type && !HANDOVER_PHOTO_TYPES.includes(file.type)) problem = 'Use a JPEG, PNG or WebP image';
  else if (file.size > HANDOVER_PHOTO_MAX) problem = 'Use an image under 3 MB';
  if (problem) { showErrors(form, { photo: problem }); return; }
  const btn = $('button[type=submit]', form); btn.disabled = true;
  try {
    if (!navigator.geolocation) throw new Error('This device does not support GPS location.');
    const position = await new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve,
      () => reject(new Error('Location permission is required. Enable GPS/location access and try again.')),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }));
    const capturedAt = new Date();
    const stamped = await stampHandoverPhoto(file, position.coords.latitude, position.coords.longitude, capturedAt);
    await API.handover.uploadPhoto(form.dataset.ref, stamped);
    toast('Geotagged photo saved.');
    render(true);
  } catch (e) {
    showErrors(form, e.fieldErrors);
    toast(e.message, true);
    btn.disabled = false;
  }
}

function actionField(status, name, d, lists) {
  const id = `act-${status}-${name}`;
  const wrap = (label, control, hint) => `<div><label for="${id}">${label}</label>${control}${hint ? `<div class="hint">${hint}</div>` : ''}<div class="error" data-error="${name}" role="alert"></div></div>`;
  const partnerOptions = (list, selected) => `<option value="">Choose</option>${list.map((p) => `<option value="${p.id}" ${p.id === selected ? 'selected' : ''}>${esc(p.name)}${p.verification !== 'VERIFIED' ? ' (sample)' : ''}</option>`).join('')}`;
  switch (name) {
    case 'collectorId':
      return wrap('Collector', `<select id="${id}" name="collectorId">${partnerOptions(lists.collectors.map((u) => ({ id: u.id, name: u.name, verification: 'VERIFIED' })), d.collectorId)}</select>`);
    case 'facilityId':
      return wrap('Collection facility', `<select id="${id}" name="facilityId">${partnerOptions(lists.facilities)}</select>`);
    case 'partnerId':
      return wrap('Recycling partner', `<select id="${id}" name="partnerId">${partnerOptions(lists.recyclers)}</select>`);
    case 'outcome':
      return wrap('Outcome', `<select id="${id}" name="outcome"><option value="">Choose</option>${Object.entries(State.meta.outcomes).map(([k, l]) => `<option value="${k}">${esc(l)}</option>`).join('')}</select>`);
    case 'weightKg':
      return wrap('Measured weight (kg)', `<input id="${id}" name="weightKg" type="number" step="0.1" min="0.1">`);
    case 'processingReference':
      return wrap('Processing reference or certificate number', `<input id="${id}" name="processingReference" maxlength="100">`, 'Required. Shown on the public tracking page.');
    case 'reason':
      return wrap('Reason', `<textarea id="${id}" name="note" maxlength="450"></textarea>`);
    default:
      return wrap('Note (optional)', `<textarea id="${id}" name="note" maxlength="450"></textarea>`);
  }
}

function actionForms(d, lists) {
  if (!d.allowedActions.length) {
    return `<div class="card"><h2>Actions</h2><p class="muted" style="margin:0">${d.status === 'COMPLETED' || d.status === 'CANCELLED' ? 'This case is closed.' : 'No actions are available to your account at this stage.'}</p></div>`;
  }
  return `<div class="stack"><h2 style="margin-bottom:0">Actions</h2>${d.allowedActions.map((a) => `
    <form class="card tight" data-action-form data-status="${esc(a.status)}" data-ref="${esc(d.reference)}" novalidate>
      <h3>${esc(a.label)}</h3>
      <div class="form-grid">${a.fields.map((f) => actionField(a.status, f, d, lists)).join('')}</div>
      <p style="margin:12px 0 0"><button class="btn ${['CANCELLED', 'REJECTED', 'FAILED_PICKUP'].includes(a.status) ? 'secondary' : ''}" type="submit">${esc(a.label)}</button></p>
    </form>`).join('')}</div>`;
}

Views.pickup = async (ref) => {
  let d;
  try { d = await API.pickup(ref); }
  catch (e) {
    return { html: `<div class="page-head"><h1>Pickup</h1></div><div class="notice error-box" role="alert">${esc(e.message)}</div><p><a href="#/pickups">Back to pickups</a></p>` };
  }
  const needs = new Set(d.allowedActions.flatMap((a) => a.fields));
  const [collectors, facilities, recyclers] = await Promise.all([
    needs.has('collectorId') ? API.admin.users({ role: 'COLLECTOR', size: 100 }).then((p) => p.content.filter((u) => u.active)) : [],
    needs.has('facilityId') ? API.partners({ type: 'COLLECTION_FACILITY' }) : [],
    needs.has('partnerId') ? API.partners({ type: 'RECYCLER' }) : []]);

  const justBooked = State.justBooked === d.reference;
  if (justBooked) State.justBooked = null;
  const reveal = State.handoverReveal && State.handoverReveal.ref === d.reference ? State.handoverReveal : null;
  State.handoverReveal = null;
  const c = d.contact;
  const html = `
    ${justBooked ? '<div class="notice" role="status"><strong>Booking received.</strong> Keep this reference. An administrator will confirm it and assign a collector. You will be notified here.</div>' : ''}
    <div class="page-head"><div>
        <h1>${esc(d.reference)}</h1>
        <div class="row">${badge(d.statusLabel, d.status)} ${sampleBadge(d.demo)}</div></div>
      <a class="btn secondary" href="#/pickups">Back to pickups</a></div>

    ${d.reason ? `<div class="notice ${d.status === 'COMPLETED' ? '' : 'error-box'}" role="status"><strong>Reason recorded:</strong> ${esc(d.reason)}${d.nextExpected ? `<br>Next: ${esc(d.nextExpected)}` : ''}</div>` : ''}

    <div class="grid cols-2" style="align-items:start">
      <div class="card"><h2>Details</h2>
        <dl class="facts">
          <dt>Pickup</dt><dd>${fmtDate(d.pickupDate)}, ${esc(d.timeSlot.replace('-', ' to '))}</dd>
          ${c ? `<dt>Address</dt><dd>${esc(c.address)}</dd><dt>Contact</dt><dd>${esc(c.name)}, ${esc(c.phone)}</dd>` : `<dt>City</dt><dd>${esc(d.city)}</dd>`}
          ${d.collectorName ? `<dt>Collector</dt><dd>${esc(d.collectorName)}</dd>` : ''}
          ${d.facilityName ? `<dt>Collection facility</dt><dd>${esc(d.facilityName)}</dd>` : ''}
          ${d.partnerName ? `<dt>Recycling partner</dt><dd>${esc(d.partnerName)}</dd>` : ''}
          ${d.estimatedWeightKg ? `<dt>Approximate weight</dt><dd>${kg(d.estimatedWeightKg)} (reported at booking)</dd>` : ''}
          ${d.outcomeLabel ? `<dt>Outcome</dt><dd>${esc(d.outcomeLabel)}, ${kg(d.verifiedWeightKg)} measured<br><span class="muted small">Reported by the recycling partner. Reference ${esc(d.processingReference)}. Not independently verified.</span></dd>` : ''}
          ${d.instructions ? `<dt>Instructions</dt><dd>${esc(d.instructions)}</dd>` : ''}
        </dl>
        <h3 style="margin-top:18px">Items</h3>
        <ul style="margin:0;padding-left:18px">${d.items.map((i) => `<li>${i.quantity} x ${esc(i.category)} (${esc(i.condition)})${i.details ? ', ' + esc(i.details) : ''}</li>`).join('')}</ul>
        <div style="margin-top:16px"><div class="qr" data-ref="${esc(d.reference)}"></div>
          <div class="hint">Public tracking: <a href="#/track/${esc(d.reference)}">${esc(trackUrl(d.reference))}</a><br>The QR code and link show status only, never personal details.</div></div>
      </div>
      <div class="card"><h2>Chain of custody</h2>${timelineHtml(d.timeline, d.status === 'COMPLETED' || d.status === 'CANCELLED' ? null : d.nextExpected)}</div>
    </div>
    ${handoverPanel(d, reveal)}
    <section class="section">${actionForms(d, { collectors, facilities, recyclers })}</section>`;
  return { html, mount: mountQr };
};

async function submitAction(form) {
  clearErrors(form);
  const status = form.dataset.status, ref = form.dataset.ref;
  if (status === 'CANCELLED' && !confirm('Cancel this pickup?')) return;
  const val = (n) => { const el = $(`[name="${n}"]`, form); return el ? el.value.trim() : ''; };
  const num = (n) => (val(n) === '' ? null : Number(val(n)));
  const body = { status, note: val('note') || null, collectorId: num('collectorId'), facilityId: num('facilityId'),
    partnerId: num('partnerId'), outcome: val('outcome') || null, weightKg: num('weightKg'), processingReference: val('processingReference') || null };
  const btn = $('button[type=submit]', form); btn.disabled = true;
  try {
    await API.transition(ref, body);
    toast('Status updated.');
    await refreshUnread();
    render(true);
  } catch (e) {
    showErrors(form, e.fieldErrors);
    toast(e.message, true);
    btn.disabled = false;
  }
}
