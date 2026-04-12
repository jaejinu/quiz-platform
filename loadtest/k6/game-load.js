import ws from 'k6/ws';
import http from 'k6/http';
import { check } from 'k6';
import { buildConnect, buildSubscribe, buildSend, parseFrame } from './lib/stomp.js';

const HTTP_BASE_URL = __ENV.HTTP_BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.BASE_URL || 'ws://localhost:8080/ws/websocket';

export const options = {
  stages: [
    { duration: '2m', target: 50 },
    { duration: '3m', target: 500 },
    { duration: '5m', target: 1000 },
    { duration: '5m', target: 1000 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    'ws_connecting{status:success}': ['rate>0.99'],
    checks: ['rate>0.95'],
  },
};

export function setup() {
  const hostId = 1;
  const roomIds = [];

  for (let r = 0; r < 10; r++) {
    const body = JSON.stringify({
      title: 'load-room-' + r,
      maxPlayers: 2000,
      defaultTimeLimit: 30,
      hostId: hostId,
    });
    const res = http.post(HTTP_BASE_URL + '/api/rooms', body, {
      headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'room created': (r) => r.status === 200 || r.status === 201 });
    if (res.status !== 200 && res.status !== 201) {
      continue;
    }
    const room = res.json();
    const roomId = room.id;
    roomIds.push(roomId);

    for (let q = 0; q < 5; q++) {
      const quizBody = JSON.stringify({
        orderIndex: q,
        question: 'Q' + q + ' for room ' + roomId + '?',
        type: 'SHORT',
        options: [],
        correctAnswer: 'A',
        timeLimit: 30,
      });
      const qres = http.post(
        HTTP_BASE_URL + '/api/rooms/' + roomId + '/quizzes?hostId=' + hostId,
        quizBody,
        { headers: { 'Content-Type': 'application/json' } }
      );
      check(qres, { 'quiz created': (r) => r.status === 200 || r.status === 201 });
    }
  }

  return { roomIds: roomIds, hostId: hostId };
}

export default function (data) {
  if (!data.roomIds || data.roomIds.length === 0) {
    return;
  }

  const vu = __VU;
  const userId = 1000 + vu;
  const nickname = 'player' + vu;
  const roomId = data.roomIds[vu % data.roomIds.length];
  const authToken = 'stub:' + userId + ':' + nickname + ':PLAYER';

  const res = ws.connect(WS_URL, null, function (socket) {
    let joined = false;

    socket.on('open', function () {
      socket.send(buildConnect(authToken));
    });

    socket.on('message', function (raw) {
      const frame = parseFrame(raw);

      if (frame.command === 'CONNECTED') {
        socket.send(buildSubscribe('sub-room-' + vu, '/topic/room/' + roomId));
        socket.send(buildSubscribe('sub-errors-' + vu, '/user/queue/errors'));
        socket.send(buildSubscribe('sub-snapshot-' + vu, '/user/queue/snapshot'));
        socket.send(buildSend('/app/room/' + roomId + '/join', '{}'));
        joined = true;
        check(true, { 'stomp connected': () => true });
        return;
      }

      if (frame.command === 'MESSAGE' && frame.body) {
        let payload;
        try {
          payload = JSON.parse(frame.body);
        } catch (_e) {
          return;
        }
        if (payload && payload.type === 'QUIZ_PUSHED') {
          const quizId = payload.payload && payload.payload.quizId;
          if (quizId != null) {
            const body = JSON.stringify({ quizId: quizId, answer: 'A' });
            socket.send(buildSend('/app/room/' + roomId + '/answer', body));
            check(true, { 'answer sent': () => true });
          }
        }
      }
    });

    socket.on('error', function (e) {
      check(false, { 'ws error': () => false });
    });

    socket.setTimeout(function () {
      socket.close();
    }, 30000);
  });

  check(res, { 'ws_connecting{status:success}': (r) => r && r.status === 101 });
}
