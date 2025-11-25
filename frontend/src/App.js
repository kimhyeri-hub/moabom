import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

// 페이지 컴포넌트 임포트
import HomePage from './pages/HomePage';
import DetailPage from './pages/DetailPage';
import ChatPage from './pages/ChatPage'; 
import AlertsPage from './pages/AlertsPage'; // AlertsPage를 임포트합니다.
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';


function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 1. 메인 홈 페이지 */}
        <Route path="/" element={<HomePage />} />
        
        {/* 2. 상세 페이지 */}
        <Route path="/support/details/*" element={<DetailPage />} />
        
        {/* 3. 로그인 및 회원가입 페이지 */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} /> 

        {/* 4. 모아챗 페이지 */}
        <Route path="/moachat" element={<ChatPage />} /> 
        
        {/* ⭐ 5. 알림 페이지 경로 추가: /alerts 경로에 AlertsPage 컴포넌트를 연결합니다. ⭐ */}
        <Route path="/alerts" element={<AlertsPage />} />

        {/* 6. 임시 경로 */}
        <Route path="/mypage" element={<div>마이페이지 화면</div>} />
        
        {/* 7. 일치하는 경로가 없을 경우 홈으로 리다이렉트 (404 역할) */}
        <Route path="*" element={<HomePage />} />
        
      </Routes>
    </BrowserRouter>
  );
}

export default App;