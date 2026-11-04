const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://127.0.0.1:8080';

async function request(path, options = {}, token) {
  let response;
  try {
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
    });
  } catch (error) {
    throw new Error('추천 서버에 연결하지 못했습니다. 127.0.0.1:8080 서버가 실행 중인지 확인해 주세요.');
  }
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const failure = new Error(body && body.message ? body.message : '요청을 처리하지 못했습니다.');
    failure.status = response.status;
    failure.body = body;
    throw failure;
  }
  return body;
}

export function getGrants() {
  return request('/grants');
}

export function getGrant(grantId) {
  return request(`/grants/${encodeURIComponent(grantId)}`);
}

export function getProfile(userId, token) {
  return request(`/users/${encodeURIComponent(userId)}/profile`, {}, token);
}

export function saveProfile(userId, token, profile) {
  return request(`/users/${encodeURIComponent(userId)}/profile`, {
    method: 'PUT',
    body: JSON.stringify(profile),
  }, token);
}

export function getBehaviors(userId, token) {
  return request(`/users/${encodeURIComponent(userId)}/behaviors`, {}, token);
}

export function recordEvent(userId, token, type, grantId) {
  return request(`/users/${encodeURIComponent(userId)}/events`, {
    method: 'POST',
    body: JSON.stringify({ type, grantId }),
  }, token);
}

export function cancelScrap(userId, token, grantId) {
  return request(`/users/${encodeURIComponent(userId)}/events`, {
    method: 'DELETE',
    body: JSON.stringify({ type: 'SCRAP', grantId }),
  }, token);
}

export function getRecommendations(userId, token) {
  return request(`/users/${encodeURIComponent(userId)}/recommendations`, {}, token);
}

export function sendChat(message) {
  return request('/chat', {
    method: 'POST',
    body: JSON.stringify({ message }),
  });
}
