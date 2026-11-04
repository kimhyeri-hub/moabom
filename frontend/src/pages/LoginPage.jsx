import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom'; // 페이지 이동을 위한 훅
import Header from '../components/Header';
import InputField from '../components/InputField';
import PrimaryButton from '../components/PrimaryButton';
import SecondaryButton from '../components/SecondaryButton';
import styles from '../styles/LoginPage.module.css';
import { login } from '../api/authApi';
import { useAuth } from '../auth/AuthContext';

const LoginPage = () => {
  const navigate = useNavigate(); // useNavigate 훅 사용
  const { setSession } = useAuth();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setMessage('');
    if (!loginId.trim() || !password) {
      setMessage('아이디와 비밀번호를 입력해 주세요.');
      return;
    }
    try {
      const session = await login({ loginId: loginId.trim(), password });
      setSession(session.token, session.user);
      navigate('/');
    } catch (error) {
      setMessage(error.message);
    }
  };

  // '모아봄 회원가입' 버튼 클릭 시 /register 경로로 이동
  const handleRegisterClick = () => {
    navigate('/register'); 
  };

  return (
    <div className={styles.container}>
      <Header />
      
      <div className={styles.content}>
        <h1 className={styles.logo}>MOABOM</h1>
        
        <form className={styles.form} onSubmit={handleLoginSubmit}>
          <InputField type="text" placeholder="아이디" value={loginId} onChange={(event) => setLoginId(event.target.value)} />
          <InputField type="password" placeholder="비밀번호" value={password} onChange={(event) => setPassword(event.target.value)} />
          {message && <p className={styles.divider}>{message}</p>}
          
          <PrimaryButton type="submit">로그인하기</PrimaryButton>
        </form>

        <div className={styles.divider}>
          아직 회원이 아니신가요?
        </div>
        
        {/* 줄어든 회원가입 버튼을 중앙 정렬하기 위한 Wrapper */}
        <div className={styles.secondaryButtonWrapper}>
          <SecondaryButton onClick={handleRegisterClick}>
            모아봄 회원가입
          </SecondaryButton>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;