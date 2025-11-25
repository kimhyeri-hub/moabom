import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import styles from '../styles/AlertsPage.module.css';
import detailStyles from '../styles/DetailPage.module.css'; // 헤더 스타일 재사용

const AlertsPage = () => {
  // 알림 목록 데모 데이터
  // read: false (읽지 않음)일 때 빨간 원이 표시됩니다.
  const [alerts, setAlerts] = useState([
    { 
      id: 1, 
      date: '2025-10-21', 
      type: '[공지사항]', 
      title: '지원금에 관련된 제목', 
      content: '관련 혜택 내용 적는 칸입니다.\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~',
      read: false 
    },
    { 
      id: 2, 
      date: '2025-10-20', 
      type: '[정책 변경]', 
      title: '청년 기본소득 자격요건 변경 안내', 
      content: '기존 만 24세 청년 지원 요건이 만 25세로 상향 조정됩니다. 자세한 내용은 공지사항을 확인해 주세요.',
      read: true 
    },
    { 
      id: 3, 
      date: '2025-10-18', 
      type: '[맞춤 알림]', 
      title: '새로운 경기도 지원금이 등록되었습니다.', 
      content: '회원님의 조건에 맞는 새로운 지원금이 MOABOM에 등록되었습니다. 놓치지 말고 확인해 보세요.',
      read: false 
    },
  ]);

  // '전체 읽음' 처리 함수: 모든 알림의 read 상태를 true로 변경
  const handleReadAll = () => {
    setAlerts(alerts.map(alert => ({ ...alert, read: true })));
  };

  // 알림 카드 클릭 처리 함수: 특정 알림의 read 상태를 true로 변경하고 상세 페이지로 이동 (옵션)
  const handleAlertClick = (id) => {
    // 1. 해당 알림을 읽음 처리
    setAlerts(alerts.map(alert => 
      alert.id === id ? { ...alert, read: true } : alert
    ));
    
    // 2. 필요하다면 알림 내용에 따라 상세 페이지로 이동하는 로직 추가
    // 예: navigate(`/support/details/${id}`);
  };

  return (
    <div className={detailStyles.container}>
      
      {/* 1. 상단 헤더 메뉴 (DetailPage의 CSS를 재사용하여 스타일 유지) */}
      <header className={detailStyles.header}>
        <div className={detailStyles.headerWrapper}> 
          
          <Link to="/" className={detailStyles.logo}>MOABOM</Link> 
          
          <nav className={detailStyles.nav}>
            <Link to="/mypage" className={detailStyles.navLink}>마이페이지</Link>
            <Link to="/alerts" className={detailStyles.navLink}>알림</Link>
            <Link to="/moachat" className={detailStyles.navLink}>모아챗</Link>
            <Link to="/login" className={detailStyles.navLink}>로그인/회원가입</Link> 
          </nav>
        </div>
      </header>
      
      {/* 2. 메인 콘텐츠 영역 */}
      <div className={styles.contentWrapper}>
        
        {/* 알림 헤더 및 '전체 읽음' 버튼 */}
        <div className={styles.alertHeader}>
          <h1 className={styles.mainTitle}>알림</h1>
          <button className={styles.readAllButton} onClick={handleReadAll}>
            전체 읽음
          </button>
        </div>
        
        <div className={styles.divider}></div>

        {/* 3. 알림 목록 */}
        <div className={styles.alertList}>
          {alerts.map(alert => (
            <div 
              key={alert.id} 
              // 읽음 상태에 따라 CSS 클래스를 다르게 적용
              className={`${styles.alertCard} ${alert.read ? styles.read : styles.unread}`}
              onClick={() => handleAlertClick(alert.id)} 
            >
              {/* 읽지 않은 경우에만 빨간 원 표시 (unreadDot) */}
              {!alert.read && <div className={styles.unreadDot}></div>}
              
              <div className={styles.date}>{alert.date}</div>
              <div className={styles.title}>
                <span className={styles.type}>{alert.type}</span> {alert.title}
              </div>
              <div className={styles.content}>
                {alert.content.split('\n').map((line, index) => (
                  <p key={index}>{line}</p>
                ))}
              </div>
            </div>
          ))}
        </div>
        
      </div>
    </div>
  );
};

export default AlertsPage;