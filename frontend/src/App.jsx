import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs';

export default function App() {
  const [status, setStatus] = useState('disconnected');
  const [roomId, setRoomId] = useState('1');
  const [userId, setUserId] = useState('42');
  const [nickname, setNickname] = useState('alice');
  const [role, setRole] = useState('PLAYER');
  const [currentQuizId, setCurrentQuizId] = useState('');
  const [answerText, setAnswerText] = useState('');
  const [messages, setMessages] = useState([]);
  const [snapshot, setSnapshot] = useState(null);
  const [errors, setErrors] = useState([]);
  const clientRef = useRef(null);

  useEffect(() => {
    return () => {
      clientRef.current?.deactivate();
    };
  }, []);

  const pushMessage = (label, body) => {
    setMessages((prev) => [...prev, { label, body, at: new Date().toISOString() }]);
  };

  const connect = () => {
    const token = `stub:${userId}:${nickname}:${role}`;
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      debug: (msg) => console.log('[STOMP]', msg),
      onConnect: () => {
        setStatus('connected');
        client.subscribe(`/topic/room/${roomId}`, (frame) => {
          try {
            const evt = JSON.parse(frame.body);
            pushMessage(evt.type, evt);
            if (evt.type === 'QUIZ_PUSHED') setCurrentQuizId(String(evt.payload?.quizId ?? ''));
          } catch {
            pushMessage('raw', frame.body);
          }
        });
        client.subscribe('/user/queue/snapshot', (frame) => {
          setSnapshot(JSON.parse(frame.body));
        });
        client.subscribe('/user/queue/errors', (frame) => {
          const err = JSON.parse(frame.body);
          setErrors((prev) => [...prev, err]);
        });
      },
      onStompError: (frame) => {
        console.error('Broker error', frame.headers['message']);
        setStatus('error');
      },
      onWebSocketClose: () => setStatus('disconnected'),
    });
    client.activate();
    clientRef.current = client;
  };

  const disconnect = () => {
    clientRef.current?.deactivate();
    setStatus('disconnected');
  };

  const publish = (destination, body = {}) => {
    clientRef.current?.publish({ destination, body: JSON.stringify(body) });
  };

  const sendJoin = () => publish(`/app/room/${roomId}/join`);
  const sendStart = () => publish(`/app/room/${roomId}/start`);
  const sendLeave = () => publish(`/app/room/${roomId}/leave`);
  const sendAnswer = () => {
    if (!currentQuizId || !answerText) return;
    publish(`/app/room/${roomId}/answer`, {
      quizId: Number(currentQuizId),
      answer: answerText,
    });
    setAnswerText('');
  };

  // REST 호출 헬퍼
  const createRoom = async () => {
    const res = await fetch('/api/rooms', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: `${nickname}'s room`,
        maxPlayers: 50,
        defaultTimeLimit: 30,
        hostId: Number(userId),
      }),
    });
    const room = await res.json();
    if (room.id) setRoomId(String(room.id));
    alert(`room created: id=${room.id} code=${room.code}`);
  };

  const addSampleQuiz = async () => {
    await fetch(`/api/rooms/${roomId}/quizzes?hostId=${userId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        question: 'What is 2+2?',
        type: 'SINGLE',
        options: ['A. 3', 'B. 4', 'C. 5', 'D. 6'],
        correctAnswer: 'B',
        timeLimit: 20,
      }),
    });
    alert('quiz added');
  };

  return (
    <div className="container">
      <h1>Quiz Platform — Test Client</h1>
      <p>상태: <strong className={`status ${status}`}>{status}</strong></p>

      <div className="row">
        <label>Room ID: <input value={roomId} onChange={(e) => setRoomId(e.target.value)} /></label>
        <label>User ID: <input value={userId} onChange={(e) => setUserId(e.target.value)} /></label>
        <label>Nickname: <input value={nickname} onChange={(e) => setNickname(e.target.value)} /></label>
        <label>
          Role:{' '}
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="PLAYER">PLAYER</option>
            <option value="HOST">HOST</option>
          </select>
        </label>
      </div>

      <h3>REST (HOST 전용)</h3>
      <div className="row">
        <button onClick={createRoom}>POST /api/rooms</button>
        <button onClick={addSampleQuiz}>POST /rooms/{roomId}/quizzes (샘플)</button>
      </div>

      <h3>WebSocket</h3>
      <div className="row">
        <button onClick={connect} disabled={status === 'connected'}>Connect</button>
        <button onClick={disconnect} disabled={status !== 'connected'}>Disconnect</button>
        <button onClick={sendJoin} disabled={status !== 'connected'}>/join</button>
        <button onClick={sendStart} disabled={status !== 'connected'}>/start (HOST)</button>
        <button onClick={sendLeave} disabled={status !== 'connected'}>/leave</button>
      </div>

      <div className="row">
        <label>현재 Quiz ID: <input value={currentQuizId} readOnly /></label>
        <label>Answer: <input value={answerText} onChange={(e) => setAnswerText(e.target.value)} /></label>
        <button onClick={sendAnswer} disabled={status !== 'connected' || !currentQuizId}>
          /answer
        </button>
      </div>

      {snapshot && (
        <>
          <h3>재접속 스냅샷</h3>
          <pre className="messages">{JSON.stringify(snapshot, null, 2)}</pre>
        </>
      )}

      {errors.length > 0 && (
        <>
          <h3>Errors ({errors.length})</h3>
          <ul className="messages">
            {errors.map((e, i) => (
              <li key={i}><code>{JSON.stringify(e)}</code></li>
            ))}
          </ul>
        </>
      )}

      <h3>Events ({messages.length})</h3>
      <ul className="messages">
        {messages.map((m, i) => (
          <li key={i}>
            <strong>[{m.label}]</strong> <code>{JSON.stringify(m.body)}</code>
          </li>
        ))}
      </ul>
    </div>
  );
}
