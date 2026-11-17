/* Thin client for the EcoTrace REST API. The session lives in an HttpOnly cookie, so no token is handled here. */
const API = (() => {
  const handlers = { unauthorized: () => {} };

  async function call(method, path, body, form) {
    // A FormData body sets its own multipart Content-Type, so only JSON bodies get that header here.
    const headers = { 'X-Requested-With': 'EcoTrace' };
    let payload;
    if (form) payload = form;
    else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
    let res;
    try {
      res = await fetch('/api' + path, { method, headers, credentials: 'same-origin', body: payload });
    } catch (e) {
      throw { status: 0, message: 'Cannot reach the server. Check your connection and try again.', fieldErrors: {} };
    }
    let data = null;
    try { data = await res.json(); } catch (e) { data = null; }
    if (!res.ok) {
      if (res.status === 401 && !path.startsWith('/auth/')) handlers.unauthorized();
      throw {
        status: res.status,
        message: (data && data.message) || 'The request failed. Please try again.',
        fieldErrors: (data && data.fieldErrors) || {}
      };
    }
    return data;
  }

  const qs = (o) => {
    const p = new URLSearchParams();
    Object.entries(o || {}).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.set(k, v); });
    const s = p.toString();
    return s ? '?' + s : '';
  };
  const enc = encodeURIComponent;

  return {
    handlers,
    config: () => call('GET', '/config'),
    meta: () => call('GET', '/meta'),
    me: () => call('GET', '/auth/me'),
    register: (b) => call('POST', '/auth/register', b),
    login: (b) => call('POST', '/auth/login', b),
    logout: () => call('POST', '/auth/logout'),
    updateProfile: (b) => call('PUT', '/account', b),
    changePassword: (b) => call('POST', '/account/password', b),
    track: (ref) => call('GET', '/track/' + enc(ref)),
    partners: (p) => call('GET', '/partners' + qs(p)),
    slots: (date) => call('GET', '/slots' + qs({ date })),
    suggest: (description) => call('POST', '/assistant/category', { description }),
    book: (b) => call('POST', '/pickups', b),
    pickups: (p) => call('GET', '/pickups' + qs(p)),
    pickup: (ref) => call('GET', '/pickups/' + enc(ref)),
    transition: (ref, b) => call('POST', '/pickups/' + enc(ref) + '/transition', b),
    handover: {
      generateCode: (ref) => call('POST', '/pickups/' + enc(ref) + '/handover/code'),
      verify: (ref, code) => call('POST', '/pickups/' + enc(ref) + '/handover/verify', { code }),
      uploadPhoto: (ref, file) => {
        const form = new FormData();
        form.append('photo', file);
        return call('POST', '/pickups/' + enc(ref) + '/handover/photo', undefined, form);
      },
      photoUrl: (ref) => '/api/pickups/' + enc(ref) + '/handover/photo'
    },
    notifications: (p) => call('GET', '/notifications' + qs(p)),
    unreadCount: () => call('GET', '/notifications/unread-count'),
    markRead: (id) => call('POST', '/notifications/' + id + '/read'),
    markAllRead: () => call('POST', '/notifications/read-all'),
    admin: {
      metrics: (p) => call('GET', '/admin/metrics' + qs(p)),
      users: (p) => call('GET', '/admin/users' + qs(p)),
      createUser: (b) => call('POST', '/admin/users', b),
      setUserActive: (id, active) => call('POST', '/admin/users/' + id + '/active', { active }),
      partners: () => call('GET', '/admin/partners'),
      createPartner: (b) => call('POST', '/admin/partners', b),
      updatePartner: (id, b) => call('PUT', '/admin/partners/' + id, b),
      setPartnerActive: (id, active) => call('POST', '/admin/partners/' + id + '/active', { active }),
      audit: (p) => call('GET', '/admin/audit' + qs(p)),
      handovers: (p) => call('GET', '/admin/handovers' + qs(p)),
      resetDemo: () => call('POST', '/admin/demo/reset')
    }
  };
})();
