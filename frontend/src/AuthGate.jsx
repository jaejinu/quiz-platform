import { useState } from 'react';
import { login, signup } from './lib/api';

export default function AuthGate({ onAuth }) {
  const [tab, setTab] = useState('login');

  return (
    <div className="auth-container">
      <h1>Quiz Platform</h1>
      <div className="auth-tabs">
        <button
          className={tab === 'login' ? 'tab active' : 'tab'}
          onClick={() => setTab('login')}
        >
          로그인
        </button>
        <button
          className={tab === 'signup' ? 'tab active' : 'tab'}
          onClick={() => setTab('signup')}
        >
          회원가입
        </button>
      </div>
      {tab === 'login' ? (
        <LoginForm onSuccess={onAuth} />
      ) : (
        <SignupForm onSuccess={onAuth} />
      )}
      <p className="auth-hint">
        HOST 계정: <code>host@local.dev</code> / <code>hostpass123</code>
      </p>
    </div>
  );
}

function LoginForm({ onSuccess }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const tokenResponse = await login(email, password);
      onSuccess(tokenResponse);
    } catch (err) {
      setError(translateError(err.message, 'login'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={submit} className="auth-form">
      {error && <div className="error-banner">{error}</div>}
      <div className="form-field">
        <label htmlFor="login-email">이메일</label>
        <input
          id="login-email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
      </div>
      <div className="form-field">
        <label htmlFor="login-password">비밀번호</label>
        <input
          id="login-password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
      </div>
      <button type="submit" disabled={loading} className="primary">
        {loading ? '로그인 중...' : '로그인'}
      </button>
    </form>
  );
}

function SignupForm({ onSuccess }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const tokenResponse = await signup(email, password, nickname);
      onSuccess(tokenResponse);
    } catch (err) {
      setError(translateError(err.message, 'signup'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={submit} className="auth-form">
      {error && <div className="error-banner">{error}</div>}
      <div className="form-field">
        <label htmlFor="signup-email">이메일</label>
        <input
          id="signup-email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
      </div>
      <div className="form-field">
        <label htmlFor="signup-nickname">닉네임</label>
        <input
          id="signup-nickname"
          type="text"
          autoComplete="nickname"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          required
          minLength={2}
          maxLength={20}
        />
      </div>
      <div className="form-field">
        <label htmlFor="signup-password">비밀번호</label>
        <input
          id="signup-password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={6}
        />
      </div>
      <button type="submit" disabled={loading} className="primary">
        {loading ? '가입 중...' : '회원가입'}
      </button>
    </form>
  );
}

function translateError(message, mode) {
  if (!message) return mode === 'login' ? '로그인에 실패했습니다.' : '회원가입에 실패했습니다.';
  const lower = message.toLowerCase();
  if (mode === 'login') {
    if (lower.includes('invalid') || lower.includes('credential') || lower.includes('unauthorized')) {
      return '잘못된 이메일 또는 비밀번호입니다.';
    }
  }
  if (mode === 'signup') {
    if (lower.includes('duplicate') || lower.includes('email_duplicate') || lower.includes('already')) {
      return '이미 가입된 이메일입니다.';
    }
  }
  return message;
}
