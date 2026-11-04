import React, { createContext, useContext, useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { currentUser, logout as logoutRequest } from '../api/authApi';

const TOKEN_KEY = 'moabom.token';
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState('');
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const stored = localStorage.getItem(TOKEN_KEY);
    if (!stored) {
      setReady(true);
      return undefined;
    }
    let active = true;
    currentUser(stored)
      .then((body) => {
        if (!active) {
          return;
        }
        setToken(stored);
        setUser(body.user);
      })
      .catch(() => {
        localStorage.removeItem(TOKEN_KEY);
        if (!active) {
          return;
        }
        setToken('');
        setUser(null);
      })
      .finally(() => {
        if (active) {
          setReady(true);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const setSession = (nextToken, nextUser) => {
    localStorage.setItem(TOKEN_KEY, nextToken);
    setToken(nextToken);
    setUser(nextUser);
  };

  const logout = async () => {
    if (token) {
      try {
        await logoutRequest(token);
      } catch (error) {
        // 서버가 잠시 응답하지 않아도 브라우저의 토큰은 삭제합니다.
      }
    }
    localStorage.removeItem(TOKEN_KEY);
    setToken('');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ ready, user, token, setSession, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('인증 상태가 준비되지 않았습니다.');
  }
  return value;
}

export function RequireAuth({ children }) {
  const { ready, user } = useAuth();
  const location = useLocation();
  if (!ready) {
    return <p>로그인 상태를 확인하는 중입니다.</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return children;
}
