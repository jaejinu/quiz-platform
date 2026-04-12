import { useState } from 'react';
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
    return <AuthGate onAuth={handleLogin} />;
  }
  return <GameApp jwt={jwt} user={currentUser} onLogout={handleLogout} />;
}
