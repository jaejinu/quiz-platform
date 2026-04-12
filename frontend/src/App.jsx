import { useEffect, useState } from 'react';
import AuthGate from './AuthGate';
import GameApp from './GameApp';

export default function App() {
  const [jwt, setJwt] = useState(() => localStorage.getItem('quiz.jwt'));
  const [currentUser, setCurrentUser] = useState(() => {
    const raw = localStorage.getItem('quiz.user');
    try {
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  });
  const [oauthError, setOauthError] = useState(null);

  useEffect(() => {
    if (window.location.pathname !== '/auth/callback') return;

    const hash = window.location.hash.slice(1);
    const params = new URLSearchParams(hash);

    const error = params.get('error');
    if (error) {
      setOauthError(error);
      window.history.replaceState(null, '', '/');
      return;
    }

    const token = params.get('token');
    const userId = params.get('userId');
    const nickname = params.get('nickname');
    const role = params.get('role');

    if (token && userId && nickname && role) {
      const user = { id: Number(userId), nickname, role };
      localStorage.setItem('quiz.jwt', token);
      localStorage.setItem('quiz.user', JSON.stringify(user));
      setJwt(token);
      setCurrentUser(user);
      window.history.replaceState(null, '', '/');
    }
  }, []);

  const handleLogin = (tokenResponse) => {
    localStorage.setItem('quiz.jwt', tokenResponse.accessToken);
    localStorage.setItem('quiz.user', JSON.stringify(tokenResponse.user));
    setJwt(tokenResponse.accessToken);
    setCurrentUser(tokenResponse.user);
  };

  const handleLogout = () => {
    localStorage.removeItem('quiz.jwt');
    localStorage.removeItem('quiz.user');
    setJwt(null);
    setCurrentUser(null);
  };

  if (!jwt || !currentUser) {
    return <AuthGate onAuth={handleLogin} oauthError={oauthError} />;
  }
  return <GameApp jwt={jwt} user={currentUser} onLogout={handleLogout} />;
}
