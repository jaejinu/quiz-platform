// STOMP 1.2 frame helpers for k6.

const NULL = '\x00';

export function buildConnect(authToken) {
  return (
    'CONNECT\n' +
    'accept-version:1.2\n' +
    'host:localhost\n' +
    'Authorization:Bearer ' + authToken + '\n' +
    '\n' +
    NULL
  );
}

export function buildSubscribe(id, destination) {
  return (
    'SUBSCRIBE\n' +
    'id:' + id + '\n' +
    'destination:' + destination + '\n' +
    '\n' +
    NULL
  );
}

export function buildSend(destination, body) {
  return (
    'SEND\n' +
    'destination:' + destination + '\n' +
    'content-type:application/json\n' +
    'content-length:' + body.length + '\n' +
    '\n' +
    body +
    NULL
  );
}

export function buildDisconnect() {
  return 'DISCONNECT\n\n' + NULL;
}

export function parseFrame(raw) {
  if (!raw || typeof raw !== 'string') {
    return { command: '', headers: {}, body: '' };
  }

  const nullIdx = raw.indexOf(NULL);
  const trimmed = nullIdx >= 0 ? raw.substring(0, nullIdx) : raw;

  const headerBodySplit = trimmed.indexOf('\n\n');
  if (headerBodySplit < 0) {
    return { command: trimmed.trim(), headers: {}, body: '' };
  }

  const headerBlock = trimmed.substring(0, headerBodySplit);
  const body = trimmed.substring(headerBodySplit + 2);

  const lines = headerBlock.split('\n');
  const command = lines.shift() || '';
  const headers = {};
  for (const line of lines) {
    const colon = line.indexOf(':');
    if (colon < 0) continue;
    const key = line.substring(0, colon).trim();
    const value = line.substring(colon + 1).trim();
    if (!(key in headers)) {
      headers[key] = value;
    }
  }

  return { command: command.trim(), headers, body };
}
