const TOKEN_KEY = 'neonroyale.token';

export function getToken() { return localStorage.getItem(TOKEN_KEY); }
export function setToken(t) {
  if (t) localStorage.setItem(TOKEN_KEY, t);
  else localStorage.removeItem(TOKEN_KEY);
}

async function call(method, url, body) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = 'Bearer ' + token;
  const res = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let data = null;
  try { data = await res.json(); } catch { /* empty */ }
  if (!res.ok) {
    const err = new Error((data && data.error) || `http_${res.status}`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

export const api = {
  createProfile: (name) => call('POST', '/api/profile', { name }),
  me: () => call('GET', '/api/me'),
  shop: () => call('GET', '/api/shop'),
  claimDaily: () => call('POST', '/api/daily'),
  buy: (id) => call('POST', '/api/buy', { id }),
  equip: (slot, id) => call('POST', '/api/equip', { slot, id }),
  matchResult: (payload) => call('POST', '/api/match-result', payload),
};
