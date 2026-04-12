import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs';
import { fetchWithAuth } from './lib/api';

export default function GameApp({ jwt, user, onLogout }) {
  const { id: userId, nickname, role } = user;
  const isHost = role === 'HOST';

  const [status, setStatus] = useState('disconnected');
  const [roomId, setRoomId] = useState('1');
  const [currentQuizId, setCurrentQuizId] = useState('');
  const [answerText, setAnswerText] = useState('');
  const [messages, setMessages] = useState([]);
  const [snapshot, setSnapshot] = useState(null);
  const [errors, setErrors] = useState([]);
  const [gameFinished, setGameFinished] = useState(false);
  const [downloading, setDownloading] = useState(false);
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
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${jwt}` },
      reconnectDelay: 5000,
      debug: (msg) => console.log('[STOMP]', msg),
      onConnect: () => {
        setStatus('connected');
        client.subscribe(`/topic/room/${roomId}`, (frame) => {
          try {
            const evt = JSON.parse(frame.body);
            pushMessage(evt.type, evt);
            if (evt.type === 'QUIZ_PUSHED') setCurrentQuizId(String(evt.payload?.quizId ?? ''));
            if (evt.type === 'GAME_FINISHED') setGameFinished(true);
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
      onWebSocketClose: () => {
        setStatus('disconnected');
        setGameFinished(false);
      },
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
  const sendStart = () => {
    publish(`/app/room/${roomId}/start`);
    setGameFinished(false);
  };
  const sendLeave = () => publish(`/app/room/${roomId}/leave`);
  const sendAnswer = () => {
    if (!currentQuizId || !answerText) return;
    publish(`/app/room/${roomId}/answer`, {
      quizId: Number(currentQuizId),
      answer: answerText,
    });
    setAnswerText('');
  };

  const createRoom = async () => {
    try {
      const res = await fetchWithAuth('/api/rooms', {
        method: 'POST',
        body: JSON.stringify({
          title: `${nickname}'s room`,
          maxPlayers: 50,
          defaultTimeLimit: 30,
        }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: 'Failed to create room' }));
        alert(`room 생성 실패: ${err.message || res.status}`);
        return;
      }
      const room = await res.json();
      if (room.id) setRoomId(String(room.id));
      alert(`room created: id=${room.id} code=${room.code}`);
    } catch (e) {
      console.error(e);
    }
  };

  const addSampleQuiz = async () => {
    try {
      const res = await fetchWithAuth(`/api/rooms/${roomId}/quizzes`, {
        method: 'POST',
        body: JSON.stringify({
          question: 'What is 2+2?',
          type: 'SINGLE',
          options: ['A. 3', 'B. 4', 'C. 5', 'D. 6'],
          correctAnswer: 'B',
          timeLimit: 20,
        }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: 'Failed to add quiz' }));
        alert(`quiz 추가 실패: ${err.message || res.status}`);
        return;
      }
      alert('quiz added');
    } catch (e) {
      console.error(e);
    }
  };

  const downloadResultPdf = async () => {
    setDownloading(true);
    try {
      const res = await fetchWithAuth(`/api/rooms/${roomId}/result.pdf`);
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: 'PDF 다운로드 실패' }));
        alert(err.message || `에러 (${res.status})`);
        return;
      }
      const blob = await res.blob();
      const disposition = res.headers.get('content-disposition') || '';
      const match = /filename\*?=(?:UTF-8'')?["]?([^";]+)["]?/i.exec(disposition);
      const filename = match ? decodeURIComponent(match[1]) : `quiz-result-${roomId}.pdf`;

      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (e) {
      alert(`다운로드 실패: ${e.message}`);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="container">
      <header className="header">
        <div>
          <strong>{nickname}</strong>{' '}
          <span className={`role-badge role-${role}`}>{role}</span>{' '}
          <small>(id={userId})</small>
        </div>
        <button onClick={onLogout}>로그아웃</button>
      </header>

      <h1>Quiz Platform — Test Client</h1>
      <p>상태: <strong className={`status ${status}`}>{status}</strong></p>

      <div className="row">
        <label>Room ID: <input value={roomId} onChange={(e) => setRoomId(e.target.value)} /></label>
      </div>

      <h3>REST (HOST 전용)</h3>
      <div className="row">
        <button onClick={createRoom} disabled={!isHost} title={isHost ? '' : 'HOST 전용'}>
          POST /api/rooms
        </button>
        <button onClick={addSampleQuiz} disabled={!isHost} title={isHost ? '' : 'HOST 전용'}>
          POST /rooms/{roomId}/quizzes (샘플)
        </button>
        {!isHost && <small>HOST 계정으로 로그인해야 사용 가능</small>}
      </div>

      <h3>WebSocket</h3>
      <div className="row">
        <button onClick={connect} disabled={status === 'connected'}>Connect</button>
        <button onClick={disconnect} disabled={status !== 'connected'}>Disconnect</button>
        <button onClick={sendJoin} disabled={status !== 'connected'}>/join</button>
        <button onClick={sendStart} disabled={status !== 'connected' || !isHost}>
          /start (HOST)
        </button>
        <button onClick={sendLeave} disabled={status !== 'connected'}>/leave</button>
      </div>

      <div className="row">
        <label>현재 Quiz ID: <input value={currentQuizId} readOnly /></label>
        <label>Answer: <input value={answerText} onChange={(e) => setAnswerText(e.target.value)} /></label>
        <button onClick={sendAnswer} disabled={status !== 'connected' || !currentQuizId}>
          /answer
        </button>
      </div>

      {isHost && gameFinished && (
        <div className="row">
          <button className="primary" onClick={downloadResultPdf} disabled={downloading}>
            {downloading ? '다운로드 중...' : '📄 결과 PDF 다운로드'}
          </button>
        </div>
      )}

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
