const JWT_KEY = 'quiz.jwt';
const USER_KEY = 'quiz.user';
const REFRESH_KEY = 'quiz.refreshToken';

let refreshPromise = null;

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  if (!refreshToken) throw new Error('No refresh token');

  const res = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });

  if (!res.ok) {
    localStorage.removeItem(JWT_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(REFRESH_KEY);
    window.location.reload();
    throw new Error('Refresh failed');
  }

  const data = await res.json();
  localStorage.setItem(JWT_KEY, data.accessToken);
  localStorage.setItem(REFRESH_KEY, data.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(data.user));
  return data.accessToken;
}

export async function fetchWithAuth(path, options = {}) {
  const jwt = localStorage.getItem(JWT_KEY);
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };
  if (jwt) headers.Authorization = `Bearer ${jwt}`;
  const res = await fetch(path, { ...options, headers });

  if (res.status === 401) {
    if (!refreshPromise) {
      refreshPromise = refreshAccessToken().finally(() => {
        refreshPromise = null;
      });
    }
    try {
      const newJwt = await refreshPromise;
      headers.Authorization = `Bearer ${newJwt}`;
      return fetch(path, { ...options, headers });
    } catch {
      throw new Error('Session expired');
    }
  }
  return res;
}

export async function login(email, password) {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: 'Login failed' }));
    throw new Error(err.message || 'Login failed');
  }
  return res.json();
}

export async function signup(email, password, nickname) {
  const res = await fetch('/api/auth/signup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, nickname }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: 'Signup failed' }));
    throw new Error(err.message || 'Signup failed');
  }
  return res.json();
}
