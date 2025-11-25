import React from 'react';
import { useNavigate } from 'react-router-dom'; // 페이지 이동을 위한 훅
import Header from '../components/Header';
import InputField from '../components/InputField';
import PrimaryButton from '../components/PrimaryButton';
import SecondaryButton from '../components/SecondaryButton';
import styles from '../styles/LoginPage.module.css';

const LoginPage = () => {
  const navigate = useNavigate(); // useNavigate 훅 사용

  const handleLoginSubmit = (e) => {
    e.preventDefault();
    console.log('로그인 시도');
    // 여기에 실제 로그인 로직을 구현합니다.
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
          <InputField type="text" placeholder="아이디" />
          <InputField type="password" placeholder="비밀번호" />
          
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