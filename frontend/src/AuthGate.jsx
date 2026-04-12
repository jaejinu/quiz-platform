import { useState } from 'react';
import { login, signup } from './lib/api';

const OAUTH_ERROR_MESSAGES = {
  email_required:
    'GitHub 계정에 public email이 필요합니다. GitHub 설정에서 이메일을 공개해주세요.',
  email_conflict:
    '이미 다른 방식으로 가입된 이메일입니다. 기존 계정으로 로그인해주세요.',
  oauth_failed: 'GitHub 로그인에 실패했습니다. 다시 시도해주세요.',
};

export default function AuthGate({ onAuth, oauthError }) {
  const [tab, setTab] = useState('login');

  const oauthErrorMessage = oauthError
    ? OAUTH_ERROR_MESSAGES[oauthError] || 'GitHub 로그인 중 오류가 발생했습니다.'
    : null;

  return (
    <div className="auth-container">
      <h1>Quiz Platform</h1>
      {oauthErrorMessage && (
        <div className="error-banner oauth-error">{oauthErrorMessage}</div>
      )}
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
        <>
          <LoginForm onSuccess={onAuth} />
          <div className="oauth-divider">또는</div>
          <button
            type="button"
            className="oauth-button github"
            onClick={() => {
              window.location.href = '/oauth2/authorization/github';
            }}
          >
            <svg width="18" height="18" viewBox="0 0 16 16" fill="currentColor">
              <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0 0 16 8c0-4.42-3.58-8-8-8z" />
            </svg>
            GitHub으로 로그인
          </button>
        </>
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
