const JWT_KEY = 'quiz.jwt';
const USER_KEY = 'quiz.user';

export async function fetchWithAuth(path, options = {}) {
  const jwt = localStorage.getItem(JWT_KEY);
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };
  if (jwt) headers.Authorization = `Bearer ${jwt}`;
  const res = await fetch(path, { ...options, headers });
  if (res.status === 401) {
    localStorage.removeItem(JWT_KEY);
    localStorage.removeItem(USER_KEY);
    window.location.reload();
    throw new Error('Session expired');
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
