// src/index.js (수정된 최종 코드)

import React from 'react';
import ReactDOM from 'react-dom/client'; // 또는 'react-dom'
import './index.css'; // 기본 CSS 파일
import App from './App'; // <-- 이 App 컴포넌트만 필요합니다.
// import reportWebVitals from './reportWebVitals'; // (있다면 그대로 두거나 삭제해도 무방)

const root = ReactDOM.createRoot(document.getElementById('root'));

root.render(
  <React.StrictMode>
    <App /> {/* App 컴포넌트만 렌더링 */}
  </React.StrictMode>
);

// 나머지 코드는 그대로 두셔도 됩니다.
