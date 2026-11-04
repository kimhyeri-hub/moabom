const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://127.0.0.1:8080';
const AUTH_BASE_URL = process.env.REACT_APP_AUTH_BASE_URL || API_BASE_URL;

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
    response = await fetch(`${AUTH_BASE_URL}${path}`, {
      ...options,
      headers,
    });
  } catch (error) {
    throw new Error('인증 서버에 연결하지 못했습니다. 127.0.0.1:8080 서버가 실행 중인지 확인해 주세요.');
  }
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const failure = new Error(body && body.message ? body.message : '인증 요청을 처리하지 못했습니다.');
    failure.status = response.status;
    throw failure;
  }
  return body;
}

export function signup(account) {
  return request('/auth/signup', {
    method: 'POST',
    body: JSON.stringify(account),
  });
}

export function login(account) {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(account),
  });
}

export function logout(token) {
  return request('/auth/logout', { method: 'POST' }, token);
}

export function currentUser(token) {
  return request('/auth/me', {}, token);
}

export function updateAccount(token, account) {
  return request('/auth/me', {
    method: 'PATCH',
    body: JSON.stringify(account),
  }, token);
}

export function checkLoginId(loginId) {
  return request(`/auth/login-id?loginId=${encodeURIComponent(loginId)}`);
}
